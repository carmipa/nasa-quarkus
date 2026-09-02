package org.nasa.endereco.domain.exceptions;

import org.nasa.core.erro.CausaRaiz;
import org.nasa.core.erro.ErroDePipeline;

/**
 * Os dados recebidos não descrevem um endereço.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO.</b> Nomeia o campo que falta, para a tela conseguir
 * destacá-lo. Erro genérico em formulário obriga o operador a adivinhar, e adivinhar leva
 * a preencher qualquer coisa só para o botão liberar — que é como entra o dado ruim.</p>
 *
 * <p><b>INVARIANTE.</b> Falha fechada no construtor do record. Inclui o caso do
 * {@code (0,0)}: coordenada ausente é ausente, e o par exato do null island é recusado no
 * domínio <b>e</b> no {@code CHECK} do banco — cinto e suspensório, porque este é o
 * defeito que não produz erro nenhum quando passa.</p>
 *
 * <p><b>FALHA.</b> Causa-raiz {@link CausaRaiz#DADO_INVALIDO}, com o campo no alvo.</p>
 */
public class EnderecoInvalidoException extends ErroDePipeline {
    public EnderecoInvalidoException(String campo, String motivo) {
        super("validar-endereco", campo, CausaRaiz.DADO_INVALIDO, "campo " + campo + ": " + motivo);
    }
}
