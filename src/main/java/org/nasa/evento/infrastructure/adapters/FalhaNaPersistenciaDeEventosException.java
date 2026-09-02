package org.nasa.evento.infrastructure.adapters;

import org.nasa.core.erro.CausaRaiz;
import org.nasa.core.erro.ErroDePipeline;

/**
 * O banco recusou uma operacao sobre eventos.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO.</b> Separa falha de banco de falha da NASA. Numa
 * sincronização as duas aparecem juntas no mesmo log, e confundi-las manda investigar o
 * lado errado.</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO.</b> Nunca carrega o corpo do evento — o
 * {@code jsonOriginal} pode ter quilobytes, e mensagem de erro vai para o arquivo de log.</p>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA.</b> Causa-raiz
 * {@link CausaRaiz#PERSISTENCIA_FALHOU}, 500 na borda.</p>
 */
public class FalhaNaPersistenciaDeEventosException extends ErroDePipeline {

    public FalhaNaPersistenciaDeEventosException(String operacao, String alvo, Throwable causa) {
        super(operacao, alvo, CausaRaiz.PERSISTENCIA_FALHOU,
              "o banco recusou a operacao de evento: " + operacao, causa);
    }
}
