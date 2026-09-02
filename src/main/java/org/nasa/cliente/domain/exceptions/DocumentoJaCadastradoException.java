package org.nasa.cliente.domain.exceptions;

import org.nasa.core.erro.CausaRaiz;
import org.nasa.core.erro.ErroDePipeline;

/**
 * Já existe um cliente com este documento.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO.</b> É a invariante INV-CLIENTE-001 chegando à tela em
 * linguagem de gente. Sem esta tradução o operador veria
 * {@code SQLITE_CONSTRAINT_UNIQUE} e não saberia que o problema é "esta pessoa já está
 * cadastrada".</p>
 *
 * <p><b>INVARIANTE — e este é o ponto.</b> Quem recusa é o <b>banco</b>. A checagem
 * prévia da aplicação existe para dar boa mensagem e <b>não substitui</b> a constraint:
 * entre o "já existe?" e o {@code INSERT} cabe outra requisição, e é exatamente aí que a
 * duplicata nasce. O clique duplo é o caso comum, não o raro — e foi assim que o legado
 * ficou sem proteção nenhuma, com a regra morando só no Java.</p>
 *
 * <p><b>FALHA.</b> Causa-raiz {@link CausaRaiz#CONFLITO_DE_ESTADO} — vira 409, o status
 * que diz "o pedido está bem formado, mas conflita com o que já existe".</p>
 */
public class DocumentoJaCadastradoException extends ErroDePipeline {
    public DocumentoJaCadastradoException(String documento) {
        super("cadastrar-cliente", documento, CausaRaiz.CONFLITO_DE_ESTADO,
              "ja existe um cliente com este documento");
    }
}
