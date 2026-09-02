package org.nasa.contato.presentation.web;

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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Prova da fatia {@code contato} ponta a ponta, no PostgreSQL de verdade.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO.</b> Aqui aparecem os defeitos que nenhum dublê mostra:
 * restrição que não existe no banco real, coluna com nome trocado, e o mais caro desta
 * fatia — a restrição {@code contato_tipo_conhecido} da V002, que é o que impede um tipo
 * inventado ser gravado por outro caminho.</p>
 *
 * <p><b>Os testes que mais importam são os NEGATIVOS e o CONCORRENTE.</b></p>
 */
@QuarkusTest
@DisplayName("API de contatos — o caminho inteiro, no PostgreSQL de verdade")
class ContatoResourceTest {

    /** E-mail único por execução: a base de teste é compartilhada entre os testes. */
    private static String emailNovo() {
        return "contato" + (System.nanoTime() % 100_000_000_000L) + "@exemplo.com";
    }

    private static String corpo(String email, String tipo) {
        return """
                {"ddd":"11","telefone":"3456-7890","celular":"98765-4321",
                 "whatsapp":null,"email":"%s","tipoContato":"%s"}"""
                .formatted(email, tipo);
    }

    private static Response cadastrar(String email, String tipo) {
        return given().contentType(ContentType.JSON).body(corpo(email, tipo))
                .when().post("/api/contatos");
    }

    @Test
    @DisplayName("cadastra, normaliza o telefone e devolve 201 com Location")
    void cadastraComSucesso() {
        String email = emailNovo();
        Response r = cadastrar(email, "PRINCIPAL");

        assertEquals(201, r.statusCode(), r.asString());
        assertNotNull(r.header("Location"), "201 sem Location deixa quem criou adivinhar a URL");
        assertEquals("34567890", r.jsonPath().getString("telefone"), "guardado so com digitos");
        assertEquals("987654321", r.jsonPath().getString("celular"));
        assertEquals("(11) 98765-4321", r.jsonPath().getString("telefoneFormatado"),
                "a resposta mostra a forma que a pessoa reconhece");
        assertTrue(r.jsonPath().getString("criadoEm").endsWith("Z"), "instante em UTC");
        System.out.println("[API] contato criado: " + r.asString());
    }

    @Test
    @DisplayName("a resposta DIZ se o contato recebe alerta — o campo que faltava no legado")
    void respostaDeclaraSeRecebeAlerta() {
        // No legado nada dizia isso. Cadastrava-se um contato achando que a cobertura
        // existia, e o silencio so aparecia no dia do evento.
        Response comum = cadastrar(emailNovo(), "PRINCIPAL");
        assertEquals(false, comum.jsonPath().getBoolean("recebeAlerta"));
        assertNotNull(comum.jsonPath().getString("motivoNaoRecebeAlerta"),
                "quem NAO recebe alerta precisa ler o motivo, e nao um campo falso e mudo");

        Response emergencia = cadastrar(emailNovo(), "EMERGENCIA");
        assertEquals(true, emergencia.jsonPath().getBoolean("recebeAlerta"));
        assertEquals("Emergência", emergencia.jsonPath().getString("tipoRotulo"));
    }

    @Test
    @DisplayName("409 no e-mail repetido — inclusive em CAIXA diferente")
    void recusaEmailRepetidoEmQualquerCaixa() {
        String email = emailNovo();
        assertEquals(201, cadastrar(email, "PRINCIPAL").statusCode());

        assertEquals(409, cadastrar(email, "PRINCIPAL").statusCode());

        // MAIUSCULA: "Ana@X.com" e "ana@x.com" sao a mesma caixa postal no mundo real.
        // Sem a normalizacao do Email, o UNIQUE do banco nao enxergaria a duplicata e o
        // alerta sairia DUAS vezes para a mesma pessoa.
        Response caixaAlta = cadastrar(email.toUpperCase(), "PRINCIPAL");
        assertEquals(409, caixaAlta.statusCode(),
                "a normalizacao e o que faz o UNIQUE enxergar a duplicata: " + caixaAlta.asString());
    }

    @Test
    @DisplayName("400 no tipo inventado — com os aceitos na mensagem")
    void recusaTipoInventado() {
        Response r = given().contentType(ContentType.JSON)
                .body(corpo(emailNovo(), "Pincipal"))
                .when().post("/api/contatos");
        assertEquals(400, r.statusCode(), r.asString());
        assertTrue(r.asString().contains("EMERGENCIA"),
                "a mensagem tem de listar os aceitos: " + r.asString());
    }

    @Test
    @DisplayName("400 no e-mail malformado — e o campo nomeado")
    void recusaEmailMalformado() {
        Response r = given().contentType(ContentType.JSON)
                .body(corpo("semarroba.com", "PRINCIPAL"))
                .when().post("/api/contatos");
        assertEquals(400, r.statusCode(), r.asString());
        assertEquals("email", r.jsonPath().getString("alvo"),
                "a tela precisa saber QUAL campo destacar");
    }

    @Test
    @DisplayName("ciclo completo: cria, le por id, le por email, altera e exclui")
    void cicloCompleto() {
        String email = emailNovo();
        long id = cadastrar(email, "PRINCIPAL").jsonPath().getLong("id");

        assertEquals(id, given().when().get("/api/contatos/" + id)
                .then().statusCode(200).extract().jsonPath().getLong("id"));

        // No legado este endpoint era ambiguo por construcao: devolvia UM contato sem
        // que nada garantisse existir apenas um.
        assertEquals(id, given().when().get("/api/contatos/email/" + email)
                .then().statusCode(200).extract().jsonPath().getLong("id"));

        // Alterar mantendo o MESMO e-mail — o caso mais comum, e o que uma checagem
        // ingenua de "ja existe este e-mail?" recusaria.
        Response alterado = given().contentType(ContentType.JSON)
                .body(corpo(email, "EMERGENCIA"))
                .when().put("/api/contatos/" + id);
        assertEquals(200, alterado.statusCode(), alterado.asString());
        assertEquals(true, alterado.jsonPath().getBoolean("recebeAlerta"),
                "promovido a EMERGENCIA, passa a receber alerta");

        assertEquals(204, given().when().delete("/api/contatos/" + id).statusCode());
        assertEquals(404, given().when().get("/api/contatos/" + id).statusCode());
    }

    @Test
    @DisplayName("404 no que nao existe — ler, alterar e excluir")
    void quatrocentosEQuatroNoInexistente() {
        assertEquals(404, given().when().get("/api/contatos/99999999").statusCode());
        assertEquals(404, given().contentType(ContentType.JSON).body(corpo(emailNovo(), "PRINCIPAL"))
                .when().put("/api/contatos/99999999").statusCode());
        assertEquals(404, given().when().delete("/api/contatos/99999999").statusCode());
        assertEquals(404, given().when().get("/api/contatos/email/naoexiste@exemplo.com").statusCode());
    }

    @Test
    @DisplayName("a busca FILTRA — controle positivo antes da asercao que importa")
    void buscaFiltraDeVerdade() {
        // Mesmo defeito medido na fatia de cliente em 02/09: termo sem digitos produzia
        // '%%' no campo de telefone, e LIKE '%%' casa com TODA linha. Aqui a fatia ja
        // nasceu com o guarda, e este teste e o que impede a regressao.
        String email = emailNovo();
        assertEquals(201, cadastrar(email, "PRINCIPAL").statusCode());

        var achou = given().when().get("/api/contatos/pesquisar?termo=" + email)
                .then().statusCode(200).extract().jsonPath().getList("id");
        assertFalse(achou.isEmpty(), "o termo existe e a busca nao achou: instrumento cego");

        var nada = given().when().get("/api/contatos/pesquisar?termo=xyzzynaoexistemesmo")
                .then().statusCode(200).extract().jsonPath().getList("id");
        assertTrue(nada.isEmpty(),
                "termo inexistente devolveu " + nada.size() + ": a busca nao esta filtrando");
    }

    @Test
    @DisplayName("CONCORRENCIA: 8 cadastros simultaneos do MESMO e-mail => exatamente 1 entra")
    void cliqueDuploNaoCriaDuplicata() throws Exception {
        // A checagem previa da aplicacao NAO protege: entre a pergunta e a insercao cabe
        // outra requisicao. Quem protege e `contato_email_unico`, no banco.
        String email = emailNovo();
        int tentativas = 8;
        var largada = new CountDownLatch(1);
        var criados = new AtomicInteger();
        var recusados = new AtomicInteger();

        ExecutorService pool = Executors.newFixedThreadPool(tentativas);
        for (int i = 0; i < tentativas; i++) {
            pool.submit(() -> {
                try {
                    largada.await();
                    int status = cadastrar(email, "PRINCIPAL").statusCode();
                    if (status == 201) {
                        criados.incrementAndGet();
                    } else if (status == 409 || status == 500) {
                        recusados.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
        }
        largada.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(60, TimeUnit.SECONDS), "as tentativas nao terminaram");

        System.out.println("[API] concorrencia contato: criados=" + criados.get()
                + " recusados=" + recusados.get());
        assertEquals(1, criados.get(), "exatamente UM cadastro pode ter entrado");
        assertEquals(tentativas - 1, recusados.get(), "o resto tem de ser RECUSADO, nao ignorado");
        assertEquals(200, given().when().get("/api/contatos/email/" + email).statusCode());
    }
}
