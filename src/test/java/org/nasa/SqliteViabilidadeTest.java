package org.nasa;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Prova de viabilidade do SQLite nesta pilha — item 1 de {@code docs/PLANO-MESTRE.md}.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO.</b> O plano-mestre troca o Oracle da FIAP por um arquivo
 * SQLite local. O SQLite <i>não</i> é extensão oficial do Quarkus, então a combinação
 * Quarkus 3.39.1 + Java 25 + Agroal + {@code sqlite-jdbc} é uma <i>premissa</i>, não um
 * fato — e o plano proíbe construir em cima de premissa. Este teste transforma a
 * premissa em artefato, ou derruba o plano cedo, quando ainda é barato.</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO.</b></p>
 * <ol>
 *   <li>O pool entrega conexão SQLite utilizável (DDL, escrita e leitura).</li>
 *   <li>{@code PRAGMA foreign_keys} vale <b>1</b> na conexão que a aplicação recebe. No
 *       SQLite a integridade referencial nasce DESLIGADA por conexão; sem esta
 *       verificação, toda FK do modelo seria decorativa e o defeito só apareceria com o
 *       dado já inconsistente.</li>
 *   <li>{@code UNIQUE} do banco recusa a duplicata — é a invariante INV-EONET-001, que
 *       o legado deixava só no Java (achado A4 da auditoria).</li>
 *   <li>A FK recusa referência a linha inexistente — controle positivo do item 2: sem
 *       ele, "nenhuma violação" e "as FKs estão desligadas" teriam a mesma cara.</li>
 * </ol>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA.</b> Qualquer asserção que falhe reprova o build
 * e o item 1 do plano continua aberto — o plano B declarado é Postgres em contêiner ou
 * H2 em arquivo, e a troca custa pouco porque o acesso a dados mora atrás de um peer.
 * Nenhuma fatia é construída enquanto este teste não estiver verde.</p>
 */
@QuarkusTest
class SqliteViabilidadeTest {

    @Inject
    DataSource dataSource;

    @Test
    @DisplayName("o pool entrega conexao SQLite e o driver e o esperado")
    void conexaoViva() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            assertNotNull(c, "o Agroal devolveu conexao nula");
            String produto = c.getMetaData().getDatabaseProductName();
            assertEquals("SQLite", produto, "o banco atras do pool nao e SQLite");
            System.out.println("[VIABILIDADE] banco=" + produto
                    + " versao=" + c.getMetaData().getDatabaseProductVersion()
                    + " driver=" + c.getMetaData().getDriverName()
                    + " " + c.getMetaData().getDriverVersion());
        }
    }

    @Test
    @DisplayName("PRAGMA foreign_keys chega LIGADO na conexao da aplicacao")
    void integridadeReferencialLigada() throws Exception {
        try (Connection c = dataSource.getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("PRAGMA foreign_keys")) {
            assertTrue(rs.next(), "PRAGMA foreign_keys nao devolveu linha");
            int ligado = rs.getInt(1);
            System.out.println("[VIABILIDADE] PRAGMA foreign_keys = " + ligado);
            assertEquals(1, ligado,
                    "integridade referencial DESLIGADA: toda FK do modelo seria decorativa");
        }
    }

    @Test
    @DisplayName("DDL, escrita e leitura funcionam ponta a ponta")
    void gravaELe() throws Exception {
        try (Connection c = dataSource.getConnection(); Statement st = c.createStatement()) {
            st.executeUpdate("DROP TABLE IF EXISTS spike_evento");
            st.executeUpdate("""
                    CREATE TABLE spike_evento (
                        id        INTEGER PRIMARY KEY AUTOINCREMENT,
                        eonet_id  TEXT    NOT NULL UNIQUE,
                        titulo    TEXT    NOT NULL,
                        ocorrido_em TEXT  NOT NULL
                    )""");
            st.executeUpdate("""
                    INSERT INTO spike_evento (eonet_id, titulo, ocorrido_em)
                    VALUES ('EONET_1001', 'Queimada sintetica', '2026-09-02T12:00:00Z')""");

            try (ResultSet rs = st.executeQuery(
                    "SELECT eonet_id, titulo FROM spike_evento WHERE eonet_id = 'EONET_1001'")) {
                assertTrue(rs.next(), "a linha gravada nao foi encontrada na leitura");
                assertEquals("EONET_1001", rs.getString("eonet_id"));
                assertEquals("Queimada sintetica", rs.getString("titulo"));
                System.out.println("[VIABILIDADE] gravou e leu: "
                        + rs.getString("eonet_id") + " / " + rs.getString("titulo"));
            }
        }
    }

    @Test
    @DisplayName("UNIQUE do banco recusa o eonet_id duplicado (INV-EONET-001)")
    void bancoRecusaDuplicata() throws Exception {
        try (Connection c = dataSource.getConnection(); Statement st = c.createStatement()) {
            st.executeUpdate("DROP TABLE IF EXISTS spike_unico");
            st.executeUpdate(
                    "CREATE TABLE spike_unico (id INTEGER PRIMARY KEY, eonet_id TEXT NOT NULL UNIQUE)");
            st.executeUpdate("INSERT INTO spike_unico (id, eonet_id) VALUES (1, 'EONET_2002')");

            var erro = assertThrows(java.sql.SQLException.class,
                    () -> st.executeUpdate("INSERT INTO spike_unico (id, eonet_id) VALUES (2, 'EONET_2002')"),
                    "o banco ACEITOU a duplicata — a invariante nao esta protegida no banco");
            System.out.println("[VIABILIDADE] duplicata recusada pelo banco: " + erro.getMessage());
            assertTrue(erro.getMessage().toUpperCase().contains("UNIQUE"),
                    "a recusa veio, mas nao por violacao de UNIQUE: " + erro.getMessage());
        }
    }

    @Test
    @DisplayName("FK recusa referencia orfa — controle positivo da integridade")
    void bancoRecusaOrfao() throws Exception {
        try (Connection c = dataSource.getConnection(); Statement st = c.createStatement()) {
            st.executeUpdate("DROP TABLE IF EXISTS spike_filho");
            st.executeUpdate("DROP TABLE IF EXISTS spike_pai");
            st.executeUpdate("CREATE TABLE spike_pai (id INTEGER PRIMARY KEY)");
            st.executeUpdate("""
                    CREATE TABLE spike_filho (
                        id     INTEGER PRIMARY KEY,
                        pai_id INTEGER NOT NULL REFERENCES spike_pai(id)
                    )""");

            var erro = assertThrows(java.sql.SQLException.class,
                    () -> st.executeUpdate("INSERT INTO spike_filho (id, pai_id) VALUES (1, 999)"),
                    "o banco ACEITOU orfao — as foreign keys estao desligadas");
            System.out.println("[VIABILIDADE] orfao recusado pelo banco: " + erro.getMessage());
        }
    }
}
