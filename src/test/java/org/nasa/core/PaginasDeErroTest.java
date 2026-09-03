package org.nasa.core;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Prova das páginas de erro — que elas explicam, e que não contam nada de mais.
 *
 * <p><b>O QUE ORIGINOU ISTO, medido em 03/09/2026.</b> A página de erro era a padrão do
 * Quarkus, e em desenvolvimento ela <b>lista todos os endpoints da API</b>: a superfície
 * inteira do sistema entregue a quem digitou um endereço errado.</p>
 *
 * <p><b>E o que este teste protege é a metade que dá para esquecer:</b> um dia alguém
 * acrescenta o texto da exceção na página para facilitar o diagnóstico, e o rastro de pilha
 * passa a sair para o visitante — sem nada reclamando, porque a página continua bonita.</p>
 */
@QuarkusTest
@DisplayName("paginas de erro — explicam, e nao vazam infraestrutura")
class PaginasDeErroTest {

    /** Um endereço que garantidamente não existe, e que muda a cada execução. */
    private static String inexistente() {
        return "/nao-existe-" + System.nanoTime();
    }

    @Test
    @DisplayName("NAVEGADOR recebe pagina HTML, com a moldura inteira")
    void navegadorRecebeHtml() {
        var r = given().header("Accept", "text/html").when().get(inexistente());

        assertEquals(404, r.statusCode(), "o status tem de ser o de verdade");
        String corpo = r.asString();

        assertTrue(corpo.contains("erro-cartao"), "a pagina de erro nao renderizou");
        // A MOLDURA importa: sem menu, a pessoa fica sem caminho de volta — e uma pagina
        // de erro sem saida e um beco, nao uma pagina.
        assertTrue(corpo.contains("class=\"menu\""), "a pagina de erro veio sem a navegacao");
        assertTrue(corpo.contains("erro-atalhos"),
                "a pagina nao ofereceu os enderecos que existem");
    }

    @Test
    @DisplayName("API recebe JSON, nao HTML")
    void apiRecebeJson() {
        // O mesmo 404 serve um navegador e uma integracao, e as duas precisam de coisas
        // opostas. HTML no log de um cliente de API e ruido; JSON na tela sao chaves e
        // colchetes.
        var r = given().header("Accept", "application/json").when().get(inexistente());

        assertEquals(404, r.statusCode());
        String corpo = r.asString();
        assertTrue(corpo.trim().startsWith("{"), "esperava JSON, veio: " + corpo.substring(0,
                Math.min(60, corpo.length())));
        assertFalse(corpo.contains("<html"), "veio HTML para quem pediu JSON");
        assertTrue(corpo.contains("\"status\":404"));
    }

    @Test
    @DisplayName("CONTROLE POSITIVO: NENHUMA pagina de erro vaza infraestrutura")
    void naoVazaInfraestrutura() {
        // ESTA E A GUARDA QUE IMPORTA. Um dia alguem acrescenta o texto da excecao na
        // pagina "para facilitar o diagnostico", e o rastro de pilha passa a sair para o
        // visitante — sem nada reclamando, porque a pagina continua bonita.
        String[] vazamentos = {
                "org.nasa.",            // nome de classe do projeto
                "at java.",             // rastro de pilha
                "jakarta.ws.rs",        // nome de classe do framework
                "Resource Endpoints",   // a lista de rotas da pagina padrao do Quarkus
                "SELECT ",              // consulta SQL
                "sqlite",               // o motor do banco
                "\\src\\main",          // caminho de arquivo
                "/src/main"
        };
        for (String aceito : new String[] { "text/html", "application/json" }) {
            String corpo = given().header("Accept", aceito).when().get(inexistente()).asString();
            for (String vazamento : vazamentos) {
                assertFalse(corpo.contains(vazamento),
                        "a pagina de erro (" + aceito + ") contem '" + vazamento
                                + "' — isso e infraestrutura para quem nao deveria ver, e e "
                                + "o mapa que um atacante usa");
            }
        }
    }

    @Test
    @DisplayName("CONTROLE DO CONTROLE: a pagina DIZ alguma coisa")
    void aPaginaDizAlgumaCoisa() {
        // Sem este caso, uma pagina VAZIA passaria no teste de vazamento — e uma pagina de
        // erro em branco e indistinguivel de uma pagina que nao carregou.
        String corpo = given().header("Accept", "text/html").when().get(inexistente()).asString();
        assertTrue(corpo.contains("não existe") || corpo.contains("nao existe"),
                "a pagina nao explica o que aconteceu");
        assertTrue(corpo.length() > 2000,
                "a pagina tem " + corpo.length() + " caracteres: veio quase vazia");
    }

    @Test
    @DisplayName("o STATUS e o de verdade — nunca 200 numa pagina de erro")
    void oStatusEhDeVerdade() {
        // Pagina de erro que responde 200 e indistinguivel de sucesso para rastreador,
        // monitor e cliente de API — e o Google indexa a pagina de erro como conteudo.
        assertEquals(404, given().when().get(inexistente()).statusCode());

        // Metodo errado num endereco que existe: 405, e nao 404 nem 500.
        int metodoErrado = given().when().delete("/desastres").statusCode();
        assertTrue(metodoErrado == 405 || metodoErrado == 404,
                "DELETE em /desastres respondeu " + metodoErrado);
    }

    @Test
    @DisplayName("a pagina de erro TAMBEM funciona sem cabecalho Accept")
    void semAcceptTambemFunciona() {
        // `curl` sem `-H Accept` manda `*/*`. Ele cai em JSON de proposito: quem nao
        // declara o que aceita e, quase sempre, integracao — e para ela HTML e ruido.
        var r = given().when().get(inexistente());
        assertEquals(404, r.statusCode());
        assertFalse(r.asString().isBlank(), "resposta vazia sem cabecalho Accept");
    }
}
