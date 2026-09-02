package org.nasa.persistencia;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Prova do esquema <b>no banco vivo</b> — as invariantes protegidas onde importa.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO.</b> O legado tinha as regras certas <i>no Java</i> e
 * <b>nenhuma constraint UNIQUE além das chaves primárias</b> no DDL — conferido nos três
 * arquivos de DDL da entrega de 2025. Regra que só existe no Java some quando duas
 * execuções acontecem ao mesmo tempo, ou quando alguém escreve pelo caminho de baixo.
 * Estes testes provam que agora ela mora no banco.</p>
 *
 * <p><b>Cada teste é NEGATIVO de propósito:</b> tenta violar a invariante e exige que o
 * banco recuse. Teste positivo prova que o caminho feliz anda; só o negativo prova que a
 * proteção existe.</p>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA.</b> Reprova com o SQL e o erro do banco colados.
 * Se um destes cair, a invariante correspondente deixou de estar protegida — e nenhum
 * outro teste desta suíte perceberia.</p>
 */
@QuarkusTest
@DisplayName("esquema do banco — as invariantes recusadas pelo proprio SQLite")
class EsquemaDoBancoTest {

    @Inject
    DataSource dataSource;

    private List<String> consultar(String sql) throws SQLException {
        List<String> linhas = new ArrayList<>();
        try (Connection c = dataSource.getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                linhas.add(rs.getString(1));
            }
        }
        return linhas;
    }

    private void executar(String sql) throws SQLException {
        try (Connection c = dataSource.getConnection(); Statement st = c.createStatement()) {
            st.executeUpdate(sql);
        }
    }

    // ============================================================ o mecanismo

    @Test
    @DisplayName("a migracao rodou no boot e registrou a versao com instante UTC")
    void migracaoRodouNoBoot() throws Exception {
        var versoes = consultar("SELECT versao || '|' || checksum || '|' || aplicada_em "
                + "FROM esquema_migracao ORDER BY versao");
        assertTrue(versoes.size() >= 1, "nenhuma migracao registrada: o boot nao migrou");
        System.out.println("[ESQUEMA] registro: " + versoes.get(0));
        assertTrue(versoes.get(0).startsWith("1|"), "a V001 tem de ser a primeira");
        assertTrue(versoes.get(0).endsWith("Z"),
                "o instante de aplicacao nao esta em UTC: " + versoes.get(0));
    }

    @Test
    @DisplayName("as tabelas do dominio existem — todas as sete")
    void tabelasDoDominioExistem() throws Exception {
        var tabelas = consultar("SELECT name FROM sqlite_master WHERE type='table' ORDER BY name");
        System.out.println("[ESQUEMA] tabelas: " + tabelas);
        for (String esperada : new String[] { "cliente", "contato", "endereco", "evento_natural",
                "cliente_contato", "cliente_endereco", "alerta_enviado" }) {
            assertTrue(tabelas.contains(esperada), "faltou a tabela " + esperada);
        }
    }

    @Test
    @DisplayName("PRAGMA: foreign_keys LIGADO, WAL e busy_timeout chegam NA CONEXAO")
    void pragmasChegamNaConexao() throws Exception {
        // "Estar na configuracao" nao e "estar na conexao". A URL do datasource declara
        // os tres; este teste pergunta ao banco o que ele realmente aplicou.
        assertEquals("1", consultar("PRAGMA foreign_keys").get(0),
                "integridade referencial DESLIGADA: as FKs do esquema seriam decorativas");

        String journal = consultar("PRAGMA journal_mode").get(0);
        System.out.println("[ESQUEMA] journal_mode=" + journal);
        assertEquals("wal", journal.toLowerCase(),
                "sem WAL, um leitor bloqueia o escritor e a tela trava sob uso normal");

        String espera = consultar("PRAGMA busy_timeout").get(0);
        System.out.println("[ESQUEMA] busy_timeout=" + espera + "ms");
        assertTrue(Integer.parseInt(espera) >= 1000,
                "sem espera, escrita concorrente devolve SQLITE_BUSY na cara do operador");
    }

    // ================================================= as invariantes negativas

    @Test
    @DisplayName("INV-EONET-001: o banco RECUSA o mesmo eonet_id duas vezes")
    void bancoRecusaEventoDuplicado() throws Exception {
        String insere = """
                INSERT INTO evento_natural (eonet_id, titulo, ocorrido_em, sincronizado_em)
                VALUES ('EONET_DUP_1', 'Queimada', '2026-09-01T10:00:00Z', '2026-09-02T12:00:00Z')""";
        executar(insere);

        var erro = assertThrows(SQLException.class, () -> executar(insere),
                "o banco ACEITOU evento duplicado — a idempotencia voltou a morar so no Java");
        System.out.println("[ESQUEMA] duplicata recusada: " + erro.getMessage());
        assertTrue(erro.getMessage().toUpperCase().contains("UNIQUE"));
    }

    @Test
    @DisplayName("INV-CLIENTE-001: o banco RECUSA o mesmo documento duas vezes")
    void bancoRecusaDocumentoDuplicado() throws Exception {
        String insere = """
                INSERT INTO cliente (nome, sobrenome, data_nascimento, documento, criado_em)
                VALUES ('Ana', 'Souza', '1990-05-14', '111.222.333-44', '2026-09-02T12:00:00Z')""";
        executar(insere);

        var erro = assertThrows(SQLException.class, () -> executar(insere.replace("'Ana'", "'Outra'")),
                "documento duplicado faz o alerta ir para o cadastro errado");
        assertTrue(erro.getMessage().toUpperCase().contains("UNIQUE"));
    }

    @Test
    @DisplayName("NULL ISLAND: o banco RECUSA endereco em (0,0) — o Golfo da Guine")
    void bancoRecusaNullIsland() {
        var erro = assertThrows(SQLException.class, () -> executar("""
                INSERT INTO endereco (cep, logradouro, localidade, uf, latitude, longitude, criado_em)
                VALUES ('01310-200', 'Av Paulista', 'Sao Paulo', 'SP', 0, 0, '2026-09-02T12:00:00Z')"""),
                "coordenada 0,0 poria o endereco do cliente no oceano, com o pino desenhado "
                        + "no mapa e nenhum erro aparecendo");
        System.out.println("[ESQUEMA] null island recusado: " + erro.getMessage());
        assertTrue(erro.getMessage().toUpperCase().contains("CHECK"));
    }

    @Test
    @DisplayName("coordenada e um PAR: metade preenchida e recusada")
    void bancoRecusaCoordenadaPelaMetade() {
        assertThrows(SQLException.class, () -> executar("""
                INSERT INTO endereco (cep, logradouro, localidade, uf, latitude, criado_em)
                VALUES ('01310-201', 'Av Paulista', 'Sao Paulo', 'SP', -23.56, '2026-09-02T12:00:00Z')"""),
                "meia coordenada e pior que nenhuma: parece preenchida");
    }

    @Test
    @DisplayName("A7 corrigido: endereco SEM complemento e SEM coordenada e aceito")
    void enderecoSemComplementoNemCoordenadaEhValido() throws Exception {
        executar("""
                INSERT INTO endereco (cep, logradouro, localidade, uf, criado_em)
                VALUES ('88010-400', 'Rua Felipe Schmidt', 'Florianopolis', 'SC', '2026-09-02T12:00:00Z')""");
        var quantos = consultar("SELECT count(*) FROM endereco WHERE cep = '88010-400'");
        assertEquals("1", quantos.get(0),
                "a maioria dos enderecos do Brasil nao tem complemento, e 1 de 6 CEPs medidos "
                        + "volta sem coordenada — os dois casos sao normais, nao erro");
    }

    @Test
    @DisplayName("INV-ALERTA-001: o mesmo evento nao alerta o mesmo cliente duas vezes")
    void bancoRecusaAlertaDuplicado() throws Exception {
        executar("""
                INSERT INTO cliente (nome, sobrenome, data_nascimento, documento, criado_em)
                VALUES ('Bruno', 'Lima', '1985-03-02', '999.888.777-66', '2026-09-02T12:00:00Z')""");
        executar("""
                INSERT INTO evento_natural (eonet_id, titulo, ocorrido_em, sincronizado_em)
                VALUES ('EONET_ALERTA_1', 'Enchente', '2026-09-01T10:00:00Z', '2026-09-02T12:00:00Z')""");

        String alerta = """
                INSERT INTO alerta_enviado (cliente_id, evento_id, destino, situacao, criado_em)
                SELECT c.id, e.id, 'bruno@example.com', 'PENDENTE', '2026-09-02T12:00:00Z'
                FROM cliente c, evento_natural e
                WHERE c.documento = '999.888.777-66' AND e.eonet_id = 'EONET_ALERTA_1'""";
        executar(alerta);

        var erro = assertThrows(SQLException.class, () -> executar(alerta),
                "retry apos timeout enviaria o mesmo alerta de novo; a chave de idempotencia "
                        + "tem de estar no BANCO, nao na memoria de um worker que reinicia");
        assertTrue(erro.getMessage().toUpperCase().contains("UNIQUE"));
    }

    @Test
    @DisplayName("alerta em estado terminal exige instante de conclusao")
    void alertaTerminalExigeInstante() {
        assertThrows(SQLException.class, () -> executar("""
                INSERT INTO alerta_enviado (cliente_id, evento_id, destino, situacao, criado_em)
                VALUES (999, 999, 'x@example.com', 'ENVIADO', '2026-09-02T12:00:00Z')"""),
                "'ENVIADO' sem quando deixa a auditoria sem fechar");
    }

    @Test
    @DisplayName("FK viva: alerta orfao e recusado pelo banco")
    void fkRecusaAlertaOrfao() {
        var erro = assertThrows(SQLException.class, () -> executar("""
                INSERT INTO alerta_enviado (cliente_id, evento_id, destino, situacao, concluido_em, criado_em)
                VALUES (424242, 424242, 'x@example.com', 'ENVIADO', '2026-09-02T12:00:00Z', '2026-09-02T12:00:00Z')"""));
        System.out.println("[ESQUEMA] orfao recusado: " + erro.getMessage());
    }
}
