package org.nasa.inscrito.domain.exceptions;

import org.nasa.core.erro.CausaRaiz;
import org.nasa.core.erro.ErroDePipeline;

/**
 * O texto informado nao e um endereco de e-mail utilizavel.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO.</b> O e-mail é o <b>único canal garantido</b> de um
 * contato: telefone, celular e WhatsApp são opcionais. Um e-mail malformado, portanto,
 * não é um campo torto — é um contato por onde ninguém será avisado, cadastrado com a
 * aparência de estar completo.</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO.</b> A validação é DELIBERADAMENTE grosseira: exige arroba
 * com algo antes e algo depois, e um ponto no domínio. Não tenta decidir se o endereço
 * existe — isso nenhum formato consegue, e expressão elaborada de e-mail é conhecida por
 * recusar endereços válidos raros enquanto aceita os inválidos comuns. A prova de que um
 * e-mail existe é uma mensagem entregue, e é outro assunto.</p>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA.</b> Causa-raiz {@link CausaRaiz#DADO_INVALIDO},
 * 400 na borda. O banco tem o mesmo guarda-corpo em {@code contato_email_tem_arroba} — as
 * duas camadas de propósito, porque a de baixo é a única que vale para quem escreve por
 * outro caminho.</p>
 */
public class EmailInvalidoException extends ErroDePipeline {

    public EmailInvalidoException(String recebido, String motivo) {
        super("validar-email", "email", CausaRaiz.DADO_INVALIDO,
              "e-mail invalido (" + motivo + ")");
    }
}
