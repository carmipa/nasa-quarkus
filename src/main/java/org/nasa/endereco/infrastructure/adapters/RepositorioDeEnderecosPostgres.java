package org.nasa.endereco.infrastructure.adapters;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.nasa.core.tempo.Relogio;
import org.nasa.endereco.domain.Cep;
import org.nasa.endereco.domain.Endereco;
import org.nasa.endereco.domain.ports.RepositorioDeEnderecosPort;
import org.nasa.geo.domain.Coordenada;
import org.nasa.persistencia.infrastructure.adapters.Conexoes;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Os endereços no PostgreSQL.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO.</b> Guardar <b>onde</b> o cliente está é o que torna o
 * alerta possível. Sem isto o sistema sabe quem avisar e não sabe se o desastre é perto —
 * e a comparação nunca acontece.</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO.</b></p>
 * <ol>
 *   <li><b>Coordenada é indivisível.</b> Grava os dois campos ou nenhum, e o {@code CHECK}
 *       do esquema recusa meia coordenada. Na leitura, {@code getDouble} devolveria
 *       {@code 0.0} para NULL — e sem checar isso o endereço iria para o null island, no
 *       Golfo da Guiné, com o pino desenhado no mapa e nenhum erro.</li>
 *   <li><b>{@link #comCoordenadaDoCliente(long)} filtra no SQL</b>, não em memória. Quem
 *       chama recebe só o que dá para comparar com um evento.</li>
 *   <li><b>Vincular é idempotente</b> ({@code ON CONFLICT DO NOTHING}): repetir é o
 *       resultado normal de um clique duplo, e falhar nisso viraria erro na cara de quem
 *       opera.</li>
 * </ol>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA.</b>
 * {@link FalhaNaPersistenciaDeEnderecosException} (500) com causa-raiz.</p>
 */
@ApplicationScoped
public class RepositorioDeEnderecosPostgres implements RepositorioDeEnderecosPort {

    private static final String COLUNAS = "id, cep, numero, logradouro, bairro, localidade, "
            + "uf, complemento, latitude, longitude, criado_em";

    @Inject
    DataSource dataSource;

    @Inject
    Relogio relogio;

    @Override
    public Endereco salvar(Endereco novo) {
        String sql = "INSERT INTO endereco (cep, numero, logradouro, bairro, localidade, uf, "
                + "complemento, latitude, longitude, criado_em) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id";
        Instant agora = relogio.agora();
        try (Connection c = Conexoes.abrir(dataSource, "endereco");
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, novo.cep().digitos());
            if (novo.numero() == null) {
                ps.setNull(2, Types.INTEGER);
            } else {
                ps.setInt(2, novo.numero());
            }
            ps.setString(3, novo.logradouro());
            ps.setString(4, novo.bairro());
            ps.setString(5, novo.localidade());
            ps.setString(6, novo.uf());
            ps.setString(7, novo.complemento());
            // Os dois, ou nenhum: e o que o CHECK do esquema exige.
            if (novo.coordenada().isEmpty()) {
                ps.setNull(8, Types.DOUBLE);
                ps.setNull(9, Types.DOUBLE);
            } else {
                ps.setDouble(8, novo.coordenada().get().latitude());
                ps.setDouble(9, novo.coordenada().get().longitude());
            }
            ps.setObject(10, agora.atOffset(ZoneOffset.UTC));

            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new FalhaNaPersistenciaDeEnderecosException("salvar",
                            novo.cep().digitos(), null);
                }
                return new Endereco(rs.getLong("id"), novo.cep(), novo.numero(),
                        novo.logradouro(), novo.bairro(), novo.localidade(), novo.uf(),
                        novo.complemento(), novo.coordenada(), agora);
            }
        } catch (SQLException e) {
            throw new FalhaNaPersistenciaDeEnderecosException("salvar", novo.cep().digitos(), e);
        }
    }

    @Override
    public Optional<Endereco> porId(long id) {
        try (Connection c = Conexoes.abrir(dataSource, "endereco");
             PreparedStatement ps = c.prepareStatement(
                     "SELECT " + COLUNAS + " FROM endereco WHERE id = ?")) {
            ps.setLong(1, id);
            return primeiro(ps);
        } catch (SQLException e) {
            throw new FalhaNaPersistenciaDeEnderecosException("buscar-por-id",
                    String.valueOf(id), e);
        }
    }

    @Override
    public List<Endereco> listar(int pagina, int tamanho) {
        String sql = "SELECT " + COLUNAS + " FROM endereco "
                + "ORDER BY localidade, logradouro, id LIMIT ? OFFSET ?";
        try (Connection c = Conexoes.abrir(dataSource, "endereco");
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, tamanho);
            ps.setInt(2, pagina * tamanho);
            return todos(ps);
        } catch (SQLException e) {
            throw new FalhaNaPersistenciaDeEnderecosException("listar", "pagina=" + pagina, e);
        }
    }

    @Override
    public List<Endereco> porCep(String digitos, int pagina, int tamanho) {
        String sql = "SELECT " + COLUNAS + " FROM endereco WHERE cep = ? "
                + "ORDER BY numero, id LIMIT ? OFFSET ?";
        try (Connection c = Conexoes.abrir(dataSource, "endereco");
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, digitos);
            ps.setInt(2, tamanho);
            ps.setInt(3, pagina * tamanho);
            return todos(ps);
        } catch (SQLException e) {
            throw new FalhaNaPersistenciaDeEnderecosException("por-cep", digitos, e);
        }
    }

    @Override
    public boolean remover(long id) {
        try (Connection c = Conexoes.abrir(dataSource, "endereco");
             PreparedStatement ps = c.prepareStatement("DELETE FROM endereco WHERE id = ?")) {
            ps.setLong(1, id);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new FalhaNaPersistenciaDeEnderecosException("remover", String.valueOf(id), e);
        }
    }

    @Override
    public void vincularAoCliente(long enderecoId, long clienteId) {
        String sql = "INSERT INTO cliente_endereco (cliente_id, endereco_id) VALUES (?, ?) "
                + "ON CONFLICT DO NOTHING";
        try (Connection c = Conexoes.abrir(dataSource, "endereco");
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, clienteId);
            ps.setLong(2, enderecoId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new FalhaNaPersistenciaDeEnderecosException("vincular",
                    enderecoId + "->" + clienteId, e);
        }
    }

    @Override
    public void desvincularDoCliente(long enderecoId, long clienteId) {
        String sql = "DELETE FROM cliente_endereco WHERE cliente_id = ? AND endereco_id = ?";
        try (Connection c = Conexoes.abrir(dataSource, "endereco");
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, clienteId);
            ps.setLong(2, enderecoId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new FalhaNaPersistenciaDeEnderecosException("desvincular",
                    enderecoId + "->" + clienteId, e);
        }
    }

    @Override
    public List<Endereco> doCliente(long clienteId) {
        return doClienteFiltrado(clienteId, false);
    }

    @Override
    public List<Endereco> comCoordenadaDoCliente(long clienteId) {
        return doClienteFiltrado(clienteId, true);
    }

    private List<Endereco> doClienteFiltrado(long clienteId, boolean exigirCoordenada) {
        String sql = "SELECT " + colunasComPrefixo("e") + " FROM endereco e "
                + "JOIN cliente_endereco ce ON ce.endereco_id = e.id "
                + "WHERE ce.cliente_id = ? "
                + (exigirCoordenada ? "AND e.latitude IS NOT NULL " : "")
                + "ORDER BY e.localidade, e.logradouro, e.id";
        try (Connection c = Conexoes.abrir(dataSource, "endereco");
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, clienteId);
            return todos(ps);
        } catch (SQLException e) {
            throw new FalhaNaPersistenciaDeEnderecosException("enderecos-do-cliente",
                    String.valueOf(clienteId), e);
        }
    }

    /**
     * Os clientes que têm ao menos um endereço com coordenada.
     *
     * <p>É por onde a varredura de alerta começa. Percorrer clientes sem endereço
     * localizável seria trabalho garantido a não produzir nada — e, numa base grande, é a
     * diferença entre uma varredura que termina e uma que trava a máquina.</p>
     */
    @Override
    public List<Long> clientesComEnderecoLocalizavel() {
        String sql = "SELECT DISTINCT ce.cliente_id FROM cliente_endereco ce "
                + "JOIN endereco e ON e.id = ce.endereco_id "
                + "WHERE e.latitude IS NOT NULL ORDER BY ce.cliente_id";
        List<Long> ids = new ArrayList<>();
        try (Connection c = Conexoes.abrir(dataSource, "endereco");
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                ids.add(rs.getLong(1));
            }
            return ids;
        } catch (SQLException e) {
            throw new FalhaNaPersistenciaDeEnderecosException("clientes-localizaveis",
                    "cliente_endereco", e);
        }
    }

    @Override
    public long contar() {
        try (Connection c = Conexoes.abrir(dataSource, "endereco");
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT count(*) FROM endereco")) {
            return rs.next() ? rs.getLong(1) : 0L;
        } catch (SQLException e) {
            throw new FalhaNaPersistenciaDeEnderecosException("contar", "endereco", e);
        }
    }

    // ------------------------------------------------------------------ apoio

    private static String colunasComPrefixo(String p) {
        return p + ".id, " + p + ".cep, " + p + ".numero, " + p + ".logradouro, "
                + p + ".bairro, " + p + ".localidade, " + p + ".uf, " + p + ".complemento, "
                + p + ".latitude, " + p + ".longitude, " + p + ".criado_em";
    }

    private static Optional<Endereco> primeiro(PreparedStatement ps) throws SQLException {
        try (ResultSet rs = ps.executeQuery()) {
            return rs.next() ? Optional.of(daLinha(rs)) : Optional.empty();
        }
    }

    private static List<Endereco> todos(PreparedStatement ps) throws SQLException {
        List<Endereco> lista = new ArrayList<>();
        try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                lista.add(daLinha(rs));
            }
        }
        return lista;
    }

    private static Endereco daLinha(ResultSet rs) throws SQLException {
        // `getObject(..., Double.class)` devolve null de verdade. `getDouble` devolveria
        // 0.0 para NULL — e o endereco iria parar no Golfo da Guine, com pino no mapa e
        // nenhum erro aparecendo.
        Double latitude = rs.getObject("latitude", Double.class);
        Double longitude = rs.getObject("longitude", Double.class);
        Integer numero = rs.getObject("numero", Integer.class);

        return new Endereco(
                rs.getLong("id"),
                new Cep(rs.getString("cep")),
                numero,
                rs.getString("logradouro"),
                rs.getString("bairro"),
                rs.getString("localidade"),
                rs.getString("uf"),
                rs.getString("complemento"),
                Coordenada.talvez(latitude, longitude),
                rs.getObject("criado_em", OffsetDateTime.class).toInstant());
    }
}
