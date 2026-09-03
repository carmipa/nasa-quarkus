package org.nasa.inscrito.infrastructure.adapters;

import org.nasa.core.erro.CausaRaiz;
import org.nasa.core.erro.ErroDePipeline;

/**
 * O banco recusou uma operação de inscrição.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO.</b> Separar falha de INFRAESTRUTURA de recusa de NEGÓCIO. As
 * duas viram tela parecida e mandam investigar lugares opostos: "e-mail já inscrito" é o
 * sistema funcionando; "o banco recusou" é o sistema quebrado.</p>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA.</b> É o próprio: carrega
 * {@link CausaRaiz#PERSISTENCIA_FALHOU} para o registrador único formatar uma vez.</p>
 */
public class FalhaNaInscricaoException extends ErroDePipeline {

    public FalhaNaInscricaoException(String operacao, String alvo, Throwable causa) {
        super(operacao, alvo, CausaRaiz.PERSISTENCIA_FALHOU,
              "o banco recusou a operacao de inscricao: " + operacao, causa);
    }
}
