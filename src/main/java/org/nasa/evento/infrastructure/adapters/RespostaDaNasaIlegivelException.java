package org.nasa.evento.infrastructure.adapters;

import org.nasa.core.erro.CausaRaiz;
import org.nasa.core.erro.ErroDePipeline;

/**
 * A NASA respondeu, e o corpo nao e o que o contrato diz.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO.</b> É a exceção do dia em que a EONET mudar o formato. Sem
 * ela, essa mudança viraria "provedor indisponível" — e alguém investigaria rede, DNS e
 * firewall durante horas enquanto o servidor respondia 200 o tempo todo.</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO.</b> Nunca é lançada por falha de rede: para isso existe
 * {@link NasaIndisponivelException}. Esta significa <b>respondeu e eu não entendi</b>.</p>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA.</b> Causa-raiz {@link CausaRaiz#PROVEDOR_RECUSOU},
 * 502 na borda. A mensagem diz "o contrato pode ter mudado", que é o que manda olhar no
 * lugar certo.</p>
 */
public class RespostaDaNasaIlegivelException extends ErroDePipeline {

    public RespostaDaNasaIlegivelException(String alvo, Throwable causa) {
        super("ler-resposta-eonet", alvo, CausaRaiz.PROVEDOR_RECUSOU,
              "a NASA respondeu, mas o corpo nao pode ser interpretado; "
              + "o contrato da EONET pode ter mudado", causa);
    }
}
