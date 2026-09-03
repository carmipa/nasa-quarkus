package org.nasa.inscrito.infrastructure.adapters;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import org.nasa.core.log.Registro;
import org.nasa.inscrito.domain.Cep;
import org.nasa.inscrito.domain.exceptions.ProvedorDeEnderecoIndisponivelException;
import org.nasa.inscrito.domain.ports.ConsultaCepPort;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Optional;

/**
 * Consulta de CEP no ViaCEP — o provedor <b>reserva</b>.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO.</b> Existe para o dia em que a BrasilAPI estiver fora. Dois
 * provedores independentes é o que separa "o cadastro de endereço parou" de "o cadastro
 * ficou um pouco mais lento" — e o custo de ter o segundo é este arquivo.</p>
 *
 * <p><b>POR QUE É RESERVA, E NÃO O PRIMÁRIO.</b> Medido em 2026-09-02: <b>1,04 s</b>
 * contra 0,23 s da BrasilAPI, e — o que decide — <b>não devolve coordenada</b>. Era
 * exatamente por isso que o legado precisava de uma segunda chamada ao Google para cada
 * endereço cadastrado.</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO.</b></p>
 * <ol>
 *   <li><b>A resposta de CEP inexistente é 200 com {@code "erro": true}</b> — não 404.
 *       É a armadilha específica deste provedor: quem confere só o status HTTP lê o corpo
 *       de erro como se fosse endereço, e grava um registro com todos os campos vazios.</li>
 *   <li><b>Nunca devolve coordenada</b>, porque o provedor não tem. O resultado sai com
 *       {@link Optional#empty()}, e a geocodificação entra depois.</li>
 * </ol>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA.</b>
 * {@link ProvedorDeEnderecoIndisponivelException} — e, como este é o último da fila, ela
 * chega ao operador como 503 "tente de novo", nunca como 404 "este CEP não existe".</p>
 */
@ApplicationScoped
public class ViaCepAdapter implements ConsultaCepPort {

    private static final Logger LOG = Logger.getLogger(ViaCepAdapter.class);

    @ConfigProperty(name = "cep.viacep.url", defaultValue = "https://viacep.com.br/ws")
    String urlBase;

    @Inject
    ObjectMapper json;

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    @Override
    public String nome() {
        return "viacep";
    }

    @Override
    public Optional<EnderecoDoCep> consultar(Cep cep) {
        HttpResponse<String> resposta = enviar(urlBase + "/" + cep.digitos() + "/json/");
        if (resposta.statusCode() != 200) {
            LOG.warn(Registro.recusa("consultar-cep-viacep", cep.digitos(),
                    "HTTP_" + resposta.statusCode()));
            throw new ProvedorDeEnderecoIndisponivelException(cep.digitos(), null);
        }
        return interpretar(cep, resposta.body());
    }

    /**
     * Traduz a resposta.
     *
     * <p><b>A ARMADILHA:</b> CEP inexistente volta como <b>HTTP 200</b> com
     * {@code {"erro": true}}. Confiar só no status faria o corpo de erro virar um endereço
     * com todos os campos vazios — que passaria pela validação de "veio resposta" e seria
     * gravado.</p>
     */
    protected Optional<EnderecoDoCep> interpretar(Cep cep, String corpo) {
        try {
            JsonNode raiz = json.readTree(corpo);
            if (raiz.path("erro").asBoolean(false)
                    || "true".equalsIgnoreCase(raiz.path("erro").asText(""))) {
                return Optional.empty();
            }
            return Optional.of(new EnderecoDoCep(cep,
                    raiz.path("logradouro").asText(""),
                    raiz.path("bairro").asText(""),
                    raiz.path("localidade").asText(""),
                    raiz.path("uf").asText(""),
                    Optional.empty(),   // o ViaCEP nao devolve coordenada. Nunca.
                    nome()));
        } catch (RuntimeException | com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new RespostaDeProvedorIlegivelException(nome(), cep.digitos(), e);
        }
    }

    /** O transporte, isolado para o teste substituir sem rede. */
    protected HttpResponse<String> enviar(String url) {
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(8))
                    .header("Accept", "application/json")
                    .GET().build();
            return http.send(req, HttpResponse.BodyHandlers.ofString());
        } catch (java.io.IOException e) {
            throw new ProvedorDeEnderecoIndisponivelException(url, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ProvedorDeEnderecoIndisponivelException(url, e);
        }
    }
}
