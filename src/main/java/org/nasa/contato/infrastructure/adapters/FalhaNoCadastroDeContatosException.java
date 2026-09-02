package org.nasa.contato.infrastructure.adapters;

import org.nasa.core.erro.CausaRaiz;
import org.nasa.core.erro.ErroDePipeline;

/**
 * O banco recusou uma operacao sobre contatos, por motivo que nao e de negocio.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO.</b> É a exceção do que quem opera <b>não</b> resolve
 * sozinho: coluna faltando, conexão perdida no meio, restrição que ninguém previu.
 * Separá-la da duplicata de e-mail é o que permite à tela dizer "tente de novo" num caso
 * e "corrija o e-mail" no outro.</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO.</b> Carrega a operação e o alvo, para o log dizer o que
 * se estava fazendo. Nunca carrega o conteúdo do registro: dado de contato é dado
 * pessoal, e mensagem de erro vai para arquivo de log, para tela e para print.</p>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA.</b> Causa-raiz
 * {@link CausaRaiz#PERSISTENCIA_FALHOU}, 500 na borda.</p>
 */
public class FalhaNoCadastroDeContatosException extends ErroDePipeline {

    public FalhaNoCadastroDeContatosException(String operacao, String alvo, Throwable causa) {
        super(operacao, alvo, CausaRaiz.PERSISTENCIA_FALHOU,
              "o banco recusou a operacao de contato: " + operacao, causa);
    }
}
