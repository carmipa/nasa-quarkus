package org.nasa.telemetria;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nasa.core.telemetria.Telemetria;
import org.nasa.telemetria.infrastructure.adapters.RepositorioDeTelemetria;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Prova da telemetria — que ela conta, que ela SOMA, e que ela não derruba nada.
 *
 * <p><b>PROPÓSITO.</b> Telemetria quebrada é pior que telemetria ausente: ela produz
 * números, e números são acreditados. Um contador que zera a cada descarga, ou que
 * substitui em vez de somar, desenha um gráfico bonito e errado — e ninguém confere um
 * gráfico contra a realidade.</p>
 */
@QuarkusTest
@DisplayName("telemetria — conta, soma, e nunca derruba a operacao medida")
class TelemetriaTest {

    @Inject
    Telemetria telemetria;

    @Inject
    RepositorioDeTelemetria repositorio;

    @Test
    @DisplayName("CONTROLE POSITIVO: a descarga RETIRA — descarregar duas vezes nao duplica")
    void aDescargaRetiraEmVezDeCopiar() {
        // Se `retirarTudo` copiasse em vez de retirar, a proxima descarga somaria de novo
        // os MESMOS numeros no banco — e a contagem dobrada num grafico e pior que a
        // contagem ausente, porque nao parece defeito.
        telemetria.retirarTudo();

        telemetria.sucesso("teste-descarga", Duration.ofMillis(10));
        telemetria.sucesso("teste-descarga", Duration.ofMillis(30));

        var primeira = telemetria.retirarTudo();
        var segunda = telemetria.retirarTudo();

        assertFalse(primeira.isEmpty(), "a primeira descarga deveria trazer a medicao");
        assertTrue(segunda.isEmpty(),
                "a segunda descarga trouxe " + segunda.size() + " — o que ja saiu voltou");

        var m = primeira.stream().filter(x -> x.operacao().equals("teste-descarga"))
                .findFirst().orElseThrow();
        assertEquals(2, m.chamadas());
        assertEquals(40, m.duracaoSomaMs(), "a soma tem de acumular as duas");
        assertEquals(10, m.duracaoMinMs());
        assertEquals(30, m.duracaoMaxMs());
        assertEquals(20, m.duracaoMediaMs(), "media = soma / contagem");
    }

    @Test
    @DisplayName("recusa e falha sao contadas SEPARADAMENTE, nunca somadas num 'erros'")
    void recusaEFalhaNaoSeMisturam() {
        // 404 e o sistema funcionando; 500 e o sistema quebrado. Somar as duas faria um
        // rastreador varrendo URLs inexistentes parecer uma pane, e mandaria investigar
        // infraestrutura quando o problema e o pedido.
        telemetria.retirarTudo();

        telemetria.sucesso("teste-desfecho", Duration.ofMillis(1));
        telemetria.recusa("teste-desfecho", Duration.ofMillis(1));
        telemetria.recusa("teste-desfecho", Duration.ofMillis(1));
        telemetria.falha("teste-desfecho", Duration.ofMillis(1));

        var m = telemetria.retirarTudo().stream()
                .filter(x -> x.operacao().equals("teste-desfecho")).findFirst().orElseThrow();

        assertEquals(4, m.chamadas(), "chamadas contam TUDO");
        assertEquals(2, m.recusas());
        assertEquals(1, m.falhas());
    }

    @Test
    @DisplayName("CONTROLE POSITIVO: a gravacao SOMA na linha existente, nao substitui")
    void aGravacaoSomaEmVezDeSubstituir() {
        // Se o ON CONFLICT substituisse, cada descarga apagaria a anterior da mesma hora
        // e o grafico mostraria so os ultimos segundos de cada hora.
        String operacao = "teste-soma-" + System.nanoTime();
        Instant hora = Instant.now().truncatedTo(ChronoUnit.HOURS);

        repositorio.somar(java.util.List.of(
                new Telemetria.Medida(operacao, hora, 3, 1, 0, 300, 50, 200)));
        repositorio.somar(java.util.List.of(
                new Telemetria.Medida(operacao, hora, 2, 0, 1, 100, 10, 900)));

        var resumo = repositorio.resumo(hora.minus(1, ChronoUnit.HOURS)).stream()
                .filter(r -> r.operacao().equals(operacao)).findFirst().orElseThrow();

        assertEquals(5, resumo.chamadas(), "as duas descargas tinham de SOMAR");
        assertEquals(1, resumo.recusas());
        assertEquals(1, resumo.falhas());
        assertEquals(80, resumo.mediaMs(), "media = (300+100) / 5");

        // MINIMO e MAXIMO nao podem ser os da ULTIMA descarga: o maximo da hora e o maior
        // de todas elas. Sobrescrever apagaria justamente o pico, que e o que se procura.
        assertEquals(10, resumo.minimoMs(), "o minimo tem de ser o MENOR das duas descargas");
        assertEquals(900, resumo.maximoMs(), "o maximo tem de ser o MAIOR das duas descargas");
    }

    @Test
    @DisplayName("CONTROLE POSITIVO: `medir` conta a FALHA e re-lanca a excecao")
    void medirContaFalhaESegueLancando() {
        // Medir so o caminho feliz produz telemetria que fica BONITA quando o sistema
        // esta quebrando: menos chamadas e menor latencia a medida que mais coisas falham.
        telemetria.retirarTudo();

        var estourou = org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException.class,
                () -> telemetria.medir("teste-medir", () -> {
                    throw new IllegalStateException("de proposito");
                }));
        assertEquals("de proposito", estourou.getMessage(),
                "a excecao original tem de chegar a quem chamou, nao ser trocada");

        var m = telemetria.retirarTudo().stream()
                .filter(x -> x.operacao().equals("teste-medir")).findFirst()
                .orElseThrow(() -> new AssertionError(
                        "a chamada que FALHOU nao foi contada — o `finally` nao rodou"));
        assertEquals(1, m.chamadas());
        assertEquals(1, m.falhas());
    }

    @Test
    @DisplayName("telemetria NUNCA lanca para quem chama — nem com entrada torta")
    void nuncaLancaParaQuemChama() {
        // Um defeito de telemetria nao pode derrubar a operacao de negocio medida.
        telemetria.registrar(null, Duration.ofMillis(1), Telemetria.Desfecho.SUCESSO);
        telemetria.registrar("  ", Duration.ofMillis(1), Telemetria.Desfecho.SUCESSO);
        telemetria.registrar("ok", null, Telemetria.Desfecho.SUCESSO);
        telemetria.registrar("ok", Duration.ofMillis(-5), Telemetria.Desfecho.SUCESSO);
        // Chegar aqui sem excecao E a asercao.
    }

    // ================================================================== a tela

    @Test
    @DisplayName("a tela de telemetria responde, e MEDE A SI MESMA")
    void aTelaMedeASiMesma() {
        // Uma tela de telemetria que se exclui da telemetria nao pode ser usada para
        // verificar se a telemetria funciona. Ver a propria visita contada e o controle
        // positivo mais barato que existe.
        var antes = telemetria.pendentes();
        assertEquals(200, given().when().get("/telemetria").statusCode());
        assertTrue(telemetria.pendentes() >= antes,
                "a visita a propria tela nao foi contada");
    }

    @Test
    @DisplayName("a tela sobrevive a janela invalida, em vez de estourar")
    void janelaInvalidaNaoDerruba() {
        for (String h : new String[] { "0", "-5", "99999", "1", "720" }) {
            int status = given().when().get("/telemetria?horas=" + h).statusCode();
            assertEquals(200, status, "janela " + h + " respondeu " + status);
        }
    }

    @Test
    @DisplayName("CONTROLE POSITIVO: nenhuma expressao de template vaza para a tela")
    void nenhumaExpressaoVaza() {
        // O Qute avalia `{...}` inclusive dentro de ATRIBUTO. Escrever o padrao de rota
        // no texto de uma dica derrubou esta pagina com 500 em 02/09/2026 — e a versao
        // anterior do mesmo defeito imprimia a expressao como TEXTO, com status 200.
        String corpo = given().when().get("/telemetria").asString();
        for (String vazamento : new String[] { "{#icone", "{cdi:", ".raw}", "{#if ", "{#for " }) {
            assertFalse(corpo.contains(vazamento),
                    "a tela imprimiu '" + vazamento + "' como TEXTO");
        }
    }
}
