package org.nasa.inscrito.domain.exceptions;

import org.nasa.core.erro.CausaRaiz;
import org.nasa.core.erro.ErroDePipeline;

/**
 * A inscrição pedida não existe.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO.</b> Separar "não existe" de "deu erro ao buscar" — as duas
 * viram tela parecida e mandam investigar lugares opostos: a primeira é o pedido, a segunda
 * é o banco.</p>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA.</b> Sobe com {@link CausaRaiz#DADO_AUSENTE} e vira
 * 404 na borda.</p>
 */
public class InscricaoNaoEncontradaException extends ErroDePipeline {

    public InscricaoNaoEncontradaException(String alvo) {
        super("buscar-inscricao", alvo, CausaRaiz.DADO_AUSENTE,
              "inscricao nao encontrada");
    }
}
