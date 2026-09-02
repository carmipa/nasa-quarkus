package org.nasa.persistencia.domain.exceptions;

import org.nasa.core.erro.CausaRaiz;
import org.nasa.core.erro.ErroDePipeline;

/**
 * Uma migração JÁ APLICADA foi editada — e isso não tem conserto automático.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO.</b> É a exceção que protege a invariante central desta
 * camada: <b>migração aplicada é imutável</b>. Quem já rodou a versão antiga tem um banco
 * com o esquema antigo; quem rodar a nova terá outro — <b>com o mesmo número de versão
 * nos dois</b>. A partir daí, "estamos na V007" deixa de significar qualquer coisa, e o
 * defeito só aparece quando uma consulta encontra coluna que existe numa máquina e não
 * na outra.</p>
 *
 * <p><b>INVARIANTE.</b> Falha fechada no boot. Não há degradação possível: aplicar de
 * novo duplicaria o DDL, e ignorar deixaria o banco divergente em silêncio.</p>
 *
 * <p><b>FALHA.</b> Causa-raiz {@link CausaRaiz#CONFLITO_DE_ESTADO}. A correção é humana e
 * é sempre a mesma: <b>reverter a edição</b> e escrever uma migração NOVA com o ajuste.</p>
 */
public class MigracaoAlteradaException extends ErroDePipeline {
    public MigracaoAlteradaException(String identificacao, String esperado, String encontrado) {
        super("aplicar-migracao", identificacao, CausaRaiz.CONFLITO_DE_ESTADO,
              "migracao JA APLICADA foi editada: o banco registrou checksum " + esperado
              + " e o arquivo agora soma " + encontrado
              + ". Reverta a edicao e escreva uma migracao NOVA — o banco de quem ja rodou "
              + "a versao antiga nao se corrige sozinho.");
    }
}
