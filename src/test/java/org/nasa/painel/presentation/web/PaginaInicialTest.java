package org.nasa.painel.presentation.web;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.response.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Prova da página inicial: o relógio, a internacionalização e as regras que a protegem.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO.</b> HTTP 200 não prova que a tela funciona. O que estes
 * testes provam é o que quebra <b>em silêncio</b>: o widget de tradução que não
 * inicializa porque os dois {@code <script>} vieram na ordem errada, o {@code lang} do
 * documento trocado que faz o Google concluir que não há nada a traduzir, e a ilha
 * técnica sem {@code translate="no"} — onde traduzir não é errar palavra, é destruir a
 * informação.</p>
 *
 * <p><b>É a guarda que a regra de i18n exige</b> (§6 de
 * {@code instrucoes/regra-internacionalizacao-automatica.md}): documento não impede
 * regressão.</p>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA.</b> Reprova nomeando exatamente qual das seis
 * condições caiu. Nenhuma delas produz erro visível no navegador quando quebra — é
 * justamente por isso que existem aqui.</p>
 */
@QuarkusTest
@DisplayName("pagina inicial — relogio, i18n e as regras que protegem as duas")
class PaginaInicialTest {

    private static String cache;

    /**
     * O HTML da home, buscado sob demanda.
     *
     * <p>Deliberadamente NÃO em {@code @BeforeAll}: o {@code QuarkusTestExtension} só
     * configura a porta do RestAssured depois que a aplicação sobe, e um
     * {@code @BeforeAll} do próprio teste pode rodar antes disso — o sintoma é
     * {@code Connection refused} apontando para a porta padrão, medido aqui em
     * 2026-09-02. Buscar na primeira chamada resolve sem depender de ordem de
     * extensão.</p>
     */
    private static synchronized String html() {
        if (cache == null) {
            Response r = RestAssured.given().accept("text/html").when().get("/");
            assertEquals(200, r.statusCode(), "a home nao respondeu 200");
            assertTrue(r.contentType().startsWith("text/html"),
                    "a home nao devolveu HTML: " + r.contentType());
            cache = r.asString();
            System.out.println("[HOME] bytes=" + cache.length());
        }
        return cache;
    }

    // ------------------------------------------------------------------ relógio

    @Test
    @DisplayName("o relogio traz a hora UTC do SERVIDOR e o instante ISO para o navegador contar")
    void relogioTrazHoraDoServidor() {
        assertTrue(html().contains("id=\"relogio-utc\""), "faltou o relogio UTC");
        assertTrue(html().contains("id=\"relogio-local\""), "faltou a hora local do visitante");
        assertTrue(html().contains("data-instante-servidor=\""),
                "faltou o instante do servidor: sem ele o relogio andaria pelo relogio do "
                        + "aparelho, e um aparelho com hora errada faria a pagina mentir");

        // O instante tem de ser ISO-8601 UTC (termina em Z) — nunca hora local.
        var m = java.util.regex.Pattern
                .compile("data-instante-servidor=\"([^\"]+)\"").matcher(html());
        assertTrue(m.find(), "atributo do instante nao encontrado");
        String instante = m.group(1);
        System.out.println("[HOME] instante do servidor: " + instante);
        assertTrue(instante.endsWith("Z"),
                "o instante do servidor nao esta em UTC: " + instante);
    }

    @Test
    @DisplayName("o bloco do relogio leva translate=no — numero de hora traduzido vira lixo")
    void relogioNaoEhTraduzido() {
        assertTrue(html().contains("class=\"relogio\" translate=\"no\"")
                        || html().contains("translate=\"no\" class=\"relogio\""),
                "o relogio precisa de translate=\"no\"");
    }

    // --------------------------------------------------------------------- i18n

    @Test
    @DisplayName("o `lang` do documento fica no idioma de ORIGEM — trocar sabota a traducao")
    void langFicaNoIdiomaDeOrigem() {
        assertTrue(html().contains("<html lang=\"pt-BR\">"),
                "o <html lang> tem de ficar em pt-BR: o Google o usa como idioma de ORIGEM, "
                        + "e marcado como outro ele conclui que a pagina ja esta traduzida "
                        + "e nao traduz nada");
    }

    @Test
    @DisplayName("o widget escondido e o element.js estao presentes")
    void widgetPresente() {
        assertTrue(html().contains("id=\"google_translate_element\""), "faltou o div do widget");
        assertTrue(html().contains("translate.google.com/translate_a/element.js"),
                "faltou o element.js do Google");
        assertTrue(html().contains("cb=googleTranslateElementInit"),
                "o element.js precisa do callback nomeado");
    }

    @Test
    @DisplayName("o NOSSO js vem ANTES do element.js — ordem trocada nao traduz nada, sem erro")
    void ordemDosScripts() {
        int nosso = html().indexOf("i18n-translate.js");
        int google = html().indexOf("translate_a/element.js");
        assertTrue(nosso > 0, "i18n-translate.js nao esta na pagina");
        assertTrue(google > 0, "element.js nao esta na pagina");
        assertTrue(nosso < google,
                "o element.js chama googleTranslateElementInit como callback: se a nossa "
                        + "funcao ainda nao existir, o widget nao inicializa e NADA traduz — "
                        + "sem erro visivel. nosso=" + nosso + " google=" + google);
    }

    @Test
    @DisplayName("as tres bandeiras estao la: Brasil, EUA e Espanha")
    void asTresBandeiras() {
        for (String idioma : new String[] { "pt", "en", "es" }) {
            assertTrue(html().contains("data-idioma=\"" + idioma + "\""),
                    "faltou a bandeira do idioma " + idioma);
        }
        assertTrue(html().contains("class=\"idiomas\" translate=\"no\"")
                        || html().contains("translate=\"no\" class=\"idiomas\""),
                "o seletor de idioma precisa de translate=\"no\" — traduzido, ele perde o "
                        + "proprio nome");
    }

    @Test
    @DisplayName("nenhum residuo de dicionario: `data-i18n` nao existe nesta pagina")
    void semResiduoDeDicionario() {
        assertFalse(html().contains("data-i18n"),
                "dicionario palavra por palavra e proibido pela regra: cada tela nova nasce "
                        + "em portugues e envelhece o dicionario, em silencio");
    }

    // ------------------------------------------------------------------- assets

    @Test
    @DisplayName("todo asset carrega com ?v= — sem isso o cache serve arquivo velho apos o deploy")
    void assetsComVersao() {
        for (String asset : new String[] { "base.css", "htmx.min.js", "relogio.js", "i18n-translate.js" }) {
            int i = html().indexOf(asset);
            assertTrue(i > 0, "asset ausente na pagina: " + asset);
            assertTrue(html().startsWith(asset + "?v=", i),
                    "asset sem ?v=: " + asset + " — com cache longo, o navegador continua "
                            + "servindo o arquivo velho depois do deploy");
        }
    }

    @Test
    @DisplayName("o HTMX e servido LOCAL — nunca por CDN")
    void htmxEhLocal() {
        assertTrue(html().contains("/estatico/js/htmx.min.js"), "htmx local ausente");
        assertFalse(html().contains("unpkg.com") || html().contains("cdn.jsdelivr")
                        || html().contains("cdnjs.cloudflare.com"),
                "dependencia por CDN em pagina do sistema e proibida: o dia em que a CDN "
                        + "cair, a tela morre inteira");
    }

    @Test
    @DisplayName("os arquivos estaticos realmente respondem — o caminho na pagina nao prova o arquivo")
    void osAssetsExistemDeVerdade() {
        for (String caminho : new String[] {
                "/estatico/css/base.css", "/estatico/js/htmx.min.js",
                "/estatico/js/relogio.js", "/estatico/js/i18n-translate.js" }) {
            Response r = RestAssured.given().when().get(caminho);
            assertEquals(200, r.statusCode(), "asset nao servido: " + caminho);
            assertTrue(r.asString().length() > 50, "asset vazio: " + caminho);
            System.out.println("[HOME] " + caminho + " -> 200, " + r.asString().length() + " bytes");
        }
    }
}
