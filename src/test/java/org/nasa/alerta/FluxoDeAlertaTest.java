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
import org.nasa.persistencia.infrastructure.adapters.InstanteEmTexto;

import java.time.Instant;
import org.nasa.persistencia.infrastructure.adapters.InstanteEmTexto;

import java.time.Instant;

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

    /**
     * Um inscrito com nome, e-mail e posição — o que antes exigia TRÊS inserções.
     *
     * <p>Este método é a medida da simplificação: cliente + endereço + contato de
     * emergência, com duas tabelas de ligação, viraram uma linha. A montagem do teste
     * encolheu junto com o modelo, e é o mesmo cenário.</p>
     */
    private long inserirInscrito(String sufixo, double lat, double lon) throws SQLException {
        return inserirRetornandoId("""
                INSERT INTO inscrito (nome, email, cep, latitude, longitude, raio_km, criado_em)
                VALUES (?, ?, '01310200', ?, ?, 100.0, ?) RETURNING id""",
                ps -> {
                    ps.setString(1, "Alerta" + sufixo);
                    ps.setString(2, sufixo + "@exemplo.test");
                    ps.setDouble(3, lat);
                    ps.setDouble(4, lon);
                    ps.setString(5, InstanteEmTexto.de(Instant.now()));
                });
    }

    private long inserirEvento(String sufixo, double lat, double lon) throws SQLException {
        return inserirRetornandoId("""
                INSERT INTO evento_natural (eonet_id, titulo, categoria, ocorrido_em,
                                            latitude, longitude, criado_em, sincronizado_em)
                VALUES (?, 'Incendio de teste', 'wildfires', ?, ?, ?, ?, ?) RETURNING id""",
                ps -> {
                    ps.setString(1, "EONET_FLUXO_" + sufixo);
                    ps.setString(2, InstanteEmTexto.de(Instant.now().minusSeconds(7200)));
                    ps.setDouble(3, lat);
                    ps.setDouble(4, lon);
                    String agora = InstanteEmTexto.de(Instant.now());
                    ps.setString(5, agora);
                    ps.setString(6, agora);
                });
    }

    // ------------------------------------------------------------------ teste

    @Test
    @Transactional
    @DisplayName("A CADEIA INTEIRA: evento perto vira aviso; longe NAO vira")
    void aCadeiaInteira() throws Exception {
        String marca = marcaUnica();

        // Perto: cliente com endereco a ~1 km do evento, com contato de emergencia.
        long inscritoPerto = inserirInscrito("perto" + marca, LAT_SE, LON_SE);

        // CONTROLE POSITIVO — o LONGE. Identico em tudo, exceto a ~440 km (Rio de
        // Janeiro). Sem este caso, uma varredura que avisasse TODO MUNDO passaria.
        long inscritoLonge = inserirInscrito("longe" + marca + "@exemplo.com".replace("@exemplo.com", ""), -22.9068, -43.1729);

        long evento = inserirEvento(marca, LAT_SE + 0.01, LON_SE);

        // ---- varredura
        var r1 = varrer.executar(100.0, 30);
        System.out.println("[FLUXO] varredura 1: " + r1);
        assertTrue(r1.novos() >= 1, "o inscrito perto tinha de gerar aviso");

        // A base de teste e COMPARTILHADA entre as classes, e outras inserem eventos
        // perto daqui. Contar o total de avisos do cliente mediria a poluicao das outras;
        // o que este teste tem a provar e sobre o SEU evento.
        var doMeuEvento = avisosDe(inscritoPerto, evento);
        assertEquals(1, doMeuEvento, "esperava UM aviso do inscrito perto para ESTE evento");
        assertEquals(SituacaoAlerta.PENDENTE,
                consultar.doInscrito(inscritoPerto, 0, 50).stream()
                        .filter(a2 -> a2.eventoId() == evento).findFirst().orElseThrow()
                        .situacao(),
                "o aviso nasce PENDENTE: gravar antes de enviar e o que torna o envio "
                        + "seguro de repetir");

        assertTrue(consultar.doInscrito(inscritoLonge, 0, 10).isEmpty(),
                "o inscrito a 440 km foi avisado: a geodesia nao esta filtrando, e o "
                        + "alerta virou spam");

        // ---- IDEMPOTENCIA: a segunda varredura NAO pode gerar nada novo
        var r2 = varrer.executar(100.0, 30);
        System.out.println("[FLUXO] varredura 2: " + r2);
        assertEquals(1, avisosDe(inscritoPerto, evento),
                "a segunda varredura duplicou o aviso: uma tempestade de cinco dias "
                        + "viraria cinco mensagens para a mesma pessoa");
        assertTrue(r2.jaExistiam() >= 1, "a repeticao tem de ser CONTADA, nao silenciosa");

        // ---- despacho
        var d = despachar.executar(50);
        System.out.println("[FLUXO] despacho: " + d);
        assertTrue(d.tentados() >= 1);
        assertFalse(d.entregaDeVerdade(),
                "sem servidor de e-mail, o resultado TEM de declarar que nao entrega");

        Alerta depois = consultar.doInscrito(inscritoPerto, 0, 50).stream()
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
    @DisplayName("CONTROLE POSITIVO: inscricao CANCELADA nao vira destinatario")
    void canceladaNaoViraDestinatario() throws Exception {
        // O QUE ESTE TESTE SUBSTITUIU. Antes ele provava que um contato do tipo
        // PRINCIPAL, e nao EMERGENCIA, ficava de fora dos avisos. Esse conceito saiu
        // com a fatia `contato`: agora nao ha tipo de contato, ha inscricao.
        //
        // O EQUIVALENTE no modelo novo e o cancelamento, e a invariante e a mesma:
        // existe um registro no banco, com posicao valida, a poucos quilometros do
        // evento — e ele NAO pode ser avisado. Sem esta guarda, cancelar viraria um
        // botao decorativo, e quem pediu para sair continuaria recebendo.
        String marca = marcaUnica();
        long cancelado = inserirInscrito("cancelado" + marca, LAT_SE, LON_SE);
        executar("UPDATE inscrito SET cancelado_em = ? WHERE id = ?",
                ps -> {
                    ps.setString(1, InstanteEmTexto.de(Instant.now()));
                    ps.setLong(2, cancelado);
                });

        long evento = inserirEvento("CANC" + marca, LAT_SE, LON_SE);

        var r = varrer.executar(100.0, 30);
        System.out.println("[FLUXO] varredura com inscrito cancelado: " + r);

        assertTrue(consultar.doInscrito(cancelado, 0, 10).isEmpty(),
                "o inscrito CANCELADO foi avisado: o cancelamento virou botao decorativo, "
                        + "e quem pediu para sair continua recebendo");

        // CONTROLE DO CONTROLE: um inscrito ATIVO no MESMO ponto tem de ser avisado.
        // Sem ele, a asercao acima passaria com uma varredura que nao avisa ninguem.
        long ativo = inserirInscrito("ativo" + marca, LAT_SE, LON_SE);
        varrer.executar(100.0, 30);
        assertFalse(consultar.doInscrito(ativo, 0, 10).isEmpty(),
                "nem o inscrito ATIVO no mesmo ponto foi avisado — a varredura nao esta "
                        + "avisando ninguem, e o teste acima nao prova nada");
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
    private long avisosDe(long inscritoId, long eventoId) {
        return consultar.doInscrito(inscritoId, 0, 50).stream()
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
