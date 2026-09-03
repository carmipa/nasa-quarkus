package org.nasa.core.log;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nasa.core.tempo.Relogio;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Prova da faxina de log — o que ela apaga por IDADE, e o que ela se RECUSA a fazer.
 *
 * <p><b>PROPÓSITO.</b> Faxina é código que apaga arquivo. O teste que importa não é o de
 * que ela apaga: é o de que ela <b>não</b> apaga quando a pasta não é só dela, quando o
 * arquivo é o da execução em curso, e quando o relógio está dessincronizado. Os três são
 * dano irreversível.</p>
 *
 * <p><b>O relógio é injetado</b>, e é por isso que dá para testar "trinta dias depois"
 * sem esperar trinta dias — e para simular relógio trocado sem mexer no do host.</p>
 */
@DisplayName("FaxinaLogExecucao — apaga por idade, recusa o que nao e dela")
class FaxinaLogExecucaoTest {

    private static final Instant AGORA = Instant.parse("2026-09-02T12:00:00Z");

    /** Relógio de teste: UTC, parado no instante que o teste escolher. */
    private static Relogio relogioEm(Instant instante) {
        return () -> instante;
    }

    private static FaxinaLogExecucao faxina(Path pasta, int dias, int teto, String carimbo) {
        FaxinaLogExecucao f = new FaxinaLogExecucao();
        f.pasta = pasta.toString();
        f.manterDias = dias;
        f.manter = teto;
        f.carimboAtual = carimbo;
        f.relogio = relogioEm(AGORA);
        return f;
    }

    /** Cria um log com a idade pedida, em dias contados a partir de {@link #AGORA}. */
    private static Path logComIdade(Path pasta, String carimbo, long diasAtras) throws IOException {
        Path p = pasta.resolve("nasa-" + carimbo + ".log");
        Files.writeString(p, "linha\n");
        Files.setLastModifiedTime(p, FileTime.from(AGORA.minus(Duration.ofDays(diasAtras))));
        return p;
    }

    @Test
    @DisplayName("pasta ausente e o primeiro boot, nao um erro")
    void pastaAusente(@TempDir Path base) {
        var r = faxina(base.resolve("nao-existe"), 30, 200, "agora").executar();
        assertFalse(r.executou());
        assertEquals("PASTA_AUSENTE", r.motivo());
    }

    @Test
    @DisplayName("CONTROLE POSITIVO: apaga o que passou de 30 dias e preserva o resto")
    void apagaPorIdade(@TempDir Path pasta) throws IOException {
        logComIdade(pasta, "hoje", 0);
        logComIdade(pasta, "d10", 10);
        logComIdade(pasta, "d29", 29);
        logComIdade(pasta, "d31", 31);
        logComIdade(pasta, "d90", 90);
        logComIdade(pasta, "d365", 365);

        var r = faxina(pasta, 30, 200, "corrente").executar();

        assertTrue(r.executou(), "motivo: " + r.motivo());
        assertEquals(6, r.examinados());
        assertEquals(3, r.apagados(), "os tres com mais de 30 dias");
        assertEquals(3, r.preservados());
        assertEquals(0, r.falhas());
        assertEquals(0, r.relogioSuspeito());

        // Conferir os ITENS, não só a contagem: contagem certa não prova conjunto certo.
        assertTrue(Files.exists(pasta.resolve("nasa-hoje.log")));
        assertTrue(Files.exists(pasta.resolve("nasa-d10.log")));
        assertTrue(Files.exists(pasta.resolve("nasa-d29.log")), "29 dias esta DENTRO da retencao");
        assertFalse(Files.exists(pasta.resolve("nasa-d31.log")), "31 dias esta FORA");
        assertFalse(Files.exists(pasta.resolve("nasa-d90.log")));
        assertFalse(Files.exists(pasta.resolve("nasa-d365.log")));
    }

    @Test
    @DisplayName("o teto por CONTAGEM tambem corta, mesmo com todos dentro da idade")
    void tetoPorContagem(@TempDir Path pasta) throws IOException {
        // Cenário real: modo dev reiniciando muitas vezes no mesmo dia. Todos "novos",
        // e a idade sozinha nunca limparia nada.
        for (int i = 0; i < 10; i++) {
            logComIdade(pasta, "run" + i, i);   // 0 a 9 dias — todos dentro dos 30
        }
        var r = faxina(pasta, 30, 4, "corrente").executar();

        assertTrue(r.executou());
        assertEquals(6, r.apagados(), "acima do teto de 4, mesmo sendo todos recentes");
        assertTrue(Files.exists(pasta.resolve("nasa-run0.log")), "os 4 mais novos ficam");
        assertFalse(Files.exists(pasta.resolve("nasa-run9.log")), "o mais velho sai");
    }

    @Test
    @DisplayName("o log DESTA execucao nunca e apagado, mesmo com 365 dias")
    void nuncaApagaOLogDaExecucaoCorrente(@TempDir Path pasta) throws IOException {
        logComIdade(pasta, "corrente", 365);
        logComIdade(pasta, "outro", 90);

        var r = faxina(pasta, 30, 200, "corrente").executar();

        assertTrue(Files.exists(pasta.resolve("nasa-corrente.log")),
                "apagar o arquivo que esta sendo escrito e o pior momento possivel");
        assertEquals(1, r.apagados());
    }

    @Test
    @DisplayName("RELOGIO DESSINCRONIZADO: arquivo no futuro NAO e apagado — falha fechada")
    void relogioDessincronizadoNaoApaga(@TempDir Path pasta) throws IOException {
        // Se o relógio do host andar para trás, tudo parece velho e a faxina apagaria o
        // acervo inteiro de uma vez. Arquivo com data no futuro é a assinatura disso.
        Path futuro = pasta.resolve("nasa-futuro.log");
        Files.writeString(futuro, "linha\n");
        Files.setLastModifiedTime(futuro, FileTime.from(AGORA.plus(Duration.ofDays(2))));
        logComIdade(pasta, "d90", 90);

        var r = faxina(pasta, 30, 200, "corrente").executar();

        assertTrue(r.executou());
        assertEquals(1, r.relogioSuspeito(), "a anomalia de relogio tem de ser CONTADA");
        assertTrue(Files.exists(futuro), "arquivo no futuro nao e apagado por idade");
        assertFalse(Files.exists(pasta.resolve("nasa-d90.log")),
                "o resto segue normalmente: uma anomalia nao paralisa a faxina inteira");
    }

    @Test
    @DisplayName("CONTROLE NEGATIVO: pasta com arquivo estranho NAO e faxinada — falha fechada")
    void recusaPastaNaoExclusiva(@TempDir Path pasta) throws IOException {
        logComIdade(pasta, "d90", 90);
        logComIdade(pasta, "d365", 365);
        Path intruso = pasta.resolve("relatorio-importante.txt");
        Files.writeString(intruso, "isto nao e log e nao pode sumir");

        var r = faxina(pasta, 30, 200, "corrente").executar();

        assertFalse(r.executou(), "faxina em pasta compartilhada apaga o que nao e dela");
        assertEquals("PASTA_NAO_EXCLUSIVA", r.motivo());
        assertEquals(0, r.apagados());
        assertTrue(Files.exists(intruso), "o arquivo estranho continua onde estava");
        assertTrue(Files.exists(pasta.resolve("nasa-d365.log")),
                "nada foi apagado quando a faxina recusou — nem o que estava velho");
    }

    @Test
    @DisplayName("nada a apagar: executou e apagou zero — diferente de nao ter executado")
    void nadaAApagarNaoEhOMesmoQueNaoExecutar(@TempDir Path pasta) throws IOException {
        logComIdade(pasta, "d1", 1);
        var r = faxina(pasta, 30, 200, "corrente").executar();

        assertTrue(r.executou(), "'nada a fazer' precisa ser distinguivel de 'nao rodei'");
        assertEquals(0, r.apagados());
        assertEquals(1, r.preservados());
    }
}
