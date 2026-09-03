package org.nasa.alerta.infrastructure.adapters;

import org.nasa.core.erro.CausaRaiz;
import org.nasa.core.erro.ErroDePipeline;

/**
 * O banco recusou a leitura dos desastres próximos.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO.</b> Separar "o banco falhou" de "não há desastre por perto".
 * As duas produzem uma tela sem lista, e são opostas: a primeira é o sistema quebrado, a
 * segunda é a melhor notícia possível para quem consultou.</p>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA.</b> É o próprio: carrega
 * {@link CausaRaiz#PERSISTENCIA_FALHOU} para o registrador único formatar uma vez.</p>
 */
public class FalhaNaLeituraDeDesastresException extends ErroDePipeline {

    public FalhaNaLeituraDeDesastresException(String operacao, String alvo, Throwable causa) {
        super(operacao, alvo, CausaRaiz.PERSISTENCIA_FALHOU,
              "o banco recusou a leitura de desastres proximos: " + operacao, causa);
    }
}
