package org.nasa.persistencia.domain.exceptions;

import org.nasa.core.erro.CausaRaiz;
import org.nasa.core.erro.ErroDePipeline;

/**
 * A migração não descreve uma mudança de esquema aplicável.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO.</b> Separa "o DDL falhou" de "isto nem era uma migração".
 * O caso mais comum e mais traiçoeiro é <b>SQL vazio</b>: o arquivo existe, foi lido como
 * string em branco, e aplicá-lo com sucesso registraria a versão <b>sem ter feito o
 * trabalho</b> — o banco ficaria uma versão à frente do próprio esquema.</p>
 *
 * <p><b>INVARIANTE.</b> Falha fechada no construtor do record: migração inválida nunca
 * chega a existir como objeto, então nenhum aplicador posterior herda o valor ruim.</p>
 *
 * <p><b>FALHA.</b> Causa-raiz {@link CausaRaiz#DADO_INVALIDO}. Derruba o boot — e é
 * deliberado, porque a alternativa é subir sobre esquema que ninguém sabe qual é.</p>
 */
public class MigracaoInvalidaException extends ErroDePipeline {
    public MigracaoInvalidaException(String versao, String motivo) {
        super("validar-migracao", "V" + versao, CausaRaiz.DADO_INVALIDO, motivo);
    }
}
