package org.nasa.painel.presentation.web;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.ArrayList;
import java.util.List;

/**
 * O sitemap — quais endereços desta vitrine merecem estar num índice de busca.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO.</b> Este projeto é portfólio: ser encontrado é metade da
 * função dele. O {@code robots.txt} diz ao rastreador o que <b>não</b> ver; o sitemap diz o
 * que <b>ver</b>, e é o que faz uma página de documentação interna ser indexada sem depender
 * de alguém ter posto link para ela em algum lugar.</p>
 *
 * <p><b>POR QUE É GERADO, e não um arquivo estático em {@code META-INF/resources}.</b> Duas
 * razões, e as duas já produziram defeito neste projeto:</p>
 * <ol>
 *   <li><b>O endereço absoluto.</b> O sitemap exige URL completa. Num arquivo estático o
 *       domínio ficaria escrito à mão — e o mesmo arquivo serviria desenvolvimento e
 *       produção, anunciando {@code localhost} ao Google ou o domínio real na máquina de
 *       desenvolvimento. Aqui ele vem de {@code nasa.endereco-publico}, que o perfil
 *       {@code %prod} define.</li>
 *   <li><b>Os documentos.</b> São catorze, declarados no catálogo. Num arquivo estático,
 *       cada documento novo exigiria lembrar de acrescentar a linha — e o esquecimento é
 *       <b>silencioso</b>: a página existe, responde 200, e simplesmente nunca é indexada.
 *       Lendo o catálogo, o sitemap não pode divergir dele.</li>
 * </ol>
 *
 * <p><b>INVARIANTES DO DOMÍNIO.</b></p>
 * <ol>
 *   <li><b>Nada que o {@code robots.txt} proíbe entra aqui.</b> Um sitemap que anuncia o que
 *       o robots bloqueia é contradição, e o Google relata como erro. É por isso que
 *       {@code /telemetria} e {@code /api/} não aparecem — e a lista é <b>declarada</b>, não
 *       varrida das rotas, justamente para que incluir algo seja uma decisão.</li>
 *   <li><b>O endereço público nunca termina em barra.</b> Concatenar
 *       {@code "https://x/" + "/desastres"} produziria {@code //desastres}, que é uma URL
 *       diferente para um rastreador — e duas URLs para a mesma página é conteúdo duplicado
 *       relatado como problema.</li>
 * </ol>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA.</b> Não há falha possível aqui: não há entrada do
 * usuário, não há acesso a rede e o catálogo é constante. A leitura do Markdown que o
 * catálogo faz <b>não</b> é chamada — só os slugs, que são literais.</p>
 */
@Path("/sitemap.xml")
public class SitemapResource {

    /**
     * As telas públicas, com a prioridade relativa entre elas.
     *
     * <p><b>É DECLARADA, e não varrida das rotas JAX-RS.</b> Varredura incluiria
     * {@code /saude}, {@code /api/} e a telemetria — exatamente o que não deve ser
     * indexado — e a decisão de expor uma tela passaria a ser o efeito colateral de
     * criá-la.</p>
     *
     * <p>A prioridade não é uma ordem ao Google; é uma dica sobre a importância relativa
     * <b>dentro deste site</b>. A home é 1.0; as telas que o menu oferece, 0.8; a
     * documentação, 0.6 — ela é profunda e numerosa, e dar 1.0 a catorze páginas diria que
     * nada é mais importante que nada.</p>
     */
    private static final List<String[]> TELAS = List.of(
            new String[] { "/", "1.0" },
            new String[] { "/desastres", "0.8" },
            new String[] { "/desastres/mapa", "0.8" },
            new String[] { "/desastres/historico", "0.7" },
            new String[] { "/alertas", "0.9" },
            new String[] { "/contato", "0.5" },
            new String[] { "/documentacao", "0.7" });

    @Inject
    DocumentacaoCatalogo catalogo;

    @ConfigProperty(name = "nasa.endereco-publico", defaultValue = "http://localhost:8080")
    String enderecoPublico;

    @GET
    @Produces("application/xml; charset=UTF-8")
    public Response sitemap() {
        // Sem a barra final: concatenar `https://x/` com `/desastres` daria `//desastres`,
        // que para um rastreador e outra URL — e duas URLs para a mesma pagina e conteudo
        // duplicado relatado como problema.
        String base = enderecoPublico.endsWith("/")
                ? enderecoPublico.substring(0, enderecoPublico.length() - 1)
                : enderecoPublico;

        var linhas = new ArrayList<String>();
        linhas.add("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        linhas.add("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">");

        for (String[] tela : TELAS) {
            linhas.add(entrada(base + tela[0], tela[1]));
        }
        for (var doc : DocumentacaoCatalogo.DOCUMENTOS) {
            linhas.add(entrada(base + "/documentacao/" + doc.slug(), "0.6"));
        }

        linhas.add("</urlset>");
        return Response.ok(String.join("\n", linhas)).build();
    }

    /**
     * Uma entrada.
     *
     * <p><b>Sem {@code <lastmod>}, de propósito.</b> A data de última modificação só ajuda
     * se for verdadeira: um {@code lastmod} igual a "hoje" em toda página, gerado do relógio
     * a cada requisição, ensina o rastreador a ignorar o campo — e ele passa a ignorá-lo
     * também quando a data for real. A data honesta aqui seria a do commit que mudou o
     * arquivo, e ela não está disponível em tempo de execução.</p>
     *
     * <p>Também sem {@code <changefreq>}: o Google
     * <a href="https://developers.google.com/search/blog/2023/06/sitemaps-lastmod-ping">declarou
     * publicamente</a> que ignora o campo. Escrever o que se sabe ignorado é ruído.</p>
     */
    private static String entrada(String url, String prioridade) {
        // Os slugs e caminhos sao literais do codigo: nao ha entrada de usuario nesta
        // string, e por isso nao ha escape a fazer. Se um dia algum vier de fora, ESTE e o
        // ponto onde o escape de XML tem de entrar.
        return "  <url><loc>" + url + "</loc><priority>" + prioridade + "</priority></url>";
    }
}
