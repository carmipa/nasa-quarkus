package org.nasa.inscrito.domain.exceptions;

import org.nasa.core.erro.CausaRaiz;
import org.nasa.core.erro.ErroDePipeline;

/**
 * O e-mail já está inscrito.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO.</b> É <b>recusa, não falha</b>: a pessoa já está na lista, e
 * o sistema funcionou. A distinção decide a tela — "você já está inscrito" em vez de
 * "erro ao inscrever" — e decide a telemetria, onde recusa e falha são contadas separado.</p>
 *
 * <p><b>É O ERRO DE BOA-FÉ MAIS COMUM QUE EXISTE NUM FORMULÁRIO:</b> clicar duas vezes
 * porque a página demorou. Sem a restrição única no banco, os dois cliques criariam duas
 * inscrições e a pessoa passaria a receber cada alerta em dobro — sem nada acusando.</p>
 *
 * <p><b>INVARIANTE.</b> A mensagem NÃO repete o e-mail. Ela é lida em log e colada em
 * ticket, e endereço de e-mail é dado pessoal.</p>
 */
public class EmailJaInscritoException extends ErroDePipeline {

    public EmailJaInscritoException(String alvo) {
        super("inscrever", alvo, CausaRaiz.CONFLITO_DE_ESTADO,
              "este e-mail ja esta inscrito — nao ha o que fazer, o aviso ja vai chegar");
    }
}
