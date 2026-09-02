package org.nasa.contato.domain.exceptions;

import org.nasa.core.erro.CausaRaiz;
import org.nasa.core.erro.ErroDePipeline;

/**
 * O contato pedido nao existe.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO.</b> Separa "não existe" de "não consegui buscar". Quem lê
 * "contato não encontrado" quando o banco caiu apaga um cadastro que estava certo, ou
 * cria um segundo em cima do primeiro.</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO.</b> Só é lançada quando a consulta <b>respondeu</b> e
 * veio vazia. Falha de infraestrutura tem exceção própria e causa-raiz diferente.</p>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA.</b> Causa-raiz {@link CausaRaiz#DADO_AUSENTE},
 * 404 na borda.</p>
 */
public class ContatoNaoEncontradoException extends ErroDePipeline {

    public ContatoNaoEncontradoException(String identificador) {
        super("buscar-contato", identificador, CausaRaiz.DADO_AUSENTE,
              "nenhum contato com este identificador");
    }
}
