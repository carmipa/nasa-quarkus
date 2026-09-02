package org.nasa.persistencia.domain.exceptions;

import org.nasa.core.erro.CausaRaiz;
import org.nasa.core.erro.ErroDePipeline;

/**
 * O DDL de uma migração não completou.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO.</b> Distingue o erro de execução do erro de conteúdo: aqui
 * a migração era válida e o banco recusou. Importa porque a reação é outra — normalmente
 * é o DDL que precisa de conserto, não o registro de versões.</p>
 *
 * <p><b>INVARIANTE.</b> A migração roda dentro da própria transação e <b>só é registrada
 * se completou</b>. Quando esta exceção sobe, nada daquele arquivo ficou aplicado e a
 * versão não foi marcada — o estado é "por aplicar", que é recuperável, e não "pela
 * metade", que não é.</p>
 *
 * <p><b>FALHA.</b> Causa-raiz {@link CausaRaiz#PERSISTENCIA_FALHOU}. Derruba o boot: subir
 * com esquema incompleto faz a primeira consulta falhar longe da causa.</p>
 */
public class MigracaoFalhouException extends ErroDePipeline {
    public MigracaoFalhouException(String identificacao, Throwable causaTecnica) {
        super("aplicar-migracao", identificacao, CausaRaiz.PERSISTENCIA_FALHOU,
              "o banco recusou o DDL desta migracao; nada dela ficou aplicado", causaTecnica);
    }
}
