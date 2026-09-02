package org.nasa.painel;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nasa.core.texto.MarkdownSeguro;
import org.nasa.painel.presentation.web.DocumentacaoCatalogo;

import java.util.List;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Prova da documentação — que ela EXISTE, que RENDERIZA, e que não executa nada.
 *
 * <p><b>PROPÓSITO.</b> Documentação quebra em silêncio. Um documento declarado no catálogo
 * cujo arquivo alguém renomeou some da página, e uma página de documentação com um item a
 * menos é <b>indistinguível de uma correta</b> — ninguém conta os itens. Esta guarda conta.</p>
 *
 * <p><b>E ela prova três coisas diferentes</b>, porque são três riscos diferentes:
 * o documento sumir, a página não renderizar, e o conteúdo virar script executando.</p>
 */
@QuarkusTest
@DisplayName("documentacao — existe, renderiza, e nao executa nada")
class DocumentacaoTest {

    @Inject
    DocumentacaoCatalogo catalogo;

    @Inject
    MarkdownSeguro marcacao;

    @Test
    @DisplayName("TODO documento declarado tem arquivo no disco")
    void todoDocumentoDeclaradoExiste() {
        // Sem este teste, renomear um `.md` faz o documento sumir da pagina sem erro
        // nenhum — e ninguem conta os itens de um indice.
        List<String> faltando = catalogo.declaradosSemArquivo();
        assertTrue(faltando.isEmpty(),
                "documentos declarados no catalogo SEM arquivo no disco: " + faltando);
    }

    @Test
    @DisplayName("CONTROLE POSITIVO: o catalogo NAO esta vazio")
    void oCatalogoNaoEstaVazio() {
        // Sem isto, o teste acima passaria folgado num catalogo vazio — que e exatamente
        // o estado em que ele nao prova nada.
        assertTrue(DocumentacaoCatalogo.DOCUMENTOS.size() >= 10,
                "o catalogo tem " + DocumentacaoCatalogo.DOCUMENTOS.size()
                        + " documentos: o teste acima esta julgando quase nada");
        assertFalse(DocumentacaoCatalogo.SECOES.isEmpty());
    }

    @Test
    @DisplayName("toda pagina de documento RENDERIZA — 200 e HTML de verdade")
    void todaPaginaRenderiza() {
        for (var doc : DocumentacaoCatalogo.DOCUMENTOS) {
            var r = given().when().get("/documentacao/" + doc.slug());
            assertEquals(200, r.statusCode(),
                    "o documento " + doc.slug() + " respondeu " + r.statusCode());
            String corpo = r.asString();
            // Um documento que renderiza vazio passa num teste de status. Este exige
            // que o Markdown tenha virado HTML de verdade.
            assertTrue(corpo.contains("<h2") || corpo.contains("<p>"),
                    "o documento " + doc.slug() + " renderizou sem conteudo");
        }
    }

    @Test
    @DisplayName("o Markdown CRU tambem e servido, para ler fora do navegador")
    void markdownCruEhServido() {
        var r = given().when().get("/documentacao/visao-geral.md");
        assertEquals(200, r.statusCode());
        String md = r.asString();
        assertTrue(md.startsWith("#"),
                "nao parece Markdown: " + md.substring(0, Math.min(40, md.length())));
    }

    @Test
    @DisplayName("slug desconhecido e 404, nao pagina em branco")
    void slugDesconhecidoEh404() {
        assertEquals(404, given().when().get("/documentacao/nao-existe").statusCode());
        assertEquals(404, given().when().get("/documentacao/nao-existe.md").statusCode());
    }

    // ============================================================== SEGURANÇA

    @Test
    @DisplayName("CONTROLE POSITIVO: HTML embutido no Markdown vira TEXTO, nao tag ativa")
    void htmlEmbutidoEhEscapado() {
        // Se este teste falhar, um documento contendo <script> passa a ser SCRIPT
        // EXECUTANDO na pagina — e nao texto sobre script.
        String html = marcacao.paraHtml(
                "Exemplo: <script>alert('xss')</script> e <img src=x onerror=alert(1)>");

        // A asercao precisa julgar se a TAG existe, nao se o TEXTO aparece. A primeira
        // versao deste teste procurava "onerror=alert" — que aparece tambem no texto
        // escapado, porque escapar troca `<` e `>`, nao o miolo. A asercao estava
        // imprecisa; o escape sempre esteve certo.
        assertFalse(html.contains("<script"), "a tag script sobreviveu: " + html);
        assertFalse(html.contains("<img"), "a tag img sobreviveu: " + html);
        // E ela precisa APARECER como texto — escapar nao pode significar sumir, senao
        // documentacao sobre HTML ficaria impossivel de escrever.
        assertTrue(html.contains("&lt;script&gt;"), "o texto deveria aparecer escapado: " + html);
    }

    @Test
    @DisplayName("CONTROLE POSITIVO: link `javascript:` nao vira link executavel")
    void urlPerigosaEhNeutralizada() {
        // E o vetor que sobra quando so se escapa tag: o link e legitimo em Markdown, e
        // o esquema e que e o problema.
        String html = marcacao.paraHtml("[clique](javascript:alert('xss'))");
        assertFalse(html.contains("href=\"javascript:"),
                "o link javascript: sobreviveu: " + html);
    }

    @Test
    @DisplayName("CONTROLE POSITIVO: o Markdown NORMAL continua funcionando")
    void markdownNormalFunciona() {
        // Sem este caso, um renderizador que escapasse TUDO — inclusive a propria
        // marcacao — passaria nos dois testes de seguranca acima e seria inutil.
        String html = marcacao.paraHtml("# Titulo\n\nUm **negrito** e uma `crase`.\n\n"
                + "| a | b |\n|---|---|\n| 1 | 2 |");
        assertTrue(html.contains("<h1>"), "titulo nao virou h1");
        assertTrue(html.contains("<strong>"), "negrito nao virou strong");
        assertTrue(html.contains("<code>"), "crase nao virou code");
        assertTrue(html.contains("<table>"), "tabela nao virou table — a extensao esta ligada?");
    }

    @Test
    @DisplayName("travessia de caminho nao le arquivo de fora da pasta")
    void travessiaDeCaminhoNaoFunciona() {
        // O slug vem do catalogo hoje, mas a trava existe para o dia em que alguem
        // passar a aceitar o nome de fora.
        for (String mau : new String[] { "../../build.gradle", "..%2F..%2Fbuild.gradle",
                "....//....//build.gradle" }) {
            int status = given().when().get("/documentacao/" + mau).statusCode();
            assertTrue(status == 404 || status == 400,
                    "a travessia '" + mau + "' respondeu " + status);
        }
    }
}
