package org.nasa.endereco.infrastructure.adapters;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import org.nasa.core.log.Registro;
import org.nasa.core.tempo.Relogio;
import org.nasa.endereco.domain.ports.GeocodificacaoPort;
import org.nasa.geo.domain.Coordenada;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Geocodificação pelo Nominatim (OpenStreetMap) — endereço em texto vira ponto no mapa.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO.</b> É a segunda linha: entra quando o CEP não trouxe
 * coordenada, que é 1 de cada 6 casos medidos. Sem ela, esses endereços ficariam
 * permanentemente fora do alerta de proximidade.</p>
 *
 * <p><b>A POLÍTICA DE USO É REGRA, NÃO RECOMENDAÇÃO.</b> A instância pública do Nominatim
 * aceita <b>uma requisição por segundo</b> e exige {@code User-Agent} identificável. Quem
 * ignora leva bloqueio de IP — e o sintoma chega como "a geocodificação parou de
 * funcionar", dias depois, sem relação aparente com a causa. Por isso o limite está no
 * código, e não na esperança de que ninguém faça um laço.</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO.</b></p>
 * <ol>
 *   <li><b>No máximo uma chamada por segundo</b>, aplicada aqui dentro. O relógio é
 *       injetado, então o teste consegue provar o intervalo sem esperar de verdade.</li>
 *   <li><b>{@code User-Agent} identificável</b> em toda requisição — é exigência da
 *       política, e requisição anônima é a primeira a ser bloqueada.</li>
 *   <li><b>Não encontrar não é falha</b>: devolve {@link Optional#empty()}. Endereço
 *       ambíguo ou inexistente é resultado normal de busca por texto livre.</li>
 *   <li><b>Nunca devolve coordenada de fachada.</b> Vazio é vazio.</li>
 * </ol>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA.</b> Provedor fora ⇒ exceção de indisponibilidade,
 * e o caso de uso salva o endereço <b>sem</b> coordenada, marcado — a tela informa que
 * aquele endereço não entra no alerta de proximidade. Degradar assim é melhor que recusar
 * o cadastro inteiro por causa de um serviço de terceiro.</p>
 */
@ApplicationScoped
public class NominatimGeocodificacaoAdapter implements GeocodificacaoPort {

    private static final Logger LOG = Logger.getLogger(NominatimGeocodificacaoAdapter.class);

    @ConfigProperty(name = "geocodificacao.nominatim.url",
            defaultValue = "https://nominatim.openstreetmap.org")
    String urlBase;

    @ConfigProperty(name = "geocodificacao.nominatim.limite-req-por-segundo", defaultValue = "1")
    int limitePorSegundo;

    @ConfigProperty(name = "geocodificacao.nominatim.user-agent",
            defaultValue = "nasa-quarkus/1.0 (projeto academico FIAP)")
    String userAgent;

    @Inject
    ObjectMapper json;

    @Inject
    Relogio relogio;

    /** Instante da última chamada — é o que sustenta o limite de vazão. */
    private final AtomicReference<Instant> ultimaChamada = new AtomicReference<>(Instant.EPOCH);

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    @Override
    public Optional<Coordenada> geocodificar(String enderecoCompleto) {
        if (enderecoCompleto == null || enderecoCompleto.isBlank()) {
            return Optional.empty();
        }
        respeitarLimiteDeVazao();

        String url = urlBase + "/search?format=jsonv2&limit=1&addressdetails=0&q="
                + URLEncoder.encode(enderecoCompleto, StandardCharsets.UTF_8);
        HttpResponse<String> resposta = enviar(url);

        if (resposta.statusCode() == 429 || resposta.statusCode() == 403) {
            // O sintoma do bloqueio por excesso de uso. Dizer o nome dele no log poupa
            // horas de investigacao depois.
            LOG.warn(Registro.recusa("geocodificar-nominatim", enderecoCompleto,
                    "BLOQUEIO_POR_POLITICA_DE_USO_HTTP_" + resposta.statusCode()));
            throw new ProvedorDeGeocodificacaoIndisponivelException(enderecoCompleto, null);
        }
        if (resposta.statusCode() != 200) {
            throw new ProvedorDeGeocodificacaoIndisponivelException(enderecoCompleto, null);
        }
        return interpretar(enderecoCompleto, resposta.body());
    }

    /**
     * Traduz a resposta em coordenada — ou em ausência.
     *
     * <p>Costura {@code protected}: o teste substitui o transporte e prova a
     * interpretação, inclusive a lista vazia, sem tocar na rede nem gastar a cota.</p>
     */
    protected Optional<Coordenada> interpretar(String consulta, String corpo) {
        try {
            JsonNode raiz = json.readTree(corpo);
            if (!raiz.isArray() || raiz.isEmpty()) {
                LOG.info(Registro.recusa("geocodificar-nominatim", consulta, "SEM_RESULTADO"));
                return Optional.empty();   // endereco ambiguo ou inexistente: normal
            }
            JsonNode primeiro = raiz.get(0);
            // O Nominatim devolve lat/lon como TEXTO.
            return Coordenada.talvez(
                    Double.valueOf(primeiro.path("lat").asText()),
                    Double.valueOf(primeiro.path("lon").asText()));
        } catch (RuntimeException | com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new RespostaDeProvedorIlegivelException("nominatim", consulta, e);
        }
    }

    /**
     * Segura a chamada até o intervalo mínimo ter passado.
     *
     * <p><b>FALHA:</b> interrupção durante a espera restaura a flag e desiste da chamada —
     * nunca a engole. Esperar é preferível a ser bloqueado: o bloqueio dura horas e atinge
     * o IP inteiro, não só esta requisição.</p>
     */
    private void respeitarLimiteDeVazao() {
        if (limitePorSegundo <= 0) {
            return;
        }
        long intervaloMinimoMs = 1000L / limitePorSegundo;
        Instant agora = relogio.agora();
        Instant anterior = ultimaChamada.getAndSet(agora);
        long desdeAUltima = Duration.between(anterior, agora).toMillis();

        if (desdeAUltima >= 0 && desdeAUltima < intervaloMinimoMs) {
            try {
                Thread.sleep(intervaloMinimoMs - desdeAUltima);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new ProvedorDeGeocodificacaoIndisponivelException("espera-de-vazao", e);
            }
        }
    }

    /** O transporte. Costura {@code protected} para o teste substituir sem rede. */
    protected HttpResponse<String> enviar(String url) {
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(10))
                    .header("Accept", "application/json")
                    // Exigencia da politica de uso: requisicao anonima e a primeira a ser
                    // bloqueada.
                    .header("User-Agent", userAgent)
                    .GET().build();
            return http.send(req, HttpResponse.BodyHandlers.ofString());
        } catch (java.io.IOException e) {
            throw new ProvedorDeGeocodificacaoIndisponivelException(url, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ProvedorDeGeocodificacaoIndisponivelException(url, e);
        }
    }
}
