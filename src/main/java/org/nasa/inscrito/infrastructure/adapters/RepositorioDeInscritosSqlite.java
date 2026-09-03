package org.nasa.inscrito.infrastructure.adapters;

import io.agroal.api.AgroalDataSource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.nasa.geo.domain.Coordenada;
import org.nasa.inscrito.domain.Cep;
import org.nasa.inscrito.domain.Email;
import org.nasa.inscrito.domain.Inscrito;
import org.nasa.inscrito.domain.exceptions.EmailJaInscritoException;
import org.nasa.inscrito.domain.ports.RepositorioDeInscritosPort;
import org.nasa.persistencia.infrastructure.adapters.Conexoes;
import org.nasa.persistencia.infrastructure.adapters.InstanteEmTexto;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * As inscrições no SQLite.
 *
 * <p><b>INVARIANTES DO DOMÍNIO.</b></p>
 * <ol>
 *   <li><b>E-mail duplicado vira {@link EmailJaInscritoException}</b>, que é <b>recusa</b> —
 *       não {@link FalhaNaInscricaoException}, que é falha. A distinção decide a tela e a
 *       telemetria, e ela é feita olhando a restrição que o banco violou, não fazendo um
 *       {@code SELECT} antes: entre o {@code SELECT} e o {@code INSERT} cabe o segundo
 *       clique, e é justamente ele o caso que se quer tratar.</li>
 *   <li><b>Todo instante passa por {@link InstanteEmTexto}.</b> É o único caminho por onde
 *       data vira texto aqui, e o esquema tem {@code CHECK} exigindo o {@code Z} — os dois
 *       juntos substituem o {@code TIMESTAMPTZ} que o SQLite não tem.</li>
 *   <li><b>Cancelar não apaga</b>, e a consulta de alcançáveis filtra por
 *       {@code cancelado_em IS NULL} <b>no banco</b>, nunca em Java: filtrar depois de
 *       trazer traria a lista inteira pela rede para descartar metade.</li>
 * </ol>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA.</b> Erro de banco vira
 * {@link FalhaNaInscricaoException} com causa-raiz. Nenhum {@code SQLException} escapa.</p>
 */
@ApplicationScoped
public class RepositorioDeInscritosSqlite implements RepositorioDeInscritosPort {

    private static final String COLUNAS =
            "id, nome, email, telefone, cep, latitude, longitude, raio_km, "
            + "criado_em, cancelado_em";

    @Inject
    AgroalDataSource dataSource;

    @Override
    public Inscrito gravar(Inscrito inscrito) {
        // RETURNING: o SQLite tem desde a 3.35, e evita a segunda viagem para descobrir o
        // id que o banco atribuiu.
        String sql = "INSERT INTO inscrito "
                + "(nome, email, telefone, cep, latitude, longitude, raio_km, criado_em) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?) RETURNING id";
        try (Connection c = Conexoes.abrir(dataSource, "inscrito");
             PreparedStatement ps = c.prepareStatement(sql)) {

            ps.setString(1, inscrito.nome());
            ps.setString(2, inscrito.email().valor());
            ps.setString(3, inscrito.telefone());
            ps.setString(4, inscrito.cep().digitos());
            if (inscrito.coordenada() == null) {
                // As duas juntas, sempre: o esquema tem CHECK exigindo que ou as duas
                // sejam nulas ou nenhuma. Metade de uma posicao nao e posicao.
                ps.setNull(5, java.sql.Types.REAL);
                ps.setNull(6, java.sql.Types.REAL);
            } else {
                ps.setDouble(5, inscrito.coordenada().latitude());
                ps.setDouble(6, inscrito.coordenada().longitude());
            }
            ps.setDouble(7, inscrito.raioKm());
            ps.setString(8, InstanteEmTexto.de(inscrito.criadoEm()));

            try (ResultSet rs = ps.executeQuery()) {
                long id = rs.next() ? rs.getLong(1) : 0L;
                return new Inscrito(id, inscrito.nome(), inscrito.email(),
                        inscrito.telefone(), inscrito.cep(), inscrito.coordenada(),
                        inscrito.raioKm(), inscrito.criadoEm(), inscrito.canceladoEm());
            }
        } catch (SQLException e) {
            if (violouEmailUnico(e)) {
                // RECUSA, nao falha: a pessoa ja esta na lista e o sistema funcionou.
                throw new EmailJaInscritoException("email");
            }
            throw new FalhaNaInscricaoException("gravar", "inscrito", e);
        }
    }

    /**
     * Se a falha foi a restrição de e-mail único.
     *
     * <p>O SQLite não usa SQLSTATE como o PostgreSQL: a informação está no texto, no formato
     * {@code UNIQUE constraint failed: inscrito.email}. Procurar pelo <b>nome da restrição
     * ou da coluna</b>, e não só por "UNIQUE", é o que impede confundir esta violação com
     * qualquer outra que venha a existir na tabela.</p>
     */
    private static boolean violouEmailUnico(SQLException e) {
        String texto = e.getMessage() == null ? "" : e.getMessage().toLowerCase();
        return texto.contains("unique")
                && (texto.contains("inscrito.email") || texto.contains("inscrito_email_unico"));
    }

    @Override
    public void atualizarCoordenada(long id, double latitude, double longitude) {
        String sql = "UPDATE inscrito SET latitude = ?, longitude = ? WHERE id = ?";
        try (Connection c = Conexoes.abrir(dataSource, "inscrito");
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setDouble(1, latitude);
            ps.setDouble(2, longitude);
            ps.setLong(3, id);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new FalhaNaInscricaoException("atualizar-coordenada", String.valueOf(id), e);
        }
    }

    @Override
    public boolean cancelar(long id, Instant agora) {
        // `AND cancelado_em IS NULL`: cancelar duas vezes nao muda a data do primeiro
        // cancelamento. Sem isso, o clique repetido reescreveria o historico.
        String sql = "UPDATE inscrito SET cancelado_em = ? "
                + "WHERE id = ? AND cancelado_em IS NULL";
        try (Connection c = Conexoes.abrir(dataSource, "inscrito");
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, InstanteEmTexto.de(agora));
            ps.setLong(2, id);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new FalhaNaInscricaoException("cancelar", String.valueOf(id), e);
        }
    }

    @Override
    public Optional<Inscrito> porId(long id) {
        return um("SELECT " + COLUNAS + " FROM inscrito WHERE id = ?",
                ps -> ps.setLong(1, id), String.valueOf(id));
    }

    @Override
    public Optional<Inscrito> porEmail(Email email) {
        return um("SELECT " + COLUNAS + " FROM inscrito WHERE email = ?",
                ps -> ps.setString(1, email.valor()), "email");
    }

    @Override
    public List<Inscrito> listar(int pagina, int tamanho) {
        // Mais recentes primeiro: quem acabou de se inscrever e quem se quer conferir.
        String sql = "SELECT " + COLUNAS + " FROM inscrito "
                + "ORDER BY criado_em DESC, id DESC LIMIT ? OFFSET ?";
        try (Connection c = Conexoes.abrir(dataSource, "inscrito");
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, tamanho);
            ps.setInt(2, Math.max(0, pagina) * tamanho);
            return todos(ps);
        } catch (SQLException e) {
            throw new FalhaNaInscricaoException("listar", "inscrito", e);
        }
    }

    @Override
    public List<Inscrito> alcancaveis(int limite) {
        // As DUAS condicoes no banco: ativo E com coordenada. Sem coordenada nao ha o que
        // comparar com a posicao do desastre, e avisar "por via das duvidas" quem o
        // sistema nao sabe localizar treinaria a pessoa a ignorar o aviso.
        String sql = "SELECT " + COLUNAS + " FROM inscrito "
                + "WHERE cancelado_em IS NULL AND latitude IS NOT NULL "
                + "ORDER BY id LIMIT ?";
        try (Connection c = Conexoes.abrir(dataSource, "inscrito");
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, Math.max(1, limite));
            return todos(ps);
        } catch (SQLException e) {
            throw new FalhaNaInscricaoException("alcancaveis", "inscrito", e);
        }
    }

    @Override
    public long contar() {
        return umNumero("SELECT count(*) FROM inscrito", "contar");
    }

    @Override
    public long contarAtivos() {
        return umNumero("SELECT count(*) FROM inscrito WHERE cancelado_em IS NULL",
                "contar-ativos");
    }

    @Override
    public long contarSemCoordenada() {
        // A tela MOSTRA este numero. Uma inscricao sem coordenada existe e nao recebe
        // alerta de proximidade; esconder isso faria alguem esperar um aviso que nunca vem.
        return umNumero("SELECT count(*) FROM inscrito "
                + "WHERE cancelado_em IS NULL AND latitude IS NULL", "contar-sem-coordenada");
    }

    // ------------------------------------------------------------------ apoio

    /** Um parâmetro a preencher, para as consultas de uma linha só. */
    @FunctionalInterface
    private interface Parametro {
        void aplicar(PreparedStatement ps) throws SQLException;
    }

    private Optional<Inscrito> um(String sql, Parametro parametro, String alvo) {
        try (Connection c = Conexoes.abrir(dataSource, "inscrito");
             PreparedStatement ps = c.prepareStatement(sql)) {
            parametro.aplicar(ps);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(daLinha(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new FalhaNaInscricaoException("buscar", alvo, e);
        }
    }

    private List<Inscrito> todos(PreparedStatement ps) throws SQLException {
        List<Inscrito> lista = new ArrayList<>();
        try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                lista.add(daLinha(rs));
            }
        }
        return lista;
    }

    private static Inscrito daLinha(ResultSet rs) throws SQLException {
        // `getObject` e nao `getDouble`: o segundo devolve 0.0 para NULL, e 0,0 e uma
        // coordenada VALIDA — no Golfo da Guine. Uma inscricao sem posicao viraria uma
        // inscricao no meio do Atlantico, recebendo alerta de desastre africano.
        Double lat = (Double) rs.getObject("latitude");
        Double lon = (Double) rs.getObject("longitude");
        Coordenada coordenada = (lat == null || lon == null) ? null : new Coordenada(lat, lon);

        return new Inscrito(
                rs.getLong("id"),
                rs.getString("nome"),
                new Email(rs.getString("email")),
                rs.getString("telefone"),
                new Cep(rs.getString("cep")),
                coordenada,
                rs.getDouble("raio_km"),
                InstanteEmTexto.para(rs.getString("criado_em")),
                InstanteEmTexto.para(rs.getString("cancelado_em")));
    }

    private long umNumero(String sql, String operacao) {
        try (Connection c = Conexoes.abrir(dataSource, "inscrito");
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getLong(1) : 0L;
        } catch (SQLException e) {
            throw new FalhaNaInscricaoException(operacao, "inscrito", e);
        }
    }
}
