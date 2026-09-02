package org.nasa.painel.presentation.web;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.CacheControl;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;
import org.nasa.core.log.Registro;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Serve as imagens do noticiário <b>pelo nosso servidor</b>, com cache e trava de destino.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO.</b> O carrossel da home mostra a imagem de cada evento. Pôr
 * a URL do GDACS direto no {@code <img>} pareceria mais simples e tem <b>dois problemas
 * medidos</b>:</p>
 * <ol>
 *   <li><b>O gdacs.org limita vazão.</b> Medido em 02/09/2026: a mesma URL responde
 *       {@code HTTP 200} isolada e <b>falha na conexão</b> quando pedida três vezes em
 *       sequência. Doze imagens por visita quebrariam — e cada visitante veria um conjunto
 *       diferente de imagens quebradas, o que é pior que não ter imagem nenhuma.</li>
 *   <li><b>Privacidade.</b> Um {@code <img>} apontando para fora faz o navegador de <b>cada
 *       visitante</b> abrir conexão com o gdacs.org, entregando o IP dele a um terceiro que
 *       ninguém escolheu contatar. Passando por aqui, quem fala com o GDACS somos nós, uma
 *       vez, e o resultado serve a todos.</li>
 * </ol>
 *
 * <p><b>ISTO É UM PROXY, E TODO PROXY É UM CONVITE A SSRF.</b> Um endpoint que busca a URL
 * que lhe mandarem é a porta para o servidor varrer a rede interna, ler
 * {@code http://169.254.169.254/} de metadados de nuvem, ou alcançar serviços que só
 * existem atrás do firewall. As travas abaixo não são precaução teórica — são o que separa
 * este recurso de uma vulnerabilidade:</p>
 * <ol>
 *   <li><b>Esquema {@code https} apenas.</b> {@code file://}, {@code gopher://} e amigos
 *       ficam de fora por construção.</li>
 *   <li><b>Host EXATAMENTE igual</b> a {@value #HOST_PERMITIDO}. Não {@code endsWith}:
 *       {@code gdacs.org.dominio-do-atacante.com} termina em {@code gdacs.org} e passaria
 *       por uma verificação de sufixo mal escrita.</li>
 *   <li><b>Sem {@code usuario:senha@} na URL</b>, que é como se disfarça o host verdadeiro
 *       em alguns analisadores.</li>
 *   <li><b>Não segue redirecionamento.</b> Sem isto, o gdacs.org — ou quem conseguisse
 *       responder por ele — mandaria o nosso servidor a qualquer outro lugar, e todas as
 *       travas acima valeriam só para o primeiro salto.</li>
 *   <li><b>A resposta tem de ser {@code image/*}</b> e caber no teto de tamanho. Um proxy
 *       que devolve o que vier é um repassador de conteúdo arbitrário sob o nosso domínio.</li>
 * </ol>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA.</b> Qualquer recusa devolve <b>404</b>, e nunca a
 * razão detalhada: dizer "host não permitido" ensina o formato do filtro a quem está
 * testando. O motivo real fica no log. Imagem que não carrega degrada a vitrine e não
 * quebra a página — o {@code <img>} tem texto alternativo.</p>
 */
@Path("/midia/noticiario")
@ApplicationScoped
public class MidiaDoNoticiarioResource {

    private static final Logger LOG = Logger.getLogger(MidiaDoNoticiarioResource.class);
    private static final String OPERACAO = "servir-midia";

    /** O ÚNICO destino permitido. Comparação exata, nunca por sufixo. */
    static final String HOST_PERMITIDO = "www.gdacs.org";

    /** Teto por imagem. As medidas reais ficam abaixo de 100 KB; 2 MB é folga larga. */
    private static final int TAMANHO_MAXIMO = 2 * 1024 * 1024;

    /**
     * Teto de imagens guardadas.
     *
     * <p>Sem ele, um visitante pedindo URLs diferentes encheria a memória — e o limite de
     * vazão do GDACS não impediria, porque o pedido chega <b>a nós</b> primeiro.</p>
     */
    private static final int MAXIMO_EM_CACHE = 200;

    private final Map<String, Imagem> cache = new ConcurrentHashMap<>();

    private record Imagem(byte[] bytes, String tipo) {
    }

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            // NUNCA segue redirecionamento: sem isto, todas as travas de destino valeriam
            // apenas para o primeiro salto.
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    @GET
    @Produces({ "image/png", "image/jpeg", "image/gif", "image/webp", MediaType.WILDCARD })
    public Response servir(@QueryParam("u") String url) {
        URI destino = validar(url);
        if (destino == null) {
            // 404, e nunca o motivo: dizer "host nao permitido" ensina o formato do filtro.
            return Response.status(Response.Status.NOT_FOUND).build();
        }

        Imagem guardada = cache.get(destino.toString());
        if (guardada != null) {
            return comCache(guardada);
        }

        try {
            HttpRequest req = HttpRequest.newBuilder(destino)
                    .timeout(Duration.ofSeconds(15))
                    .header("Accept", "image/*")
                    .header("User-Agent", "nasa-quarkus/1.0 (projeto academico FIAP)")
                    .GET().build();
            HttpResponse<byte[]> r = http.send(req, HttpResponse.BodyHandlers.ofByteArray());

            String tipo = r.headers().firstValue("content-type").orElse("");
            if (r.statusCode() != 200 || !tipo.toLowerCase(Locale.ROOT).startsWith("image/")) {
                LOG.info(Registro.recusa(OPERACAO, destino.getPath(),
                        "RESPOSTA_NAO_E_IMAGEM_HTTP_" + r.statusCode()));
                return Response.status(Response.Status.NOT_FOUND).build();
            }
            if (r.body().length > TAMANHO_MAXIMO) {
                LOG.info(Registro.recusa(OPERACAO, destino.getPath(), "IMAGEM_GRANDE_DEMAIS"));
                return Response.status(Response.Status.NOT_FOUND).build();
            }

            Imagem imagem = new Imagem(r.body(), tipo);
            if (cache.size() < MAXIMO_EM_CACHE) {
                cache.put(destino.toString(), imagem);
            }
            return comCache(imagem);

        } catch (java.io.IOException naoBaixou) {
            // O limite de vazao do GDACS aparece aqui. Uma imagem faltando degrada a
            // vitrine; nao derruba nada.
            LOG.info(Registro.recusa(OPERACAO, destino.getPath(), "NAO_BAIXOU"));
            return Response.status(Response.Status.NOT_FOUND).build();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Response.status(Response.Status.NOT_FOUND).build();
        }
    }

    private static Response comCache(Imagem imagem) {
        // Uma hora no navegador: a imagem de um evento nao muda, e sem isto cada rolagem
        // do carrossel voltaria ao servidor.
        CacheControl cc = new CacheControl();
        cc.setMaxAge(3600);
        cc.setPrivate(false);
        return Response.ok(imagem.bytes(), imagem.tipo()).cacheControl(cc).build();
    }

    /**
     * Devolve o destino se — e somente se — ele passar por TODAS as travas.
     *
     * <p>Costura {@code static} de propósito: é a parte que decide segurança, e o teste a
     * exercita com as formas de ataque conhecidas, sem rede.</p>
     *
     * @return o destino aprovado, ou {@code null} — nunca uma exceção com o motivo, que
     *         acabaria vazando para quem está testando o filtro
     */
    static URI validar(String url) {
        if (url == null || url.isBlank() || url.length() > 500) {
            return null;
        }
        URI uri;
        try {
            uri = new URI(url);
        } catch (URISyntaxException naoEhUri) {
            return null;
        }
        if (!"https".equalsIgnoreCase(uri.getScheme())) {
            return null;   // fora file://, http://, gopher://, e o resto
        }
        // `usuario:senha@host` e como se disfarca o host verdadeiro em alguns analisadores.
        if (uri.getUserInfo() != null) {
            return null;
        }
        String host = uri.getHost();
        if (host == null) {
            return null;
        }
        // IGUALDADE EXATA. Um `endsWith(".gdacs.org")` aprovaria
        // `gdacs.org.dominio-do-atacante.com`, que e o engano classico deste filtro.
        if (!HOST_PERMITIDO.equalsIgnoreCase(host)) {
            return null;
        }
        if (uri.getPort() != -1 && uri.getPort() != 443) {
            return null;
        }
        return uri;
    }
}
