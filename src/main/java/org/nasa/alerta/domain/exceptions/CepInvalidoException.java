package org.nasa.alerta.domain.exceptions;

import org.nasa.core.erro.CausaRaiz;
import org.nasa.core.erro.ErroDePipeline;

/**
 * O texto recebido não tem forma de CEP.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO.</b> Barra o pedido <b>antes</b> de gastar uma chamada
 * externa. O provedor de geocodificação aceita uma requisição por segundo; queimar essa
 * cota para receber "não encontrado" de um CEP com 7 dígitos é desperdício que aparece
 * como lentidão em quem digitou certo.</p>
 *
 * <p><b>INVARIANTE.</b> Falha fechada na construção do valor, sem rede.</p>
 *
 * <p><b>FALHA.</b> Causa-raiz {@link CausaRaiz#DADO_INVALIDO} — vira 400, com o valor
 * recebido no alvo para a tela poder mostrar o que foi digitado.</p>
 */
public class CepInvalidoException extends ErroDePipeline {
    public CepInvalidoException(String recebido, String motivo) {
        super("validar-cep", recebido, CausaRaiz.DADO_INVALIDO, motivo);
    }
}
