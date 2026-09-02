package org.nasa.geo.domain;

import org.nasa.geo.domain.exceptions.RaioInvalidoException;

import java.util.Locale;

/**
 * A caixa geográfica que a API da NASA usa para filtrar eventos por região.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO.</b> Perguntar à EONET "que eventos aconteceram perto deste
 * endereço" exige mandar uma caixa, não um raio — a API filtra por retângulo. Esta classe
 * traduz "50 km em volta daqui" para o retângulo que a NASA entende, e o faz do lado
 * seguro: <b>a caixa é sempre igual ou maior que o círculo</b>, e a distância exata é
 * conferida depois com {@link Geodesia}.</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO.</b></p>
 * <ol>
 *   <li><b>Erra para o lado de trazer MAIS.</b> Num sistema de alerta, deixar de buscar
 *       um evento é pior que buscar um a mais e descartá-lo no filtro exato. Toda decisão
 *       ambígua aqui alarga a caixa.</li>
 *   <li><b>Latitude é grampeada em [-90, 90].</b> Um raio grande perto do polo estouraria
 *       o limite, e a API recusaria a consulta inteira — o alerta simplesmente não
 *       rodaria, sem ninguém saber por quê.</li>
 *   <li><b>Perto dos polos, a longitude vira o globo inteiro.</b> A conversão de km para
 *       graus de longitude divide por {@code cos(latitude)}, que tende a zero no polo e
 *       faz a largura explodir. O legado não tratava isso e produzia caixa inválida.</li>
 *   <li><b>Antimeridiano alarga para o globo inteiro</b> em vez de partir a caixa em
 *       duas. É a mesma escolha do item 1: a EONET não aceita caixa que cruza os 180°, e
 *       a alternativa — cortar a caixa — perderia metade dos eventos em silêncio.</li>
 * </ol>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA.</b> Raio zero ou negativo lança
 * {@link RaioInvalidoException} — "zero km de raio" quase sempre é configuração não lida,
 * e devolver uma caixa degenerada faria o alerta parar de encontrar qualquer coisa,
 * silenciosamente.</p>
 *
 * @param oeste  menor longitude
 * @param sul    menor latitude
 * @param leste  maior longitude
 * @param norte  maior latitude
 */
public record CaixaDelimitadora(double oeste, double sul, double leste, double norte) {

    /** Acima desta latitude, um raio grande já cobre meridianos demais para valer a conta. */
    private static final double LATITUDE_POLAR = 89.0;

    /**
     * A caixa que contém o círculo de {@code raioKm} em volta do centro.
     *
     * @throws RaioInvalidoException se o raio não for positivo
     */
    public static CaixaDelimitadora emVoltaDe(Coordenada centro, double raioKm) {
        if (raioKm <= 0 || Double.isNaN(raioKm)) {
            throw new RaioInvalidoException(raioKm);
        }

        double grausDeLatitude = Math.toDegrees(raioKm / Geodesia.RAIO_DA_TERRA_KM);
        double sul = Math.max(-90.0, centro.latitude() - grausDeLatitude);
        double norte = Math.min(90.0, centro.latitude() + grausDeLatitude);

        // Perto do polo, `cos(latitude)` tende a zero e a largura em graus explode.
        // Cobrir o globo inteiro é a resposta correta: a alternativa é caixa invalida.
        if (Math.abs(centro.latitude()) >= LATITUDE_POLAR) {
            return new CaixaDelimitadora(-180.0, sul, 180.0, norte);
        }

        double cosseno = Math.cos(Math.toRadians(centro.latitude()));
        double grausDeLongitude = Math.toDegrees(raioKm / (Geodesia.RAIO_DA_TERRA_KM * cosseno));

        // Meia-largura de 180° ou mais já dá a volta no mundo.
        if (grausDeLongitude >= 180.0) {
            return new CaixaDelimitadora(-180.0, sul, 180.0, norte);
        }

        double oeste = centro.longitude() - grausDeLongitude;
        double leste = centro.longitude() + grausDeLongitude;

        // Antimeridiano: em vez de partir a caixa em duas (que a API não aceita),
        // alarga para o globo. Traz eventos a mais, e o filtro exato descarta.
        if (oeste < -180.0 || leste > 180.0) {
            return new CaixaDelimitadora(-180.0, sul, 180.0, norte);
        }
        return new CaixaDelimitadora(oeste, sul, leste, norte);
    }

    /**
     * No formato que a EONET espera: {@code oeste,sul,leste,norte}.
     *
     * <p><b>INVARIANTE:</b> {@link Locale#US} explícito. Sem ele, uma JVM em pt-BR
     * formataria {@code -23,56} com vírgula, e a API leria a caixa errada — ou recusaria
     * a consulta. É o tipo de defeito que só aparece na máquina de quem tem o idioma
     * diferente do de quem escreveu.</p>
     */
    public String comoParametroEonet() {
        return String.format(Locale.US, "%.5f,%.5f,%.5f,%.5f", oeste, sul, leste, norte);
    }

    /** O ponto está dentro desta caixa? Filtro grosseiro, antes da distância exata. */
    public boolean contem(Coordenada ponto) {
        return ponto.latitude() >= sul && ponto.latitude() <= norte
                && ponto.longitude() >= oeste && ponto.longitude() <= leste;
    }
}
