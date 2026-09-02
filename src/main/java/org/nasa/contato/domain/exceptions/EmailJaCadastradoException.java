package org.nasa.contato.domain.exceptions;

import org.nasa.core.erro.CausaRaiz;
import org.nasa.core.erro.ErroDePipeline;

/**
 * Ja existe um contato com este e-mail.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO.</b> O legado expunha
 * {@code GET /api/contatos/email/...} devolvendo UM contato. Sem unicidade, esse endpoint
 * é ambíguo por construção: com dois contatos no mesmo e-mail, qual dos dois ele devolve?
 * A resposta tem de ser "não existem dois", e quem garante isso é a restrição
 * {@code contato_email_unico} do banco.</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO.</b> A checagem que vale é a do BANCO, não uma consulta
 * prévia do tipo "já existe?". Entre a pergunta e a inserção cabe outra requisição — e
 * dois cadastros simultâneos do mesmo e-mail é o caso comum do clique duplo, não o raro.</p>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA.</b> Causa-raiz
 * {@link CausaRaiz#CONFLITO_DE_ESTADO}, 409 na borda. É a única falha desta fatia que
 * quem opera resolve sozinho — por isso é a única traduzida para exceção de negócio.</p>
 */
public class EmailJaCadastradoException extends ErroDePipeline {

    public EmailJaCadastradoException(String email) {
        super("cadastrar-contato", email, CausaRaiz.CONFLITO_DE_ESTADO,
              "ja existe um contato com este e-mail");
    }
}
