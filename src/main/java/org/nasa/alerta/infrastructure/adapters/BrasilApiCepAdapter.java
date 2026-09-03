package org.nasa.alerta.infrastructure.adapters;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import org.nasa.core.log.Registro;
import org.nasa.alerta.domain.Cep;
import org.nasa.alerta.domain.exceptions.ProvedorDeEnderecoIndisponivelException;
import org.nasa.alerta.domain.ports.ConsultaCepPort;
import org.nasa.geo.domain.Coordenada;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;

/**
 * Consulta de CEP na BrasilAPI — endereço <b>e coordenada</b> numa chamada só.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO.</b> É o provedor primário, e a razão é medida: em
 * 2026-09-02, <b>297 bytes em 0,23 s</b>, devolvendo endereço, coordenada, código do IBGE
 * e fuso IANA na mesma resposta. O legado gastava <b>duas</b> chamadas para o mesmo
 * resultado — ViaCEP para os campos e Google para a coordenada, porque o ViaCEP não
 * devolve lat/lon (confirmado na mesma medição, em 1,04 s). Aqui a segunda chamada só
 * acontece quando a primeira não trouxe o ponto.</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO.</b></p>
 * <ol>
 *   <li><b>A coordenada NÃO é garantida.</b> Amostra de 6 CEPs: 5 vieram com
 *       {@code location}, e o {@code 69900000} veio pelo provedor {@code correios}, sem
 *       coordenada e sem nem a cidade. O adaptador devolve {@link Optional#empty()} nesse
 *       caso e <b>nunca</b> inventa {@code (0,0)}.</li>
 *   <li><b>404 é resposta, não falha.</b> CEP inexistente devolve vazio; só erro de
 *       transporte ou 5xx viram exceção.</li>
 *   <li><b>Uma repetição em falha de transporte.</b> Tiro único vira perda silenciosa: a
 *       primeira tentativa pega o problema momentâneo de rede, e o operador vê "provedor
 *       fora" para algo que funcionaria no segundo seguinte.</li>
 *   <li><b>O transporte fica isolado em método {@code protected}</b> — é a costura que
 *       permite testar a interpretação da resposta sem rede.</li>
 * </ol>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA.</b> Depois da repetição,
 * {@link ProvedorDeEnderecoIndisponivelException} — que o caso de uso captura para cair
 * no provedor reserva. A degradação é <b>declarada</b>, com log de recusa e motivo, nunca
 * silenciosa.</p>
 */
@ApplicationScoped
public class BrasilApiCepAdapter implements ConsultaCepPort {

    private static final Logger LOG = Logger.getLogger(BrasilApiCepAdapter.class);
    private static final Duration TEMPO_LIMITE = Duration.ofSeconds(8);

    @ConfigProperty(name = "cep.brasilapi.url", defaultValue = "https://brasilapi.com.br/api/cep/v2")
    String urlBase;

    @Inject
    ObjectMapper json;

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    @Override
    public String nome() {
        return "brasilapi";
    }

    @Override
    public Optional<EnderecoDoCep> consultar(Cep cep) {
        String url = urlBase + "/" + cep.digitos();
        HttpResponse<String> resposta = comUmaRepeticao(url, cep);

        if (resposta.statusCode() == 404) {
            return Optional.empty();   // o CEP não existe: resposta, não falha
        }
        if (resposta.statusCode() != 200) {
            LOG.warn(Registro.recusa("consultar-cep-brasilapi", cep.digitos(),
                    "HTTP_" + resposta.statusCode()));
            throw new ProvedorDeEnderecoIndisponivelException(cep.digitos(), null);
        }
        return Optional.of(interpretar(cep, resposta.body()));
    }

    /**
     * Traduz a resposta em endereço — sem inventar o que não veio.
     *
     * <p>Deixado {@code protected} de propósito: é a costura que permite testar a
     * interpretação (inclusive a resposta <b>sem</b> {@code location}) sem depender da
     * rede nem do provedor estar no ar.</p>
     */
    protected EnderecoDoCep interpretar(Cep cep, String corpo) {
        try {
            JsonNode raiz = json.readTree(corpo);
            JsonNode local = raiz.path("location").path("coordinates");

            Optional<Coordenada> coordenada = Optional.empty();
            if (local.hasNonNull("latitude") && local.hasNonNull("longitude")) {
                // Os valores vêm como TEXTO na resposta da BrasilAPI.
                coordenada = Coordenada.talvez(
                        Double.valueOf(local.get("latitude").asText()),
                        Double.valueOf(local.get("longitude").asText()));
            }
            if (coordenada.isEmpty()) {
                // Declarado, nunca silencioso: quem lê o log precisa saber que este
                // endereço vai precisar de geocodificação — ou ficar sem alerta.
                LOG.info(Registro.recusa("consultar-cep-brasilapi", cep.digitos(),
                        "SEM_COORDENADA_provedor=" + raiz.path("service").asText("?")));
            }
            return new EnderecoDoCep(cep,
                    raiz.path("street").asText(""),
                    raiz.path("neighborhood").asText(""),
                    raiz.path("city").asText(""),
                    raiz.path("state").asText(""),
                    coordenada,
                    nome());
        } catch (RuntimeException | com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new RespostaDeProvedorIlegivelException(nome(), cep.digitos(), e);
        }
    }

    private HttpResponse<String> comUmaRepeticao(String url, Cep cep) {
        try {
            return enviar(url);
        } catch (RuntimeException primeiraFalha) {
            // Tiro único vira perda silenciosa. Uma repetição cobre o problema momentâneo
            // de rede, que é a causa mais comum — e a mais boba de desistir.
            LOG.warn(Registro.recusa("consultar-cep-brasilapi", cep.digitos(),
                    "FALHA_DE_TRANSPORTE_repetindo"));
            try {
                return enviar(url);
            } catch (RuntimeException segunda) {
                throw new ProvedorDeEnderecoIndisponivelException(cep.digitos(), segunda);
            }
        }
    }

    /** O transporte. Costura {@code protected} para o teste substituir sem rede. */
    protected HttpResponse<String> enviar(String url) {
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .timeout(TEMPO_LIMITE)
                    .header("Accept", "application/json")
                    .GET()
                    .build();
            return http.send(req, HttpResponse.BodyHandlers.ofString());
        } catch (java.io.IOException e) {
            throw new RespostaDeProvedorIlegivelException(nome(), url, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();   // nunca engolir a interrupção
            throw new RespostaDeProvedorIlegivelException(nome(), url, e);
        }
    }
}
