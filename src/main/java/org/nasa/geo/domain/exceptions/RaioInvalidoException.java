package org.nasa.geo.domain.exceptions;

import org.nasa.core.erro.CausaRaiz;
import org.nasa.core.erro.ErroDePipeline;

/**
 * O raio de busca não é um número positivo.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO.</b> Raio zero quase sempre é <b>configuração que não foi
 * lida</b> — a propriedade não chegou, o valor caiu no padrão do tipo primitivo. Aceitar
 * o zero produziria uma caixa degenerada, a consulta à NASA não traria evento nenhum, e o
 * alerta pararia de funcionar <b>em silêncio</b>: sem erro, sem log, com todo mundo
 * achando que não houve desastre por perto.</p>
 *
 * <p><b>INVARIANTE.</b> Falha fechada e imediata, na construção da caixa. É melhor a
 * consulta explodir do que devolver "nenhum evento encontrado" quando a verdade é
 * "nenhuma busca foi feita".</p>
 *
 * <p><b>FALHA.</b> Causa-raiz {@link CausaRaiz#DADO_INVALIDO}, com o valor recusado na
 * mensagem — sem o número, quem lê o log não sabe se o problema foi o dado ou a régua.</p>
 */
public class RaioInvalidoException extends ErroDePipeline {
    public RaioInvalidoException(double raioKm) {
        super("montar-caixa-delimitadora", "raio", CausaRaiz.DADO_INVALIDO,
              "raio tem de ser positivo, recebi " + raioKm
              + " — zero quase sempre e configuracao que nao foi lida, e a busca "
              + "devolveria 'nenhum evento' sem ter procurado");
    }
}
