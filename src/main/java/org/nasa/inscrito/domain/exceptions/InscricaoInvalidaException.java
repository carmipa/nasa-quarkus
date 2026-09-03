package org.nasa.inscrito.domain.exceptions;

import org.nasa.core.erro.CausaRaiz;
import org.nasa.core.erro.ErroDePipeline;

/**
 * A inscrição não pode existir com esse dado.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO.</b> Recusar na borda do domínio, nomeando <b>qual campo</b>
 * está errado. Uma mensagem genérica — "dados inválidos" — obriga quem preencheu a adivinhar
 * entre cinco campos, e quem preenche errado duas vezes desiste.</p>
 *
 * <p><b>INVARIANTE.</b> A mensagem nunca carrega o VALOR recusado, só o campo e o motivo.
 * Nome, e-mail e telefone são dado pessoal, e mensagem de erro é lida em log, copiada em
 * ticket e colada em chat — canais que ninguém controla.</p>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA.</b> É o próprio comportamento em caso de falha: sobe
 * com {@link CausaRaiz#DADO_INVALIDO} para o mapeador de borda transformar em 400.</p>
 */
public class InscricaoInvalidaException extends ErroDePipeline {

    public InscricaoInvalidaException(String campo, String motivo) {
        super("validar-inscricao", campo, CausaRaiz.DADO_INVALIDO,
              "inscricao invalida: o campo `" + campo + "` esta " + motivo);
    }
}
