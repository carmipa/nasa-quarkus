package org.nasa.core.tempo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nasa.core.erro.FusoHorarioNaoUtcException;

import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Prova da catraca de fuso — com os casos que a versão ingênua deixaria passar.
 *
 * <p><b>PROPÓSITO.</b> A catraca existe porque em 02/09/2026 a produção gravou log em
 * {@code -03:00} enquanto a API respondia em {@code Z}, e nada acusou. Estes testes são o
 * controle positivo dela: um instrumento que só é exercitado no ambiente já correto não
 * prova nada — foi exatamente assim que o defeito sobreviveu.</p>
 */
@DisplayName("catraca de fuso — offset zero E fixo, nao 'zero hoje'")
class CatracaDeFusoUtcTest {

    @Test
    @DisplayName("UTC passa — nas formas em que ele aparece")
    void utcPassa() {
        assertDoesNotThrow(() -> CatracaDeFusoUtc.verificar(ZoneOffset.UTC));
        assertDoesNotThrow(() -> CatracaDeFusoUtc.verificar(ZoneId.of("UTC")));
        assertDoesNotThrow(() -> CatracaDeFusoUtc.verificar(ZoneId.of("Etc/UTC")));
        assertDoesNotThrow(() -> CatracaDeFusoUtc.verificar(ZoneId.of("Z")));
    }

    @Test
    @DisplayName("CONTROLE POSITIVO: o fuso desta maquina (Sao Paulo) REPROVA")
    void saoPauloReprova() {
        // Sem este caso, a catraca poderia ser um metodo vazio e todos os outros passariam.
        var erro = assertThrows(FusoHorarioNaoUtcException.class,
                () -> CatracaDeFusoUtc.verificar(ZoneId.of("America/Sao_Paulo")));

        System.out.println("[FUSO] " + erro.linhaDeLog());
        assertTrue(erro.getMessage().contains("-Duser.timezone=UTC"),
                "quem topa com isto esta a meio de outra tarefa: a mensagem tem de trazer "
                        + "o comando pronto, e nao mandar investigar");
    }

    @Test
    @DisplayName("o caso SUTIL: Europe/London reprova, porque so e UTC no inverno")
    void londresReprova() {
        // A versao ingenua desta checagem — comparar o offset de AGORA — passaria em
        // janeiro e reprovaria em julho. Invariante que vale metade do ano nao e
        // invariante, e o defeito apareceria na virada do horario de verao, longe da causa.
        assertThrows(FusoHorarioNaoUtcException.class,
                () -> CatracaDeFusoUtc.verificar(ZoneId.of("Europe/London")));
    }

    @Test
    @DisplayName("offset fixo diferente de zero tambem reprova")
    void offsetFixoNaoZeroReprova() {
        assertThrows(FusoHorarioNaoUtcException.class,
                () -> CatracaDeFusoUtc.verificar(ZoneOffset.ofHours(-3)));
    }
}
