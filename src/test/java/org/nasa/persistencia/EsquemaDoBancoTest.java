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
 * banco recusa, e não há como esquecer. Cada caso aqui é um {@code INSERT} que precisa
 * <b>falhar</b>, e ao lado dele um que precisa <b>passar</b>.</p>
 *
 * <p><b>POR QUE ESTE TESTE FICOU MAIS IMPORTANTE COM O SQLITE.</b> No PostgreSQL a disciplina
 * de UTC morava no TIPO da coluna ({@code TIMESTAMPTZ}), e tipo não se esquece. O SQLite não
 * tem tipo de data: aceita qualquer coisa em qualquer coluna. O mecanismo passou a ser
 * {@code CHECK (coluna LIKE '%Z')}, e <b>este teste é a prova de que ele funciona</b> — sem
 * ele, a garantia teria virado a convenção que o projeto recusou.</p>
 *
 * <p><b>TRÊS TABELAS, e a ausência das outras é testada.</b> O sistema deixou de guardar
 * gente: não há cadastro nem fila de alertas. {@link #naoGuardaGente()} trava isso no
 * esquema — sem ele, alguém reintroduz uma tabela de inscritos numa migração futura e o
 * projeto volta a guardar e-mail em silêncio.</p>
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
    @DisplayName("as DUAS migracoes rodaram, com instante em UTC")
    void asMigracoesRodaram() throws Exception {
        assertEquals(2, consultar("SELECT count(*) FROM esquema_migracao"),
                "esperava as duas migracoes registradas");
        assertEquals(0, consultar(
                "SELECT count(*) FROM esquema_migracao WHERE aplicada_em NOT LIKE '%Z'"),
                "alguma migracao registrou o instante fora de UTC");
    }

    @Test
    @DisplayName("CONTROLE POSITIVO: as tabelas do dominio existem")
    void asTabelasExistem() throws Exception {
        // O DEFEITO QUE ESTE TESTE TRAVA, medido em 03/09/2026: o driver do SQLite executa
        // o PRIMEIRO comando de um script e ignora o resto, sem erro. A migracao registrou
        // "aplicada" tendo criado 1 tabela de 9 — e ficou marcada como aplicada, entao o
        // segundo arranque nao a repetiria. O banco ficaria pela metade para sempre.
        var existentes = tabelas();
        for (String esperada : new String[] { "evento_natural", "telemetria_operacao",
                "esquema_migracao" }) {
            assertTrue(existentes.contains(esperada),
                    "a tabela `" + esperada + "` nao existe. As que existem: " + existentes
                            + " — a migracao rodou pela metade?");
        }
    }

    @Test
    @DisplayName("CONTROLE POSITIVO: o banco NAO tem tabela de cadastro nem de fila")
    void naoGuardaGente() throws Exception {
        // A DECISAO QUE DEFINE O SISTEMA, travada no esquema. Sem esta guarda, alguem
        // reintroduz uma tabela de inscritos numa migracao futura e o projeto volta a
        // guardar e-mail — em silencio, porque nada mais reclamaria.
        var existentes = tabelas();
        for (String proibida : new String[] { "inscrito", "alerta_enviado", "cliente",
                "contato", "endereco" }) {
            assertFalse(existentes.contains(proibida),
                    "a tabela `" + proibida + "` voltou a existir: o sistema guarda dado "
                            + "pessoal de novo, e ninguem foi avisado");
        }
    }

    // ============================================ o UTC, que virou CHECK

    @Test
    @DisplayName("CONTROLE POSITIVO: o banco RECUSA instante em hora LOCAL")
    void instanteEmHoraLocalEhRecusado() {
        // ESTA E A GUARDA QUE SUBSTITUIU O `TIMESTAMPTZ`. Sem ela, a garantia de UTC seria
        // a convencao que este projeto recusou — e o defeito do log em -03:00 poderia
        // voltar por outro caminho.
        String m = marca();
        var erro = assertThrows(SQLException.class, () -> executar("""
                INSERT INTO evento_natural (eonet_id, titulo, ocorrido_em, criado_em,
                                            sincronizado_em)
                VALUES ('EONET_LOCAL_%s', 'Hora local', '2026-09-03T10:00:00-03:00',
                        '2026-09-03T13:00:00Z', '2026-09-03T13:00:00Z')
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
        String agora = InstanteEmTexto.de(Instant.now());
        executar("""
                INSERT INTO evento_natural (eonet_id, titulo, ocorrido_em, criado_em,
                                            sincronizado_em)
                VALUES ('EONET_UTC_%s', 'Hora UTC', '%s', '%s', '%s')
                """.formatted(m, agora, agora, agora));
        assertEquals(1, consultar(
                "SELECT count(*) FROM evento_natural WHERE eonet_id = 'EONET_UTC_" + m + "'"));
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
    @DisplayName("INV-EONET-001: o banco RECUSA o mesmo eonet_id duas vezes")
    void eonetIdDuplicadoEhRecusado() throws Exception {
        // No legado esta garantia morava so no Java (`findByEonetId().orElse(new)`), e
        // duas sincronizacoes simultaneas liam "nao existe" e inseriam as duas — evento
        // duplicado inflando estatistica e mapa, sem nenhum erro.
        String m = marca();
        String agora = InstanteEmTexto.de(Instant.now());
        String sql = """
                INSERT INTO evento_natural (eonet_id, titulo, ocorrido_em, criado_em,
                                            sincronizado_em)
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
        String agora = InstanteEmTexto.de(Instant.now());
        assertThrows(SQLException.class, () -> executar("""
                INSERT INTO evento_natural (eonet_id, titulo, ocorrido_em, latitude,
                                            criado_em, sincronizado_em)
                VALUES ('EONET_MEIA_%s', 'Meia posicao', '%s', -23.5, '%s', '%s')
                """.formatted(m, agora, agora, agora)));
    }

    @Test
    @DisplayName("coordenada FORA da Terra e recusada")
    void coordenadaForaDaTerraEhRecusada() {
        String m = marca();
        String agora = InstanteEmTexto.de(Instant.now());
        assertThrows(SQLException.class, () -> executar("""
                INSERT INTO evento_natural (eonet_id, titulo, ocorrido_em, latitude,
                                            longitude, criado_em, sincronizado_em)
                VALUES ('EONET_FORA_%s', 'Fora da Terra', '%s', 91.0, 0.0, '%s', '%s')
                """.formatted(m, agora, agora, agora)));
    }

    @Test
    @DisplayName("CONTROLE DO CONTROLE: evento SEM coordenada e ACEITO")
    void eventoSemCoordenadaEhAceito() throws Exception {
        // Coordenada nula e ESTADO LEGITIMO: a NASA nem sempre publica posicao, e recusar
        // o evento por isso apagaria dado que existe. Sem este caso, um CHECK exigindo
        // coordenada passaria nos dois testes acima.
        String m = marca();
        String agora = InstanteEmTexto.de(Instant.now());
        executar("""
                INSERT INTO evento_natural (eonet_id, titulo, ocorrido_em, criado_em,
                                            sincronizado_em)
                VALUES ('EONET_SEMPOS_%s', 'Sem posicao', '%s', '%s', '%s')
                """.formatted(m, agora, agora, agora));
        assertEquals(1, consultar(
                "SELECT count(*) FROM evento_natural WHERE eonet_id = 'EONET_SEMPOS_" + m + "'"));
    }

    @Test
    @DisplayName("a telemetria e UNICA por (operacao, hora)")
    void telemetriaUnicaPorOperacaoEHora() throws Exception {
        // E ela que torna a descarga idempotente: o processo acumula em memoria e
        // descarrega periodicamente, e duas descargas na mesma hora precisam SOMAR na
        // linha existente. Sem isto, um reinicio no meio da hora produziria duas linhas e
        // todo grafico contaria em dobro.
        String m = marca();
        String hora = InstanteEmTexto.de(Instant.now().truncatedTo(
                java.time.temporal.ChronoUnit.HOURS));
        String sql = """
                INSERT INTO telemetria_operacao (operacao, hora, chamadas, atualizado_em)
                VALUES ('teste-unico-%s', '%s', 1, '%s')
                """.formatted(m, hora, InstanteEmTexto.de(Instant.now()));
        executar(sql);
        assertThrows(SQLException.class, () -> executar(sql),
                "duas linhas para a mesma (operacao, hora) fariam todo grafico contar em dobro");
    }
}
