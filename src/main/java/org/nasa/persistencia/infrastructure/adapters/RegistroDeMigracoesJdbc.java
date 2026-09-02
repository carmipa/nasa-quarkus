package org.nasa.persistencia.infrastructure.adapters;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.nasa.core.tempo.Relogio;
import org.nasa.persistencia.domain.Migracao;
import org.nasa.persistencia.domain.exceptions.MigracaoFalhouException;
import org.nasa.persistencia.domain.ports.RegistroDeMigracoesPort;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A memória do esquema, guardada no próprio banco.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO.</b> Sem registro, toda subida reaplicaria todo o DDL e a
 * segunda execução falharia com "tabela já existe". Esta tabela é o que faz a migração
 * ser idempotente.</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO.</b></p>
 * <ol>
 *   <li><b>Aplicar e registrar acontecem na MESMA transação.</b> Registrar sem aplicar
 *       deixaria o banco uma versão à frente do próprio esquema; aplicar sem registrar
 *       faria a próxima subida repetir o DDL. Os dois estados são piores que a falha.</li>
 *   <li><b>Autocommit desligado durante a migração e RESTAURADO no fim</b>, inclusive em
 *       caso de erro — a conexão volta ao pool como veio. Conexão devolvida com estado
 *       alterado contamina a operação seguinte, e o defeito aparece longe daqui.</li>
 *   <li><b>O instante é UTC</b>, vindo do relógio injetado, gravado como texto ISO-8601:
 *       o SQLite não tem tipo de data, e texto ISO ordena corretamente como string.</li>
 *   <li><b>A tabela de controle não é migração.</b> Ela é criada por
 *       {@code CREATE TABLE IF NOT EXISTS} — não pode depender do mecanismo que ela
 *       própria sustenta.</li>
 * </ol>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA.</b> Qualquer erro do banco vira
 * {@link MigracaoFalhouException} depois de <b>rollback</b>: nada daquele arquivo fica
 * aplicado e a versão não é marcada. O estado resultante é "por aplicar" — recuperável —
 * e nunca "pela metade".</p>
 */
@ApplicationScoped
public class RegistroDeMigracoesJdbc implements RegistroDeMigracoesPort {

    /** Nome fixo e sem prefixo de fatia: o controle é do esquema inteiro. */
    static final String TABELA = "esquema_migracao";

    @Inject
    DataSource dataSource;

    @Inject
    Relogio relogio;

    @Override
    public void prepararControle() {
        String ddl = """
                CREATE TABLE IF NOT EXISTS %s (
                    versao       INTEGER PRIMARY KEY,
                    nome         TEXT NOT NULL,
                    checksum     TEXT NOT NULL,
                    aplicada_em  TEXT NOT NULL
                )""".formatted(TABELA);
        try (Connection c = Conexoes.abrir(dataSource, TABELA); Statement st = c.createStatement()) {
            st.executeUpdate(ddl);
        } catch (SQLException e) {
            throw new MigracaoFalhouException("tabela-de-controle", e);
        }
    }

    @Override
    public Map<Integer, String> checksumsAplicados() {
        Map<Integer, String> aplicadas = new LinkedHashMap<>();
        String sql = "SELECT versao, checksum FROM " + TABELA + " ORDER BY versao";
        try (Connection c = Conexoes.abrir(dataSource, TABELA);
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                aplicadas.put(rs.getInt("versao"), rs.getString("checksum"));
            }
            return aplicadas;
        } catch (SQLException e) {
            throw new MigracaoFalhouException("ler-controle", e);
        }
    }

    @Override
    public void aplicarERegistrar(Migracao migracao) {
        try (Connection c = Conexoes.abrir(dataSource, TABELA)) {
            boolean autocommitOriginal = c.getAutoCommit();
            c.setAutoCommit(false);
            try {
                // O script inteiro numa chamada só. A versão do SQLite dividia o
                // arquivo em `;` porque ele executa uma instrução por vez — e essa
                // divisão é uma bomba-relógio: o primeiro `;` dentro de uma string
                // literal ou de um corpo de função parte o comando ao meio e produz
                // erro de sintaxe em SQL que está correto. O PostgreSQL aceita o
                // script completo, então o problema deixa de existir em vez de ficar
                // esperando a migração que o acione.
                try (Statement st = c.createStatement()) {
                    st.execute(migracao.sql());
                }

                String registro = "INSERT INTO " + TABELA
                        + " (versao, nome, checksum, aplicada_em) VALUES (?, ?, ?, ?)";
                try (PreparedStatement ps = c.prepareStatement(registro)) {
                    ps.setInt(1, migracao.versao());
                    ps.setString(2, migracao.nome());
                    ps.setString(3, migracao.checksum());
                    ps.setString(4, relogio.agora().toString());   // ISO-8601 UTC
                    ps.executeUpdate();
                }
                c.commit();
            } catch (SQLException | RuntimeException falhou) {
                try {
                    c.rollback();
                } catch (SQLException aoDesfazer) {
                    falhou.addSuppressed(aoDesfazer);
                }
                throw new MigracaoFalhouException(migracao.identificacao(), falhou);
            } finally {
                // A conexão volta ao pool como veio. Sempre.
                c.setAutoCommit(autocommitOriginal);
            }
        } catch (SQLException e) {
            throw new MigracaoFalhouException(migracao.identificacao(), e);
        }
    }
}
