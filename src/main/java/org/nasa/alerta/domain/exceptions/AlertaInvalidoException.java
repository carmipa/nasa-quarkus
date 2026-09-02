package org.nasa.alerta.domain.exceptions;

import org.nasa.core.erro.CausaRaiz;
import org.nasa.core.erro.ErroDePipeline;

/**
 * Um campo do alerta nao descreve um aviso entregavel.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO.</b> Recusa no domínio o que não chegaria a ninguém: aviso
 * sem destino, estado terminal sem instante de conclusão. Um alerta malformado não dá
 * erro em lugar nenhum — ele simplesmente fica na tabela sem nunca sair, e o silêncio é
 * idêntico ao de "não havia nada perto".</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO.</b> O alvo é o nome do campo. O <b>destino nunca entra na
 * mensagem</b>: é o e-mail de uma pessoa, e mensagem de erro vai para arquivo de log e
 * para print colado em chat.</p>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA.</b> Causa-raiz {@link CausaRaiz#DADO_INVALIDO}.</p>
 */
public class AlertaInvalidoException extends ErroDePipeline {

    public AlertaInvalidoException(String campo, String motivo) {
        super("validar-alerta", campo, CausaRaiz.DADO_INVALIDO, "campo " + campo + ": " + motivo);
    }
}
