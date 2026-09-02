package org.nasa.alerta.domain.exceptions;

import org.nasa.alerta.domain.SituacaoAlerta;
import org.nasa.core.erro.CausaRaiz;
import org.nasa.core.erro.ErroDePipeline;

/**
 * A situacao informada nao e uma das tres conhecidas.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO.</b> O conjunto é fechado no banco
 * ({@code alerta_situacao_conhecida}) e aqui. Um quarto estado inventado passaria a
 * existir em consultas que ninguém escreveu para tratá-lo — e alertas nesse estado
 * ficariam invisíveis, sem nunca serem despachados nem investigados.</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO.</b> A mensagem lista os três aceitos.</p>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA.</b> Causa-raiz {@link CausaRaiz#DADO_INVALIDO},
 * 400 na borda.</p>
 */
public class SituacaoDeAlertaDesconhecidaException extends ErroDePipeline {

    public SituacaoDeAlertaDesconhecidaException(String recebido) {
        super("validar-alerta", "situacao", CausaRaiz.DADO_INVALIDO,
              "situacao desconhecida: " + recebido + ". Aceitas: "
              + java.util.Arrays.toString(SituacaoAlerta.values()));
    }
}
