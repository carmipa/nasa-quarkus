package org.nasa.evento.domain.exceptions;

import org.nasa.core.erro.CausaRaiz;
import org.nasa.core.erro.ErroDePipeline;

/**
 * O evento pedido nao existe na nossa base.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO.</b> Distingue "a NASA nunca publicou este evento" de "nossa
 * base ainda não sincronizou". As duas produzem a mesma tela vazia e pedem ações
 * diferentes: no primeiro caso o identificador está errado, no segundo basta sincronizar.</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO.</b> Só é lançada quando a consulta respondeu e veio
 * vazia. Falha de infraestrutura tem exceção própria.</p>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA.</b> Causa-raiz {@link CausaRaiz#DADO_AUSENTE},
 * 404 na borda.</p>
 */
public class EventoNaoEncontradoException extends ErroDePipeline {

    public EventoNaoEncontradoException(String identificador) {
        super("buscar-evento", identificador, CausaRaiz.DADO_AUSENTE,
              "nenhum evento com este identificador na base local; talvez falte sincronizar");
    }
}
