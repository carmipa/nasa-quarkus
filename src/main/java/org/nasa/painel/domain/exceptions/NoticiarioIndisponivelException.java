package org.nasa.painel.domain.exceptions;

import org.nasa.core.erro.CausaRaiz;
import org.nasa.core.erro.ErroDePipeline;

/**
 * A fonte de noticias nao respondeu, ou respondeu algo ilegivel.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO.</b> O noticiário é <b>vitrine</b>, não função. Esta exceção
 * existe para que a falha dele seja tratada como degradação — a home mostra tudo o mais e
 * avisa que as notícias estão indisponíveis — e nunca como erro da página.</p>
 *
 * <p><b>A HISTÓRIA QUE ELA CARREGA</b> (medida em 02/09/2026): a fonte do legado,
 * {@code api.reliefweb.int/v1}, foi <b>desativada</b> e responde {@code HTTP 410}. A v2
 * responde {@code 403} sem um {@code appname} aprovado. Uma fonte externa pode morrer sem
 * avisar, e a tela que depende dela precisa continuar de pé — por isso esta falha é
 * <b>aberta</b>.</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO.</b> Nunca derruba a home. Quem a captura é o caso de uso,
 * que devolve lista vazia com o motivo declarado.</p>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA.</b> Causa-raiz
 * {@link CausaRaiz#PROVEDOR_INDISPONIVEL}. Se chegasse à borda seria 503, mas na prática
 * não chega: a home a trata antes.</p>
 */
public class NoticiarioIndisponivelException extends ErroDePipeline {

    public NoticiarioIndisponivelException(String alvo, Throwable causa) {
        super("buscar-noticias", alvo, CausaRaiz.PROVEDOR_INDISPONIVEL,
              "a fonte de noticias nao respondeu; a home continua inteira sem elas", causa);
    }
}
