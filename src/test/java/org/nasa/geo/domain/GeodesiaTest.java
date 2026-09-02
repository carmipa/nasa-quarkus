package org.nasa.geo.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Prova da conta que decide se alguém é avisado.
 *
 * <p><b>PROPÓSITO.</b> Distância errada não produz erro visível — produz alerta que não
 * chega, ou que chega a quem mora longe. Os números abaixo são distâncias reais
 * conhecidas, com tolerância declarada: é assim que se prova que a fórmula é a certa, e
 * não apenas que ela roda.</p>
 */
@DisplayName("Geodesia — distancia real, conferida contra distancias conhecidas")
class GeodesiaTest {

    // Pontos reais, para conferir contra distância publicada.
    private static final Coordenada SAO_PAULO = new Coordenada(-23.5505, -46.6333);
    private static final Coordenada RIO = new Coordenada(-22.9068, -43.1729);
    private static final Coordenada MANAUS = new Coordenada(-3.1190, -60.0217);

    @Test
    @DisplayName("Sao Paulo -> Rio: ~357 km em linha reta")
    void saoPauloRio() {
        double km = Geodesia.distanciaEmKm(SAO_PAULO, RIO);
        System.out.println("[GEO] SP->Rio = " + Math.round(km) + " km");
        // A distância geodésica publicada é ~357 km. 5 km de folga cobre a diferença
        // entre esfera e elipsoide, que é o que a aproximação custa.
        assertEquals(357.0, km, 5.0);
    }

    @Test
    @DisplayName("Sao Paulo -> Manaus: ~2.690 km, para pegar erro de escala")
    void saoPauloManaus() {
        double km = Geodesia.distanciaEmKm(SAO_PAULO, MANAUS);
        System.out.println("[GEO] SP->Manaus = " + Math.round(km) + " km");
        // Distância longa serve para pegar erro proporcional: uma fórmula plana em graus
        // acertaria a curta e erraria esta por centenas de quilômetros.
        assertEquals(2689.0, km, 25.0);
    }

    @Test
    @DisplayName("o mesmo ponto dista ZERO de si mesmo")
    void mesmoPontoDistaZero() {
        assertEquals(0.0, Geodesia.distanciaEmKm(SAO_PAULO, SAO_PAULO), 0.0001);
    }

    @Test
    @DisplayName("a distancia e SIMETRICA — ida e volta dao o mesmo numero")
    void simetrica() {
        assertEquals(Geodesia.distanciaEmKm(SAO_PAULO, RIO),
                Geodesia.distanciaEmKm(RIO, SAO_PAULO), 0.0001,
                "assimetria aqui faria o mesmo par de pontos ter duas distancias");
    }

    @Test
    @DisplayName("antipodas: metade da circunferencia, sem NaN")
    void antipodas() {
        // O caso que quebra implementacoes ingenuas: erro de ponto flutuante empurra o
        // argumento do arco-seno acima de 1 e a conta devolve NaN — que passaria por
        // qualquer comparacao "menor que o raio" como FALSO, em silencio.
        double km = Geodesia.distanciaEmKm(new Coordenada(0, 0), new Coordenada(0, 180));
        System.out.println("[GEO] antipodas = " + Math.round(km) + " km");
        assertFalse(Double.isNaN(km), "NaN passaria despercebido como 'fora do raio'");
        assertEquals(Math.PI * Geodesia.RAIO_DA_TERRA_KM, km, 1.0);
    }

    @Test
    @DisplayName("dentroDoRaio: a borda EXATA conta como dentro")
    void bordaContaComoDentro() {
        double km = Geodesia.distanciaEmKm(SAO_PAULO, RIO);
        assertTrue(Geodesia.dentroDoRaio(SAO_PAULO, RIO, km),
                "na duvida entre avisar e nao avisar, um sistema de desastre avisa");
        assertTrue(Geodesia.dentroDoRaio(SAO_PAULO, RIO, km + 0.001));
        assertFalse(Geodesia.dentroDoRaio(SAO_PAULO, RIO, km - 0.001));
    }

    @Test
    @DisplayName("um grau de longitude vale menos longe do Equador — a prova de que nao e plano")
    void grauDeLongitudeEncolheComALatitude() {
        double noEquador = Geodesia.distanciaEmKm(new Coordenada(0, 0), new Coordenada(0, 1));
        double noSul = Geodesia.distanciaEmKm(new Coordenada(-60, 0), new Coordenada(-60, 1));
        System.out.println("[GEO] 1 grau de longitude: equador=" + Math.round(noEquador)
                + "km  lat-60=" + Math.round(noSul) + "km");
        assertTrue(noSul < noEquador / 1.8,
                "tratar graus como plano erraria por centenas de km no sul do pais");
    }
}
