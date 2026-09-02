package org.nasa.endereco.infrastructure.adapters;

import org.nasa.core.erro.CausaRaiz;
import org.nasa.core.erro.ErroDePipeline;

/**
 * O banco recusou uma operacao sobre enderecos.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO.</b> Separa falha de banco de falha dos provedores de CEP.
 * As duas aparecem no mesmo fluxo de cadastro e pedem correções opostas: uma é
 * infraestrutura nossa, a outra é serviço de terceiro que caiu. Confundi-las manda
 * investigar o lado errado.</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO.</b> Carrega a operação e o alvo; nunca o endereço
 * completo, que é dado pessoal e acabaria no arquivo de log.</p>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA.</b> Causa-raiz
 * {@link CausaRaiz#PERSISTENCIA_FALHOU}, 500 na borda — diferente do 503 de "provedor de
 * CEP indisponível", que pede tentar de novo.</p>
 */
public class FalhaNaPersistenciaDeEnderecosException extends ErroDePipeline {

    public FalhaNaPersistenciaDeEnderecosException(String operacao, String alvo, Throwable causa) {
        super(operacao, alvo, CausaRaiz.PERSISTENCIA_FALHOU,
              "o banco recusou a operacao de endereco: " + operacao, causa);
    }
}
