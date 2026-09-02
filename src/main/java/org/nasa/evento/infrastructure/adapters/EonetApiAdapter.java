package org.nasa.evento.infrastructure.adapters;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import org.nasa.core.log.Registro;
import org.nasa.evento.domain.EventoNatural;
import org.nasa.evento.domain.exceptions.EventoInvalidoException;
import org.nasa.evento.domain.ports.FonteDeEventosNaturaisPort;
import org.nasa.geo.domain.CaixaDelimitadora;
import org.nasa.geo.domain.Coordenada;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Lê os eventos naturais da EONET v3 da NASA.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO.</b> É a porta de entrada do dado que dá sentido ao sistema.
 * A EONET é <b>aberta</b>: não usa chave de API, só pede um {@code User-Agent} que
 * identifique quem chama.</p>
 *
 * <p><b>O DEFEITO DE 456 KM QUE ESTE ADAPTADOR CORRIGE.</b> A EONET devolve <b>vários</b>
 * pontos de geometria por evento — a trajetória, com uma data por ponto. O legado usava
 * {@code getGeometry().get(0)}, o primeiro, que é onde o evento <b>começou</b>. Medido na
 * resposta real em 02/09/2026, evento {@code EONET_23800} (Tropical Storm Marie, seis
 * pontos):</p>
 * <pre>
 * primeiro ponto  2026-09-01T06:00Z   lat  14.10  lon -108.10   &lt;- o que o legado usava
 * último ponto    2026-09-02T12:00Z   lat  16.80  lon -111.30   &lt;- onde ela está agora
 * distância ............................................ 456 km
 * </pre>
 * <p>Num alerta de raio 100 km isso avisa quem está longe e cala para quem está perto —
 * sem erro nenhum aparecer. <b>Aqui a posição é sempre a do ponto de data MAIS RECENTE.</b></p>
 *
 * <p><b>A SEGUNDA ARMADILHA: a ordem das coordenadas.</b> GeoJSON é
 * <b>{@code [longitude, latitude]}</b>, ao contrário do que quase todo mundo escreve ao
 * falar. Ler na ordem intuitiva põe o evento do outro lado do planeta — e, quando os dois
 * números estão na faixa válida, <b>não dá exceção nenhuma</b>: só um pino no lugar
 * errado do mapa.</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO.</b></p>
 * <ol>
 *   <li><b>Só geometria do tipo {@code Point} vira coordenada.</b> {@code Polygon} é uma
 *       área, e reduzir área a ponto exigiria escolher um centro que a NASA não declarou.
 *       Evento assim entra <b>sem</b> coordenada, e a tela diz por quê — nunca vira
 *       {@code (0,0)}.</li>
 *   <li><b>Um evento torto não derruba o lote.</b> É contado, registrado e pulado. Perder
 *       a sincronização inteira por causa de um evento trocaria um problema pequeno por um
 *       apagão de dados.</li>
 *   <li><b>{@code User-Agent} identificável</b> em toda requisição, como a NASA pede.</li>
 * </ol>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA.</b> Rede fora ⇒ {@link NasaIndisponivelException}
 * (503) e a base local <b>continua válida</b>. Corpo ilegível ⇒
 * {@link RespostaDaNasaIlegivelException} (502), que manda olhar o contrato em vez da
 * rede.</p>
 */
@ApplicationScoped
public class EonetApiAdapter implements FonteDeEventosNaturaisPort {

    private static final Logger LOG = Logger.getLogger(EonetApiAdapter.class);
    private static final String OPERACAO = "sincronizar-eonet";

    @ConfigProperty(name = "nasa.eonet.url",
            defaultValue = "https://eonet.gsfc.nasa.gov/api/v3/events")
    String urlBase;

    @ConfigProperty(name = "nasa.eonet.user-agent",
            defaultValue = "nasa-quarkus/1.0 (projeto academico FIAP)")
    String userAgent;

    @Inject
    ObjectMapper json;

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    @Override
    public List<EventoNatural> buscar(int limite, Integer dias, boolean apenasAtivos,
                                      Optional<CaixaDelimitadora> caixa) {
        StringBuilder url = new StringBuilder(urlBase).append("?limit=").append(Math.max(1, limite));
        if (dias != null && dias > 0) {
            url.append("&days=").append(dias);
        }
        if (apenasAtivos) {
            url.append("&status=open");
        }
        caixa.ifPresent(c -> url.append("&bbox=").append(c.comoParametroEonet()));

        return interpretar(enviar(url.toString()));
    }

    /**
     * Traduz o corpo da EONET em eventos.
     *
     * <p>Costura {@code protected}: é onde estão as duas armadilhas — a escolha da
     * geometria e a ordem das coordenadas — e o teste as exercita com o corpo REAL
     * medido, sem rede e sem depender de haver tempestade no dia do teste.</p>
     */
    protected List<EventoNatural> interpretar(String corpo) {
        JsonNode raiz;
        try {
            raiz = json.readTree(corpo);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new RespostaDaNasaIlegivelException("corpo-nao-e-json", e);
        }

        JsonNode eventos = raiz.path("events");
        if (!eventos.isArray()) {
            throw new RespostaDaNasaIlegivelException("sem o array `events`", null);
        }

        List<EventoNatural> lidos = new ArrayList<>();
        int tortos = 0;
        for (JsonNode e : eventos) {
            try {
                lidos.add(umEvento(e));
            } catch (RuntimeException falha) {
                // Um evento torto NAO derruba o lote: e contado e pulado.
                tortos++;
                LOG.warn(Registro.recusa(OPERACAO, e.path("id").asText("?"),
                        "EVENTO_IGNORADO_" + falha.getClass().getSimpleName()));
            }
        }
        if (tortos > 0) {
            LOG.warn(Registro.de(OPERACAO, "lote",
                    "eventos ignorados por dado invalido: " + tortos + " de " + eventos.size()));
        }
        return lidos;
    }

    private EventoNatural umEvento(JsonNode e) {
        String eonetId = e.path("id").asText(null);
        String titulo = e.path("title").asText(null);

        // A EONET permite varias categorias; a primeira e a principal. Guardamos uma
        // porque e por ela que a estatistica agrupa, e evento com duas categorias e raro.
        String categoria = e.path("categories").isArray() && !e.path("categories").isEmpty()
                ? e.path("categories").get(0).path("id").asText(null)
                : null;

        Instant encerradoEm = instanteOuNulo(e.path("closed").asText(null));

        JsonNode maisRecente = geometriaMaisRecente(e.path("geometry"));
        Instant ocorridoEm = maisRecente == null ? null
                : instanteOuNulo(maisRecente.path("date").asText(null));
        if (ocorridoEm == null) {
            // Sem data nao ha como ordenar nem filtrar por janela: o evento seria
            // invisivel em toda consulta que usa tempo.
            throw new EventoInvalidoException("ocorridoEm", "a NASA nao publicou data de geometria");
        }

        Coordenada posicao = coordenadaDe(maisRecente).orElse(null);
        return EventoNatural.lidoDaNasa(eonetId, titulo, categoria, ocorridoEm, posicao,
                e.toString(), encerradoEm);
    }

    /**
     * A geometria de data MAIS RECENTE — onde o evento está <b>agora</b>.
     *
     * <p>É a correção do defeito de 456 km. O legado usava {@code geometry[0]}, que é
     * onde o evento começou; para uma tempestade em movimento, isso é uma posição de
     * ontem, e o alerta de proximidade decide sobre ela.</p>
     *
     * <p>Não confia na ordem do array: a EONET costuma devolver em ordem cronológica, mas
     * "costuma" não é garantia de contrato — e ordenar aqui custa nada.</p>
     */
    static JsonNode geometriaMaisRecente(JsonNode geometria) {
        if (!geometria.isArray() || geometria.isEmpty()) {
            return null;
        }
        JsonNode melhor = null;
        Instant melhorData = null;
        for (JsonNode g : geometria) {
            Instant data = instanteOuNulo(g.path("date").asText(null));
            if (data == null) {
                continue;
            }
            if (melhorData == null || data.isAfter(melhorData)) {
                melhorData = data;
                melhor = g;
            }
        }
        return melhor;
    }

    /**
     * A coordenada de um ponto de geometria.
     *
     * <p><b>GeoJSON é {@code [longitude, latitude]}</b> — a ordem inversa da intuitiva.
     * Trocar os dois põe o evento do outro lado do planeta, e quando ambos os números
     * estão na faixa válida <b>não há exceção nenhuma</b>: só um pino errado no mapa.</p>
     */
    static Optional<Coordenada> coordenadaDe(JsonNode geometria) {
        if (geometria == null) {
            return Optional.empty();
        }
        // Poligono e uma AREA. Reduzi-la a um ponto exigiria escolher um centro que a
        // NASA nao declarou, e esse centro entraria no alerta como se fosse medido.
        if (!"Point".equalsIgnoreCase(geometria.path("type").asText(""))) {
            return Optional.empty();
        }
        JsonNode c = geometria.path("coordinates");
        if (!c.isArray() || c.size() < 2) {
            return Optional.empty();
        }
        double longitude = c.get(0).asDouble();
        double latitude = c.get(1).asDouble();
        return Coordenada.talvez(latitude, longitude);
    }

    private static Instant instanteOuNulo(String texto) {
        if (texto == null || texto.isBlank() || "null".equals(texto)) {
            return null;
        }
        try {
            return Instant.parse(texto);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    /** O transporte. Costura {@code protected} para o teste substituir sem rede. */
    protected String enviar(String url) {
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(30))
                    .header("Accept", "application/json")
                    // Exigencia da NASA: requisicao anonima e a primeira a ser bloqueada.
                    .header("User-Agent", userAgent)
                    .GET().build();
            HttpResponse<String> resposta = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resposta.statusCode() != 200) {
                throw new NasaIndisponivelException("HTTP " + resposta.statusCode(), null);
            }
            return resposta.body();
        } catch (java.io.IOException e) {
            throw new NasaIndisponivelException(url, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new NasaIndisponivelException(url, e);
        }
    }
}
