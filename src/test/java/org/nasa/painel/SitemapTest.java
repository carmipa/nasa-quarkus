package org.nasa.painel;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Prova do sitemap — que ele existe, não mente e não contradiz o {@code robots.txt}.
 *
 * <p><b>O QUE ORIGINOU ISTO.</b> {@code /sitemap.xml} respondia <b>404</b> enquanto o
 * {@code robots.txt} já estava escrito e completo. E o defeito que este teste protege é o
 * inverso, mais caro: um sitemap que <b>anuncia endereço que não existe</b>. Ele não quebra
 * tela nenhuma — quem descobre é o rastreador, semanas depois, e o efeito é perder confiança
 * de domínio sem nada no log da aplicação.</p>
 *
 * <p><b>A CONTRADIÇÃO com o {@code robots.txt} é o segundo defeito silencioso.</b> Um
 * sitemap que lista o que o robots bloqueia é relatado como erro pelo Google, e o sintoma
 * aparece num painel que ninguém deste projeto abre.</p>
 */
@QuarkusTest
@DisplayName("sitemap — existe, aponta so para o que responde, e concorda com o robots")
class SitemapTest {

    private static final Pattern LOC = Pattern.compile("<loc>([^<]+)</loc>");

    private static String sitemap() {
        return given().when().get("/sitemap.xml").then().statusCode(200)
                .extract().asString();
    }

    /** Os caminhos anunciados, já sem o endereço público na frente. */
    private static List<String> caminhos() {
        var achados = new ArrayList<String>();
        Matcher m = LOC.matcher(sitemap());
        while (m.find()) {
            String url = m.group(1);
            int corte = url.indexOf('/', url.indexOf("//") + 2);
            achados.add(corte < 0 ? "/" : url.substring(corte));
        }
        return achados;
    }

    @Test
    @DisplayName("responde 200, em XML, e nao vem vazio")
    void respondeXml() {
        var r = given().when().get("/sitemap.xml");
        assertEquals(200, r.statusCode());
        assertTrue(r.contentType().contains("xml"),
                "o tipo veio '" + r.contentType() + "': rastreador pode recusar");
        String corpo = r.asString();
        assertTrue(corpo.startsWith("<?xml"), "sem declaracao XML");
        assertTrue(corpo.contains("sitemaps.org/schemas/sitemap/0.9"),
                "sem o namespace do padrao — o arquivo nao e um sitemap valido");
    }

    @Test
    @DisplayName("TODO endereco anunciado RESPONDE — nenhum 404 no sitemap")
    void todoEnderecoResponde() {
        // ESTA E A GUARDA QUE IMPORTA. Um sitemap que anuncia endereco inexistente nao
        // quebra tela nenhuma: quem descobre e o rastreador, semanas depois.
        var caminhos = caminhos();
        assertFalse(caminhos.isEmpty(), "o sitemap veio sem nenhuma URL");

        for (String caminho : caminhos) {
            int status = given().header("Accept", "text/html").when().get(caminho).statusCode();
            assertEquals(200, status,
                    "o sitemap anuncia '" + caminho + "', que respondeu " + status
                            + " — rastreador mandado a um endereco morto");
        }
    }

    @Test
    @DisplayName("NENHUM endereco anunciado e proibido pelo robots.txt")
    void naoContradizORobots() {
        // Sitemap que lista o que o robots bloqueia e contradicao, e o Google relata como
        // erro num painel que ninguem deste projeto abre.
        String robots = given().when().get("/robots.txt").then().statusCode(200)
                .extract().asString();

        var proibidos = new ArrayList<String>();
        for (String linha : robots.split("\\R")) {
            String limpa = linha.trim();
            if (limpa.startsWith("Disallow:")) {
                String alvo = limpa.substring("Disallow:".length()).trim();
                if (!alvo.isEmpty()) {
                    proibidos.add(alvo);
                }
            }
        }
        assertFalse(proibidos.isEmpty(),
                "o robots.txt nao tem nenhum Disallow: este teste nao mede nada");

        for (String caminho : caminhos()) {
            for (String proibido : proibidos) {
                assertFalse(caminho.startsWith(proibido),
                        "o sitemap anuncia '" + caminho + "' e o robots.txt proibe '"
                                + proibido + "'");
            }
        }
    }

    @Test
    @DisplayName("o robots.txt DECLARA o sitemap")
    void oRobotsApontaOSitemap() {
        String robots = given().when().get("/robots.txt").then().statusCode(200)
                .extract().asString();
        assertTrue(robots.contains("Sitemap:"),
                "o robots.txt nao aponta o sitemap — ele so sera achado por sorte");
        assertTrue(robots.contains("/sitemap.xml"),
                "a diretiva Sitemap nao termina em /sitemap.xml");
    }

    @Test
    @DisplayName("TODO documento do catalogo esta no sitemap")
    void todoDocumentoEstaNoSitemap() {
        // Esta e a razao de o sitemap ser GERADO. Num arquivo estatico, cada documento
        // novo exigiria lembrar de acrescentar a linha — e o esquecimento e silencioso: a
        // pagina existe, responde 200, e nunca e indexada.
        var caminhos = caminhos();
        for (var doc : org.nasa.painel.presentation.web.DocumentacaoCatalogo.DOCUMENTOS) {
            assertTrue(caminhos.contains("/documentacao/" + doc.slug()),
                    "o documento '" + doc.slug() + "' nao esta no sitemap");
        }
    }

    @Test
    @DisplayName("nenhuma URL tem barra dobrada")
    void semBarraDobrada() {
        // `https://x/` + `/desastres` daria `//desastres`, que para um rastreador e OUTRA
        // URL — e duas URLs para a mesma pagina e conteudo duplicado.
        Matcher m = LOC.matcher(sitemap());
        while (m.find()) {
            String url = m.group(1);
            String semEsquema = url.substring(url.indexOf("//") + 2);
            assertFalse(semEsquema.contains("//"),
                    "barra dobrada em '" + url + "'");
        }
    }

    @Test
    @DisplayName("nenhuma URL repete")
    void nadaRepete() {
        var vistos = new java.util.HashSet<String>();
        for (String c : caminhos()) {
            assertTrue(vistos.add(c), "o sitemap lista '" + c + "' duas vezes");
        }
    }
}
