package org.nasa.geo.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nasa.geo.domain.exceptions.RaioInvalidoException;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Prova da caixa que vai para a NASA — e dos casos em que o legado produzia caixa inválida.
 *
 * <p><b>PROPÓSITO.</b> A caixa é o filtro grosseiro da consulta. Se ela sair menor que o
 * círculo, eventos reais somem antes de qualquer regra rodar; se sair inválida, a API
 * recusa a consulta inteira e o alerta não roda — nos dois casos, <b>sem erro visível</b>.</p>
 */
@DisplayName("CaixaDelimitadora — sempre igual ou maior que o circulo, nunca invalida")
class CaixaDelimitadoraTest {

    private static final Coordenada SAO_PAULO = new Coordenada(-23.5505, -46.6333);

    @Test
    @DisplayName("a caixa CONTEM o circulo: pontos no limite do raio caem dentro dela")
    void aCaixaContemOCirculo() {
        double raio = 50;
        var caixa = CaixaDelimitadora.emVoltaDe(SAO_PAULO, raio);
        System.out.println("[GEO] caixa 50km SP = " + caixa.comoParametroEonet());

        // Quatro pontos exatamente no raio, nas quatro direções. Todos têm de estar
        // dentro da caixa — senão o filtro grosseiro descartaria evento legítimo.
        double grausLat = Math.toDegrees(raio / Geodesia.RAIO_DA_TERRA_KM);
        double grausLon = grausLat / Math.cos(Math.toRadians(SAO_PAULO.latitude()));
        assertTrue(caixa.contem(new Coordenada(SAO_PAULO.latitude() + grausLat, SAO_PAULO.longitude())), "norte");
        assertTrue(caixa.contem(new Coordenada(SAO_PAULO.latitude() - grausLat, SAO_PAULO.longitude())), "sul");
        assertTrue(caixa.contem(new Coordenada(SAO_PAULO.latitude(), SAO_PAULO.longitude() + grausLon)), "leste");
        assertTrue(caixa.contem(new Coordenada(SAO_PAULO.latitude(), SAO_PAULO.longitude() - grausLon)), "oeste");
    }

    @Test
    @DisplayName("o centro esta dentro, e um ponto bem longe esta fora")
    void centroDentroLongeFora() {
        var caixa = CaixaDelimitadora.emVoltaDe(SAO_PAULO, 50);
        assertTrue(caixa.contem(SAO_PAULO));
        assertTrue(!caixa.contem(new Coordenada(-3.119, -60.0217)), "Manaus nao cabe em 50km de SP");
    }

    @Test
    @DisplayName("POLO: a caixa vira o globo inteiro em vez de estourar a longitude")
    void perotoDoPoloAbreOGlobo() {
        // O legado dividia por cos(latitude) sem tratar o polo: perto de 90 graus o
        // cosseno tende a zero e a largura explode, produzindo caixa que a API recusa.
        var caixa = CaixaDelimitadora.emVoltaDe(new Coordenada(89.9, 0), 100);
        System.out.println("[GEO] caixa polar = " + caixa.comoParametroEonet());
        assertEquals(-180.0, caixa.oeste());
        assertEquals(180.0, caixa.leste());
        assertTrue(caixa.norte() <= 90.0, "latitude nunca passa de 90");
        assertTrue(caixa.sul() >= -90.0);
    }

    @Test
    @DisplayName("ANTIMERIDIANO: alarga para o globo em vez de partir a caixa em duas")
    void antimeridianoAlarga() {
        // A EONET nao aceita caixa que cruza os 180 graus. Partir em duas perderia
        // metade dos eventos em silencio; alargar traz alguns a mais, e o filtro exato
        // de distancia descarta. Erra para o lado de trazer MAIS.
        var caixa = CaixaDelimitadora.emVoltaDe(new Coordenada(0, 179.9), 100);
        System.out.println("[GEO] caixa no antimeridiano = " + caixa.comoParametroEonet());
        assertEquals(-180.0, caixa.oeste());
        assertEquals(180.0, caixa.leste());
    }

    @Test
    @DisplayName("latitude e grampeada em [-90, 90] mesmo com raio enorme")
    void latitudeGrampeada() {
        var caixa = CaixaDelimitadora.emVoltaDe(new Coordenada(0, 0), 20000);
        assertTrue(caixa.norte() <= 90.0 && caixa.sul() >= -90.0,
                "latitude fora do limite faz a API recusar a consulta inteira");
    }

    @Test
    @DisplayName("raio zero ou negativo LANCA — zero e configuracao que nao foi lida")
    void raioInvalidoLanca() {
        var erro = assertThrows(RaioInvalidoException.class,
                () -> CaixaDelimitadora.emVoltaDe(SAO_PAULO, 0));
        System.out.println("[GEO] " + erro.linhaDeLog());
        assertThrows(RaioInvalidoException.class, () -> CaixaDelimitadora.emVoltaDe(SAO_PAULO, -1));
        assertThrows(RaioInvalidoException.class, () -> CaixaDelimitadora.emVoltaDe(SAO_PAULO, Double.NaN));
    }

    @Test
    @DisplayName("o parametro da EONET usa PONTO decimal, mesmo em JVM pt-BR")
    void formatoComPontoDecimal() {
        Locale anterior = Locale.getDefault();
        try {
            // Sem Locale.US explicito, uma JVM em pt-BR formataria "-23,55" com virgula,
            // e a API leria a caixa errada — ou recusaria a consulta. E o defeito so
            // apareceria na maquina de quem tem o idioma diferente do de quem escreveu.
            Locale.setDefault(Locale.forLanguageTag("pt-BR"));
            String p = CaixaDelimitadora.emVoltaDe(SAO_PAULO, 50).comoParametroEonet();
            System.out.println("[GEO] parametro sob pt-BR = " + p);
            // O parametro TEM virgulas — sao os 3 separadores dos 4 numeros. O que ele
            // nao pode ter e virgula DECIMAL, que somaria mais 4. Contar e o jeito
            // preciso de dizer isso; "nao contem virgula" seria falso por construcao.
            assertEquals(3L, p.chars().filter(ch -> ch == ',').count(),
                    "3 virgulas = so separadores; 7 = a JVM formatou decimal com virgula: " + p);
            assertEquals(4L, p.chars().filter(ch -> ch == '.').count(),
                    "os 4 numeros precisam de ponto decimal: " + p);
        } finally {
            Locale.setDefault(anterior);
        }
    }
}
