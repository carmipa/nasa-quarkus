package org.nasa.geo.domain.exceptions;

import org.nasa.core.erro.CausaRaiz;
import org.nasa.core.erro.ErroDePipeline;

/**
 * O par de graus recebido não descreve um ponto na superfície da Terra.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO.</b> Uma coordenada inválida não é um detalhe de validação:
 * ela vira distância errada no cálculo de proximidade, e distância errada é alerta de
 * desastre que não chega a quem devia — ou chega a quem não devia. Esta exceção existe
 * para que essa falha tenha nome próprio no painel, separada de qualquer outro
 * {@code DADO_INVALIDO} do sistema.</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO.</b></p>
 * <ol>
 *   <li>Latitude em {@code [-90, +90]}, longitude em {@code [-180, +180]}, nenhum
 *       {@code NaN}.</li>
 *   <li><b>Dado ausente NÃO passa por aqui.</b> Coordenada que a origem não tinha é
 *       {@code Optional.empty()}, não exceção — recusa esperada é valor de retorno. Esta
 *       exceção é só para valor <b>presente e errado</b>, que é defeito de quem chamou.</li>
 *   <li>A mensagem nomeia o campo e o valor recusado: sem o número, quem lê o log não
 *       sabe se o problema foi o dado ou a régua.</li>
 * </ol>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA.</b> Causa-raiz {@link CausaRaiz#DADO_INVALIDO}.
 * Falha fechada e imediata, no construtor do record: coordenada inválida nunca chega a
 * existir como objeto, então nenhum cálculo posterior herda o valor ruim.</p>
 */
public class CoordenadaForaDaTerraException extends ErroDePipeline {

    public CoordenadaForaDaTerraException(String campo, double valorRecusado, String regua) {
        super("validar-coordenada", campo, CausaRaiz.DADO_INVALIDO,
              campo + " fora do intervalo " + regua + ": " + valorRecusado);
    }

    public CoordenadaForaDaTerraException(String motivo) {
        super("validar-coordenada", "coordenada", CausaRaiz.DADO_INVALIDO, motivo);
    }
}
