package org.nasa.persistencia.domain.exceptions;

import org.nasa.core.erro.CausaRaiz;
import org.nasa.core.erro.ErroDePipeline;

/**
 * O banco não está utilizável, e a mensagem diz <b>qual</b> das causas foi.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO.</b> "Não consegui usar o banco" tem quatro causas comuns e
 * quatro correções completamente diferentes: o servidor não está no ar, a credencial está
 * errada, a base não existe, ou o servidor está lá e recusou por outro motivo. Uma
 * mensagem genérica obriga quem opera a testar as quatro na mão, em produção, no pior
 * momento possível.</p>
 *
 * <p><b>O PREJUÍZO QUE A ORIGINOU.</b> Esta classe é a herdeira direta de
 * {@code ArmazenamentoIndisponivelException}, escrita em 02/09/2026 quando o SQLite não
 * subia porque o diretório {@code data/} não existia — e o log dizia
 * <i>"o banco recusou o DDL desta migracao"</i>, mandando investigar o SQL, que estava
 * correto. A troca para PostgreSQL <b>não elimina essa classe de falha</b>: apenas troca
 * "o diretório não existe" por "connection refused", "password authentication failed" e
 * "database does not exist". São mais formas de não estar pronto, não menos — e por isso a
 * lição foi portada junto com o banco.</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO.</b> A mensagem nomeia a causa e diz o que fazer, e o campo
 * {@code alvo} carrega <b>host, porta e base</b> — nunca usuário ou senha. Credencial em
 * mensagem de erro vaza para log, para tela e para o print que alguém cola num chat.</p>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA.</b> Causa-raiz
 * {@link CausaRaiz#PERSISTENCIA_FALHOU} e o arranque <b>cai</b>. Subir sem banco troca um
 * erro claro agora por um erro obscuro na primeira requisição de quem estiver usando.</p>
 */
public class BancoIndisponivelException extends ErroDePipeline {

    public BancoIndisponivelException(String alvo, String motivo, Throwable causaTecnica) {
        super("verificar-banco", alvo, CausaRaiz.PERSISTENCIA_FALHOU, motivo, causaTecnica);
    }
}
