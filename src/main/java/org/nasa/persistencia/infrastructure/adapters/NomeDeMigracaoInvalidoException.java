package org.nasa.persistencia.infrastructure.adapters;

import org.nasa.core.erro.CausaRaiz;
import org.nasa.core.erro.ErroDePipeline;

/**
 * O nome do arquivo de migração não segue {@code V<numero>__<descricao>.sql}.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO.</b> A versão vem do NOME do arquivo — é o que torna a ordem
 * visível a olho nu na pasta. Um nome fora do padrão não tem versão, e sem versão não há
 * como registrar nem como saber se já foi aplicada.</p>
 *
 * <p><b>INVARIANTE.</b> Falha fechada na leitura, antes de qualquer DDL rodar.</p>
 *
 * <p><b>FALHA.</b> Causa-raiz {@link CausaRaiz#DADO_INVALIDO}, com o nome recusado no
 * alvo — para que a mensagem diga qual arquivo renomear.</p>
 */
public class NomeDeMigracaoInvalidoException extends ErroDePipeline {
    public NomeDeMigracaoInvalidoException(String nomeDoArquivo) {
        super("ler-migracao", nomeDoArquivo, CausaRaiz.DADO_INVALIDO,
              "nome fora do padrao V<numero>__<descricao>.sql — sem numero nao ha versao");
    }
}
