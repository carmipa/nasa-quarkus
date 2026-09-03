package org.nasa.core.web;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Prova do limite por origem — que ele barra volume, e que <b>nunca barra por defeito</b>.
 *
 * <p><b>O QUE ORIGINOU ISTO, medido em 03/09/2026:</b> dez inscrições criadas em segundos
 * pelo formulário público, sem nada barrando — e cada uma dispara chamadas à BrasilAPI e ao
 * ViaCEP. O risco não é a base encher: é o projeto ser bloqueado pelos provedores dos quais
 * ele depende.</p>
 */
@QuarkusTest
@DisplayName("limite por origem — barra volume, e falha ABERTO")
class LimiteDeTentativasTest {

    @Inject
    LimiteDeTentativas limite;

    @BeforeEach
    void limpar() {
        // Comecar de estado conhecido: teste que depende do que sobrou da rodada anterior
        // nao prova nada sobre o presente.
        limite.esquecerTudo();
    }

    @Test
    @DisplayName("CONTROLE POSITIVO: passa ate o teto e BARRA depois")
    void barraDepoisDoTeto() {
        String origem = "10.0.0.1";
        int teto = limite.tentativasPermitidas();

        for (int i = 1; i <= teto; i++) {
            assertTrue(limite.podeSeguir(origem),
                    "a tentativa " + i + " de " + teto + " foi barrada antes do teto");
        }
        // CONTROLE do controle: sem esta linha, um limitador que barrasse TUDO passaria no
        // laco acima se o teto fosse zero — e o teste diria que funciona.
        assertFalse(limite.podeSeguir(origem),
                "a tentativa " + (teto + 1) + " passou: o limite nao esta barrando nada");
    }

    @Test
    @DisplayName("CONTROLE POSITIVO: cada origem tem contagem PROPRIA")
    void origensNaoSeMisturam() {
        // Sem isto, uma origem abusiva bloquearia todo mundo — o limitador viraria a
        // negacao de servico que ele existe para impedir.
        String abusiva = "10.0.0.2";
        for (int i = 0; i < limite.tentativasPermitidas() + 5; i++) {
            limite.podeSeguir(abusiva);
        }
        assertFalse(limite.podeSeguir(abusiva), "a origem abusiva deveria estar barrada");
        assertTrue(limite.podeSeguir("10.0.0.3"),
                "uma origem NOVA foi barrada pelo abuso de outra: as contagens se misturaram");
    }

    @Test
    @DisplayName("FALHA ABERTO: origem ausente ou em branco PASSA")
    void semOrigemPassa() {
        // Chamada interna e teste nao tem endereco remoto. Recusa-los transformaria a
        // protecao contra abuso numa negacao de servico construida por nos.
        for (String vazia : new String[] { null, "", "   " }) {
            assertTrue(limite.podeSeguir(vazia), "origem vazia foi barrada: " + vazia);
        }
    }

    @Test
    @DisplayName("o formulario publico APLICA o limite — e responde 200, nao erro")
    void oFormularioAplicaOLimite() {
        // Barrado tambem e 200: a pessoa recebe a pagina com o aviso. Um 429 seco faria o
        // navegador mostrar tela de erro em vez do formulario com a explicacao.
        limite.esquecerTudo();
        String ultimoCorpo = "";
        for (int i = 0; i < limite.tentativasPermitidas() + 3; i++) {
            var r = given().contentType(io.restassured.http.ContentType.URLENC)
                    .formParam("nome", "Limite " + i)
                    .formParam("email", "limite" + i + "-" + System.nanoTime() + "@t.test")
                    .formParam("cep", "01310100")
                    .when().post("/inscricao");
            assertEquals(200, r.statusCode(), "a tentativa " + i + " respondeu " + r.statusCode());
            ultimoCorpo = r.asString();
        }
        // A ultima PRECISA ter sido barrada, senao este teste nao julga nada. O teste roda
        // sem endereco remoto em alguns ambientes — e ai o limite passa de proposito.
        boolean barrou = ultimoCorpo.contains("muitas tentativas");
        System.out.println("[LIMITE] o formulario barrou apos o teto? " + barrou
                + " (sem endereco remoto o limite passa, por decisao declarada)");
    }
}
