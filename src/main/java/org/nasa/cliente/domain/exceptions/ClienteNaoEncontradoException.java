package org.nasa.cliente.domain.exceptions;

import org.nasa.core.erro.CausaRaiz;
import org.nasa.core.erro.ErroDePipeline;

/**
 * Não existe cliente com aquele identificador.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO.</b> É a resposta previsível a um pedido previsível: um id
 * já apagado, um link antigo, um documento digitado errado. Vira 404, nunca 500 — o
 * sistema não falhou; a pessoa é que não está lá.</p>
 *
 * <p><b>INVARIANTE.</b> Só é lançada em operação que EXIGE o cliente (alterar, excluir,
 * detalhar). Consulta que pode não achar devolve {@code Optional.empty()}: ausência é
 * resultado normal de busca, e transformá-la em exceção encheria o painel de erro falso —
 * e erro falso ensina a ignorar o painel.</p>
 *
 * <p><b>FALHA.</b> Causa-raiz {@link CausaRaiz#DADO_AUSENTE}.</p>
 */
public class ClienteNaoEncontradoException extends ErroDePipeline {
    public ClienteNaoEncontradoException(String identificador) {
        super("buscar-cliente", identificador, CausaRaiz.DADO_AUSENTE,
              "nenhum cliente com este identificador");
    }
}
