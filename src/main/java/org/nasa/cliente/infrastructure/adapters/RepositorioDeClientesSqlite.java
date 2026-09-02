package org.nasa.cliente.infrastructure.adapters;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.nasa.cliente.domain.Cliente;
import org.nasa.cliente.domain.Documento;
import org.nasa.cliente.domain.exceptions.DocumentoJaCadastradoException;
import org.nasa.cliente.domain.ports.RepositorioDeClientesPort;
import org.nasa.core.tempo.Relogio;

import org.nasa.persistencia.infrastructure.adapters.Conexoes;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * O cadastro de clientes no SQLite.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO.</b> É o único lugar do sistema que sabe como um cliente
 * vira linha de tabela. Toda regra vive nos casos de uso; aqui só há tradução.</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO.</b></p>
 * <ol>
 *   <li><b>Consulta sempre parametrizada.</b> Nenhum valor entra no SQL por concatenação
 *       — inclusive na pesquisa por texto, que é justamente onde a tentação aparece.</li>
 *   <li><b>Ordenação determinística com desempate por {@code id}.</b> Ordenar só por nome
 *       faz dois homônimos trocarem de lugar entre uma página e outra, e a paginação passa
 *       a repetir um e pular o outro — sem erro nenhum.</li>
 *   <li><b>Instante em UTC, gravado como texto ISO-8601</b>, vindo do relógio injetado.</li>
 *   <li><b>Violação de UNIQUE é traduzida</b> para
 *       {@link DocumentoJaCadastradoException}: o operador precisa ler "esta pessoa já
 *       está cadastrada", não {@code SQLITE_CONSTRAINT_UNIQUE}.</li>
 * </ol>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA.</b> Erro de banco vira
 * {@link FalhaNoCadastroDeClientesException} com causa-raiz e a operação no alvo. A
 * violação de unicidade é a única traduzida para exceção de negócio — porque é a única
 * que o operador consegue resolver sozinho.</p>
 */
@ApplicationScoped
public class RepositorioDeClientesSqlite implements RepositorioDeClientesPort {

    private static final String COLUNAS =
            "id, nome, sobrenome, data_nascimento, documento, criado_em";

    @Inject
    DataSource dataSource;

    @Inject
    Relogio relogio;

    @Override
    public Cliente salvar(Cliente novo) {
        String sql = "INSERT INTO cliente (nome, sobrenome, data_nascimento, documento, criado_em) "
                + "VALUES (?, ?, ?, ?, ?)";
        Instant agora = relogio.agora();
        try (Connection c = Conexoes.abrir(dataSource, "cliente");
             PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, novo.nome());
            ps.setString(2, novo.sobrenome());
            ps.setString(3, novo.dataNascimento().toString());
            ps.setString(4, novo.documento().digitos());
            ps.setString(5, agora.toString());
            ps.executeUpdate();

            try (ResultSet chaves = ps.getGeneratedKeys()) {
                long id = chaves.next() ? chaves.getLong(1) : 0L;
                return new Cliente(id, novo.nome(), novo.sobrenome(),
                        novo.dataNascimento(), novo.documento(), agora);
            }
        } catch (SQLException e) {
            throw traduzir(e, "salvar", novo.documento().digitos());
        }
    }

    @Override
    public Cliente atualizar(Cliente existente) {
        String sql = "UPDATE cliente SET nome = ?, sobrenome = ?, data_nascimento = ?, "
                + "documento = ? WHERE id = ?";
        try (Connection c = Conexoes.abrir(dataSource, "cliente");
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, existente.nome());
            ps.setString(2, existente.sobrenome());
            ps.setString(3, existente.dataNascimento().toString());
            ps.setString(4, existente.documento().digitos());
            ps.setLong(5, existente.id());
            ps.executeUpdate();
            return existente;
        } catch (SQLException e) {
            throw traduzir(e, "atualizar", String.valueOf(existente.id()));
        }
    }

    @Override
    public boolean remover(long id) {
        try (Connection c = Conexoes.abrir(dataSource, "cliente");
             PreparedStatement ps = c.prepareStatement("DELETE FROM cliente WHERE id = ?")) {
            ps.setLong(1, id);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw traduzir(e, "remover", String.valueOf(id));
        }
    }

    @Override
    public Optional<Cliente> porId(long id) {
        try (Connection c = Conexoes.abrir(dataSource, "cliente");
             PreparedStatement ps = c.prepareStatement(
                     "SELECT " + COLUNAS + " FROM cliente WHERE id = ?")) {
            ps.setLong(1, id);
            return primeiro(ps);
        } catch (SQLException e) {
            throw traduzir(e, "buscar-por-id", String.valueOf(id));
        }
    }

    @Override
    public Optional<Cliente> porDocumento(Documento documento) {
        try (Connection c = Conexoes.abrir(dataSource, "cliente");
             PreparedStatement ps = c.prepareStatement(
                     "SELECT " + COLUNAS + " FROM cliente WHERE documento = ?")) {
            ps.setString(1, documento.digitos());
            return primeiro(ps);
        } catch (SQLException e) {
            throw traduzir(e, "buscar-por-documento", documento.digitos());
        }
    }

    @Override
    public boolean existeComDocumento(Documento documento) {
        return porDocumento(documento).isPresent();
    }

    @Override
    public List<Cliente> listar(int pagina, int tamanho) {
        // Desempate por id: sem ele, homônimos trocam de lugar entre páginas e a
        // paginação repete um e pula outro, sem erro nenhum.
        String sql = "SELECT " + COLUNAS + " FROM cliente ORDER BY nome, sobrenome, id "
                + "LIMIT ? OFFSET ?";
        try (Connection c = Conexoes.abrir(dataSource, "cliente");
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, tamanho);
            ps.setInt(2, pagina * tamanho);
            return todos(ps);
        } catch (SQLException e) {
            throw traduzir(e, "listar", "pagina=" + pagina);
        }
    }

    @Override
    public List<Cliente> pesquisar(String termo, int pagina, int tamanho) {
        String sql = "SELECT " + COLUNAS + " FROM cliente "
                + "WHERE nome LIKE ? OR sobrenome LIKE ? OR documento LIKE ? "
                + "ORDER BY nome, sobrenome, id LIMIT ? OFFSET ?";
        // O termo é PARÂMETRO, nunca concatenado: é aqui que a injeção de SQL entraria.
        String curinga = "%" + termo.replaceAll("[^\\p{L}\\p{N} ]", "") + "%";
        String soDigitos = "%" + termo.replaceAll("[^0-9]", "") + "%";
        try (Connection c = Conexoes.abrir(dataSource, "cliente");
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, curinga);
            ps.setString(2, curinga);
            ps.setString(3, soDigitos);
            ps.setInt(4, tamanho);
            ps.setInt(5, pagina * tamanho);
            return todos(ps);
        } catch (SQLException e) {
            throw traduzir(e, "pesquisar", termo);
        }
    }

    @Override
    public long contar() {
        try (Connection c = Conexoes.abrir(dataSource, "cliente");
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT count(*) FROM cliente")) {
            return rs.next() ? rs.getLong(1) : 0L;
        } catch (SQLException e) {
            throw traduzir(e, "contar", "cliente");
        }
    }

    // ------------------------------------------------------------------ apoio

    private static Optional<Cliente> primeiro(PreparedStatement ps) throws SQLException {
        try (ResultSet rs = ps.executeQuery()) {
            return rs.next() ? Optional.of(daLinha(rs)) : Optional.empty();
        }
    }

    private static List<Cliente> todos(PreparedStatement ps) throws SQLException {
        List<Cliente> lista = new ArrayList<>();
        try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                lista.add(daLinha(rs));
            }
        }
        return lista;
    }

    private static Cliente daLinha(ResultSet rs) throws SQLException {
        return new Cliente(
                rs.getLong("id"),
                rs.getString("nome"),
                rs.getString("sobrenome"),
                LocalDate.parse(rs.getString("data_nascimento")),
                new Documento(rs.getString("documento")),
                Instant.parse(rs.getString("criado_em")));
    }

    /**
     * Traduz o erro do banco para a linguagem da fatia.
     *
     * <p>A violação de unicidade é a única que vira exceção de <b>negócio</b>: é a única
     * que o operador consegue resolver sozinho. As demais são falha de infraestrutura e
     * sobem como tal, com causa-raiz.</p>
     */
    private static RuntimeException traduzir(SQLException e, String operacao, String alvo) {
        String texto = e.getMessage() == null ? "" : e.getMessage().toUpperCase();
        if (texto.contains("UNIQUE") && texto.contains("DOCUMENTO")) {
            return new DocumentoJaCadastradoException(alvo);
        }
        return new FalhaNoCadastroDeClientesException(operacao, alvo, e);
    }
}
