package org.nasa.contato.domain.exceptions;

import org.nasa.core.erro.CausaRaiz;
import org.nasa.core.erro.ErroDePipeline;

/**
 * Um campo do contato nao descreve um contato utilizavel.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO.</b> Recusa no domínio o que a tela não deve conseguir
 * gravar: DDD com letra, telefone de três dígitos, campo obrigatório vazio. É a mesma
 * classe de guarda que {@code ClienteInvalidoException} cumpre na fatia de cliente.</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO.</b> O campo {@code alvo} carrega o NOME do campo, nunca o
 * valor. É por ele que a tela sabe qual caixa destacar, e é ele que vai para o log — de
 * modo que tela e log falem do mesmo campo. Valor digitado não entra no alvo: em
 * cadastro, valor digitado é dado pessoal, e mensagem de erro acaba em arquivo de log e
 * em print colado num chat.</p>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA.</b> Causa-raiz {@link CausaRaiz#DADO_INVALIDO},
 * 400 na borda.</p>
 */
public class ContatoInvalidoException extends ErroDePipeline {

    public ContatoInvalidoException(String campo, String motivo) {
        super("validar-contato", campo, CausaRaiz.DADO_INVALIDO,
              "campo " + campo + ": " + motivo);
    }
}
