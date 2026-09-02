package org.nasa.persistencia.domain.exceptions;

import org.nasa.core.erro.CausaRaiz;
import org.nasa.core.erro.ErroDePipeline;

/**
 * O lugar onde o banco deveria morar não existe, ou não aceita escrita.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO.</b> Separa "não consigo chegar ao arquivo do banco" de
 * "o banco recusou o comando". São causas diferentes, com correções diferentes: a primeira
 * se resolve com diretório e permissão, a segunda com SQL. Confundir as duas custou tempo
 * real em 02/09/2026, quando um diretório inexistente foi reportado como
 * <i>"o banco recusou o DDL desta migracao"</i> — mensagem que manda investigar o SQL, que
 * estava correto.</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO.</b> O campo {@code alvo} carrega o <b>caminho absoluto</b>
 * resolvido. Caminho relativo em mensagem de erro manda procurar no diretório errado
 * sempre que o processo roda com outro diretório de trabalho — que é o caso do
 * {@code quarkusDev}, de um serviço {@code systemd} e de um contêiner.</p>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA.</b> Causa-raiz {@link CausaRaiz#ARQUIVO_INACESSIVEL}.
 * Derruba o arranque de propósito: subir sem banco acessível troca um erro claro agora por
 * um erro obscuro na primeira requisição de quem estiver usando.</p>
 */
public class ArmazenamentoIndisponivelException extends ErroDePipeline {

    public ArmazenamentoIndisponivelException(String caminhoAbsoluto, String motivo,
                                              Throwable causaTecnica) {
        super("preparar-armazenamento", caminhoAbsoluto, CausaRaiz.ARQUIVO_INACESSIVEL,
              "o diretorio do banco nao esta utilizavel: " + motivo, causaTecnica);
    }
}
