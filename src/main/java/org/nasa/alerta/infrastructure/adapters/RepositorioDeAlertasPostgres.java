package org.nasa.alerta.infrastructure.adapters;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.nasa.alerta.domain.Alerta;
import org.nasa.alerta.domain.SituacaoAlerta;
import org.nasa.alerta.domain.ports.RepositorioDeAlertasPort;
import org.nasa.core.tempo.Relogio;
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
 * A caixa de saída dos alertas, e o modelo de leitura que descobre quem avisar.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO.</b> Guarda o aviso <b>antes</b> de ele sair — é o que torna
 * o envio seguro de repetir — e responde a pergunta central do sistema: "quem está perto
 * de qual desastre, e por onde falo com essa pessoa?".</p>
 *
 * <p><b>O SQL ATRAVESSA TABELAS DE OUTRAS FATIAS, E ISSO É DELIBERADO.</b> A regra
 * arquitetural proíbe <b>import</b> entre fatias, e ela é respeitada: nenhuma classe de
 * {@code cliente}, {@code contato}, {@code endereco} ou {@code evento} aparece aqui. O que
 * esta classe usa é o <b>esquema</b>, que é compartilhado e pertence ao peer
 * {@code persistencia}. A fatia de alerta tem o próprio modelo de leitura, e é isso que
 * permite ao cadastro de cliente mudar de forma amanhã sem quebrar o alerta.</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO.</b></p>
 * <ol>
 *   <li><b>{@code ON CONFLICT DO NOTHING} na chave {@code (cliente_id, evento_id)}.</b>
 *       Aqui {@code DO NOTHING} é o certo — ao contrário da sincronização de eventos, onde
 *       seria defeito. A diferença: lá a posição muda e precisa ser atualizada; aqui o
 *       aviso já foi dado, e regravá-lo o reenviaria.</li>
 *   <li><b>O candidato traz as coordenadas CRUAS dos dois lados.</b> Quem calcula a
 *       distância é o caso de uso, com a mesma geodésia do resto do sistema — um segundo
 *       cálculo em SQL seria um segundo lugar para divergir.</li>
 *   <li><b>Só contato de EMERGÊNCIA vira destinatário.</b> Filtrado no SQL: trazer os
 *       demais empurraria a decisão para quem chama, e um dia alguém esqueceria.</li>
 * </ol>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA.</b>
 * {@link FalhaNaPersistenciaDeAlertasException} (500) com causa-raiz. O destino nunca
 * entra na mensagem: é o e-mail de uma pessoa.</p>
 */
@ApplicationScoped
public class RepositorioDeAlertasPostgres implements RepositorioDeAlertasPort {

    private static final String COLUNAS = "id, cliente_id, evento_id, destino, situacao, "
            + "causa_raiz, tentativas, criado_em, concluido_em";

    /**
     * Quantos quilômetros um grau de latitude vale, aproximadamente.
     *
     * <p>Constante de <b>recorte grosseiro</b>, não de cálculo: serve para o SQL reduzir o
     * conjunto por índice. A distância que decide é a da geodésia.</p>
     */
    private static final double KM_POR_GRAU_DE_LATITUDE = 111.0;

    @Inject
    DataSource dataSource;

    @Inject
    Relogio relogio;

    @Override
    public boolean registrarSeNovo(Alerta novo) {
        // DO NOTHING e o CERTO aqui, e e o oposto da sincronizacao de eventos: la a
        // posicao muda e precisa ser atualizada; aqui o aviso ja foi dado, e regravar
        // significaria reenviar.
        String sql = "INSERT INTO alerta_enviado "
                + "(cliente_id, evento_id, destino, situacao, tentativas, criado_em) "
                + "VALUES (?, ?, ?, ?, 0, ?) ON CONFLICT DO NOTHING";
        try (Connection c = Conexoes.abrir(dataSource, "alerta_enviado");
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, novo.clienteId());
            ps.setLong(2, novo.eventoId());
            ps.setString(3, novo.destino());
            ps.setString(4, SituacaoAlerta.PENDENTE.name());
            ps.setObject(5, relogio.agora().atOffset(ZoneOffset.UTC));
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            throw new FalhaNaPersistenciaDeAlertasException("registrar",
                    novo.clienteId() + "/" + novo.eventoId(), e);
        }
    }

    @Override
    public List<Alerta> pendentes(int limite) {
        // Ordem de chegada: o mais antigo e o que espera ha mais tempo.
        String sql = "SELECT " + COLUNAS + " FROM alerta_enviado WHERE situacao = ? "
                + "ORDER BY criado_em, id LIMIT ?";
        try (Connection c = Conexoes.abrir(dataSource, "alerta_enviado");
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, SituacaoAlerta.PENDENTE.name());
            ps.setInt(2, limite);
            return todos(ps);
        } catch (SQLException e) {
            throw new FalhaNaPersistenciaDeAlertasException("pendentes", "limite=" + limite, e);
        }
    }

    @Override
    public Alerta atualizar(Alerta alerta) {
        String sql = "UPDATE alerta_enviado SET situacao = ?, causa_raiz = ?, tentativas = ?, "
                + "concluido_em = ? WHERE id = ?";
        try (Connection c = Conexoes.abrir(dataSource, "alerta_enviado");
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, alerta.situacao().name());
            ps.setString(2, alerta.causaRaiz());
            ps.setInt(3, alerta.tentativas());
            ps.setObject(4, alerta.concluidoEm() == null ? null
                    : alerta.concluidoEm().atOffset(ZoneOffset.UTC));
            ps.setLong(5, alerta.id());
            ps.executeUpdate();
            return alerta;
        } catch (SQLException e) {
            throw new FalhaNaPersistenciaDeAlertasException("atualizar",
                    String.valueOf(alerta.id()), e);
        }
    }

    @Override
    public Optional<Alerta> porId(long id) {
        try (Connection c = Conexoes.abrir(dataSource, "alerta_enviado");
             PreparedStatement ps = c.prepareStatement(
                     "SELECT " + COLUNAS + " FROM alerta_enviado WHERE id = ?")) {
            ps.setLong(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(daLinha(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new FalhaNaPersistenciaDeAlertasException("por-id", String.valueOf(id), e);
        }
    }

    @Override
    public List<Alerta> listar(int pagina, int tamanho) {
        String sql = "SELECT " + COLUNAS + " FROM alerta_enviado "
                + "ORDER BY criado_em DESC, id DESC LIMIT ? OFFSET ?";
        try (Connection c = Conexoes.abrir(dataSource, "alerta_enviado");
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, tamanho);
            ps.setInt(2, pagina * tamanho);
            return todos(ps);
        } catch (SQLException e) {
            throw new FalhaNaPersistenciaDeAlertasException("listar", "pagina=" + pagina, e);
        }
    }

    @Override
    public List<Alerta> porSituacao(SituacaoAlerta situacao, int pagina, int tamanho) {
        String sql = "SELECT " + COLUNAS + " FROM alerta_enviado WHERE situacao = ? "
                + "ORDER BY criado_em DESC, id DESC LIMIT ? OFFSET ?";
        try (Connection c = Conexoes.abrir(dataSource, "alerta_enviado");
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, situacao.name());
            ps.setInt(2, tamanho);
            ps.setInt(3, pagina * tamanho);
            return todos(ps);
        } catch (SQLException e) {
            throw new FalhaNaPersistenciaDeAlertasException("por-situacao", situacao.name(), e);
        }
    }

    @Override
    public List<Alerta> doCliente(long clienteId, int pagina, int tamanho) {
        String sql = "SELECT " + COLUNAS + " FROM alerta_enviado WHERE cliente_id = ? "
                + "ORDER BY criado_em DESC, id DESC LIMIT ? OFFSET ?";
        try (Connection c = Conexoes.abrir(dataSource, "alerta_enviado");
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, clienteId);
            ps.setInt(2, tamanho);
            ps.setInt(3, pagina * tamanho);
            return todos(ps);
        } catch (SQLException e) {
            throw new FalhaNaPersistenciaDeAlertasException("do-cliente",
                    String.valueOf(clienteId), e);
        }
    }

    @Override
    public List<ContagemPorSituacao> contarPorSituacao() {
        String sql = "SELECT situacao, count(*) AS quantos FROM alerta_enviado "
                + "GROUP BY situacao ORDER BY situacao";
        List<ContagemPorSituacao> contagens = new ArrayList<>();
        try (Connection c = Conexoes.abrir(dataSource, "alerta_enviado");
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) {
                contagens.add(new ContagemPorSituacao(
                        rs.getString("situacao"), rs.getLong("quantos")));
            }
            return contagens;
        } catch (SQLException e) {
            throw new FalhaNaPersistenciaDeAlertasException("contar", "alerta_enviado", e);
        }
    }

    /**
     * Os pares (endereço de cliente, evento ativo) próximos em GRAUS.
     *
     * <p><b>A conversão de km para graus aqui é grosseira de propósito.</b> Um grau de
     * latitude vale ~111 km em qualquer lugar; um grau de longitude vale 111 km no equador
     * e encolhe até zero nos polos, por isso a divisão pelo cosseno da latitude. O
     * {@code GREATEST(..., 0.01)} evita a divisão por zero perto dos polos, onde a janela
     * de longitude tenderia ao globo inteiro.</p>
     *
     * <p>Este recorte devolve pares <b>a mais</b>, e é assim que tem de ser: quem decide é
     * a geodésia, no caso de uso. Apertar aqui para "economizar" descartaria pares que a
     * distância real aprovaria — e o descarte seria invisível.</p>
     */
    @Override
    public List<Candidato> candidatos(double raioKm, Instant desde, int limite) {
        double grausDeLatitude = raioKm / KM_POR_GRAU_DE_LATITUDE;

        String sql = """
                SELECT cl.id            AS cliente_id,
                       cl.nome || ' ' || cl.sobrenome AS nome_cliente,
                       ct.email         AS destino,
                       ev.id            AS evento_id,
                       ev.titulo        AS evento_titulo,
                       en.latitude      AS lat_endereco,
                       en.longitude     AS lon_endereco,
                       ev.latitude      AS lat_evento,
                       ev.longitude     AS lon_evento
                  FROM cliente cl
                  JOIN cliente_endereco ce ON ce.cliente_id = cl.id
                  JOIN endereco en         ON en.id = ce.endereco_id AND en.latitude IS NOT NULL
                  JOIN cliente_contato cc  ON cc.cliente_id = cl.id
                  JOIN contato ct          ON ct.id = cc.contato_id AND ct.tipo_contato = 'EMERGENCIA'
                  JOIN evento_natural ev   ON ev.latitude IS NOT NULL
                                          AND ev.encerrado_em IS NULL
                                          AND ev.ocorrido_em >= ?
                                          AND abs(ev.latitude - en.latitude) <= ?
                                          AND abs(ev.longitude - en.longitude)
                                              <= ? / GREATEST(cos(radians(en.latitude)), 0.01)
                 ORDER BY ev.ocorrido_em DESC
                 LIMIT ?""";

        List<Candidato> candidatos = new ArrayList<>();
        try (Connection c = Conexoes.abrir(dataSource, "alerta_enviado");
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, desde.atOffset(ZoneOffset.UTC));
            ps.setDouble(2, grausDeLatitude);
            ps.setDouble(3, grausDeLatitude);
            ps.setInt(4, limite);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    candidatos.add(new Candidato(
                            rs.getLong("cliente_id"),
                            rs.getString("nome_cliente"),
                            rs.getString("destino"),
                            rs.getLong("evento_id"),
                            rs.getString("evento_titulo"),
                            rs.getDouble("lat_endereco"),
                            rs.getDouble("lon_endereco"),
                            rs.getDouble("lat_evento"),
                            rs.getDouble("lon_evento")));
                }
            }
            return candidatos;
        } catch (SQLException e) {
            throw new FalhaNaPersistenciaDeAlertasException("candidatos",
                    "raio=" + raioKm + "km", e);
        }
    }

    // ------------------------------------------------------------------ apoio

    private static List<Alerta> todos(PreparedStatement ps) throws SQLException {
        List<Alerta> lista = new ArrayList<>();
        try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                lista.add(daLinha(rs));
            }
        }
        return lista;
    }

    private static Alerta daLinha(ResultSet rs) throws SQLException {
        OffsetDateTime concluido = rs.getObject("concluido_em", OffsetDateTime.class);
        return new Alerta(
                rs.getLong("id"),
                rs.getLong("cliente_id"),
                rs.getLong("evento_id"),
                rs.getString("destino"),
                SituacaoAlerta.de(rs.getString("situacao")),
                rs.getString("causa_raiz"),
                rs.getInt("tentativas"),
                rs.getObject("criado_em", OffsetDateTime.class).toInstant(),
                concluido == null ? null : concluido.toInstant());
    }
}
