package org.nasa.telemetria.infrastructure.adapters;

import io.agroal.api.AgroalDataSource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.nasa.core.telemetria.Telemetria;
import org.nasa.persistencia.infrastructure.adapters.Conexoes;
import org.nasa.persistencia.infrastructure.adapters.InstanteEmTexto;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Lê e grava a telemetria no PostgreSQL.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO.</b> É o que faz a telemetria sobreviver a reinício — e
 * reinício é exatamente o que acontece depois de um incidente, que é exatamente quando se
 * quer olhar o histórico.</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO.</b></p>
 * <ol>
 *   <li><b>A gravação SOMA, nunca substitui.</b> O {@code ON CONFLICT DO UPDATE} acumula
 *       na linha existente. Substituir faria cada descarga apagar a anterior da mesma
 *       hora, e o gráfico mostraria só os últimos segundos de cada hora.</li>
 *   <li><b>É idempotente por (operação, hora)</b>, garantido pela restrição única do
 *       banco — não por checagem daqui, que não sobreviveria a duas descargas
 *       simultâneas.</li>
 *   <li><b>MÍNIMO e MÁXIMO usam {@code LEAST}/{@code GREATEST}</b>, não o valor novo. O
 *       máximo da hora é o maior de todas as descargas dela; sobrescrever com o da última
 *       descarga apagaria justamente o pico, que é o que se procura.</li>
 * </ol>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA.</b> Erro de banco vira
 * {@link FalhaNaTelemetriaException}, com causa-raiz. Quem chama decide — e o descarregador
 * decide continuar, porque telemetria é apoio.</p>
 */
@ApplicationScoped
public class RepositorioDeTelemetria {

    @Inject
    AgroalDataSource dataSource;

    /**
     * Soma as medidas nas linhas de (operação, hora).
     *
     * <p><b>Um lote, uma transação, um {@code addBatch}.</b> Uma ida ao banco por medida
     * faria a descarga de vinte operações custar vinte viagens de rede — e a descarga roda
     * de minuto em minuto.</p>
     *
     * @return quantas linhas foram tocadas
     */
    public int somar(List<Telemetria.Medida> medidas) {
        if (medidas.isEmpty()) {
            return 0;
        }
        // ON CONFLICT DO UPDATE que SOMA: e o que torna a descarga periodica correta.
        // `EXCLUDED` e a linha que teria sido inserida.
        String sql = """
                INSERT INTO telemetria_operacao
                       (operacao, hora, chamadas, recusas, falhas,
                        duracao_soma_ms, duracao_min_ms, duracao_max_ms, atualizado_em)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (operacao, hora) DO UPDATE SET
                    chamadas        = telemetria_operacao.chamadas + excluded.chamadas,
                    recusas         = telemetria_operacao.recusas  + excluded.recusas,
                    falhas          = telemetria_operacao.falhas   + excluded.falhas,
                    duracao_soma_ms = telemetria_operacao.duracao_soma_ms
                                      + excluded.duracao_soma_ms,
                    duracao_min_ms  = MIN(
                        COALESCE(telemetria_operacao.duracao_min_ms, excluded.duracao_min_ms),
                        excluded.duracao_min_ms),
                    duracao_max_ms  = MAX(
                        COALESCE(telemetria_operacao.duracao_max_ms, 0),
                        excluded.duracao_max_ms),
                    atualizado_em   = excluded.atualizado_em""";

        try (Connection c = Conexoes.abrir(dataSource, "telemetria_operacao");
             PreparedStatement ps = c.prepareStatement(sql)) {
            for (var m : medidas) {
                ps.setString(1, m.operacao());
                ps.setString(2, InstanteEmTexto.de(m.hora()));
                ps.setLong(3, m.chamadas());
                ps.setLong(4, m.recusas());
                ps.setLong(5, m.falhas());
                ps.setLong(6, m.duracaoSomaMs());
                ps.setLong(7, m.duracaoMinMs());
                ps.setLong(8, m.duracaoMaxMs());
                // `now()` do banco NAO serve: ele nao produz o `Z` que o CHECK exige, e a
                // catraca de UTC proibe ler relogio fora do `Relogio` injetado. O instante
                // vem da propria medida, que ja e UTC por construcao.
                ps.setString(9, InstanteEmTexto.de(m.hora()));
                ps.addBatch();
            }
            int[] r = ps.executeBatch();
            return r.length;
        } catch (SQLException e) {
            throw new FalhaNaTelemetriaException("gravar", medidas.size() + " medidas", e);
        }
    }

    /**
     * O resumo por operação numa janela — o que a tabela da página mostra.
     *
     * <p>Agregado no BANCO, não em Java: somar mil linhas horárias no servidor de
     * aplicação exigiria trazer as mil linhas pela rede para produzir vinte.</p>
     */
    public List<ResumoDaOperacao> resumo(Instant desde) {
        String sql = """
                SELECT operacao,
                       sum(chamadas)        AS chamadas,
                       sum(recusas)         AS recusas,
                       sum(falhas)          AS falhas,
                       sum(duracao_soma_ms) AS soma,
                       min(duracao_min_ms)  AS minimo,
                       max(duracao_max_ms)  AS maximo,
                       max(hora)            AS ultima
                  FROM telemetria_operacao
                 WHERE hora >= ?
                 GROUP BY operacao
                 ORDER BY sum(chamadas) DESC, operacao""";
        List<ResumoDaOperacao> resumos = new ArrayList<>();
        try (Connection c = Conexoes.abrir(dataSource, "telemetria_operacao");
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, InstanteEmTexto.de(desde));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    long chamadas = rs.getLong("chamadas");
                    resumos.add(new ResumoDaOperacao(
                            rs.getString("operacao"),
                            chamadas,
                            rs.getLong("recusas"),
                            rs.getLong("falhas"),
                            chamadas == 0 ? 0 : rs.getLong("soma") / chamadas,
                            rs.getLong("minimo"),
                            rs.getLong("maximo"),
                            InstanteEmTexto.para(rs.getString("ultima"))));
                }
            }
            return resumos;
        } catch (SQLException e) {
            throw new FalhaNaTelemetriaException("resumo", desde.toString(), e);
        }
    }

    /**
     * O resumo de uma operação.
     *
     * @param operacao   no vocabulário do log
     * @param chamadas   total na janela
     * @param recusas    quantas foram recusa deliberada
     * @param falhas     quantas quebraram
     * @param mediaMs    média da janela, calculada de soma ÷ contagem — nunca média de
     *                   médias, que estaria errada
     * @param minimoMs   a mais rápida
     * @param maximoMs   a mais lenta; é o caso que a média esconde
     * @param ultimaHora a hora mais recente com registro — diz se a operação ainda roda
     */
    public record ResumoDaOperacao(String operacao, long chamadas, long recusas, long falhas,
                                   long mediaMs, long minimoMs, long maximoMs,
                                   Instant ultimaHora) {

        /** Chamadas que deram certo. */
        public long sucessos() {
            return chamadas - recusas - falhas;
        }

        /** Percentual de sucesso, inteiro. Sem chamadas, 100 — nada falhou. */
        public long percentualDeSucesso() {
            return chamadas == 0 ? 100 : sucessos() * 100 / chamadas;
        }
    }

    /**
     * A série por hora de uma operação — ou de todas somadas.
     *
     * @param operacao {@code null} soma todas; é o gráfico geral da página
     */
    public List<PontoPorHora> porHora(Instant desde, String operacao) {
        String filtro = operacao == null || operacao.isBlank() ? "" : " AND operacao = ?";
        String sql = """
                SELECT hora,
                       sum(chamadas)        AS chamadas,
                       sum(recusas + falhas) AS problemas,
                       sum(duracao_soma_ms) AS soma
                  FROM telemetria_operacao
                 WHERE hora >= ?""" + filtro + """

                 GROUP BY hora
                 ORDER BY hora""";
        List<PontoPorHora> serie = new ArrayList<>();
        try (Connection c = Conexoes.abrir(dataSource, "telemetria_operacao");
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, InstanteEmTexto.de(desde));
            if (!filtro.isEmpty()) {
                ps.setString(2, operacao);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    long chamadas = rs.getLong("chamadas");
                    serie.add(new PontoPorHora(
                            InstanteEmTexto.para(rs.getString("hora")),
                            chamadas,
                            rs.getLong("problemas"),
                            chamadas == 0 ? 0 : rs.getLong("soma") / chamadas));
                }
            }
            return serie;
        } catch (SQLException e) {
            throw new FalhaNaTelemetriaException("por-hora", desde.toString(), e);
        }
    }

    /**
     * Um ponto da série horária.
     *
     * <p><b>Horas sem registro NÃO aparecem</b>, porque o banco só devolve o que existe.
     * Quem completa as horas vazias é a camada de cima, com o relógio — e a diferença
     * importa: um gráfico que desenha só as horas retornadas encurta a linha do tempo e faz
     * duas horas de atividade separadas por um dia parecerem consecutivas.</p>
     */
    public record PontoPorHora(Instant hora, long chamadas, long problemas, long mediaMs) {
    }

    /** Quantas linhas a tabela tem — a própria telemetria se mede. */
    public long contarLinhas() {
        try (Connection c = Conexoes.abrir(dataSource, "telemetria_operacao");
             PreparedStatement ps = c.prepareStatement(
                     "SELECT count(*) FROM telemetria_operacao");
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getLong(1) : 0L;
        } catch (SQLException e) {
            throw new FalhaNaTelemetriaException("contar", "telemetria_operacao", e);
        }
    }

    /** Contagem de linhas das tabelas de negócio — o painel mostra o tamanho da base. */
    /**
     * O tamanho de cada tabela de negócio.
     *
     * <p><b>TABELA AUSENTE É FALHA, NÃO SEÇÃO VAZIA — e isso é uma correção.</b> Em
     * 03/09/2026 esta lista ainda continha {@code cliente}, {@code endereco} e
     * {@code contato}, removidas do projeto. A consulta estourava com "no such table", o
     * {@code catch} da camada de cima devolvia lista vazia, e a seção <b>"Tamanho da base"
     * sumia da página</b> — sem erro, sem log, sem nada. Uma página com uma seção a menos
     * é indistinguível de uma correta.</p>
     *
     * <p>Agora a tabela ausente é <b>nomeada</b> na exceção. A tela continua inteira (a
     * camada de cima ainda degrada), mas o log passa a dizer <b>qual</b> tabela sumiu — que
     * é a única informação capaz de levar alguém ao conserto.</p>
     */
    public Map<String, Long> tamanhoDasTabelas() {
        // Lista DECLARADA, nao deduzida do catalogo do banco: varredura de catalogo
        // traria tabelas internas do Flyway e do proprio Postgres, e a pagina mostraria
        // ruido de infraestrutura no lugar do tamanho do negocio.
        List<String> tabelas = List.of("inscrito", "evento_natural",
                "alerta_enviado", "telemetria_operacao");
        Map<String, Long> contagens = new LinkedHashMap<>();
        try (Connection c = Conexoes.abrir(dataSource, "tabelas")) {
            for (String t : tabelas) {
                // O nome vem da lista ACIMA, nunca de fora — por isso a concatenacao aqui
                // e segura. `?` nao vale para nome de tabela em SQL nenhum.
                // O nome vem da lista ACIMA, nunca de fora — por isso a concatenacao
                // aqui e segura. `?` nao vale para nome de tabela em SQL nenhum.
                try (PreparedStatement ps = c.prepareStatement("SELECT count(*) FROM " + t);
                     ResultSet rs = ps.executeQuery()) {
                    contagens.put(t, rs.next() ? rs.getLong(1) : 0L);
                } catch (SQLException tabelaSumiu) {
                    // NOMEIA a tabela. Sem isto, a falha subia generica, a camada de cima
                    // devolvia lista vazia, e a secao sumia da tela sem dizer por que.
                    throw new FalhaNaTelemetriaException("tamanho-tabelas", t, tabelaSumiu);
                }
            }
            return contagens;
        } catch (SQLException e) {
            throw new FalhaNaTelemetriaException("tamanho-tabelas", "todas", e);
        }
    }
}
