package org.nasa.alerta.domain;

import org.nasa.geo.domain.Coordenada;

import java.time.Instant;

/**
 * Um desastre que a NASA publicou perto do lugar consultado.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO.</b> É a linha do e-mail de alerta: o que aconteceu, a que
 * distância, e há quanto tempo. As três coisas juntas — sem uma delas, quem lê não consegue
 * decidir nada.</p>
 *
 * <p><b>A DISTÂNCIA É A GEODÉSICA, e é por isso que este campo existe.</b> A consulta ao
 * banco usa uma <b>caixa</b> em graus, porque caixa usa índice. Mas caixa é retângulo e raio
 * é círculo: o canto de uma caixa de 100 km fica a <b>141 km</b> do centro. O valor aqui é o
 * da geodésia, calculada depois — e é ele que decide se o evento entra na mensagem. O
 * projeto original parava na caixa, e alertava gente a 40% além do raio pedido.</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO.</b></p>
 * <ol>
 *   <li><b>A distância nunca é negativa nem infinita.</b> Um NaN vindo de coordenada torta
 *       ordenaria a lista de forma imprevisível e poria o evento errado no assunto.</li>
 *   <li><b>O evento tem sempre coordenada.</b> Sem posição não há distância, e um desastre
 *       sem posição não pode aparecer numa lista ordenada por proximidade — ele iria para
 *       algum lugar arbitrário dela.</li>
 * </ol>
 *
 * @param eonetId     o identificador da NASA, para quem quiser conferir na fonte
 * @param titulo      o que a NASA publicou
 * @param categoria   o tipo, no código da EONET
 * @param ocorridoEm  quando, em UTC
 * @param coordenada  onde
 * @param distanciaKm a geodésica até o ponto consultado — <b>não</b> a da caixa
 * @param ativo       se a NASA ainda não deu o evento por encerrado
 */
public record DesastreProximo(String eonetId, String titulo, String categoria,
                              Instant ocorridoEm, Coordenada coordenada,
                              double distanciaKm, boolean ativo) {

    public DesastreProximo {
        if (coordenada == null) {
            throw new org.nasa.alerta.domain.exceptions.AlertaSemPosicaoException(
                    eonetId == null ? "?" : eonetId);
        }
        if (Double.isNaN(distanciaKm) || Double.isInfinite(distanciaKm) || distanciaKm < 0) {
            // NaN ordena de forma imprevisivel, e o evento errado acabaria no assunto.
            throw new org.nasa.alerta.domain.exceptions.AlertaSemPosicaoException(
                    "distancia invalida para " + (eonetId == null ? "?" : eonetId));
        }
    }

    /** A distância arredondada, para a mensagem. Casa decimal em quilômetro é ruído. */
    public long distanciaArredondadaKm() {
        return Math.round(distanciaKm);
    }
}
