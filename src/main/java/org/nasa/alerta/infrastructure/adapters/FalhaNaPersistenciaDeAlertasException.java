package org.nasa.alerta.infrastructure.adapters;

import org.nasa.core.erro.CausaRaiz;
import org.nasa.core.erro.ErroDePipeline;

/**
 * O banco recusou uma operacao sobre alertas.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO.</b> Separa "não consegui gravar o aviso" de "não consegui
 * enviar o aviso". As duas param o alerta e pedem correções opostas: uma é o banco, a
 * outra é o meio de entrega.</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO.</b> O alvo carrega o par {@code cliente/evento}, nunca o
 * destino — que é o e-mail de uma pessoa e acabaria no arquivo de log.</p>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA.</b> Causa-raiz
 * {@link CausaRaiz#PERSISTENCIA_FALHOU}, 500 na borda.</p>
 */
public class FalhaNaPersistenciaDeAlertasException extends ErroDePipeline {

    public FalhaNaPersistenciaDeAlertasException(String operacao, String alvo, Throwable causa) {
        super(operacao, alvo, CausaRaiz.PERSISTENCIA_FALHOU,
              "o banco recusou a operacao de alerta: " + operacao, causa);
    }
}
