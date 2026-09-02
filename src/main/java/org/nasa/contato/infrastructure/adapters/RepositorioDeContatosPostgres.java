package org.nasa.contato.infrastructure.adapters;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.nasa.contato.domain.Contato;
import org.nasa.contato.domain.Email;
import org.nasa.contato.domain.TipoContato;
import org.nasa.contato.domain.exceptions.EmailJaCadastradoException;
import org.nasa.contato.domain.ports.RepositorioDeContatosPort;
import org.nasa.core.tempo.Relogio;
import org.nasa.persistencia.infrastructure.adapters.Conexoes;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Os contatos no PostgreSQL.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO.</b> Único lugar que sabe como um contato vira linha de
 * tabela. Toda regra vive nos casos de uso; aqui só há tradução.</p>
 *
 * <p><b>AS LIÇÕES DE 02/09 APLICADAS DESDE O PRIMEIRO DIA</b>, para não repetir na fatia
 * nova os defeitos que custaram a manhã na fatia de cliente:</p>
 * <ol>
 *   <li><b>{@code ILIKE}, não {@code LIKE}.</b> No PostgreSQL o {@code LIKE} é sensível a
 *       maiúsculas: procurar {@code ana@} não acharia {@code Ana@} — sem erro, só lista
 *       vazia que parece "não existe".</li>
 *   <li><b>Cada metade do filtro só vale se tiver o que procurar.</b> Sem este guarda, um
 *       termo sem dígitos produz {@code '%%'} no campo de telefone, e
 *       {@code LIKE '%%'} casa com <b>toda</b> linha — a busca aceita o texto, responde
 *       rápido e devolve a base inteira. Foi exatamente o defeito medido na fatia de
 *       cliente, e ele nasce de novo em toda busca que junta texto e número.</li>
 *   <li><b>{@code SQLSTATE 23505} e o NOME da restrição</b>, nunca procurar "UNIQUE" na
 *       mensagem — que muda de versão e é traduzida pelo idioma do servidor.</li>
 *   <li><b>{@code RETURNING id}</b>, nunca {@code getGeneratedKeys()} devolvendo zero
 *       calado.</li>
 *   <li><b>{@code OffsetDateTime} em UTC explícito</b>, nunca {@code LocalDateTime}, que
 *       não carrega fuso e seria interpretado no fuso da sessão.</li>
 * </ol>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA.</b> Duplicata de e-mail vira
 * {@link EmailJaCadastradoException} (409) — a única resolvível por quem opera. O resto
 * vira {@link FalhaNoCadastroDeContatosException} (500) com causa-raiz.</p>
 */
@ApplicationScoped
public class RepositorioDeContatosPostgres implements RepositorioDeContatosPort {

    private static final String COLUNAS =
            "id, ddd, telefone, celular, whatsapp, email, tipo_contato, criado_em";

    private static final String UNIQUE_VIOLATION = "23505";
    private static final String RESTRICAO_EMAIL = "contato_email_unico";

    @Inject
    DataSource dataSource;

    @Inject
    Relogio relogio;

    @Override
    public Contato salvar(Contato novo) {
        String sql = "INSERT INTO contato (ddd, telefone, celular, whatsapp, email, "
                + "tipo_contato, criado_em) VALUES (?, ?, ?, ?, ?, ?, ?) RETURNING id";
        Instant agora = relogio.agora();
        try (Connection c = Conexoes.abrir(dataSource, "contato");
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, novo.ddd());
            ps.setString(2, novo.telefone());
            ps.setString(3, novo.celular());
            ps.setString(4, novo.whatsapp());
            ps.setString(5, novo.email().valor());
            ps.setString(6, novo.tipo().name());
            ps.setObject(7, agora.atOffset(ZoneOffset.UTC));

            try (ResultSet chaves = ps.executeQuery()) {
                if (!chaves.next()) {
                    throw new FalhaNoCadastroDeContatosException("salvar",
                            novo.email().valor(), null);
                }
                return new Contato(chaves.getLong("id"), novo.ddd(), novo.telefone(),
                        novo.celular(), novo.whatsapp(), novo.email(), novo.tipo(), agora);
            }
        } catch (SQLException e) {
            throw traduzir(e, "salvar", novo.email().valor());
        }
    }

    @Override
    public Contato atualizar(Contato existente) {
        String sql = "UPDATE contato SET ddd = ?, telefone = ?, celular = ?, whatsapp = ?, "
                + "email = ?, tipo_contato = ? WHERE id = ?";
        try (Connection c = Conexoes.abrir(dataSource, "contato");
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, existente.ddd());
            ps.setString(2, existente.telefone());
            ps.setString(3, existente.celular());
            ps.setString(4, existente.whatsapp());
            ps.setString(5, existente.email().valor());
            ps.setString(6, existente.tipo().name());
            ps.setLong(7, existente.id());
            ps.executeUpdate();
            return existente;
        } catch (SQLException e) {
            throw traduzir(e, "atualizar", String.valueOf(existente.id()));
        }
    }

    @Override
    public boolean remover(long id) {
        try (Connection c = Conexoes.abrir(dataSource, "contato");
             PreparedStatement ps = c.prepareStatement("DELETE FROM contato WHERE id = ?")) {
            ps.setLong(1, id);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw traduzir(e, "remover", String.valueOf(id));
        }
    }

    @Override
    public Optional<Contato> porId(long id) {
        try (Connection c = Conexoes.abrir(dataSource, "contato");
             PreparedStatement ps = c.prepareStatement(
                     "SELECT " + COLUNAS + " FROM contato WHERE id = ?")) {
            ps.setLong(1, id);
            return primeiro(ps);
        } catch (SQLException e) {
            throw traduzir(e, "buscar-por-id", String.valueOf(id));
        }
    }

    @Override
    public Optional<Contato> porEmail(Email email) {
        try (Connection c = Conexoes.abrir(dataSource, "contato");
             PreparedStatement ps = c.prepareStatement(
                     "SELECT " + COLUNAS + " FROM contato WHERE email = ?")) {
            // Comparacao EXATA: o `Email` ja normalizou para minusculas na construcao,
            // entao os dois lados estao na mesma forma. Usar ILIKE aqui esconderia uma
            // eventual falha dessa normalizacao, e a unicidade do banco tambem e exata.
            ps.setString(1, email.valor());
            return primeiro(ps);
        } catch (SQLException e) {
            throw traduzir(e, "buscar-por-email", email.valor());
        }
    }

    @Override
    public List<Contato> listar(int pagina, int tamanho) {
        String sql = "SELECT " + COLUNAS + " FROM contato ORDER BY email, id LIMIT ? OFFSET ?";
        try (Connection c = Conexoes.abrir(dataSource, "contato");
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, tamanho);
            ps.setInt(2, pagina * tamanho);
            return todos(ps);
        } catch (SQLException e) {
            throw traduzir(e, "listar", "pagina=" + pagina);
        }
    }

    @Override
    public List<Contato> pesquisar(String termo, int pagina, int tamanho) {
        // CADA METADE DO FILTRO SO VALE SE TIVER O QUE PROCURAR. Sem os dois booleanos,
        // um termo sem digitos produz '%%' nos campos de telefone, e LIKE '%%' casa com
        // TODA linha — a busca devolve a base inteira parecendo funcionar. Foi o defeito
        // medido na fatia de cliente em 02/09; aqui ele ja nasce fechado.
        String sql = "SELECT " + COLUNAS + " FROM contato "
                + "WHERE (? AND email ILIKE ?) "
                + "   OR (? AND (telefone LIKE ? OR celular LIKE ? OR whatsapp LIKE ?)) "
                + "ORDER BY email, id LIMIT ? OFFSET ?";

        String texto = termo.strip();
        String digitos = termo.replaceAll("\\D", "");

        try (Connection c = Conexoes.abrir(dataSource, "contato");
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setBoolean(1, !texto.isEmpty());
            ps.setString(2, "%" + texto + "%");
            ps.setBoolean(3, !digitos.isEmpty());
            ps.setString(4, "%" + digitos + "%");
            ps.setString(5, "%" + digitos + "%");
            ps.setString(6, "%" + digitos + "%");
            ps.setInt(7, tamanho);
            ps.setInt(8, pagina * tamanho);
            return todos(ps);
        } catch (SQLException e) {
            throw traduzir(e, "pesquisar", "termo");
        }
    }

    @Override
    public List<Contato> porTipo(TipoContato tipo, int pagina, int tamanho) {
        String sql = "SELECT " + COLUNAS + " FROM contato WHERE tipo_contato = ? "
                + "ORDER BY email, id LIMIT ? OFFSET ?";
        try (Connection c = Conexoes.abrir(dataSource, "contato");
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, tipo.name());
            ps.setInt(2, tamanho);
            ps.setInt(3, pagina * tamanho);
            return todos(ps);
        } catch (SQLException e) {
            throw traduzir(e, "listar-por-tipo", tipo.name());
        }
    }

    /**
     * Quem recebe o alerta de desastre deste cliente — <b>todos</b>, sem paginação.
     *
     * <p>Paginar aqui avisaria a primeira página de pessoas e esqueceria as demais, sem
     * erro nenhum. É a única consulta da fatia sem teto, e é deliberado.</p>
     */
    @Override
    public List<Contato> deEmergenciaDoCliente(long clienteId) {
        String sql = "SELECT " + colunasComPrefixo("c") + " FROM contato c "
                + "JOIN cliente_contato cc ON cc.contato_id = c.id "
                + "WHERE cc.cliente_id = ? AND c.tipo_contato = ? "
                + "ORDER BY c.email, c.id";
        try (Connection con = Conexoes.abrir(dataSource, "contato");
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, clienteId);
            ps.setString(2, TipoContato.EMERGENCIA.name());
            return todos(ps);
        } catch (SQLException e) {
            throw traduzir(e, "contatos-de-emergencia", String.valueOf(clienteId));
        }
    }

    @Override
    public List<Contato> doCliente(long clienteId) {
        String sql = "SELECT " + colunasComPrefixo("c") + " FROM contato c "
                + "JOIN cliente_contato cc ON cc.contato_id = c.id "
                + "WHERE cc.cliente_id = ? ORDER BY c.email, c.id";
        try (Connection con = Conexoes.abrir(dataSource, "contato");
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setLong(1, clienteId);
            return todos(ps);
        } catch (SQLException e) {
            throw traduzir(e, "contatos-do-cliente", String.valueOf(clienteId));
        }
    }

    /**
     * Liga um contato a um cliente.
     *
     * <p><b>Idempotente por construção:</b> {@code ON CONFLICT DO NOTHING} sobre a chave
     * primária composta. Vincular duas vezes é o resultado normal de um clique duplo ou
     * de um reenvio, e falhar nisso transformaria uma repetição inofensiva em erro na
     * cara de quem opera.</p>
     */
    @Override
    public void vincularAoCliente(long contatoId, long clienteId) {
        String sql = "INSERT INTO cliente_contato (cliente_id, contato_id) VALUES (?, ?) "
                + "ON CONFLICT DO NOTHING";
        try (Connection c = Conexoes.abrir(dataSource, "contato");
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, clienteId);
            ps.setLong(2, contatoId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw traduzir(e, "vincular-ao-cliente", contatoId + "->" + clienteId);
        }
    }

    @Override
    public void desvincularDoCliente(long contatoId, long clienteId) {
        String sql = "DELETE FROM cliente_contato WHERE cliente_id = ? AND contato_id = ?";
        try (Connection c = Conexoes.abrir(dataSource, "contato");
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, clienteId);
            ps.setLong(2, contatoId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw traduzir(e, "desvincular-do-cliente", contatoId + "->" + clienteId);
        }
    }

    @Override
    public long contar() {
        try (Connection c = Conexoes.abrir(dataSource, "contato");
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT count(*) FROM contato")) {
            return rs.next() ? rs.getLong(1) : 0L;
        } catch (SQLException e) {
            throw traduzir(e, "contar", "contato");
        }
    }

    // ------------------------------------------------------------------ apoio

    /** As mesmas colunas, qualificadas — necessário quando há JOIN. */
    private static String colunasComPrefixo(String prefixo) {
        return prefixo + ".id, " + prefixo + ".ddd, " + prefixo + ".telefone, "
                + prefixo + ".celular, " + prefixo + ".whatsapp, " + prefixo + ".email, "
                + prefixo + ".tipo_contato, " + prefixo + ".criado_em";
    }

    private static Optional<Contato> primeiro(PreparedStatement ps) throws SQLException {
        try (ResultSet rs = ps.executeQuery()) {
            return rs.next() ? Optional.of(daLinha(rs)) : Optional.empty();
        }
    }

    private static List<Contato> todos(PreparedStatement ps) throws SQLException {
        List<Contato> lista = new ArrayList<>();
        try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                lista.add(daLinha(rs));
            }
        }
        return lista;
    }

    private static Contato daLinha(ResultSet rs) throws SQLException {
        return new Contato(
                rs.getLong("id"),
                rs.getString("ddd"),
                rs.getString("telefone"),
                rs.getString("celular"),
                rs.getString("whatsapp"),
                new Email(rs.getString("email")),
                TipoContato.de(rs.getString("tipo_contato")),
                rs.getObject("criado_em", OffsetDateTime.class).toInstant());
    }

    /**
     * Traduz o erro do banco para a linguagem da fatia.
     *
     * <p>Reconhece pelo {@code SQLSTATE} padrão e pelo <b>nome</b> da restrição. Um
     * {@code 23505} de outra restrição não é traduzido de propósito: dizer "e-mail já
     * cadastrado" para uma duplicata diferente mandaria corrigir o campo errado.</p>
     */
    private static RuntimeException traduzir(SQLException e, String operacao, String alvo) {
        if (UNIQUE_VIOLATION.equals(e.getSQLState())) {
            String texto = e.getMessage() == null ? "" : e.getMessage().toLowerCase();
            if (texto.contains(RESTRICAO_EMAIL)) {
                return new EmailJaCadastradoException(alvo);
            }
        }
        return new FalhaNoCadastroDeContatosException(operacao, alvo, e);
    }
}
