package org.nasa.persistencia.infrastructure.adapters;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;
import org.nasa.core.log.Registro;
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

    private static final Logger LOG = Logger.getLogger(RegistroDeMigracoesJdbc.class);

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
                // UM COMANDO POR CHAMADA, e isto NAO e preferencia de estilo.
                //
                // MEDIDO em 03/09/2026: o driver do SQLite executa o PRIMEIRO comando de
                // um script e ignora o resto — sem erro. A migracao registrou "aplicada"
                // tendo criado 1 de 9 objetos, e o sistema so falhou depois, na primeira
                // consulta, com "no such table".
                //
                // O PIOR e que a migracao ficou marcada como aplicada: o segundo arranque
                // nao a repetiria, o banco ficaria permanentemente pela metade, e o log
                // dizendo "aplicadas=1" afirmava que estava tudo certo.
                //
                // O comentario anterior avisava que dividir por `;` e bomba-relogio, e ele
                // estava certo — por isso a divisao nao e um `split(";")`, e sim
                // `ComandosDoScript`, que sabe quando o `;` esta dentro de literal, de
                // identificador entre aspas ou de comentario.
                //
                // A CONTAGEM E VERIFICADA: zero comando num arquivo de migracao e defeito,
                // nao migracao vazia. Sem esta guarda, um erro no divisor voltaria a
                // marcar como aplicada uma migracao que nao criou nada.
                var comandos = ComandosDoScript.de(migracao.sql());
                if (comandos.isEmpty()) {
                    throw new MigracaoFalhouException(
                            migracao.identificacao() + " — nenhum comando SQL no arquivo", null);
                }
                try (Statement st = c.createStatement()) {
                    for (String comando : comandos) {
                        st.execute(comando);
                    }
                }
                LOG.debug(Registro.de("migrar-banco", migracao.identificacao(),
                        comandos.size() + " comando(s) executado(s)"));

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
