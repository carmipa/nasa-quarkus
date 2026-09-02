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
 * <p><b>O QUE MUDOU NA PORTABILIDADE PARA POSTGRESQL</b> (02/09/2026):</p>
 * <ul>
 *   <li>O teste de {@code PRAGMA} deixou de existir e foi <b>substituído por algo mais
 *       forte</b>. Ele provava que a integridade referencial estava <i>ligada</i> — no
 *       SQLite ela nasce desligada e as FKs viram decorativas. No PostgreSQL não há como
 *       desligá-la, então a checagem perdeu sentido; no lugar dela entrou a prova de que
 *       <b>todo instante do esquema é {@code TIMESTAMPTZ}</b>, que é onde o UTC agora
 *       mora.</li>
 *   <li>As asserções passaram a usar <b>SQLSTATE</b> em vez de procurar palavras na
 *       mensagem. Mensagem de erro é texto do fornecedor: muda de versão para versão e é
 *       traduzida conforme o idioma do servidor.</li>
 * </ul>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA.</b> Reprova com o SQL e o erro do banco colados.
 * Se um destes cair, a invariante correspondente deixou de estar protegida — e nenhum
 * outro teste desta suíte perceberia.</p>
 */
@QuarkusTest
@DisplayName("esquema do banco — as invariantes recusadas pelo proprio PostgreSQL")
class EsquemaDoBancoTest {

    /** SQLSTATEs padrão. Não são texto do fornecedor. */
    private static final String UNIQUE_VIOLATION = "23505";
    private static final String CHECK_VIOLATION = "23514";
    private static final String FK_VIOLATION = "23503";

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
        var tabelas = consultar("""
                SELECT table_name FROM information_schema.tables
                WHERE table_schema = 'public' AND table_type = 'BASE TABLE'
                ORDER BY table_name""");
        System.out.println("[ESQUEMA] tabelas: " + tabelas);
        for (String esperada : new String[] { "cliente", "contato", "endereco", "evento_natural",
                "cliente_contato", "cliente_endereco", "alerta_enviado" }) {
            assertTrue(tabelas.contains(esperada), "faltou a tabela " + esperada);
        }
    }

    @Test
    @DisplayName("todo instante do esquema e TIMESTAMPTZ — o UTC mora no TIPO")
    void todoInstanteCarregaFuso() throws Exception {
        // Substitui o antigo teste de PRAGMA. Uma coluna `timestamp without time zone`
        // aceita e devolve hora sem fuso: o valor parece certo, ordena certo, e passa a
        // significar coisas diferentes conforme o fuso de quem grava. E exatamente a
        // familia de defeito encontrada no LOG em 02/09 — a diferenca e que ali dava para
        // ver `-03:00` na linha, e aqui nao daria para ver nada.
        var semFuso = consultar("""
                SELECT table_name || '.' || column_name
                FROM information_schema.columns
                WHERE table_schema = 'public'
                  AND data_type = 'timestamp without time zone'
                ORDER BY 1""");
        assertTrue(semFuso.isEmpty(),
                "estas colunas guardam instante SEM fuso, e o UTC deixa de ser garantido "
                        + "pelo tipo: " + semFuso);

        // CONTROLE POSITIVO, e sem ele o teste acima passaria ate com a consulta quebrada:
        // se a busca nao enxerga coluna nenhuma, "nenhuma esta errada" e vacuo.
        var comFuso = consultar("""
                SELECT table_name || '.' || column_name
                FROM information_schema.columns
                WHERE table_schema = 'public'
                  AND data_type = 'timestamp with time zone'
                ORDER BY 1""");
        System.out.println("[ESQUEMA] colunas TIMESTAMPTZ: " + comFuso);
        assertTrue(comFuso.size() >= 7,
                "o esquema declara 7 colunas de instante; achei " + comFuso.size()
                        + " — a consulta acima nao esta medindo o que eu penso que mede");
    }

    @Test
    @DisplayName("data_nascimento e DATE de verdade: 31 de fevereiro e recusado")
    void dataInvalidaEhRecusadaPeloTipo() {
        // No SQLite era TEXT com CHECK de posicao de hifen, declarado "grosseiro de
        // proposito" — e 2026-02-31 passava por ele sem dificuldade.
        var erro = assertThrows(SQLException.class, () -> executar("""
                INSERT INTO cliente (nome, sobrenome, data_nascimento, documento, criado_em)
                VALUES ('Data', 'Impossivel', '2026-02-31', '55544433322', '2026-09-02T12:00:00Z')"""),
                "o tipo DATE tem de recusar um dia que nao existe");
        System.out.println("[ESQUEMA] data impossivel recusada: " + erro.getMessage());
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
        assertEquals(UNIQUE_VIOLATION, erro.getSQLState());
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
        assertEquals(UNIQUE_VIOLATION, erro.getSQLState());
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
        assertEquals(CHECK_VIOLATION, erro.getSQLState());
    }

    @Test
    @DisplayName("coordenada e um PAR: metade preenchida e recusada")
    void bancoRecusaCoordenadaPelaMetade() {
        var erro = assertThrows(SQLException.class, () -> executar("""
                INSERT INTO endereco (cep, logradouro, localidade, uf, latitude, criado_em)
                VALUES ('01310-201', 'Av Paulista', 'Sao Paulo', 'SP', -23.56, '2026-09-02T12:00:00Z')"""),
                "meia coordenada e pior que nenhuma: parece preenchida");
        assertEquals(CHECK_VIOLATION, erro.getSQLState());
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
        assertEquals(UNIQUE_VIOLATION, erro.getSQLState());
    }

    @Test
    @DisplayName("alerta em estado terminal exige instante de conclusao")
    void alertaTerminalExigeInstante() throws Exception {
        // Cliente e evento REAIS: sem eles a FK reprovaria primeiro, e o teste passaria
        // pelo motivo errado — provando a chave estrangeira em vez do CHECK que ele diz
        // provar. Foi por isso que este caso ganhou dados proprios.
        executar("""
                INSERT INTO cliente (nome, sobrenome, data_nascimento, documento, criado_em)
                VALUES ('Terminal', 'Teste', '1990-01-01', '10101010101', '2026-09-02T12:00:00Z')""");
        executar("""
                INSERT INTO evento_natural (eonet_id, titulo, ocorrido_em, sincronizado_em)
                VALUES ('EONET_TERMINAL_1', 'Seca', '2026-09-01T10:00:00Z', '2026-09-02T12:00:00Z')""");

        var erro = assertThrows(SQLException.class, () -> executar("""
                INSERT INTO alerta_enviado (cliente_id, evento_id, destino, situacao, criado_em)
                SELECT c.id, e.id, 'x@example.com', 'ENVIADO', '2026-09-02T12:00:00Z'
                FROM cliente c, evento_natural e
                WHERE c.documento = '10101010101' AND e.eonet_id = 'EONET_TERMINAL_1'"""),
                "'ENVIADO' sem quando deixa a auditoria sem fechar");
        System.out.println("[ESQUEMA] terminal sem instante recusado: " + erro.getMessage());
        assertEquals(CHECK_VIOLATION, erro.getSQLState());
    }

    @Test
    @DisplayName("FK viva: alerta orfao e recusado pelo banco")
    void fkRecusaAlertaOrfao() {
        // No SQLite este teste dependia de `PRAGMA foreign_keys=ON` ter chegado na
        // conexao — sem ele a FK era decorativa e o insert passava. No PostgreSQL a
        // integridade referencial nao se desliga, entao o que se prova aqui e a FK em si.
        var erro = assertThrows(SQLException.class, () -> executar("""
                INSERT INTO alerta_enviado (cliente_id, evento_id, destino, situacao, concluido_em, criado_em)
                VALUES (424242, 424242, 'x@example.com', 'ENVIADO', '2026-09-02T12:00:00Z', '2026-09-02T12:00:00Z')"""));
        System.out.println("[ESQUEMA] orfao recusado: " + erro.getMessage());
        assertEquals(FK_VIOLATION, erro.getSQLState());
    }
}
