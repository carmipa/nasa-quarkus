package org.nasa.persistencia.domain.exceptions;

import org.nasa.core.erro.CausaRaiz;
import org.nasa.core.erro.ErroDePipeline;

/**
 * Não foi possível <b>abrir</b> uma conexão com o banco.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO.</b> Marca o ponto exato da falha: o comando nunca chegou a
 * ser enviado. Sem esta distinção, toda falha de conexão herda a mensagem da operação que
 * ia acontecer depois — e o log passa a afirmar, com confiança, algo que não aconteceu.</p>
 *
 * <p><b>O PREJUÍZO QUE ORIGINOU ESTA CLASSE</b> (02/09/2026): {@code prepararControle()}
 * abria a conexão e executava o DDL dentro do <b>mesmo</b> {@code try}, e atribuía os dois
 * ao mesmo erro. Um diretório inexistente produziu no log
 * <i>"o banco recusou o DDL desta migracao; nada dela ficou aplicado"</i>. As duas metades
 * eram falsas: o banco não recusou nada, porque nada foi enviado. Um diagnóstico que
 * explica o sintoma bem demais é mais caro que nenhum diagnóstico, porque dirige a
 * investigação para o lugar errado com autoridade.</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO.</b> Nunca é lançada depois de um comando ter sido enviado
 * — se foi enviado, o erro é da operação, não da conexão.</p>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA.</b> Causa-raiz
 * {@link CausaRaiz#PERSISTENCIA_FALHOU}. No arranque derruba o boot; em requisição vira
 * 503 pelo mapeador de borda — "tente de novo" é a reação certa para banco fora, e
 * diferente de "seu dado está errado".</p>
 */
public class ConexaoComOBancoIndisponivelException extends ErroDePipeline {

    public ConexaoComOBancoIndisponivelException(String alvo, Throwable causaTecnica) {
        super("abrir-conexao", alvo, CausaRaiz.PERSISTENCIA_FALHOU,
              "nao foi possivel abrir conexao com o banco; nenhum comando chegou a ser enviado",
              causaTecnica);
    }
}
