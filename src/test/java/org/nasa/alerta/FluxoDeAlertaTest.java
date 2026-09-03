package org.nasa.alerta;

import io.agroal.api.AgroalDataSource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nasa.alerta.domain.ports.LeituraDeDesastresProximosPort;
import org.nasa.geo.domain.Coordenada;
import org.nasa.persistencia.infrastructure.adapters.InstanteEmTexto;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A cadeia inteira, sem nada ser gravado.
 *
 * <p><b>PROPÓSITO.</b> Prova a pergunta que o sistema existe para responder: <i>"tem desastre
 * perto daqui?"</i>. Um evento a poucos quilômetros vira linha na mensagem; um a centenas de
 * quilômetros <b>não</b> vira — e é esse segundo caso que faz o teste valer alguma coisa.</p>
 *
 * <p><b>O CASO DO LONGE É O CONTROLE POSITIVO.</b> Sem ele, um sistema que colocasse
 * <b>todo</b> evento na mensagem passaria no primeiro caso e o teste diria que funciona. Ele
 * fica a 440 km — dentro da caixa de 500 km que o índice usa, e <b>fora</b> do raio de 100 km
 * que a geodésia decide. É exatamente a faixa onde o projeto original errava.</p>
 *
 * <p><b>O QUE MUDOU NESTE TESTE.</b> Antes ele montava cliente, endereço e contato de
 * emergência — três inserções e duas tabelas de ligação — e conferia a fila de alertas
 * gravada. Agora não há cadastro nem fila: o teste insere eventos e pergunta ao modelo de
 * leitura. A montagem encolheu junto com o modelo.</p>
 */
@QuarkusTest
@DisplayName("a cadeia: perto entra na mensagem, longe NAO entra")
class FluxoDeAlertaTest {

    /** Praça da Sé, São Paulo — o ponto de onde se mede. */
    private static final double LAT_SE = -23.5505;
    private static final double LON_SE = -46.6333;

    /** ~440 km a oeste. Dentro da caixa de 500 km, FORA do raio de 100 km. */
    private static final double LAT_LONGE = -23.5505;
    private static final double LON_LONGE = -50.9500;

    @Inject
    AgroalDataSource dataSource;

    @Inject
    LeituraDeDesastresProximosPort leitura;

    private static String marca() {
        return String.valueOf(System.nanoTime() % 1_000_000_000L);
    }

    private void inserirEvento(String sufixo, double lat, double lon) throws SQLException {
        String agora = InstanteEmTexto.de(Instant.now());
        String sql = """
                INSERT INTO evento_natural (eonet_id, titulo, categoria, ocorrido_em,
                                            latitude, longitude, criado_em, sincronizado_em)
                VALUES (?, 'Incendio de teste', 'wildfires', ?, ?, ?, ?, ?)""";
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, "EONET_FLUXO_" + sufixo);
            ps.setString(2, InstanteEmTexto.de(Instant.now().minusSeconds(7200)));
            ps.setDouble(3, lat);
            ps.setDouble(4, lon);
            ps.setString(5, agora);
            ps.setString(6, agora);
            ps.executeUpdate();
        }
    }

    @Test
    @DisplayName("A CADEIA: o evento PERTO entra; o de 440 km NAO entra")
    void aCadeiaInteira() throws Exception {
        String m = marca();
        inserirEvento("PERTO" + m, LAT_SE, LON_SE);
        inserirEvento("LONGE" + m, LAT_LONGE, LON_LONGE);

        var onde = new Coordenada(LAT_SE, LON_SE);
        var achados = leitura.proximos(onde, 100.0, Instant.now().minusSeconds(86400), 50);

        boolean temPerto = achados.stream()
                .anyMatch(d -> d.eonetId().equals("EONET_FLUXO_PERTO" + m));
        boolean temLonge = achados.stream()
                .anyMatch(d -> d.eonetId().equals("EONET_FLUXO_LONGE" + m));

        assertTrue(temPerto, "o evento em cima do ponto NAO entrou na mensagem");

        // O CONTROLE POSITIVO. A caixa de indice cobre 500 km; a geodesia recorta em 100.
        // Se este passar, o sistema esta alertando gente que nao esta no raio — que foi
        // exatamente o defeito do projeto original.
        assertFalse(temLonge,
                "o evento a 440 km entrou na mensagem: a caixa filtrou e a GEODESIA NAO — "
                        + "o alerta virou spam para quem esta longe");
    }

    @Test
    @DisplayName("CONTROLE POSITIVO: aumentando o raio, o de 440 km PASSA a entrar")
    void comRaioMaiorOLongeEntra() throws Exception {
        // Sem este caso, o teste acima passaria com uma leitura que NAO DEVOLVE NADA — e
        // diria que a geodesia funciona. Aqui o mesmo evento, com raio de 500 km, precisa
        // aparecer: e o que prova que ele estava sendo RECUSADO, e nao ignorado.
        String m = marca();
        inserirEvento("RAIO" + m, LAT_LONGE, LON_LONGE);

        var onde = new Coordenada(LAT_SE, LON_SE);
        var estreito = leitura.proximos(onde, 100.0, Instant.now().minusSeconds(86400), 50);
        var largo = leitura.proximos(onde, 500.0, Instant.now().minusSeconds(86400), 50);

        String id = "EONET_FLUXO_RAIO" + m;
        assertFalse(estreito.stream().anyMatch(d -> d.eonetId().equals(id)),
                "com 100 km ele nao deveria aparecer");
        assertTrue(largo.stream().anyMatch(d -> d.eonetId().equals(id)),
                "com 500 km ele deveria aparecer — se nao aparece, a leitura nao acha nada e "
                        + "o teste acima nao prova coisa alguma");
    }

    @Test
    @DisplayName("a distancia devolvida e a GEODESICA, nao a da caixa")
    void aDistanciaEhGeodesica() throws Exception {
        String m = marca();
        inserirEvento("DIST" + m, LAT_LONGE, LON_LONGE);

        var onde = new Coordenada(LAT_SE, LON_SE);
        var achados = leitura.proximos(onde, 500.0, Instant.now().minusSeconds(86400), 50);
        var alvo = achados.stream()
                .filter(d -> d.eonetId().equals("EONET_FLUXO_DIST" + m))
                .findFirst().orElseThrow();

        // ~440 km. A tolerancia e larga de proposito: o que se prova aqui e que a distancia
        // e MEDIDA, nao inventada — nao a precisao da formula, que o teste da geodesia ja
        // cobre com pares conhecidos.
        assertTrue(alvo.distanciaKm() > 400 && alvo.distanciaKm() < 480,
                "a distancia devolvida foi " + alvo.distanciaKm() + " km; o esperado e ~440");
    }

    @Test
    @DisplayName("a lista vem ORDENADA do mais proximo ao mais distante")
    void vemOrdenadaPelaProximidade() throws Exception {
        // Quem le um alerta quer saber PRIMEIRO o que esta em cima dele. Fora de ordem, o
        // assunto do e-mail nomearia um evento distante enquanto um proximo fica no fim.
        String m = marca();
        inserirEvento("ORD_A" + m, LAT_SE, LON_SE);
        inserirEvento("ORD_B" + m, LAT_LONGE, LON_LONGE);

        var achados = leitura.proximos(new Coordenada(LAT_SE, LON_SE), 500.0,
                Instant.now().minusSeconds(86400), 50);

        assertTrue(achados.size() >= 2, "precisava de pelo menos dois para julgar ordem");
        for (int i = 1; i < achados.size(); i++) {
            assertTrue(achados.get(i - 1).distanciaKm() <= achados.get(i).distanciaKm(),
                    "fora de ordem entre a posicao " + (i - 1) + " e " + i);
        }
    }

    @Test
    @DisplayName("evento FORA da janela de tempo nao entra")
    void foraDaJanelaNaoEntra() throws Exception {
        // Um desastre de dois anos atras nao e alerta, e historia — e encheria a mensagem
        // escondendo o que importa.
        String m = marca();
        String antigo = InstanteEmTexto.de(Instant.now().minusSeconds(400L * 86400));
        String agora = InstanteEmTexto.de(Instant.now());
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement("""
                     INSERT INTO evento_natural (eonet_id, titulo, categoria, ocorrido_em,
                                                 latitude, longitude, criado_em, sincronizado_em)
                     VALUES (?, 'Antigo', 'wildfires', ?, ?, ?, ?, ?)""")) {
            ps.setString(1, "EONET_FLUXO_VELHO" + m);
            ps.setString(2, antigo);
            ps.setDouble(3, LAT_SE);
            ps.setDouble(4, LON_SE);
            ps.setString(5, agora);
            ps.setString(6, agora);
            ps.executeUpdate();
        }

        var achados = leitura.proximos(new Coordenada(LAT_SE, LON_SE), 100.0,
                Instant.now().minusSeconds(30L * 86400), 50);
        assertFalse(achados.stream().anyMatch(d -> d.eonetId().equals("EONET_FLUXO_VELHO" + m)),
                "um evento de 400 dias atras entrou numa janela de 30 dias");
    }

    @Test
    @DisplayName("CONTROLE POSITIVO: a base NAO tem tabela de cadastro nem de fila")
    void naoGuardaGente() throws Exception {
        // A DECISAO QUE DEFINE ESTA FATIA, travada em teste. Sem esta guarda, alguem
        // reintroduz uma tabela de inscritos numa migracao futura e o sistema volta a
        // guardar e-mail — silenciosamente, porque nada mais reclamaria.
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(
                     "SELECT count(*) FROM sqlite_master WHERE type='table' AND name IN (?, ?)")) {
            ps.setString(1, "inscrito");
            ps.setString(2, "alerta_enviado");
            try (var rs = ps.executeQuery()) {
                rs.next();
                assertEquals(0, rs.getInt(1),
                        "voltou a existir tabela de cadastro ou de fila de alerta — o sistema "
                                + "guarda dado pessoal de novo, e ninguem foi avisado");
            }
        }
    }
}
