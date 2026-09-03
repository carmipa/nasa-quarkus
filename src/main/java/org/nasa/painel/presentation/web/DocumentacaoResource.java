package org.nasa.painel.presentation.web;

import io.quarkus.qute.Location;
import io.quarkus.qute.Template;
import io.quarkus.qute.TemplateInstance;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.nasa.core.presentation.web.MolduraDaPagina;
import org.nasa.core.texto.MarkdownSeguro;
import org.nasa.painel.domain.exceptions.SecaoDeDocumentoInexistenteException;

/**
 * A documentação do sistema, renderizada no servidor.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO.</b> Este projeto é vitrine, e a documentação é parte do que
 * ele mostra. Ela vive em arquivos Markdown versionados junto com o código — não num
 * documento à parte que envelhece sozinho — e é servida como página, com índice, para
 * quem chega sem contexto.</p>
 *
 * <p><b>É PÚBLICA</b>, junto com a home, o contato e a equipe. Documentação atrás de login
 * não serve à vitrine, e não há nada aqui que exija segredo: o que se descreve é o que o
 * código-fonte já mostra.</p>
 *
 * <p><b>TRÊS DECISÕES QUE VALEM SER DITAS.</b></p>
 * <ol>
 *   <li><b>Markdown convertido no SERVIDOR</b>, com o HTML embutido escapado. A
 *       alternativa comum — mandar o texto cru e converter no navegador — poria a
 *       interpretação do lado de quem lê, e um documento contendo {@code <script>}
 *       passaria a ser script executando na página.</li>
 *   <li><b>Cada documento tem URL própria</b>, e não é uma aba de JavaScript. Voltar,
 *       compartilhar, imprimir e abrir em nova janela funcionam porque são páginas de
 *       verdade.</li>
 *   <li><b>O Markdown cru também é servido</b>, em {@code /documentacao/{slug}.md}. Quem
 *       quiser ler no editor, versionar ou converter em PDF não precisa raspar HTML.</li>
 * </ol>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA.</b> Slug desconhecido é 404. Documento declarado
 * cujo arquivo sumiu também é 404, <b>e fica registrado em WARN</b> — uma página de
 * documentação com um item a menos é indistinguível de uma correta, e o silêncio aqui
 * duraria meses.</p>
 */
@Path("/documentacao")
public class DocumentacaoResource {

    @Inject
    MolduraDaPagina moldura;

    @Inject
    DocumentacaoCatalogo catalogo;

    @Inject
    MarkdownSeguro marcacao;

    @Inject
    @Location("paginas/documentacao/indice.html")
    Template telaIndice;

    @Inject
    @Location("paginas/documentacao/documento.html")
    Template telaDocumento;

    /** O índice — por onde se começa. */
    @GET
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance indice() {
        return moldura.vestir(telaIndice
                .data("secoes", DocumentacaoCatalogo.SECOES)
                .data("catalogo", catalogo)
                .data("quantos", DocumentacaoCatalogo.DOCUMENTOS.size()), "documentacao");
    }

    /**
     * O Markdown cru, para quem quiser ler fora do navegador.
     *
     * <p>Vem antes da rota de leitura de propósito: {@code /documentacao/visao-geral.md}
     * precisa cair aqui, e não ser interpretado como um slug chamado
     * {@code visao-geral.md}.</p>
     */
    @GET
    @Path("/{slug}.md")
    @Produces("text/markdown; charset=UTF-8")
    public Response markdown(@PathParam("slug") String slug) {
        String md = catalogo.markdownDe(slug)
                .orElseThrow(() -> new NotFoundException("documento nao encontrado"));
        return Response.ok(md).build();
    }

    /** Um documento. */
    @GET
    @Path("/{slug}")
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance documento(@PathParam("slug") String slug) {
        var doc = catalogo.porSlug(slug)
                .orElseThrow(() -> new NotFoundException("documento nao encontrado"));
        String markdown = catalogo.markdownDe(slug)
                .orElseThrow(() -> new NotFoundException("arquivo do documento ausente"));

        var todos = DocumentacaoCatalogo.DOCUMENTOS;
        int posicao = todos.indexOf(doc);

        // A SECAO E OBRIGATORIA NA TELA: a trilha e o titulo colorido dependem dela, e
        // o Qute e ESTRITO — chave ausente e 500, nao espaco em branco. Documento cuja
        // secao nao existe no catalogo e erro de catalogo, nao de quem navegou; ele para
        // aqui, com o nome do slug, em vez de virar uma pagina meio desenhada.
        var secao = catalogo.secaoDe(doc).orElseThrow(
                () -> new SecaoDeDocumentoInexistenteException(slug, doc.secao()));

        return moldura.vestir(telaDocumento
                .data("doc", doc)
                .data("secao", secao)
                // Contado do arquivo, nao declarado: numero escrito a mao envelhece
                // calado quando o texto cresce.
                .data("minutos", catalogo.minutosDe(slug))
                .data("html", marcacao.paraHtml(markdown))
                .data("secoes", DocumentacaoCatalogo.SECOES)
                .data("catalogo", catalogo)
                // Anterior e proximo pela ordem DECLARADA: a documentacao tem uma ordem de
                // leitura, e quem esta lendo em sequencia nao devia ter de voltar ao indice.
                .data("anterior", posicao > 0 ? todos.get(posicao - 1) : null)
                .data("proximo", posicao < todos.size() - 1 ? todos.get(posicao + 1) : null),
                "documentacao");
    }
}
