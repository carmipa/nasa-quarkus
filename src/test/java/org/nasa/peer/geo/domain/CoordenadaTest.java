package org.nasa.peer.geo.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Prova das invariantes de {@link Coordenada}.
 *
 * <p><b>PROPÓSITO.</b> A coordenada é o peer que liga endereço do cliente e evento
 * natural; um valor inválido aqui vira distância errada no alerta, e distância errada
 * no alerta é alerta que não chega ou chega para quem não devia.</p>
 *
 * <p><b>O caso que mais importa</b> é {@link #nullIslandEhAceitoNestePeerDePropósito()}:
 * ele documenta em código uma decisão que parece um bug e não é, para que a próxima
 * pessoa não a "conserte".</p>
 */
@DisplayName("Coordenada — invariantes do peer geo")
class CoordenadaTest {

    @Test
    @DisplayName("aceita um ponto valido")
    void aceitaPontoValido() {
        Coordenada c = new Coordenada(-23.5614961, -46.6559677);
        assertEquals(-23.5614961, c.latitude());
        assertEquals(-46.6559677, c.longitude());
    }

    @Test
    @DisplayName("recusa latitude fora de [-90, 90]")
    void recusaLatitudeForaDoIntervalo() {
        var erro = assertThrows(IllegalArgumentException.class, () -> new Coordenada(90.1, 0));
        assertTrue(erro.getMessage().contains("latitude"), erro.getMessage());
        assertThrows(IllegalArgumentException.class, () -> new Coordenada(-90.1, 0));
    }

    @Test
    @DisplayName("recusa longitude fora de [-180, 180]")
    void recusaLongitudeForaDoIntervalo() {
        var erro = assertThrows(IllegalArgumentException.class, () -> new Coordenada(0, 180.1));
        assertTrue(erro.getMessage().contains("longitude"), erro.getMessage());
        assertThrows(IllegalArgumentException.class, () -> new Coordenada(0, -180.1));
    }

    @Test
    @DisplayName("recusa NaN")
    void recusaNaN() {
        assertThrows(IllegalArgumentException.class, () -> new Coordenada(Double.NaN, 0));
        assertThrows(IllegalArgumentException.class, () -> new Coordenada(0, Double.NaN));
    }

    @Test
    @DisplayName("aceita os limites exatos do intervalo")
    void aceitaOsLimites() {
        new Coordenada(90, 180);
        new Coordenada(-90, -180);
    }

    @Test
    @DisplayName("talvez(): origem sem o dado devolve VAZIO, nunca 0,0")
    void talvezDevolveVazioQuandoFalta() {
        assertTrue(Coordenada.talvez(null, -46.65).isEmpty());
        assertTrue(Coordenada.talvez(-23.56, null).isEmpty());
        assertTrue(Coordenada.talvez(null, null).isEmpty());

        // É este o caso real: 1 dos 6 CEPs medidos na BrasilAPI voltou sem `location`.
        Optional<Coordenada> semDado = Coordenada.talvez(null, null);
        assertTrue(semDado.isEmpty(),
                "coordenada ausente tem de ser AUSENCIA; 0,0 poria o endereco no oceano");
    }

    @Test
    @DisplayName("talvez(): valor presente porem invalido LANCA — ausente e errado sao coisas diferentes")
    void talvezDistingueAusenteDeErrado() {
        assertThrows(IllegalArgumentException.class, () -> Coordenada.talvez(999.0, 0.0));
    }

    @Test
    @DisplayName("o par (0,0) e ACEITO neste peer, de proposito — nao 'consertar'")
    void nullIslandEhAceitoNestePeerDePropósito() {
        // O null island (Golfo da Guine) e destino classico de coordenada que faltou.
        // Recusa-lo AQUI seria errado: um evento natural da NASA pode legitimamente
        // ocorrer em alto-mar sobre aquele ponto, e a guarda descartaria dado verdadeiro.
        // A regra "endereco de cliente nunca fica em (0,0)" e verdadeira, mas e regra
        // DO ENDERECO — mora na fatia, com o CHECK correspondente no banco.
        Coordenada aceita = new Coordenada(0, 0);
        assertEquals(0, aceita.latitude());
        assertEquals(0, aceita.longitude());
    }
}
