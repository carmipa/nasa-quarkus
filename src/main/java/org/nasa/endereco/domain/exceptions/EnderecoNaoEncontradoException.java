package org.nasa.endereco.domain.exceptions;

import org.nasa.core.erro.CausaRaiz;
import org.nasa.core.erro.ErroDePipeline;

/**
 * Não existe endereço com aquele identificador no cadastro.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO.</b> Resposta previsível a link antigo ou id já removido.
 * Vira 404 — o sistema não falhou, o registro é que não está lá.</p>
 *
 * <p><b>INVARIANTE.</b> Só em operação que EXIGE o endereço. Consulta que pode não achar
 * devolve {@code Optional.empty()}.</p>
 *
 * <p><b>FALHA.</b> Causa-raiz {@link CausaRaiz#DADO_AUSENTE}.</p>
 */
public class EnderecoNaoEncontradoException extends ErroDePipeline {
    public EnderecoNaoEncontradoException(String identificador) {
        super("buscar-endereco", identificador, CausaRaiz.DADO_AUSENTE,
              "nenhum endereco com este identificador");
    }
}
