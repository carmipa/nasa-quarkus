package org.nasa.persistencia.infrastructure.adapters;

import org.nasa.core.erro.CausaRaiz;
import org.nasa.core.erro.ErroDePipeline;

/**
 * O índice de migrações, ou um arquivo que ele lista, não está no classpath.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO.</b> Transforma em erro alto o cenário mais silencioso desta
 * camada: o arquivo não empacotado. Sem esta exceção, o sistema subiria anunciando
 * "nenhuma migração pendente" com o banco vazio — e a primeira consulta falharia falando
 * de tabela inexistente, a quilômetros da causa real.</p>
 *
 * <p><b>INVARIANTE.</b> Falha fechada no boot. Nunca lista vazia: <i>"nenhuma migração"</i>
 * e <i>"não achei o índice"</i> não podem ter a mesma cara.</p>
 *
 * <p><b>FALHA.</b> Causa-raiz {@link CausaRaiz#ARQUIVO_INACESSIVEL}, com o caminho do
 * recurso como alvo.</p>
 */
public class RecursoDeMigracaoAusenteException extends ErroDePipeline {
    public RecursoDeMigracaoAusenteException(String caminho) {
        this(caminho, null);
    }

    public RecursoDeMigracaoAusenteException(String caminho, Throwable causaTecnica) {
        super("ler-migracao", caminho, CausaRaiz.ARQUIVO_INACESSIVEL,
              "recurso de migracao ausente no classpath — provavelmente nao foi empacotado",
              causaTecnica);
    }
}
