package org.nasa.cliente.presentation.web;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Prova da fatia {@code cliente} ponta a ponta, no banco de verdade.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO.</b> Teste de caso de uso com dublê prova a regra; este
 * prova o <b>caminho inteiro</b> — borda HTTP, tradução de erro, caso de uso, adaptador e
 * SQLite. É onde aparecem os defeitos que nenhum dublê mostra: status errado, constraint
 * que não existe no banco real, coluna com nome trocado.</p>
 *
 * <p><b>Os testes que mais importam são os NEGATIVOS e o CONCORRENTE.</b> O positivo
 * prova que o caminho feliz anda; só os outros provam que a proteção existe.</p>
 */
@QuarkusTest
@DisplayName("API de clientes — o caminho inteiro, no SQLite de verdade")
class ClienteResourceTest {

    /** Documento único por execução: a base de teste é compartilhada entre os testes. */
    private static String documentoNovo() {
        long n = System.nanoTime() % 100_000_000_000L;
        return String.format("%011d", n);
    }

    private static String corpo(String nome, String documento) {
        return """
                {"nome":"%s","sobrenome":"Souza","dataNascimento":"1990-05-14","documento":"%s"}"""
                .formatted(nome, documento);
    }

    private static Response cadastrar(String nome, String documento) {
        return given().contentType(ContentType.JSON).body(corpo(nome, documento))
                .when().post("/api/clientes");
    }

    @Test
    @DisplayName("cadastra e devolve 201 com Location e o documento formatado")
    void cadastraComSucesso() {
        String doc = documentoNovo();
        Response r = cadastrar("Ana", doc);

        assertEquals(201, r.statusCode(), r.asString());
        assertNotNull(r.header("Location"), "201 sem Location deixa o cliente adivinhar a URL");
        assertTrue(r.header("Location").contains("/api/clientes/"));
        assertEquals(doc, r.jsonPath().getString("documento"), "guardado so com digitos");
        assertTrue(r.jsonPath().getString("documentoFormatado").contains("."),
                "a resposta mostra a forma que a pessoa reconhece");
        assertEquals("Ana Souza", r.jsonPath().getString("nomeCompleto"));
        assertTrue(r.jsonPath().getString("criadoEm").endsWith("Z"), "instante em UTC");
        System.out.println("[API] criado: " + r.asString());
    }

    @Test
    @DisplayName("409 no documento repetido — e TAMBEM na forma PONTUADA do mesmo CPF")
    void recusaDocumentoRepetidoEmQualquerForma() {
        String doc = documentoNovo();
        assertEquals(201, cadastrar("Original", doc).statusCode());

        // Mesmo documento, mesma forma.
        Response igual = cadastrar("Copia", doc);
        assertEquals(409, igual.statusCode(), igual.asString());
        assertEquals("CONFLITO_DE_ESTADO", igual.jsonPath().getString("causa"));

        // Mesmo documento, forma PONTUADA. No legado isto criava uma segunda pessoa.
        String pontuado = doc.substring(0, 3) + "." + doc.substring(3, 6) + "."
                + doc.substring(6, 9) + "-" + doc.substring(9);
        Response comPontos = cadastrar("Copia pontuada", pontuado);
        assertEquals(409, comPontos.statusCode(),
                "a normalizacao e o que faz o UNIQUE do banco enxergar a duplicata: "
                        + comPontos.asString());
    }

    @Test
    @DisplayName("400 quando o dado nao descreve um cliente — com o CAMPO nomeado")
    void recusaDadoInvalido() {
        Response semNome = given().contentType(ContentType.JSON)
                .body("""
                        {"nome":"","sobrenome":"Souza","dataNascimento":"1990-05-14","documento":"12345678901"}""")
                .when().post("/api/clientes");
        assertEquals(400, semNome.statusCode(), semNome.asString());
        assertEquals("nome", semNome.jsonPath().getString("alvo"),
                "a tela precisa saber QUAL campo destacar");

        Response dataRuim = given().contentType(ContentType.JSON)
                .body("""
                        {"nome":"Ana","sobrenome":"Souza","dataNascimento":"14/05/1990","documento":"12345678902"}""")
                .when().post("/api/clientes");
        assertEquals(400, dataRuim.statusCode());
        assertTrue(dataRuim.jsonPath().getString("erro").contains("AAAA-MM-DD"),
                "a mensagem tem de dizer o formato esperado: " + dataRuim.asString());
    }

    @Test
    @DisplayName("404 no que nao existe — buscar, alterar e excluir")
    void quatrocentosEQuatroNoInexistente() {
        assertEquals(404, given().when().get("/api/clientes/99999999").statusCode());
        assertEquals(404, given().contentType(ContentType.JSON).body(corpo("X", documentoNovo()))
                .when().put("/api/clientes/99999999").statusCode());
        assertEquals(404, given().when().delete("/api/clientes/99999999").statusCode());
        assertEquals(404, given().when().get("/api/clientes/documento/00000000000").statusCode());
    }

    @Test
    @DisplayName("ciclo completo: cria, le, altera, pesquisa e exclui")
    void cicloCompleto() {
        String doc = documentoNovo();
        long id = cadastrar("Bruno", doc).jsonPath().getLong("id");

        // ler
        assertEquals("Bruno", given().when().get("/api/clientes/" + id)
                .then().statusCode(200).extract().jsonPath().getString("nome"));

        // por documento
        assertEquals(id, given().when().get("/api/clientes/documento/" + doc)
                .then().statusCode(200).extract().jsonPath().getLong("id"));

        // alterar mantendo O MESMO documento — o caso mais comum, e o que uma checagem
        // ingenua de "ja existe este documento?" recusaria.
        Response alterado = given().contentType(ContentType.JSON)
                .body("""
                        {"nome":"Bruno Alterado","sobrenome":"Souza","dataNascimento":"1990-05-14","documento":"%s"}"""
                        .formatted(doc))
                .when().put("/api/clientes/" + id);
        assertEquals(200, alterado.statusCode(), alterado.asString());
        assertEquals("Bruno Alterado", alterado.jsonPath().getString("nome"));

        // pesquisar
        assertTrue(given().when().get("/api/clientes/pesquisar?termo=Bruno")
                .then().statusCode(200).extract().jsonPath().getList("id").size() >= 1);

        // excluir, e conferir que sumiu de verdade
        assertEquals(204, given().when().delete("/api/clientes/" + id).statusCode());
        assertEquals(404, given().when().get("/api/clientes/" + id).statusCode());
    }

    @Test
    @DisplayName("a listagem tem TETO: pedir um milhao nao carrega a base inteira")
    void listagemTemTeto() {
        var ids = given().when().get("/api/clientes?tamanho=1000000")
                .then().statusCode(200).extract().jsonPath().getList("id");
        assertTrue(ids.size() <= 100,
                "sem teto, ?tamanho=1000000 carrega a base na memoria e a lentidao "
                        + "aparece longe da causa. Vieram " + ids.size());
    }

    @Test
    @DisplayName("pesquisa com aspas e apostrofo nao quebra — consulta e parametrizada")
    void pesquisaNaoAceitaInjecao() {
        // Nao e teste de seguranca completo; e a prova de que o termo entra como
        // PARAMETRO. Concatenado, isto derrubaria a consulta ou pior.
        Response r = given().when().get("/api/clientes/pesquisar?termo=%27%20OR%201%3D1%20--");
        assertEquals(200, r.statusCode(), "a consulta quebrou com aspas: " + r.asString());
    }

    @Test
    @DisplayName("CONCORRENCIA: 8 cadastros simultaneos do MESMO documento => exatamente 1 entra")
    void cliqueDuploNaoCriaDuplicata() throws Exception {
        // Este e o teste que prova que a invariante mora no BANCO. A checagem previa da
        // aplicacao ("ja existe?") NAO protege: entre a pergunta e o INSERT cabe outra
        // requisicao, e o clique duplo e o caso comum, nao o raro.
        String doc = documentoNovo();
        int tentativas = 8;
        var largada = new CountDownLatch(1);
        var criados = new AtomicInteger();
        var conflitos = new AtomicInteger();

        ExecutorService pool = Executors.newFixedThreadPool(tentativas);
        for (int i = 0; i < tentativas; i++) {
            pool.submit(() -> {
                try {
                    largada.await();
                    int status = cadastrar("Concorrente", doc).statusCode();
                    if (status == 201) {
                        criados.incrementAndGet();
                    } else if (status == 409 || status == 500) {
                        conflitos.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }
        largada.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(60, TimeUnit.SECONDS), "as tentativas nao terminaram");

        System.out.println("[API] concorrencia: criados=" + criados.get()
                + " recusados=" + conflitos.get());
        assertEquals(1, criados.get(),
                "exatamente UM cadastro pode ter entrado — o resto tem de ser recusado "
                        + "pelo UNIQUE do banco");
        assertEquals(tentativas - 1, conflitos.get(),
                "as demais tentativas precisam ser recusadas, nao silenciosamente ignoradas");

        // E o estado final do banco confirma: um unico registro com aquele documento.
        assertEquals(200, given().when().get("/api/clientes/documento/" + doc).statusCode());
    }
}
