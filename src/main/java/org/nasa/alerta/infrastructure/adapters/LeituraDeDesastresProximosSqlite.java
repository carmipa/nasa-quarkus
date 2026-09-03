package org.nasa.alerta.infrastructure.adapters;

import io.agroal.api.AgroalDataSource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.nasa.alerta.domain.DesastreProximo;
import org.nasa.alerta.domain.ports.LeituraDeDesastresProximosPort;
import org.nasa.geo.domain.Coordenada;
import org.nasa.geo.domain.Geodesia;
import org.nasa.persistencia.infrastructure.adapters.Conexoes;
import org.nasa.persistencia.infrastructure.adapters.InstanteEmTexto;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Lê os desastres próximos direto da tabela {@code evento_natural}.
 *
 * <p><b>Conhece o ESQUEMA, não a fatia.</b> Nenhum import de {@code org.nasa.evento} —
 * fatia não conhece fatia, e a guarda de fronteira reprova o build se conhecer. O que este
 * adaptador conhece é o contrato do banco, que é público entre as fatias por definição.</p>
 *
 * <p><b>AS DUAS ETAPAS, E POR QUE AS DUAS.</b></p>
 * <ol>
 *   <li><b>A caixa reduz.</b> Um retângulo em graus é o que o índice consegue usar; sem ele,
 *       cada consulta calcularia a geodésia contra a tabela inteira.</li>
 *   <li><b>A geodésia decide.</b> A caixa é um retângulo e o raio é um círculo: o canto de
 *       uma caixa de 100 km fica a <b>141 km</b> do centro — 41% além. O projeto original
 *       parava na caixa e alertava gente que não estava no raio.</li>
 * </ol>
 *
 * <p><b>A CAIXA É GENEROSA DE PROPÓSITO.</b> Ela usa {@code raio × √2} em latitude, que é a
 * diagonal do quadrado: uma caixa justa cortaria eventos que a geodésia aceitaria. Filtro
 * grosseiro que exclui resposta correta é pior que filtro nenhum, porque o erro é
 * invisível.</p>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA.</b> Erro de banco vira
 * {@link FalhaNaLeituraDeDesastresException}, com causa-raiz.</p>
 */
@ApplicationScoped
public class LeituraDeDesastresProximosSqlite implements LeituraDeDesastresProximosPort {

    /** Um grau de latitude tem sempre ~111 km. Longitude varia com o cosseno. */
    private static final double KM_POR_GRAU_DE_LATITUDE = 111.0;

    /**
     * O fator da diagonal.
     *
     * <p>{@code √2}. A caixa precisa conter o círculo inteiro, e o ponto mais distante do
     * centro dentro de um quadrado é o canto — a {@code raio × √2}. Uma caixa de lado
     * {@code raio} cortaria eventos legítimos perto das bordas, e o corte seria silencioso:
     * o resultado sairia menor, sem erro nenhum.</p>
     */
    private static final double DIAGONAL = Math.sqrt(2);

    @Inject
    AgroalDataSource dataSource;

    @Override
    public List<DesastreProximo> proximos(Coordenada onde, double raioKm, Instant desde,
                                          int limite) {
        double grausDeLatitude = (raioKm * DIAGONAL) / KM_POR_GRAU_DE_LATITUDE;
        // Perto dos polos o cosseno tende a zero e a caixa em longitude tenderia ao
        // infinito. O piso de 0,01 mantem a consulta finita — e ali a geodesia filtra o
        // excesso, que e o papel dela.
        double cosseno = Math.max(Math.cos(Math.toRadians(onde.latitude())), 0.01);
        double grausDeLongitude = grausDeLatitude / cosseno;

        String sql = """
                SELECT eonet_id, titulo, categoria, ocorrido_em, latitude, longitude,
                       encerrado_em
                  FROM evento_natural
                 WHERE latitude IS NOT NULL
                   AND ocorrido_em >= ?
                   AND latitude  BETWEEN ? AND ?
                   AND longitude BETWEEN ? AND ?
                 ORDER BY ocorrido_em DESC
                 LIMIT ?""";

        List<DesastreProximo> dentroDoRaio = new ArrayList<>();
        try (Connection c = Conexoes.abrir(dataSource, "evento_natural");
             PreparedStatement ps = c.prepareStatement(sql)) {

            ps.setString(1, InstanteEmTexto.de(desde));
            ps.setDouble(2, onde.latitude() - grausDeLatitude);
            ps.setDouble(3, onde.latitude() + grausDeLatitude);
            ps.setDouble(4, onde.longitude() - grausDeLongitude);
            ps.setDouble(5, onde.longitude() + grausDeLongitude);
            // O teto e aplicado na CAIXA, e por isso e generoso: a geodesia ainda vai
            // descartar parte. Um teto justo aqui poderia devolver menos que o pedido
            // depois do filtro fino.
            ps.setInt(6, Math.max(1, limite) * 4);

            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Double lat = (Double) rs.getObject("latitude");
                    Double lon = (Double) rs.getObject("longitude");
                    if (lat == null || lon == null) {
                        continue;
                    }
                    var posicao = new Coordenada(lat, lon);

                    // A GEODESIA DECIDE. A caixa so reduziu.
                    double distancia = Geodesia.distanciaEmKm(onde, posicao);
                    if (distancia > raioKm) {
                        continue;
                    }
                    dentroDoRaio.add(new DesastreProximo(
                            rs.getString("eonet_id"),
                            rs.getString("titulo"),
                            rs.getString("categoria"),
                            InstanteEmTexto.para(rs.getString("ocorrido_em")),
                            posicao,
                            distancia,
                            rs.getString("encerrado_em") == null));
                }
            }
        } catch (SQLException e) {
            throw new FalhaNaLeituraDeDesastresException("proximos", "evento_natural", e);
        }

        // Do MAIS PROXIMO ao mais distante: e a ordem em que a informacao e util. Quem le
        // um alerta quer saber primeiro o que esta em cima dele.
        dentroDoRaio.sort(Comparator.comparingDouble(DesastreProximo::distanciaKm));
        return dentroDoRaio.size() > limite
                ? new ArrayList<>(dentroDoRaio.subList(0, Math.max(1, limite)))
                : dentroDoRaio;
    }
}
