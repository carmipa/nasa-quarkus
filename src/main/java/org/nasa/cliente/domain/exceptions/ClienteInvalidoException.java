package org.nasa.cliente.domain.exceptions;

import org.nasa.core.erro.CausaRaiz;
import org.nasa.core.erro.ErroDePipeline;

/**
 * Os dados recebidos não descrevem um cliente.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO.</b> Separa "faltou preencher" de qualquer outra falha do
 * cadastro. Importa porque a reação é outra: aqui a resposta é 400 e a tela precisa dizer
 * <b>qual campo</b> — erro genérico obriga o operador a adivinhar, e adivinhar em
 * formulário é o caminho mais curto para ele desistir ou preencher qualquer coisa.</p>
 *
 * <p><b>INVARIANTE.</b> Falha fechada no construtor do record: cliente inválido nunca
 * chega a existir como objeto, então nenhum repositório o grava por engano.</p>
 *
 * <p><b>FALHA.</b> Causa-raiz {@link CausaRaiz#DADO_INVALIDO}, com o nome do campo no
 * alvo — é o que permite a tela destacar exatamente o campo problemático.</p>
 */
public class ClienteInvalidoException extends ErroDePipeline {
    public ClienteInvalidoException(String campo, String motivo) {
        super("validar-cliente", campo, CausaRaiz.DADO_INVALIDO, "campo " + campo + ": " + motivo);
    }
}
