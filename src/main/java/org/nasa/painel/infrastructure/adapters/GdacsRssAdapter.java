package org.nasa.painel.infrastructure.adapters;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import org.nasa.core.log.Registro;
import org.nasa.core.tempo.Relogio;
import org.nasa.geo.domain.Coordenada;
import org.nasa.painel.domain.NivelDeAlerta;
import org.nasa.painel.domain.Noticia;
import org.nasa.painel.domain.exceptions.NoticiarioIndisponivelException;
import org.nasa.painel.domain.ports.FonteDeNoticiasPort;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.StringReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Lê o noticiário de desastres do GDACS (ONU / União Europeia).
 *
 * <p><b>PROPÓSITO DE NEGÓCIO.</b> Alimenta o carrossel da home com o que está acontecendo
 * no mundo agora. O GDACS é aberto e não exige cadastro — ao contrário da fonte do legado,
 * que morreu (medido: {@code HTTP 410} na v1, {@code 403} na v2 sem appname aprovado).</p>
 *
 * <p><b>PROTEÇÃO CONTRA XXE, E ELA NÃO É OPCIONAL.</b> Este adaptador lê <b>XML de origem
 * externa</b>. Um {@code DocumentBuilderFactory} com as opções de fábrica aceita
 * declarações de entidade e referências a arquivos e URLs — o que transforma um feed
 * hostil em leitura de arquivos do servidor, varredura da rede interna, ou uma expansão de
 * entidades que consome toda a memória. As quatro travas abaixo desligam isso, e a
 * principal é {@code FEATURE_SECURE_PROCESSING} somada a
 * {@code disallow-doctype-decl}, que recusa o documento antes de qualquer entidade
 * existir. Não é precaução teórica: é a vulnerabilidade padrão de todo parser de XML
 * em Java que ninguém configurou.</p>
 *
 * <p><b>O FEED É GRANDE, E POR ISSO HÁ CACHE.</b> Medido em 02/09/2026: <b>1 MB e 348
 * itens</b>. A home é pública; buscar 1 MB a cada visita castigaria a VPS e o GDACS. O
 * cache é em memória, com validade curta — notícia de desastre não muda de minuto em
 * minuto, e o custo de mostrá-la cinco minutos atrasada é zero.</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO.</b></p>
 * <ol>
 *   <li><b>Ordena por GRAVIDADE e depois por recência.</b> Num feed de 348 itens, listar
 *       por data esconderia o vermelho de ontem atrás de dez verdes de hoje.</li>
 *   <li><b>O nível vem do campo {@code gdacs:alertlevel}, NUNCA do título.</b> Medido no
 *       feed real em 02/09/2026: o item <i>"<b>Orange</b> flood alert in Nepal"</i> tem
 *       {@code <gdacs:alertlevel>Red</gdacs:alertlevel>} — o GDACS <b>elevou</b> o nível e
 *       não reescreveu o título. Um leitor que confiasse no texto mostraria "laranja" para
 *       o que a fonte já classifica como vermelho, subestimando um evento grave
 *       exatamente quando ele piorou.</li>
 *   <li><b>Item torto é pulado e CONTADO</b>, nunca derruba o feed inteiro.</li>
 *   <li><b>Coordenada ausente é ausente</b>, jamais {@code (0,0)}.</li>
 *   <li><b>Falha ABERTA.</b> Notícia é vitrine: sem ela a home continua inteira.</li>
 * </ol>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA.</b> {@link NoticiarioIndisponivelException}, que o
 * caso de uso captura e transforma em "noticiário indisponível" na tela. Se houver cache
 * válido, ele é servido em vez do erro — dado velho é melhor que nada numa vitrine.</p>
 */
@ApplicationScoped
public class GdacsRssAdapter implements FonteDeNoticiasPort {

    private static final Logger LOG = Logger.getLogger(GdacsRssAdapter.class);
    private static final String OPERACAO = "buscar-noticias";

    @ConfigProperty(name = "noticias.gdacs.url", defaultValue = "https://www.gdacs.org/xml/rss.xml")
    String url;

    @ConfigProperty(name = "noticias.gdacs.user-agent",
            defaultValue = "nasa-quarkus/1.0 (projeto academico FIAP)")
    String userAgent;

    @ConfigProperty(name = "noticias.gdacs.validade-do-cache-minutos", defaultValue = "10")
    int validadeEmMinutos;

    /**
     * O relógio, injetado.
     *
     * <p>A catraca de UTC reprovou a primeira versão desta classe, que usava
     * {@code Instant.now()} direto para carimbar o cache — e estava certa: só o
     * {@code RelogioSistema} lê o relógio do sistema. A validade do cache é estado
     * calculado contra o relógio, e um relógio injetável é o que permite ao teste provar a
     * expiração sem esperar dez minutos.</p>
     */
    @Inject
    Relogio relogio;

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    /** O que foi lido, e quando. Trocado inteiro, nunca alterado no lugar. */
    private final AtomicReference<Cache> cache = new AtomicReference<>(null);

    private record Cache(List<Noticia> noticias, Instant lidoEm) {
    }

    @Override
    public List<Noticia> maisRecentes(int limite) {
        Cache atual = cache.get();
        if (atual != null && atual.lidoEm().isAfter(
                relogio.agora().minus(Duration.ofMinutes(Math.max(1, validadeEmMinutos))))) {
            return recortar(atual.noticias(), limite);
        }

        try {
            List<Noticia> lidas = interpretar(baixar());
            cache.set(new Cache(lidas, relogio.agora()));
            return recortar(lidas, limite);
        } catch (NoticiarioIndisponivelException falha) {
            if (atual != null) {
                // Dado velho e melhor que nada numa vitrine. A alternativa seria a home
                // perder o carrossel toda vez que o GDACS piscasse.
                LOG.warn(Registro.recusa(OPERACAO, "gdacs",
                        "FONTE_FORA_SERVINDO_CACHE_DE_" + atual.lidoEm()));
                return recortar(atual.noticias(), limite);
            }
            throw falha;
        }
    }

    private static List<Noticia> recortar(List<Noticia> todas, int limite) {
        return todas.size() <= limite ? todas : todas.subList(0, Math.max(0, limite));
    }

    /**
     * Traduz o RSS em notícias.
     *
     * <p>Costura {@code protected}: o teste exercita a leitura com o XML real medido, sem
     * rede — inclusive o caso do documento hostil, que precisa ser recusado.</p>
     */
    protected List<Noticia> interpretar(String xml) {
        Document doc = lerComSeguranca(xml);
        NodeList itens = doc.getElementsByTagName("item");

        List<Noticia> noticias = new ArrayList<>();
        int tortos = 0;
        for (int i = 0; i < itens.getLength(); i++) {
            try {
                Noticia n = umaNoticia((Element) itens.item(i));
                if (n != null) {
                    noticias.add(n);
                } else {
                    tortos++;
                }
            } catch (RuntimeException falha) {
                tortos++;
            }
        }
        if (tortos > 0) {
            LOG.info(Registro.de(OPERACAO, "feed",
                    "itens ignorados por dado invalido: " + tortos + " de " + itens.getLength()));
        }

        // GRAVIDADE primeiro, recencia depois. Por data, o vermelho de ontem ficaria
        // escondido atras de dez verdes de hoje — e a home existe para destacar.
        noticias.sort(Comparator
                .comparingInt((Noticia n) -> n.nivel().gravidade()).reversed()
                .thenComparing(Noticia::publicadaEm, Comparator.nullsLast(Comparator.reverseOrder())));
        return List.copyOf(noticias);
    }

    private Noticia umaNoticia(Element item) {
        String titulo = texto(item, "title");
        String link = texto(item, "link");
        if (titulo == null || titulo.isBlank() || link == null || link.isBlank()) {
            // Noticia sem link e um texto que nao leva a lugar nenhum, e quem clica
            // conclui que a tela quebrou.
            return null;
        }
        return new Noticia(
                texto(item, "guid") != null ? texto(item, "guid") : link,
                titulo.strip(),
                link.strip(),
                instante(texto(item, "pubDate")),
                texto(item, "gdacs:eventtype"),
                NivelDeAlerta.de(texto(item, "gdacs:alertlevel")),
                texto(item, "gdacs:country"),
                texto(item, "gdacs:severity"),
                coordenada(texto(item, "geo:lat"), texto(item, "geo:long")));
    }

    private static Coordenada coordenada(String lat, String lon) {
        if (lat == null || lon == null) {
            return null;
        }
        try {
            return Coordenada.talvez(Double.valueOf(lat.strip()), Double.valueOf(lon.strip()))
                    .orElse(null);
        } catch (NumberFormatException fora) {
            // Coordenada ilegivel e AUSENCIA, nunca (0,0).
            return null;
        }
    }

    private static Instant instante(String rfc1123) {
        if (rfc1123 == null || rfc1123.isBlank()) {
            return null;
        }
        try {
            return DateTimeFormatter.RFC_1123_DATE_TIME.parse(rfc1123.strip(), Instant::from);
        } catch (RuntimeException fora) {
            return null;
        }
    }

    private static String texto(Element item, String tag) {
        NodeList n = item.getElementsByTagName(tag);
        if (n.getLength() == 0) {
            return null;
        }
        Node primeiro = n.item(0);
        String conteudo = primeiro.getTextContent();
        return conteudo == null || conteudo.isBlank() ? null : conteudo.strip();
    }

    /**
     * Lê o XML com as travas contra XXE ligadas.
     *
     * <p><b>As quatro linhas abaixo são o que separa um leitor de RSS de uma porta aberta
     * no servidor.</b> Com as opções de fábrica, um feed hostil pode declarar entidades que
     * leem arquivos locais, alcançam a rede interna, ou se expandem até consumir a memória
     * inteira. {@code disallow-doctype-decl} é a mais forte: recusa o documento antes de
     * qualquer entidade existir — e um feed RSS legítimo nunca precisa de DOCTYPE.</p>
     */
    static Document lerComSeguranca(String xml) {
        try {
            String limpo = semPrologoInvalido(xml);
            DocumentBuilderFactory fabrica = DocumentBuilderFactory.newInstance();
            fabrica.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            fabrica.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            fabrica.setFeature("http://xml.org/sax/features/external-general-entities", false);
            fabrica.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            fabrica.setXIncludeAware(false);
            fabrica.setExpandEntityReferences(false);
            fabrica.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            fabrica.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");

            DocumentBuilder leitor = fabrica.newDocumentBuilder();
            return leitor.parse(new InputSource(new StringReader(limpo)));
        } catch (ParserConfigurationException naoConfigurou) {
            // Se as travas nao puderam ser aplicadas, NAO se le o XML. Ler sem elas seria
            // trocar uma vitrine por uma vulnerabilidade.
            throw new NoticiarioIndisponivelException(
                    "nao consegui ligar as protecoes de XML; recusei ler o feed", naoConfigurou);
        } catch (Exception naoLeu) {
            throw new NoticiarioIndisponivelException("feed ilegivel", naoLeu);
        }
    }

    /**
     * Tira o que vier antes do {@code <} inicial.
     *
     * <p><b>DEFEITO REAL, medido em 02/09/2026.</b> O feed do GDACS começa com um
     * <b>BOM de UTF-8</b> ({@code EF BB BF}). O {@code BodyHandlers.ofString()} do Java
     * decodifica esses três bytes como o caractere {@code U+FEFF} no início da String, e o
     * parser de XML recusa o documento com <i>"O conteúdo não é permitido no prólogo"</i> —
     * mensagem que não menciona BOM nenhum e manda procurar erro de sintaxe num XML
     * perfeitamente válido.</p>
     *
     * <p><b>A LIÇÃO É SOBRE O TESTE, não sobre o código.</b> Meu fixture de teste foi
     * escrito à mão e não tinha BOM: ele era <b>mais limpo que a realidade</b>, e por isso
     * passou enquanto a home mostrava "noticiário indisponível". Fixture inventado prova
     * que o código lê o que eu imaginei; só o corpo medido prova que ele lê o que a fonte
     * manda. O caso com BOM entrou no teste junto com esta correção.</p>
     */
    static String semPrologoInvalido(String xml) {
        if (xml == null) {
            return "";
        }
        int inicio = xml.indexOf('<');
        // Sem `<` nenhum, devolve como veio: quem decide que nao e XML e o parser, com a
        // excecao propria — e nao um `return ""` daqui, que viraria "feed vazio".
        return inicio <= 0 ? xml : xml.substring(inicio);
    }

    /** O transporte. Costura {@code protected} para o teste substituir sem rede. */
    protected String baixar() {
        try {
            HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(20))
                    .header("Accept", "application/rss+xml, application/xml, text/xml")
                    .header("User-Agent", userAgent)
                    .GET().build();
            HttpResponse<String> r = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (r.statusCode() != 200) {
                throw new NoticiarioIndisponivelException("HTTP " + r.statusCode(), null);
            }
            return r.body();
        } catch (java.io.IOException e) {
            throw new NoticiarioIndisponivelException(url, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new NoticiarioIndisponivelException(url, e);
        }
    }
}
