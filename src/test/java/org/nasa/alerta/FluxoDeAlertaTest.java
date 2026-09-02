package org.nasa.alerta;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nasa.alerta.application.ConsultarAlertasUseCase;
import org.nasa.alerta.application.DespacharAlertasUseCase;
import org.nasa.alerta.application.VarrerEGerarAlertasUseCase;
import org.nasa.alerta.domain.Alerta;
import org.nasa.alerta.domain.SituacaoAlerta;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Prova do fluxo INTEIRO de alerta, no PostgreSQL de verdade.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO.</b> Este é o teste que responde "o sistema faz o que promete
 * fazer?". Cliente cadastrado, endereço com coordenada, contato de emergência, evento da
 * NASA por perto — e um aviso registrado no fim. Todo o resto do projeto existe para esta
 * cadeia funcionar.</p>
 *
 * <p><b>Os dados são inseridos por SQL direto</b>, de propósito: o teste é da fatia
 * {@code alerta}, e usar os casos de uso das outras fatias faria uma falha delas reprovar
 * este teste — que passaria a acusar o lugar errado.</p>
 *
 * <p><b>O CASO DO LONGE É O CONTROLE POSITIVO.</b> Um cliente idêntico ao primeiro em tudo,
 * exceto por estar a centenas de quilômetros, existe para reprovar uma varredura que
 * simplesmente avise todo mundo. Sem ele, um {@code WHERE} quebrado passaria neste
 * arquivo inteiro.</p>
 */
@QuarkusTest
@DisplayName("fluxo de alerta — do evento da NASA ate o aviso registrado")
class FluxoDeAlertaTest {

    /** Praça da Sé, São Paulo. */
    private static final double LAT_SE = -23.5505;
    private static final double LON_SE = -46.6333;

    @Inject
    DataSource dataSource;

    @Inject
    VarrerEGerarAlertasUseCase varrer;

    @Inject
    DespacharAlertasUseCase despachar;

    @Inject
    ConsultarAlertasUseCase consultar;

    // ------------------------------------------------------------- montagem

    private long inserirCliente(String sufixo) throws SQLException {
        return inserirRetornandoId("""
                INSERT INTO cliente (nome, sobrenome, data_nascimento, documento, criado_em)
                VALUES (?, 'Teste', DATE '1990-01-01', ?, ?) RETURNING id""",
                ps -> {
                    ps.setString(1, "Alerta" + sufixo);
                    ps.setString(2, sufixo);
                    ps.setObject(3, OffsetDateTime.now(ZoneOffset.UTC));
                });
    }

    private long inserirEndereco(long clienteId, double lat, double lon) throws SQLException {
        long id = inserirRetornandoId("""
                INSERT INTO endereco (cep, logradouro, localidade, uf, latitude, longitude, criado_em)
                VALUES ('01310200', 'Rua de teste', 'Sao Paulo', 'SP', ?, ?, ?) RETURNING id""",
                ps -> {
                    ps.setDouble(1, lat);
                    ps.setDouble(2, lon);
                    ps.setObject(3, OffsetDateTime.now(ZoneOffset.UTC));
                });
        executar("INSERT INTO cliente_endereco (cliente_id, endereco_id) VALUES (?, ?)",
                ps -> { ps.setLong(1, clienteId); ps.setLong(2, id); });
        return id;
    }

    private long inserirContatoDeEmergencia(long clienteId, String email) throws SQLException {
        long id = inserirRetornandoId("""
                INSERT INTO contato (email, tipo_contato, criado_em)
                VALUES (?, 'EMERGENCIA', ?) RETURNING id""",
                ps -> {
                    ps.setString(1, email);
                    ps.setObject(2, OffsetDateTime.now(ZoneOffset.UTC));
                });
        executar("INSERT INTO cliente_contato (cliente_id, contato_id) VALUES (?, ?)",
                ps -> { ps.setLong(1, clienteId); ps.setLong(2, id); });
        return id;
    }

    private long inserirEvento(String sufixo, double lat, double lon) throws SQLException {
        return inserirRetornandoId("""
                INSERT INTO evento_natural (eonet_id, titulo, categoria, ocorrido_em,
                                            latitude, longitude, sincronizado_em)
                VALUES (?, 'Incendio de teste', 'wildfires', ?, ?, ?, ?) RETURNING id""",
                ps -> {
                    ps.setString(1, "EONET_FLUXO_" + sufixo);
                    ps.setObject(2, OffsetDateTime.now(ZoneOffset.UTC).minusHours(2));
                    ps.setDouble(3, lat);
                    ps.setDouble(4, lon);
                    ps.setObject(5, OffsetDateTime.now(ZoneOffset.UTC));
                });
    }

    // ------------------------------------------------------------------ teste

    @Test
    @Transactional
    @DisplayName("A CADEIA INTEIRA: evento perto vira aviso; longe NAO vira")
    void aCadeiaInteira() throws Exception {
        String marca = marcaUnica();

        // Perto: cliente com endereco a ~1 km do evento, com contato de emergencia.
        long clientePerto = inserirCliente(marca);
        inserirEndereco(clientePerto, LAT_SE, LON_SE);
        inserirContatoDeEmergencia(clientePerto, "perto" + marca + "@exemplo.com");

        // CONTROLE POSITIVO — o LONGE. Identico em tudo, exceto a ~440 km (Rio de
        // Janeiro). Sem este caso, uma varredura que avisasse TODO MUNDO passaria.
        long clienteLonge = inserirCliente("9" + marca.substring(1));
        inserirEndereco(clienteLonge, -22.9068, -43.1729);
        inserirContatoDeEmergencia(clienteLonge, "longe" + marca + "@exemplo.com");

        long evento = inserirEvento(marca, LAT_SE + 0.01, LON_SE);

        // ---- varredura
        var r1 = varrer.executar(100.0, 30);
        System.out.println("[FLUXO] varredura 1: " + r1);
        assertTrue(r1.novos() >= 1, "o cliente perto tinha de gerar aviso");

        // A base de teste e COMPARTILHADA entre as classes, e outras inserem eventos
        // perto daqui. Contar o total de avisos do cliente mediria a poluicao das outras;
        // o que este teste tem a provar e sobre o SEU evento.
        var doMeuEvento = avisosDe(clientePerto, evento);
        assertEquals(1, doMeuEvento, "esperava UM aviso do cliente perto para ESTE evento");
        assertEquals(SituacaoAlerta.PENDENTE,
                consultar.doCliente(clientePerto, 0, 50).stream()
                        .filter(a2 -> a2.eventoId() == evento).findFirst().orElseThrow()
                        .situacao(),
                "o aviso nasce PENDENTE: gravar antes de enviar e o que torna o envio "
                        + "seguro de repetir");

        assertTrue(consultar.doCliente(clienteLonge, 0, 10).isEmpty(),
                "o cliente a 440 km foi avisado: a geodesia nao esta filtrando, e o "
                        + "alerta virou spam");

        // ---- IDEMPOTENCIA: a segunda varredura NAO pode gerar nada novo
        var r2 = varrer.executar(100.0, 30);
        System.out.println("[FLUXO] varredura 2: " + r2);
        assertEquals(1, avisosDe(clientePerto, evento),
                "a segunda varredura duplicou o aviso: uma tempestade de cinco dias "
                        + "viraria cinco mensagens para a mesma pessoa");
        assertTrue(r2.jaExistiam() >= 1, "a repeticao tem de ser CONTADA, nao silenciosa");

        // ---- despacho
        var d = despachar.executar(50);
        System.out.println("[FLUXO] despacho: " + d);
        assertTrue(d.tentados() >= 1);
        assertFalse(d.entregaDeVerdade(),
                "sem servidor de e-mail, o resultado TEM de declarar que nao entrega");

        Alerta depois = consultar.doCliente(clientePerto, 0, 50).stream()
                .filter(a2 -> a2.eventoId() == evento).findFirst().orElseThrow();
        assertEquals(SituacaoAlerta.ENVIADO, depois.situacao());
        assertEquals(1, depois.tentativas(), "tentativas sempre incrementa");
        assertTrue(depois.concluidoEm() != null,
                "estado terminal exige instante: 'enviado' sem quando deixa a auditoria "
                        + "sem fechar");

        // ---- a tela nao pode mentir
        var meio = consultar.meioDeEntrega();
        assertFalse(meio.entregaDeVerdade());
        assertTrue(meio.ressalva().contains("NAO chegam"),
                "o painel precisa dizer que 'ENVIADO' aqui significa REGISTRADO: "
                        + meio.ressalva());
        System.out.println("[FLUXO] meio de entrega: " + meio);
    }

    @Test
    @Transactional
    @DisplayName("contato que NAO e de emergencia nao vira destinatario")
    void soEmergenciaViraDestinatario() throws Exception {
        String marca = marcaUnica();
        long cliente = inserirCliente("8" + marca.substring(1));
        inserirEndereco(cliente, LAT_SE, LON_SE);

        // PRINCIPAL, nao EMERGENCIA: ninguem o inscreveu em avisos de desastre.
        long id = inserirRetornandoId("""
                INSERT INTO contato (email, tipo_contato, criado_em)
                VALUES (?, 'PRINCIPAL', ?) RETURNING id""",
                ps -> {
                    ps.setString(1, "principal" + marca + "@exemplo.com");
                    ps.setObject(2, OffsetDateTime.now(ZoneOffset.UTC));
                });
        executar("INSERT INTO cliente_contato (cliente_id, contato_id) VALUES (?, ?)",
                ps -> { ps.setLong(1, cliente); ps.setLong(2, id); });

        inserirEvento("SOEMERG" + marca, LAT_SE + 0.01, LON_SE);
        varrer.executar(100.0, 30);

        assertTrue(consultar.doCliente(cliente, 0, 50).isEmpty(),
                "um contato PRINCIPAL recebeu alerta de desastre sem ter sido inscrito nisso");
    }

    @Test
    @DisplayName("o destino sai MASCARADO — a auditoria e uma tela que se mostra a outros")
    void destinoSaiMascarado() {
        assertEquals("pa***@exemplo.com", Alerta.mascarar("paulo@exemplo.com"));
        assertEquals("(sem destino)", Alerta.mascarar(null));
        assertEquals("***", Alerta.mascarar("semarroba"));
        // Curto demais para mascarar mantendo utilidade: mostra o que tem e marca.
        assertEquals("ab***@x.com", Alerta.mascarar("ab@x.com"));
    }

    // ------------------------------------------------------------------ apoio

    /** Quantos avisos este cliente tem para ESTE evento — imune a poluicao das outras classes. */
    private long avisosDe(long clienteId, long eventoId) {
        return consultar.doCliente(clienteId, 0, 50).stream()
                .filter(a -> a.eventoId() == eventoId).count();
    }

    /** Onze digitos, SEMPRE — o documento e a marca dos e-mails dependem da largura. */
    private static String marcaUnica() {
        return String.format("%011d", System.nanoTime() % 100_000_000_000L);
    }

    private interface Parametros {
        void aplicar(PreparedStatement ps) throws SQLException;
    }

    private long inserirRetornandoId(String sql, Parametros p) throws SQLException {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            p.aplicar(ps);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong("id");
            }
        }
    }

    private void executar(String sql, Parametros p) throws SQLException {
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            p.aplicar(ps);
            ps.executeUpdate();
        }
    }

    @SuppressWarnings("unused")
    private static Instant agora() {
        return Instant.now();
    }
}
