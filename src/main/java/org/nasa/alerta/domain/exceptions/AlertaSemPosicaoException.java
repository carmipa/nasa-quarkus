package org.nasa.alerta.domain.exceptions;

import org.nasa.core.erro.CausaRaiz;
import org.nasa.core.erro.ErroDePipeline;

/**
 * Um desastre chegou à lista de proximidade sem posição utilizável.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO.</b> Numa lista ordenada por distância, um item sem distância
 * não tem lugar — ele iria para uma posição arbitrária, e o evento errado acabaria no
 * assunto do e-mail. Recusar na construção é o que impede isso de virar uma mensagem que
 * parece certa e não está.</p>
 *
 * <p><b>É DEFEITO DE PROGRAMA, não dado ruim.</b> A consulta já filtra por coordenada não
 * nula; chegar aqui significa que alguém montou o objeto por outro caminho. Por isso ela é
 * ruidosa: silenciar transformaria um bug em uma linha faltando na mensagem.</p>
 */
public class AlertaSemPosicaoException extends ErroDePipeline {

    public AlertaSemPosicaoException(String alvo) {
        super("montar-alerta", alvo, CausaRaiz.DADO_INVALIDO,
              "evento sem posicao utilizavel numa lista ordenada por distancia");
    }
}
