package org.nasa.evento.infrastructure.adapters;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nasa.geo.domain.Coordenada;
import org.nasa.geo.domain.Geodesia;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Prova da leitura da EONET — as duas armadilhas, com a resposta REAL da NASA.
 *
 * <p><b>PROPÓSITO.</b> Estas duas são as que não dão erro nenhum quando erradas: o evento
 * entra no banco, a tela desenha um pino, tudo parece funcionar, e o alerta decide sobre
 * um lugar que não é onde a coisa está. Só um teste comparando posições revela.</p>
 *
 * <p>O corpo abaixo é o <b>real</b>, medido em 02/09/2026 contra
 * {@code eonet.gsfc.nasa.gov/api/v3/events} — não inventado. Corpo imaginário provaria
 * que o código lê o que eu imaginei, não o que a NASA manda.</p>
 */
@DisplayName("leitura da EONET — a geometria mais recente e a ordem [lon, lat]")
class EonetApiAdapterTest {

    /**
     * Resposta REAL, com o primeiro e o último ponto de uma tempestade de seis pontos.
     *
     * <p>{@code EONET_23800} — Tropical Storm Marie, medida em 02/09/2026.</p>
     */
    private static final String CORPO_REAL = """
            {
              "events": [
                {
                  "id": "EONET_23800",
                  "title": "Tropical Storm Marie",
                  "closed": null,
                  "categories": [ { "id": "severeStorms", "title": "Severe Storms" } ],
                  "geometry": [
                    { "magnitudeValue": 40.0, "magnitudeUnit": "kts",
                      "date": "2026-09-01T06:00:00Z", "type": "Point",
                      "coordinates": [ -108.1, 14.1 ] },
                    { "magnitudeValue": 60.0, "magnitudeUnit": "kts",
                      "date": "2026-09-02T12:00:00Z", "type": "Point",
                      "coordinates": [ -111.3, 16.8 ] }
                  ]
                }
              ]
            }""";

    private static EonetApiAdapter adaptador() {
        var a = new EonetApiAdapter();
        a.json = new ObjectMapper();
        return a;
    }

    @Test
    @DisplayName("456 KM: usa o ponto MAIS RECENTE, nao o primeiro como o legado")
    void usaAPosicaoMaisRecente() {
        // O legado fazia `getGeometry().get(0)` — onde a tempestade COMECOU. Este teste
        // e a prova de que aqui e diferente, e o numero abaixo e o tamanho do engano.
        var eventos = adaptador().interpretar(CORPO_REAL);

        assertEquals(1, eventos.size());
        var e = eventos.get(0);
        assertEquals("EONET_23800", e.eonetId());
        assertEquals("severeStorms", e.categoria());

        var posicao = e.coordenada();
        assertEquals(16.8, posicao.latitude(), 0.001,
                "usou o primeiro ponto (14.1) em vez do mais recente (16.8)");
        assertEquals(-111.3, posicao.longitude(), 0.001);
        assertEquals("2026-09-02T12:00:00Z", e.ocorridoEm().toString(),
                "a data tem de ser a do ponto mais recente");

        // O TAMANHO do engano, medido: e por isso que esta escolha importa.
        double erroDoLegado = Geodesia.distanciaEmKm(
                new Coordenada(14.1, -108.1), new Coordenada(16.8, -111.3));
        System.out.printf("[EONET] o primeiro ponto fica a %.0f km do atual%n", erroDoLegado);
        assertTrue(erroDoLegado > 400,
                "se este numero encolher, o caso de teste deixou de exercitar o defeito");
    }

    @Test
    @DisplayName("GeoJSON e [lon, lat]: trocar poe o evento do outro lado do planeta")
    void ordemDasCoordenadas() {
        // `[-111.3, 16.8]` e longitude -111.3 e latitude 16.8 — Pacifico, costa do
        // Mexico. Lido na ordem intuitiva daria latitude -111.3, que nem existe; mas
        // para uma coordenada como [-40, -20] os DOIS numeros sao validos, e a inversao
        // passaria sem excecao nenhuma. Por isso a ordem se prova, nao se confia.
        var e = adaptador().interpretar(CORPO_REAL).get(0);
        assertTrue(e.coordenada().latitude() >= -90 && e.coordenada().latitude() <= 90);
        assertEquals(16.8, e.coordenada().latitude(), 0.001, "latitude e o SEGUNDO elemento");
        assertEquals(-111.3, e.coordenada().longitude(), 0.001, "longitude e o PRIMEIRO");
    }

    @Test
    @DisplayName("evento ATIVO participa do alerta; encerrado NAO")
    void ativoParticipaDoAlerta() {
        var ativo = adaptador().interpretar(CORPO_REAL).get(0);
        assertTrue(ativo.ativo());
        assertTrue(ativo.participaDoAlertaDeProximidade());

        var encerrado = adaptador().interpretar(
                CORPO_REAL.replace("\"closed\": null", "\"closed\": \"2026-09-03T00:00:00Z\""))
                .get(0);
        assertFalse(encerrado.ativo());
        assertFalse(encerrado.participaDoAlertaDeProximidade(),
                "incendio apagado ha tres semanas nao pode avisar ninguem");
        assertTrue(encerrado.motivoForaDoAlerta().contains("encerrado"));
    }

    @Test
    @DisplayName("POLIGONO nao vira ponto: entra SEM coordenada, com o motivo dito")
    void poligonoNaoViraPonto() {
        // Reduzir uma area a um ponto exigiria escolher um centro que a NASA nao
        // declarou — e esse centro entraria no alerta como se tivesse sido medido.
        var e = adaptador().interpretar(CORPO_REAL.replace("\"Point\"", "\"Polygon\"")).get(0);
        assertFalse(e.participaDoAlertaDeProximidade());
        assertTrue(e.motivoForaDoAlerta().contains("coordenada"),
                "a tela precisa DIZER que este evento nao entra no alerta: " + e.motivoForaDoAlerta());
    }

    @Test
    @DisplayName("um evento torto NAO derruba o lote — e contado e pulado")
    void eventoTortoNaoDerrubaOLote() {
        // Perder a sincronizacao inteira por causa de um evento trocaria um problema
        // pequeno por um apagao de dados.
        String comUmTorto = CORPO_REAL.replace("\"events\": [",
                "\"events\": [ { \"id\": \"EONET_TORTO\", \"title\": \"sem geometria\","
                        + " \"categories\": [], \"geometry\": [] },");
        var eventos = adaptador().interpretar(comUmTorto);
        assertEquals(1, eventos.size(), "o evento bom tinha de entrar mesmo com um torto ao lado");
        assertEquals("EONET_23800", eventos.get(0).eonetId());
    }

    @Test
    @DisplayName("CONTROLE POSITIVO: corpo que nao e da EONET vira excecao PROPRIA")
    void corpoIlegivelViraExcecaoPropria() {
        // Sem exceção própria, uma mudança de contrato viraria "provedor indisponível" —
        // e alguém investigaria rede e firewall enquanto a NASA respondia 200.
        assertThrows(RespostaDaNasaIlegivelException.class,
                () -> adaptador().interpretar("{ isto nao e json"));
        var erro = assertThrows(RespostaDaNasaIlegivelException.class,
                () -> adaptador().interpretar("{\"outra\":\"coisa\"}"));
        System.out.println("[EONET] " + erro.linhaDeLog());
        assertTrue(erro.getMessage().contains("contrato"),
                "a mensagem tem de mandar olhar o contrato, nao a rede");
    }
}
