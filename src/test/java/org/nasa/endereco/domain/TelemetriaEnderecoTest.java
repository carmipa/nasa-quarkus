package org.nasa.endereco.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nasa.core.erro.CausaRaiz;
import org.nasa.core.telemetria.Veredito;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Prova das regras de veredito — o alarme do job silencioso em código.
 *
 * <p><b>PROPÓSITO.</b> O caso que importa não é o do caminho feliz: é o de <b>zero</b>.
 * Zero processado pode significar "não havia trabalho" (normal) ou "eu estava cego"
 * (grave), e um sistema que não distingue os dois reporta sucesso quando parou de
 * funcionar. Estes testes são o que garante a distinção.</p>
 */
@DisplayName("TelemetriaEndereco — o veredito acusa o job silencioso")
class TelemetriaEnderecoTest {

    private static final Instant AGORA = Instant.parse("2026-09-02T12:00:00Z");

    @Test
    @DisplayName("tudo resolvido, nada pendente: OK sem motivo")
    void tudoResolvido() {
        var t = TelemetriaEndereco.avaliar("resolver-lote", AGORA, 42, 0, Map.of(), true);
        assertEquals(Veredito.OK, t.veredito());
        assertNull(t.motivo(), "OK nao carrega motivo — motivo e para o que precisa de acao");
    }

    @Test
    @DisplayName("ALARME: havia trabalho e nada aconteceu ⇒ ANOMALIA, nunca sucesso silencioso")
    void zeroComTrabalhoDisponivelEhAnomalia() {
        var t = TelemetriaEndereco.avaliar("resolver-lote", AGORA, 0, 0, Map.of(), true);
        assertEquals(Veredito.ANOMALIA, t.veredito(),
                "'nao rodou' e mais dificil de perceber que 'vazou' — por isso acusa");
        assertEquals("ZERO_PROCESSADO_COM_TRABALHO_DISPONIVEL", t.motivo());
    }

    @Test
    @DisplayName("zero SEM trabalho disponivel e OK — o zero legitimo dito com todas as letras")
    void zeroSemTrabalhoEhLegitimo() {
        var t = TelemetriaEndereco.avaliar("resolver-lote", AGORA, 0, 0, Map.of(), false);
        assertEquals(Veredito.OK, t.veredito());
    }

    @Test
    @DisplayName("contador NULO e 'nao medi' — e isso e ANOMALIA, nao zero")
    void naoMedidoNaoEhZero() {
        var semAgiu = TelemetriaEndereco.avaliar("resolver-lote", AGORA, null, 0, Map.of(), false);
        assertEquals(Veredito.ANOMALIA, semAgiu.veredito());
        assertEquals("CONTADOR_NAO_MEDIDO", semAgiu.motivo());

        var semAbsteve = TelemetriaEndereco.avaliar("resolver-lote", AGORA, 10, null, Map.of(), true);
        assertEquals(Veredito.ANOMALIA, semAbsteve.veredito(),
                "Integer null significa 'nao medi'; com int primitivo isto seria 0 e passaria");
    }

    @Test
    @DisplayName("endereco sem coordenada e degradacao declarada: ATENCAO com a contagem")
    void semCoordenadaEhAtencao() {
        var t = TelemetriaEndereco.avaliar("resolver-lote", AGORA, 38, 4,
                Map.of(CausaRaiz.DADO_AUSENTE, 4), true);
        assertEquals(Veredito.ATENCAO, t.veredito());
        assertEquals("ENDERECOS_SEM_COORDENADA=4", t.motivo());
        assertEquals(4, t.recusasPorCausa().get(CausaRaiz.DADO_AUSENTE),
                "o KPI causal responde POR QUE, nao so quanto");
    }

    @Test
    @DisplayName("o mapa de causas e imutavel — telemetria gravada nao se reescreve")
    void mapaDeCausasEhImutavel() {
        var t = TelemetriaEndereco.avaliar("x", AGORA, 1, 0, Map.of(CausaRaiz.TEMPO_ESGOTADO, 1), true);
        assertThrows(UnsupportedOperationException.class,
                () -> t.recusasPorCausa().put(CausaRaiz.DADO_INVALIDO, 99));
    }

    @Test
    @DisplayName("mapa nulo vira vazio, nao NullPointer na hora de ler o painel")
    void mapaNuloViraVazio() {
        var t = TelemetriaEndereco.avaliar("x", AGORA, 1, 0, null, true);
        assertNotNull(t.recusasPorCausa());
        assertTrue(t.recusasPorCausa().isEmpty());
    }
}
