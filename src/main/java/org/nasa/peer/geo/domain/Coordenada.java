package org.nasa.peer.geo.domain;

import java.util.Optional;

/**
 * Um ponto na superfície da Terra, em graus decimais.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO.</b> É a moeda comum entre o endereço do cliente e o
 * evento natural da NASA: o alerta de proximidade só existe porque os dois falam a
 * mesma linguagem de coordenada. Por isso a coordenada é um <b>peer</b> — divergir aqui
 * não seria evolução, seria bug de cálculo de distância.</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO.</b></p>
 * <ol>
 *   <li>Latitude em {@code [-90, +90]} e longitude em {@code [-180, +180]}. Fora disso
 *       não é ponto na Terra, e o construtor recusa.</li>
 *   <li>Nenhum dos dois valores é nulo. Coordenada "meio preenchida" não existe.</li>
 *   <li><b>Coordenada ausente é representada por ausência</b> ({@link Optional#empty()}),
 *       nunca por um valor de fachada. Ver a nota sobre o par {@code (0,0)} abaixo.</li>
 * </ol>
 *
 * <p><b>Por que o par {@code (0,0)} NÃO é recusado aqui.</b> O "null island" — latitude
 * e longitude exatamente zero, no Golfo da Guiné — é o destino clássico de coordenada
 * que faltou e alguém preencheu com o valor padrão do tipo. Recusá-lo parece óbvio, e
 * seria <b>errado neste peer</b>: um evento natural da NASA <i>pode</i> legitimamente
 * ocorrer em alto-mar sobre aquele ponto, e a guarda estaria descartando dado verdadeiro.
 *
 * <p>A regra "endereço de cliente nunca fica em {@code (0,0)}" é verdadeira, mas é uma
 * regra <b>do endereço</b>, não da coordenada — e é lá que ela mora, com o
 * {@code CHECK} correspondente no banco. Esta observação está escrita porque a
 * simplificação "leva o CHECK para o peer, é o mesmo" reintroduziria o defeito
 * silenciosamente, e quem fizer isso daqui a seis meses não vai ter o contexto.</p>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA.</b> O construtor canônico lança
 * {@link IllegalArgumentException} com o valor recusado na mensagem. Para a origem
 * incerta — que é o caso real da BrasilAPI, cujo campo {@code location} veio ausente em
 * 1 de 6 CEPs medidos — use {@link #talvez(Double, Double)}, que devolve
 * {@link Optional#empty()} em vez de inventar um ponto.</p>
 *
 * @param latitude  graus decimais, sul negativo
 * @param longitude graus decimais, oeste negativo
 */
public record Coordenada(double latitude, double longitude) {

    private static final double LAT_MIN = -90.0;
    private static final double LAT_MAX = 90.0;
    private static final double LON_MIN = -180.0;
    private static final double LON_MAX = 180.0;

    public Coordenada {
        if (latitude < LAT_MIN || latitude > LAT_MAX) {
            throw new IllegalArgumentException(
                    "latitude fora do intervalo [-90, 90]: " + latitude);
        }
        if (longitude < LON_MIN || longitude > LON_MAX) {
            throw new IllegalArgumentException(
                    "longitude fora do intervalo [-180, 180]: " + longitude);
        }
        if (Double.isNaN(latitude) || Double.isNaN(longitude)) {
            throw new IllegalArgumentException("coordenada com NaN nao e um ponto na Terra");
        }
    }

    /**
     * Constrói a coordenada quando a origem <b>pode não ter</b> o dado.
     *
     * <p><b>PROPÓSITO.</b> A BrasilAPI devolve endereço com ou sem {@code location},
     * dependendo do provedor que atendeu o CEP (medido: 5 de 6). Este método é o ponto
     * onde essa incerteza vira ausência explícita em vez de virar {@code 0.0}.</p>
     *
     * <p><b>FALHA.</b> Qualquer um dos dois nulo ⇒ {@link Optional#empty()}. Valor
     * presente mas fora do intervalo ⇒ {@link IllegalArgumentException}: dado ausente e
     * dado <i>errado</i> são coisas diferentes e não podem ter a mesma resposta — a
     * primeira é normal, a segunda é defeito de quem chamou.</p>
     */
    public static Optional<Coordenada> talvez(Double latitude, Double longitude) {
        if (latitude == null || longitude == null) {
            return Optional.empty();
        }
        return Optional.of(new Coordenada(latitude, longitude));
    }
}
