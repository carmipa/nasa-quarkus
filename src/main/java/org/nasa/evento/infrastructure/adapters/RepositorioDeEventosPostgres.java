package org.nasa.evento.infrastructure.adapters;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.nasa.core.tempo.Relogio;
import org.nasa.evento.domain.EventoNatural;
import org.nasa.evento.domain.ports.RepositorioDeEventosPort;
import org.nasa.geo.domain.CaixaDelimitadora;
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
 * Os eventos naturais no PostgreSQL.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO.</b> Guarda o que a NASA publica e responde a pergunta que
 * dispara o alerta: "quais eventos ativos estão perto daqui?".</p>
 *
 * <p><b>A OPERAÇÃO CENTRAL É UM UPSERT, E ISSO NÃO É DETALHE.</b> A sincronização roda
 * repetidamente sobre os mesmos eventos: uma tempestade aberta reaparece em toda chamada,
 * com posição nova. Três desenhos possíveis, e dois deles quebram:</p>
 * <ul>
 *   <li><b>inserir sempre</b> — cria uma cópia por sincronização, e o mapa e a estatística
 *       incham sem erro nenhum. Foi o defeito do legado, onde a unicidade morava só no
 *       Java ({@code findByEonetIdApi().orElse(new)}) e duas sincronizações simultâneas
 *       liam "não existe" e inseriam as duas;</li>
 *   <li><b>ignorar o repetido</b> ({@code DO NOTHING}) — congela a posição no primeiro dia,
 *       e o alerta passa a decidir sobre onde a tempestade estava, não onde ela está;</li>
 *   <li><b>atualizar</b> ({@code DO UPDATE}) — o certo, e é o que está aqui.</li>
 * </ul>
 *
 * <p><b>INVARIANTES DO DOMÍNIO.</b></p>
 * <ol>
 *   <li><b>{@code ON CONFLICT (eonet_id) DO UPDATE}</b> torna a gravação idempotente <b>no
 *       banco</b>, não numa checagem prévia da aplicação — que não sobrevive a duas
 *       sincronizações simultâneas.</li>
 *   <li><b>{@code xmax = 0} distingue INSERIU de ATUALIZOU</b> na mesma ida ao banco. Sem
 *       isso a sincronização não consegue relatar quantos eventos são novos, e "trouxe 50"
 *       ficaria indistinguível de "os mesmos 50 de sempre".</li>
 *   <li><b>A busca por caixa é FILTRO GROSSEIRO.</b> Ela usa índice e reduz o conjunto;
 *       quem decide a distância é a geodésia, porque caixa é retângulo e raio é círculo —
 *       os cantos ficam mais longe que o raio.</li>
 *   <li><b>Coordenada é indivisível.</b> Ou os dois campos, ou nenhum — o {@code CHECK} do
 *       esquema recusa meia coordenada.</li>
 * </ol>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA.</b>
 * {@link FalhaNaPersistenciaDeEventosException} (500) com causa-raiz. Nunca carrega o
 * {@code jsonOriginal} na mensagem: pode ter quilobytes e vai para o arquivo de log.</p>
 */
@ApplicationScoped
public class RepositorioDeEventosPostgres implements RepositorioDeEventosPort {

    private static final String COLUNAS = "id, eonet_id, titulo, categoria, ocorrido_em, "
            + "latitude, longitude, json_original, sincronizado_em, encerrado_em";

    @Inject
    DataSource dataSource;

    @Inject
    Relogio relogio;

    @Override
    public Resultado gravarOuAtualizar(EventoNatural evento) {
        // `xmax = 0` e verdadeiro somente na linha recem-INSERIDA. E como o PostgreSQL
        // responde "inseriu ou atualizou?" sem uma segunda consulta — e sem a corrida que
        // uma segunda consulta teria.
        String sql = """
                INSERT INTO evento_natural
                    (eonet_id, titulo, categoria, ocorrido_em, latitude, longitude,
                     json_original, sincronizado_em, encerrado_em)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (eonet_id) DO UPDATE SET
                    titulo = EXCLUDED.titulo,
                    categoria = EXCLUDED.categoria,
                    ocorrido_em = EXCLUDED.ocorrido_em,
                    latitude = EXCLUDED.latitude,
                    longitude = EXCLUDED.longitude,
                    json_original = EXCLUDED.json_original,
                    sincronizado_em = EXCLUDED.sincronizado_em,
                    encerrado_em = EXCLUDED.encerrado_em
                RETURNING id, (xmax = 0) AS inserido""";

        Instant agora = relogio.agora();
        try (Connection c = Conexoes.abrir(dataSource, "evento_natural");
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, evento.eonetId());
            ps.setString(2, evento.titulo());
            ps.setString(3, evento.categoria());
            ps.setObject(4, evento.ocorridoEm().atOffset(ZoneOffset.UTC));
            if (evento.coordenada() == null) {
                ps.setNull(5, Types.DOUBLE);
                ps.setNull(6, Types.DOUBLE);
            } else {
                ps.setDouble(5, evento.coordenada().latitude());
                ps.setDouble(6, evento.coordenada().longitude());
            }
            ps.setString(7, evento.jsonOriginal());
            ps.setObject(8, agora.atOffset(ZoneOffset.UTC));
            ps.setObject(9, evento.encerradoEm() == null ? null
                    : evento.encerradoEm().atOffset(ZoneOffset.UTC));

            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new FalhaNaPersistenciaDeEventosException("gravar",
                            evento.eonetId(), null);
                }
                EventoNatural gravado = new EventoNatural(rs.getLong("id"), evento.eonetId(),
                        evento.titulo(), evento.categoria(), evento.ocorridoEm(),
                        evento.coordenada(), evento.jsonOriginal(), agora, evento.encerradoEm());
                return new Resultado(gravado, rs.getBoolean("inserido"));
            }
        } catch (SQLException e) {
            throw new FalhaNaPersistenciaDeEventosException("gravar", evento.eonetId(), e);
        }
    }

    @Override
    public Optional<EventoNatural> porId(long id) {
        try (Connection c = Conexoes.abrir(dataSource, "evento_natural");
             PreparedStatement ps = c.prepareStatement(
                     "SELECT " + COLUNAS + " FROM evento_natural WHERE id = ?")) {
            ps.setLong(1, id);
            return primeiro(ps);
        } catch (SQLException e) {
            throw new FalhaNaPersistenciaDeEventosException("buscar-por-id",
                    String.valueOf(id), e);
        }
    }

    @Override
    public Optional<EventoNatural> porEonetId(String eonetId) {
        try (Connection c = Conexoes.abrir(dataSource, "evento_natural");
             PreparedStatement ps = c.prepareStatement(
                     "SELECT " + COLUNAS + " FROM evento_natural WHERE eonet_id = ?")) {
            ps.setString(1, eonetId);
            return primeiro(ps);
        } catch (SQLException e) {
            throw new FalhaNaPersistenciaDeEventosException("buscar-por-eonet-id", eonetId, e);
        }
    }

    @Override
    public List<EventoNatural> listar(int pagina, int tamanho) {
        // Mais recente primeiro: numa lista de eventos naturais, o de ontem importa mais
        // que o de tres meses atras, e ninguem rola cinco paginas para chegar nele.
        String sql = "SELECT " + COLUNAS + " FROM evento_natural "
                + "ORDER BY ocorrido_em DESC, id DESC LIMIT ? OFFSET ?";
        try (Connection c = Conexoes.abrir(dataSource, "evento_natural");
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, tamanho);
            ps.setInt(2, pagina * tamanho);
            return todos(ps);
        } catch (SQLException e) {
            throw new FalhaNaPersistenciaDeEventosException("listar", "pagina=" + pagina, e);
        }
    }

    @Override
    public List<EventoNatural> porCategoria(String categoria, int pagina, int tamanho) {
        String sql = "SELECT " + COLUNAS + " FROM evento_natural WHERE categoria = ? "
                + "ORDER BY ocorrido_em DESC, id DESC LIMIT ? OFFSET ?";
        try (Connection c = Conexoes.abrir(dataSource, "evento_natural");
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, categoria);
            ps.setInt(2, tamanho);
            ps.setInt(3, pagina * tamanho);
            return todos(ps);
        } catch (SQLException e) {
            throw new FalhaNaPersistenciaDeEventosException("por-categoria", categoria, e);
        }
    }

    /**
     * Eventos com coordenada, das categorias pedidas.
     *
     * <p><b>A lista de categorias vira {@code ?} um a um, nunca texto concatenado.</b> Os
     * valores vêm da URL — é entrada de fora, e montar {@code IN ('a','b')} com concatenação
     * é a construção que produz injeção de SQL. O número de marcadores é gerado a partir do
     * <b>tamanho</b> da lista; os valores só entram por {@code setString}.</p>
     */
    @Override
    public List<EventoNatural> comCoordenadaNasCategorias(java.util.Collection<String> categorias,
                                                          int limite) {
        var limpas = categorias.stream()
                .filter(c -> c != null && !c.isBlank())
                .distinct()
                .toList();

        StringBuilder sql = new StringBuilder("SELECT ").append(COLUNAS)
                .append(" FROM evento_natural WHERE latitude IS NOT NULL"
                        + " AND longitude IS NOT NULL");
        if (!limpas.isEmpty()) {
            sql.append(" AND categoria IN (")
               .append("?,".repeat(limpas.size() - 1)).append("?)");
        }
        sql.append(" ORDER BY ocorrido_em DESC, id DESC LIMIT ?");

        try (Connection c = Conexoes.abrir(dataSource, "evento_natural");
             PreparedStatement ps = c.prepareStatement(sql.toString())) {
            int i = 1;
            for (String categoria : limpas) {
                ps.setString(i++, categoria);
            }
            ps.setInt(i, limite);
            return todos(ps);
        } catch (SQLException e) {
            throw new FalhaNaPersistenciaDeEventosException("mapa-por-categoria",
                    String.join(",", limpas), e);
        }
    }

    /**
     * Eventos ativos com coordenada dentro da caixa.
     *
     * <p><b>FILTRO GROSSEIRO, e é deliberado.</b> A caixa é um retângulo em graus; o raio
     * do alerta é um círculo em quilômetros. Os cantos do retângulo ficam <b>mais longe</b>
     * que o raio, então isto devolve candidatos a mais — e a geodésia decide. O ganho é
     * que a caixa usa índice e o círculo não.</p>
     *
     * <p><b>Antimeridiano:</b> quando a caixa cruza a linha de data, {@code oeste > leste},
     * e a comparação vira um OU em vez de um E — senão nenhum ponto satisfaria as duas
     * pontas ao mesmo tempo, e a consulta devolveria vazio em silêncio.</p>
     */
    @Override
    public List<EventoNatural> ativosNaCaixa(CaixaDelimitadora caixa, Instant desde, int limite) {
        boolean cruzaAntimeridiano = caixa.oeste() > caixa.leste();
        String longitudeNaFaixa = cruzaAntimeridiano
                ? "(longitude >= ? OR longitude <= ?)"
                : "(longitude >= ? AND longitude <= ?)";

        String sql = "SELECT " + COLUNAS + " FROM evento_natural "
                + "WHERE encerrado_em IS NULL "
                + "  AND latitude IS NOT NULL "
                + "  AND latitude BETWEEN ? AND ? "
                + "  AND " + longitudeNaFaixa
                + "  AND ocorrido_em >= ? "
                + "ORDER BY ocorrido_em DESC LIMIT ?";

        try (Connection c = Conexoes.abrir(dataSource, "evento_natural");
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setDouble(1, caixa.sul());
            ps.setDouble(2, caixa.norte());
            ps.setDouble(3, caixa.oeste());
            ps.setDouble(4, caixa.leste());
            ps.setObject(5, desde.atOffset(ZoneOffset.UTC));
            ps.setInt(6, limite);
            return todos(ps);
        } catch (SQLException e) {
            throw new FalhaNaPersistenciaDeEventosException("ativos-na-caixa",
                    caixa.comoParametroEonet(), e);
        }
    }

    @Override
    public List<ContagemPorCategoria> contarPorCategoria(Instant desde) {
        // `categoria IS NULL` vira um grupo proprio com nome declarado, em vez de sumir.
        // Categoria ausente e um fato sobre a fonte, e esconde-lo faria a soma das
        // categorias nao bater com o total de eventos — e alguem procuraria o defeito
        // na contagem, que estaria certa.
        String sql = "SELECT COALESCE(categoria, 'SEM_CATEGORIA') AS categoria, count(*) AS quantos "
                + "FROM evento_natural WHERE ocorrido_em >= ? "
                + "GROUP BY COALESCE(categoria, 'SEM_CATEGORIA') "
                + "ORDER BY quantos DESC, categoria";
        List<ContagemPorCategoria> contagens = new ArrayList<>();
        try (Connection c = Conexoes.abrir(dataSource, "evento_natural");
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, desde.atOffset(ZoneOffset.UTC));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    contagens.add(new ContagemPorCategoria(
                            rs.getString("categoria"), rs.getLong("quantos")));
                }
            }
            return contagens;
        } catch (SQLException e) {
            throw new FalhaNaPersistenciaDeEventosException("contar-por-categoria",
                    desde.toString(), e);
        }
    }

    /**
     * Contagem por dia, agrupada em UTC.
     *
     * <p><b>O {@code AT TIME ZONE 'UTC'} não é enfeite.</b> {@code ocorrido_em} é
     * {@code TIMESTAMPTZ}, e {@code date_trunc('day', ...)} sem fuso explícito usa o fuso
     * da <b>sessão</b> do banco. Uma sessão em São Paulo e outra em Lisboa agrupariam a
     * mesma linha em dias diferentes — e o gráfico mudaria conforme quem o abrisse, sem
     * erro nenhum. É a mesma família de defeito do log em {@code -03:00}.</p>
     */
    @Override
    public List<ContagemPorDia> contarPorDia(Instant desde) {
        String sql = """
                SELECT (date_trunc('day', ocorrido_em AT TIME ZONE 'UTC'))::date AS dia,
                       count(*) AS quantos
                  FROM evento_natural
                 WHERE ocorrido_em >= ?
                 GROUP BY 1
                 ORDER BY 1""";
        List<ContagemPorDia> serie = new ArrayList<>();
        try (Connection c = Conexoes.abrir(dataSource, "evento_natural");
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, desde.atOffset(ZoneOffset.UTC));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    serie.add(new ContagemPorDia(
                            rs.getObject("dia", java.time.LocalDate.class),
                            rs.getLong("quantos")));
                }
            }
            return serie;
        } catch (SQLException e) {
            throw new FalhaNaPersistenciaDeEventosException("contar-por-dia", desde.toString(), e);
        }
    }

    /**
     * Contagem por ANO, agrupada em UTC.
     *
     * <p>O {@code AT TIME ZONE 'UTC'} é obrigatório pela mesma razão do agrupamento por
     * dia — e aqui a consequência é mais visível: um evento de 31/12 às 22h UTC cairia no
     * ano seguinte numa sessão em fuso positivo, mudando <b>duas</b> colunas do gráfico.</p>
     *
     * <p>{@code count(DISTINCT categoria)} na mesma varredura: um ano com 400 eventos de
     * uma só categoria conta uma história diferente de um com 400 de doze, e fazer uma
     * segunda consulta por ano seria uma consulta por coluna do gráfico.</p>
     */
    @Override
    public List<ContagemPorAno> contarPorAno() {
        String sql = """
                SELECT EXTRACT(YEAR FROM ocorrido_em AT TIME ZONE 'UTC')::int AS ano,
                       count(*) AS quantos,
                       count(DISTINCT categoria) AS categorias
                  FROM evento_natural
                 GROUP BY 1
                 ORDER BY 1""";
        List<ContagemPorAno> serie = new ArrayList<>();
        try (Connection c = Conexoes.abrir(dataSource, "evento_natural");
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                serie.add(new ContagemPorAno(rs.getInt("ano"), rs.getLong("quantos"),
                        rs.getLong("categorias")));
            }
            return serie;
        } catch (SQLException e) {
            throw new FalhaNaPersistenciaDeEventosException("contar-por-ano", "todos", e);
        }
    }

    @Override
    public List<ContagemPorCategoria> contarPorCategoriaComCoordenada() {
        // A MESMA condicao de `comCoordenadaNasCategorias`. As duas consultas precisam
        // concordar: o numero no chip e a promessa do que o filtro vai desenhar.
        String sql = """
                SELECT COALESCE(categoria, 'SEM_CATEGORIA') AS categoria, count(*) AS quantos
                  FROM evento_natural
                 WHERE latitude IS NOT NULL AND longitude IS NOT NULL
                 GROUP BY 1
                 ORDER BY quantos DESC, categoria""";
        List<ContagemPorCategoria> contagens = new ArrayList<>();
        try (Connection c = Conexoes.abrir(dataSource, "evento_natural");
             PreparedStatement ps = c.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                contagens.add(new ContagemPorCategoria(
                        rs.getString("categoria"), rs.getLong("quantos")));
            }
            return contagens;
        } catch (SQLException e) {
            throw new FalhaNaPersistenciaDeEventosException("contar-categoria-com-coordenada",
                    "mapa", e);
        }
    }

    @Override
    public List<ContagemPorCategoria> contarPorCategoriaNoAno(int ano) {
        String sql = """
                SELECT COALESCE(categoria, 'SEM_CATEGORIA') AS categoria, count(*) AS quantos
                  FROM evento_natural
                 WHERE EXTRACT(YEAR FROM ocorrido_em AT TIME ZONE 'UTC') = ?
                 GROUP BY 1
                 ORDER BY quantos DESC, categoria""";
        List<ContagemPorCategoria> contagens = new ArrayList<>();
        try (Connection c = Conexoes.abrir(dataSource, "evento_natural");
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, ano);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    contagens.add(new ContagemPorCategoria(
                            rs.getString("categoria"), rs.getLong("quantos")));
                }
            }
            return contagens;
        } catch (SQLException e) {
            throw new FalhaNaPersistenciaDeEventosException("contar-categoria-do-ano",
                    String.valueOf(ano), e);
        }
    }

    @Override
    public long contarDoAno(int ano) {
        String sql = "SELECT count(*) FROM evento_natural "
                + "WHERE EXTRACT(YEAR FROM ocorrido_em AT TIME ZONE 'UTC') = ?";
        try (Connection c = Conexoes.abrir(dataSource, "evento_natural");
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setInt(1, ano);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0L;
            }
        } catch (SQLException e) {
            throw new FalhaNaPersistenciaDeEventosException("contar-do-ano",
                    String.valueOf(ano), e);
        }
    }

    @Override
    public long contar() {
        return umNumero("SELECT count(*) FROM evento_natural", "contar");
    }

    @Override
    public long contarAtivos() {
        return umNumero("SELECT count(*) FROM evento_natural WHERE encerrado_em IS NULL",
                "contar-ativos");
    }

    // ------------------------------------------------------------------ apoio

    private long umNumero(String sql, String operacao) {
        try (Connection c = Conexoes.abrir(dataSource, "evento_natural");
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            return rs.next() ? rs.getLong(1) : 0L;
        } catch (SQLException e) {
            throw new FalhaNaPersistenciaDeEventosException(operacao, "evento_natural", e);
        }
    }

    private static Optional<EventoNatural> primeiro(PreparedStatement ps) throws SQLException {
        try (ResultSet rs = ps.executeQuery()) {
            return rs.next() ? Optional.of(daLinha(rs)) : Optional.empty();
        }
    }

    private static List<EventoNatural> todos(PreparedStatement ps) throws SQLException {
        List<EventoNatural> lista = new ArrayList<>();
        try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                lista.add(daLinha(rs));
            }
        }
        return lista;
    }

    private static EventoNatural daLinha(ResultSet rs) throws SQLException {
        // Coordenada e INDIVISIVEL: le-se latitude e, so se ela existir, longitude.
        // `getDouble` devolve 0.0 para NULL — ler sem checar `wasNull` poria o evento no
        // null island, no Golfo da Guine, sem erro nenhum.
        Double latitude = rs.getObject("latitude", Double.class);
        Double longitude = rs.getObject("longitude", Double.class);
        Coordenada coordenada = Coordenada.talvez(latitude, longitude).orElse(null);

        OffsetDateTime encerrado = rs.getObject("encerrado_em", OffsetDateTime.class);
        OffsetDateTime sincronizado = rs.getObject("sincronizado_em", OffsetDateTime.class);

        return new EventoNatural(
                rs.getLong("id"),
                rs.getString("eonet_id"),
                rs.getString("titulo"),
                rs.getString("categoria"),
                rs.getObject("ocorrido_em", OffsetDateTime.class).toInstant(),
                coordenada,
                rs.getString("json_original"),
                sincronizado == null ? null : sincronizado.toInstant(),
                encerrado == null ? null : encerrado.toInstant());
    }
}
