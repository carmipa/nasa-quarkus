package org.nasa.painel;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nasa.core.presentation.web.Icones;
import org.nasa.painel.presentation.web.DocumentacaoCatalogo;

import java.util.HashSet;
import java.util.Set;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * O catálogo da documentação — que ele aponta para coisas que existem.
 *
 * <p><b>O QUE ORIGINOU ISTO.</b> {@link Icones#svg} devolve um ícone genérico para nome
 * desconhecido, <b>de propósito</b>: um erro de digitação não deve derrubar a página. O
 * efeito colateral é que o erro <b>não aparece</b> — a seção fica com um ícone sem sentido
 * e a página continua respondendo 200. É a mesma armadilha que já custou caro neste
 * projeto três vezes: <i>200 não prova que a tela está certa</i>.</p>
 *
 * <p>O mesmo já é feito com as categorias de desastre, em
 * {@code CategoriasDeDesastreTest}. Aqui é a segunda tabela de nomes de ícone do sistema,
 * e ela nasceu sem guarda.</p>
 */
@QuarkusTest
@DisplayName("catalogo da documentacao — aponta para coisas que existem")
class DocumentacaoCatalogoTest {

    @Inject
    DocumentacaoCatalogo catalogo;

    @Test
    @DisplayName("TODO icone declarado EXISTE no catalogo de icones")
    void todoIconeExiste() {
        for (var s : DocumentacaoCatalogo.SECOES) {
            assertTrue(Icones.existe(s.icone()),
                    "a secao '" + s.titulo() + "' pede o icone '" + s.icone()
                            + "', que NAO existe — ela vai desenhar o icone generico e "
                            + "ninguem vai perceber, porque a pagina continua respondendo 200");
        }
        for (var d : DocumentacaoCatalogo.DOCUMENTOS) {
            assertTrue(Icones.existe(d.icone()),
                    "o documento '" + d.titulo() + "' pede o icone '" + d.icone()
                            + "', que NAO existe");
        }
    }

    @Test
    @DisplayName("CONTROLE POSITIVO: o teste acima REPROVA um nome inventado")
    void oControleReprovaNomeInventado() {
        // Sem este caso, `Icones.existe` poderia devolver `true` para qualquer coisa e o
        // teste de cima passaria sem medir nada.
        assertFalse(Icones.existe("icone-que-nao-existe-" + System.nanoTime()));
        assertFalse(Icones.existe(null));
        assertFalse(Icones.existe(""));
    }

    @Test
    @DisplayName("TODO documento declarado TEM arquivo no disco")
    void todoDocumentoTemArquivo() {
        // `declaradosSemArquivo` ja registra WARN, e WARN em log ninguem le. Aqui isso
        // vira reprovacao — que e o unico jeito de a ausencia parar o commit.
        var faltando = catalogo.declaradosSemArquivo();
        assertTrue(faltando.isEmpty(),
                "documento(s) declarado(s) no catalogo e AUSENTE(s) no disco: " + faltando
                        + " — a pagina fica com um item a menos, que e indistinguivel "
                        + "de uma pagina correta");
    }

    @Test
    @DisplayName("TODO documento pertence a uma secao QUE EXISTE")
    void todoDocumentoTemSecaoValida() {
        // Secao orfa derruba a pagina do documento com 500, porque o Qute e estrito e a
        // trilha depende dela. Melhor descobrir aqui.
        Set<String> slugs = new HashSet<>();
        for (var s : DocumentacaoCatalogo.SECOES) {
            slugs.add(s.slug());
        }
        for (var d : DocumentacaoCatalogo.DOCUMENTOS) {
            assertTrue(slugs.contains(d.secao()),
                    "o documento '" + d.slug() + "' declara a secao '" + d.secao()
                            + "', que nao esta no catalogo — a pagina dele responderia 500");
        }
    }

    @Test
    @DisplayName("nenhum slug REPETIDO — dois documentos na mesma URL")
    void nenhumSlugRepetido() {
        // `porSlug` faz `findFirst`: com slug repetido, o segundo documento fica
        // INALCANCAVEL e nada reclama. Ele aparece no indice, e o link abre o outro.
        Set<String> vistos = new HashSet<>();
        for (var d : DocumentacaoCatalogo.DOCUMENTOS) {
            assertTrue(vistos.add(d.slug()),
                    "o slug '" + d.slug() + "' aparece duas vezes: o segundo documento "
                            + "e inalcancavel, e o link dele abre o primeiro");
        }
        assertEquals(DocumentacaoCatalogo.DOCUMENTOS.size(), vistos.size());
    }

    @Test
    @DisplayName("o tempo de leitura e MEDIDO, e nunca zero num documento que existe")
    void oTempoDeLeituraEhMedido() {
        for (var d : DocumentacaoCatalogo.DOCUMENTOS) {
            int min = catalogo.minutosDe(d.slug());
            assertTrue(min >= 1,
                    "o documento '" + d.slug() + "' devolveu " + min + " min: ou o arquivo "
                            + "nao foi lido, ou ele esta vazio");
        }
        // CONTROLE: um slug inexistente devolve 0, e a tela OMITE o rotulo nesse caso.
        // Sem esta linha, um `minutosDe` que devolvesse 5 para tudo passaria acima.
        assertEquals(0, catalogo.minutosDe("nao-existe-" + System.nanoTime()));
    }

    @Test
    @DisplayName("TODA pagina de documento ABRE — as 15, uma a uma")
    void todaPaginaAbre() {
        // O teste que mais vale, e o mais chato de escrever a mao. O Qute e ESTRITO:
        // uma chave que a tela pede e o Resource nao passa e 500, nao espaco em branco.
        // Sem isto, so se descobre navegando — e ninguem navega nas quinze.
        for (var d : DocumentacaoCatalogo.DOCUMENTOS) {
            int status = given().header("Accept", "text/html")
                    .when().get("/documentacao/" + d.slug()).statusCode();
            assertEquals(200, status,
                    "a pagina de '" + d.slug() + "' respondeu " + status);
        }
    }

    @Test
    @DisplayName("a pagina NAO vaza expressao de template como TEXTO")
    void naoVazaExpressaoDeTemplate() {
        // TERCEIRA VEZ NESTE PROJETO que 200 nao prova tela certa. O Qute nao aceita
        // expressao comecando por aspas e NAO FALHA: ele emite o proprio codigo-fonte do
        // template na pagina. Status 200, tela errada, nada no log.
        String corpo = given().header("Accept", "text/html")
                .when().get("/documentacao/visao-geral").asString();

        for (String vazamento : new String[] { "{#icone", "{#for", "{#if", "{cdi:",
                                               ".raw}", "{#menudoc", "{doc.", "{secao." }) {
            assertFalse(corpo.contains(vazamento),
                    "a pagina imprimiu '" + vazamento + "' como TEXTO: o Qute nao "
                            + "conseguiu resolver a expressao e emitiu o template cru");
        }
    }
}
