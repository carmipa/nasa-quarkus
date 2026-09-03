package org.nasa.persistencia;

import io.agroal.api.AgroalDataSource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nasa.persistencia.infrastructure.adapters.InstanteEmTexto;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * As invariantes que o PRÓPRIO BANCO recusa.
 *
 * <p><b>PROPÓSITO.</b> Regra que vive só no Java é convenção: ela vale enquanto todo caminho
 * de gravação lembrar dela, e um caminho novo não lembra. Regra no esquema é mecanismo — o
 * banco recusa, e não há como esquecer. Este teste prova que as regras estão <b>no banco</b>,
 * e cada caso é um {@code INSERT} que precisa <b>falhar</b>.</p>
 *
 * <p><b>O QUE MUDOU COM O SQLITE, e por que este teste ficou mais importante.</b> No
 * PostgreSQL a disciplina de UTC morava no TIPO da coluna ({@code TIMESTAMPTZ}), e tipo não
 * se esquece. O SQLite não tem tipo de data: ele aceita qualquer coisa em qualquer coluna.
 * O mecanismo passou a ser {@code CHECK (coluna LIKE '%Z')}, e <b>este teste é a prova de
 * que ele funciona</b> — sem ele, a garantia teria virado a convenção que o projeto recusou.</p>
 */
@QuarkusTest
@DisplayName("esquema do banco — as invariantes recusadas pelo proprio SQLite")
class EsquemaDoBancoTest {

    @Inject
    AgroalDataSource dataSource;

    /** Sufixo único: os testes rodam na mesma base e não podem colidir entre si. */
    private static String marca() {
        return String.valueOf(System.nanoTime() % 1_000_000_000L);
    }

    private void executar(String sql) throws SQLException {
        try (Connection c = dataSource.getConnection(); Statement st = c.createStatement()) {
            st.executeUpdate(sql);
        }
    }

    private long consultar(String sql) throws SQLException {
        try (Connection c = dataSource.getConnection();
             Statement st = c.createStatement();
             var rs = st.executeQuery(sql)) {
            return rs.next() ? rs.getLong(1) : -1;
        }
    }

    private List<String> tabelas() throws SQLException {
        List<String> nomes = new ArrayList<>();
        try (Connection c = dataSource.getConnection();
             Statement st = c.createStatement();
             var rs = st.executeQuery(
                     "SELECT name FROM sqlite_master WHERE type='table' ORDER BY name")) {
            while (rs.next()) {
                nomes.add(rs.getString(1));
            }
        }
        return nomes;
    }

    // =================================================== a migração aconteceu

    @Test
    @DisplayName("a migracao rodou no arranque e registrou a versao com instante UTC")
    void aMigracaoRodou() throws Exception {
        assertEquals(1, consultar("SELECT count(*) FROM esquema_migracao WHERE versao = 1"),
                "a V001 nao esta registrada — a migracao nao rodou");
        try (Connection c = dataSource.getConnection();
             Statement st = c.createStatement();
             var rs = st.executeQuery("SELECT aplicada_em FROM esquema_migracao WHERE versao = 1")) {
            assertTrue(rs.next());
            String quando = rs.getString(1);
            assertTrue(quando.endsWith("Z"),
                    "o instante da migracao nao esta em UTC: " + quando);
        }
    }

    @Test
    @DisplayName("CONTROLE POSITIVO: as QUATRO tabelas do dominio existem")
    void asTabelasExistem() throws Exception {
        // O DEFEITO QUE ESTE TESTE TRAVA, medido em 03/09/2026: o driver do SQLite executa
        // o PRIMEIRO comando de um script e ignora o resto, sem erro. A migracao registrou
        // "aplicada" tendo criado 1 tabela de 9 — e ficou marcada como aplicada, entao o
        // segundo arranque nao a repetiria. O banco ficaria pela metade para sempre.
        var existentes = tabelas();
        for (String esperada : new String[] { "inscrito", "evento_natural", "alerta_enviado",
                "telemetria_operacao" }) {
            assertTrue(existentes.contains(esperada),
                    "a tabela `" + esperada + "` nao existe. As que existem: " + existentes
                            + " — a migracao rodou pela metade?");
        }
    }

    // ============================================ o UTC, que virou CHECK

    @Test
    @DisplayName("CONTROLE POSITIVO: o banco RECUSA instante em hora LOCAL")
    void instanteEmHoraLocalEhRecusado() {
        // ESTA E A GUARDA QUE SUBSTITUIU O `TIMESTAMPTZ`.
        //
        // No PostgreSQL o tipo da coluna resolvia isto sozinho. O SQLite nao tem tipo de
        // data e aceita qualquer texto — entao a garantia passou a ser um CHECK exigindo
        // o `Z`. Sem este teste, a garantia seria a convencao que este projeto recusou, e
        // o defeito do log em -03:00 poderia voltar por outro caminho.
        String m = marca();
        var erro = assertThrows(SQLException.class, () -> executar("""
                INSERT INTO inscrito (nome, email, cep, criado_em)
                VALUES ('Local', 'local%s@exemplo.test', '01310100', '2026-09-03T10:00:00-03:00')
                """.formatted(m)));
        assertTrue(erro.getMessage().toUpperCase().contains("CONSTRAINT")
                        || erro.getMessage().toUpperCase().contains("CHECK"),
                "o banco recusou por outro motivo: " + erro.getMessage());
    }

    @Test
    @DisplayName("CONTROLE DO CONTROLE: o mesmo INSERT em UTC e ACEITO")
    void instanteEmUtcEhAceito() throws Exception {
        // Sem este caso, um CHECK que recusasse TUDO passaria no teste acima e tornaria o
        // sistema inutilizavel — e o teste acima diria que a guarda funciona.
        String m = marca();
        executar("""
                INSERT INTO inscrito (nome, email, cep, criado_em)
                VALUES ('Utc', 'utc%s@exemplo.test', '01310100', '%s')
                """.formatted(m, InstanteEmTexto.de(Instant.now())));
        assertEquals(1, consultar(
                "SELECT count(*) FROM inscrito WHERE email = 'utc" + m + "@exemplo.test'"));
    }

    @Test
    @DisplayName("o formato tem LARGURA FIXA — e por isso a ordem alfabetica e a cronologica")
    void oFormatoTemLarguraFixa() {
        // A ordem alfabetica so coincide com a cronologica se a largura for fixa.
        // `Instant.toString()` OMITE a fracao quando ela e zero, e a largura variavel
        // inverteria a ordem de dois eventos separados por um decimo de segundo.
        Instant redondo = Instant.parse("2026-09-03T01:23:45Z");
        Instant comFracao = Instant.parse("2026-09-03T01:23:45.100Z");

        String a = InstanteEmTexto.de(redondo);
        String b = InstanteEmTexto.de(comFracao);

        assertEquals(InstanteEmTexto.TAMANHO, a.length(), "largura variavel: " + a);
        assertEquals(InstanteEmTexto.TAMANHO, b.length(), "largura variavel: " + b);
        assertTrue(InstanteEmTexto.valido(a));
        assertTrue(InstanteEmTexto.valido(b));

        // CONTROLE POSITIVO da razao de existir do truncamento: SEM ele, o texto do
        // instante redondo ordena DEPOIS do que tem fracao, invertendo a cronologia.
        assertTrue(redondo.toString().length() < comFracao.toString().length(),
                "o Instant nao omite mais a fracao — o truncamento pode ser revisto");
        assertTrue(redondo.toString().compareTo(comFracao.toString()) > 0,
                "sem truncar, a ordem alfabetica ja coincidiria e este cuidado seria inutil");
    }

    @Test
    @DisplayName("CONTROLE POSITIVO: o validador RECUSA o que nao e do formato")
    void oValidadorRecusaOTorto() {
        assertTrue(InstanteEmTexto.valido("2026-09-03T01:23:45Z"));
        for (String torto : new String[] { null, "", "2026-09-03T01:23:45-03:00",
                "2026-09-03 01:23:45", "2026-09-03T01:23:45.100Z", "ontem" }) {
            assertFalse(InstanteEmTexto.valido(torto), "aceitou o torto: " + torto);
        }
    }

    // ================================================ as invariantes de negócio

    @Test
    @DisplayName("INV-INSCRITO-001: o banco RECUSA o mesmo e-mail duas vezes")
    void emailDuplicadoEhRecusado() throws Exception {
        // O clique duplo no formulario. Sem esta restricao, as duas gravacoes passam e a
        // pessoa recebe cada alerta em dobro — sem nada acusando.
        String m = marca();
        String agora = InstanteEmTexto.de(Instant.now());
        String sql = """
                INSERT INTO inscrito (nome, email, cep, criado_em)
                VALUES ('Dup', 'dup%s@exemplo.test', '01310100', '%s')
                """.formatted(m, agora);
        executar(sql);
        assertThrows(SQLException.class, () -> executar(sql),
                "o banco aceitou o mesmo e-mail duas vezes: o clique duplo cria duas "
                        + "inscricoes e a pessoa recebe tudo em dobro");
    }

    @Test
    @DisplayName("INV-EONET-001: o banco RECUSA o mesmo eonet_id duas vezes")
    void eonetIdDuplicadoEhRecusado() throws Exception {
        String m = marca();
        String agora = InstanteEmTexto.de(Instant.now());
        String sql = """
                INSERT INTO evento_natural (eonet_id, titulo, ocorrido_em, criado_em, sincronizado_em)
                VALUES ('EONET_DUP_%s', 'Duplo', '%s', '%s', '%s')
                """.formatted(m, agora, agora, agora);
        executar(sql);
        assertThrows(SQLException.class, () -> executar(sql),
                "evento duplicado infla estatistica e mapa, sem nenhum erro");
    }

    @Test
    @DisplayName("NULL ISLAND: coordenada e um PAR — metade preenchida e recusada")
    void metadeDeUmaCoordenadaEhRecusada() {
        // Metade de uma posicao nao e posicao. Sem esta regra, uma latitude sem longitude
        // seria lida como (lat, 0) — a longitude do meridiano de Greenwich.
        String m = marca();
        assertThrows(SQLException.class, () -> executar("""
                INSERT INTO inscrito (nome, email, cep, latitude, criado_em)
                VALUES ('Meia', 'meia%s@exemplo.test', '01310100', -23.5, '%s')
                """.formatted(m, InstanteEmTexto.de(Instant.now()))));
    }

    @Test
    @DisplayName("coordenada FORA da Terra e recusada")
    void coordenadaForaDaTerraEhRecusada() {
        String m = marca();
        assertThrows(SQLException.class, () -> executar("""
                INSERT INTO inscrito (nome, email, cep, latitude, longitude, criado_em)
                VALUES ('Fora', 'fora%s@exemplo.test', '01310100', 91.0, 0.0, '%s')
                """.formatted(m, InstanteEmTexto.de(Instant.now()))));
    }

    @Test
    @DisplayName("CONTROLE DO CONTROLE: inscricao SEM coordenada e ACEITA")
    void inscricaoSemCoordenadaEhAceita() throws Exception {
        // Coordenada nula e ESTADO LEGITIMO: o provedor de CEP pode estar fora, e recusar
        // a inscricao por isso seria punir a pessoa por uma falha nossa. Sem este caso, um
        // CHECK exigindo coordenada passaria nos dois testes acima.
        String m = marca();
        executar("""
                INSERT INTO inscrito (nome, email, cep, criado_em)
                VALUES ('Sem posicao', 'sem%s@exemplo.test', '01310100', '%s')
                """.formatted(m, InstanteEmTexto.de(Instant.now())));
        assertEquals(1, consultar(
                "SELECT count(*) FROM inscrito WHERE email = 'sem" + m + "@exemplo.test'"));
    }

    @Test
    @DisplayName("raio fora de 1 a 20000 km e recusado")
    void raioInutilEhRecusado() {
        // 20.000 km e metade da circunferencia da Terra: acima disso o raio cobre o
        // planeta e "proximidade" deixa de significar coisa alguma.
        for (double raio : new double[] { 0.0, -5.0, 20001.0 }) {
            String m = marca();
            assertThrows(SQLException.class, () -> executar("""
                    INSERT INTO inscrito (nome, email, cep, raio_km, criado_em)
                    VALUES ('Raio', 'raio%s@exemplo.test', '01310100', %s, '%s')
                    """.formatted(m, raio, InstanteEmTexto.de(Instant.now()))),
                    "o banco aceitou raio de " + raio + " km");
        }
    }

    @Test
    @DisplayName("alerta TERMINAL exige instante de conclusao")
    void alertaTerminalExigeInstante() throws Exception {
        // Sem isto um alerta fica "ENVIADO" sem que ninguem saiba quando, e a auditoria
        // nao fecha.
        String m = marca();
        String agora = InstanteEmTexto.de(Instant.now());
        executar("""
                INSERT INTO inscrito (nome, email, cep, criado_em)
                VALUES ('Alvo', 'alvo%s@exemplo.test', '01310100', '%s')
                """.formatted(m, agora));
        executar("""
                INSERT INTO evento_natural (eonet_id, titulo, ocorrido_em, criado_em, sincronizado_em)
                VALUES ('EONET_TERM_%s', 'Terminal', '%s', '%s', '%s')
                """.formatted(m, agora, agora, agora));

        assertThrows(SQLException.class, () -> executar("""
                INSERT INTO alerta_enviado (inscrito_id, evento_id, destino, situacao, criado_em)
                SELECT i.id, e.id, 'alvo%s@exemplo.test', 'ENVIADO', '%s'
                  FROM inscrito i, evento_natural e
                 WHERE i.email = 'alvo%s@exemplo.test' AND e.eonet_id = 'EONET_TERM_%s'
                """.formatted(m, agora, m, m)),
                "o banco aceitou um alerta ENVIADO sem instante de conclusao");
    }
}
