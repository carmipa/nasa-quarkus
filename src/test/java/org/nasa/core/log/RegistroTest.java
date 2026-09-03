package org.nasa.core.log;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Prova do formato canônico da linha de log (§9.2 da planta).
 *
 * <p><b>PROPÓSITO.</b> O formato só vale se for mecanismo. Estes testes provam que os
 * campos obrigatórios não somem em silêncio quando o chamador esquece — que é o modo de
 * falha real, não o teórico.</p>
 */
@DisplayName("Registro — a linha de log carrega operacao, alvo e motivo")
class RegistroTest {

    @Test
    @DisplayName("linha completa: operacao, alvo, mensagem e duracao")
    void linhaCompleta() {
        String linha = Registro.de("sincronizar-nasa", "EONET_1001",
                "42 eventos gravados", Duration.ofMillis(1300));
        assertEquals("sincronizar-nasa alvo=EONET_1001 — 42 eventos gravados (1,3s)"
                .replace(',', '.'), linha.replace(',', '.'));
    }

    @Test
    @DisplayName("sem duracao, a linha nao inventa parenteses vazio")
    void semDuracao() {
        String linha = Registro.de("geocodificar", "01310-200", "coordenada obtida");
        assertEquals("geocodificar alvo=01310-200 — coordenada obtida", linha);
    }

    @Test
    @DisplayName("recusa SEMPRE carrega o motivo — a regra que mais acha bug")
    void recusaCarregaMotivo() {
        String linha = Registro.recusa("geocodificar", "69900-000", "SEM_COORDENADA");
        assertEquals("geocodificar alvo=69900-000 — motivo=SEM_COORDENADA", linha);
    }

    @Test
    @DisplayName("campo esquecido vira NAO_INFORMADO greppavel — e NAO derruba a operacao")
    void campoEsquecidoNaoDerrubaMasGrita() {
        // A lente de boa-fé aplicada à própria ferramenta: lançar aqui trocaria um
        // defeito de log por uma queda de serviço. O campo grita, e `grep NAO_INFORMADO`
        // lista exatamente as chamadas a consertar.
        assertTrue(Registro.de("", "alvo", "msg").startsWith(Registro.NAO_INFORMADO));
        assertTrue(Registro.de("op", "  ", "msg").contains("alvo=" + Registro.NAO_INFORMADO));
        assertTrue(Registro.recusa("op", "alvo", null).contains("motivo=" + Registro.NAO_INFORMADO));
        assertTrue(Registro.recusa("op", "alvo", "   ").contains("motivo=" + Registro.NAO_INFORMADO));
    }

    @Test
    @DisplayName("duracao: milissegundos abaixo de 1s, segundos com uma casa acima")
    void formatoDaDuracao() {
        assertEquals("0ms", Registro.formatar(Duration.ZERO));
        assertEquals("999ms", Registro.formatar(Duration.ofMillis(999)));
        assertEquals("1.0s", Registro.formatar(Duration.ofMillis(1000)));
        assertEquals("12.3s", Registro.formatar(Duration.ofMillis(12345)));
    }
}
