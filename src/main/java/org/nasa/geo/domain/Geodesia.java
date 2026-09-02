package org.nasa.geo.domain;

/**
 * Distância entre dois pontos da Terra — a conta que decide se alguém é avisado.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO.</b> O alerta existe porque um evento natural aconteceu
 * <b>perto</b> de um endereço. "Perto" é esta função. Errar aqui não produz erro visível:
 * produz alerta que não chega a quem devia, ou que chega a quem mora a mil quilômetros.</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO.</b></p>
 * <ol>
 *   <li><b>Fórmula única.</b> Este é o dono da conta de distância. Se duas fatias
 *       calculassem do próprio jeito, o mesmo evento teria dois raios diferentes — e o
 *       erro seria silencioso, porque os dois números "parecem" certos.</li>
 *   <li><b>Haversine, não Pitágoras em graus.</b> Um grau de longitude vale ~111 km na
 *       linha do Equador e ~0 no polo; tratar graus como plano erra por centenas de
 *       quilômetros no sul do país.</li>
 *   <li><b>Simétrica e não negativa.</b> {@code de(a,b) == de(b,a)}, e nunca menor que
 *       zero — provado em teste, porque é o tipo de coisa que ninguém confere.</li>
 *   <li><b>Domínio puro:</b> sem framework, sem I/O, sem estado.</li>
 * </ol>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA.</b> Não falha: recebe {@link Coordenada}, que já
 * nasce validada. O raio da Terra usado é o médio (6371 km) — a Terra não é esfera
 * perfeita, e o erro dessa aproximação fica abaixo de 0,5%, muito menor que a incerteza
 * do próprio evento natural, cuja posição a NASA publica como um ponto para uma área
 * inteira.</p>
 */
public final class Geodesia {

    /** Raio médio da Terra, em quilômetros. */
    public static final double RAIO_DA_TERRA_KM = 6371.0;

    private Geodesia() {
    }

    /**
     * Distância em quilômetros pela superfície, entre dois pontos.
     *
     * <p><b>FALHA:</b> nenhuma. Pontos iguais devolvem zero; antípodas devolvem metade da
     * circunferência.</p>
     */
    public static double distanciaEmKm(Coordenada a, Coordenada b) {
        double latA = Math.toRadians(a.latitude());
        double latB = Math.toRadians(b.latitude());
        double deltaLat = Math.toRadians(b.latitude() - a.latitude());
        double deltaLon = Math.toRadians(b.longitude() - a.longitude());

        double h = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2)
                + Math.cos(latA) * Math.cos(latB)
                * Math.sin(deltaLon / 2) * Math.sin(deltaLon / 2);

        // `min(1, ...)` protege contra erro de ponto flutuante empurrar `h` acima de 1,
        // o que faria `asin` devolver NaN para pontos praticamente antípodas.
        return 2 * RAIO_DA_TERRA_KM * Math.asin(Math.min(1.0, Math.sqrt(h)));
    }

    /**
     * O ponto {@code alvo} está dentro do raio a partir de {@code centro}?
     *
     * <p><b>INVARIANTE:</b> o limite é <b>inclusivo</b>. Um evento exatamente na borda do
     * raio configurado gera alerta — na dúvida entre avisar e não avisar, um sistema de
     * desastre avisa.</p>
     */
    public static boolean dentroDoRaio(Coordenada centro, Coordenada alvo, double raioKm) {
        return distanciaEmKm(centro, alvo) <= raioKm;
    }
}
