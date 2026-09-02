package org.nasa.persistencia.domain.ports;

/**
 * Garante que o armazenamento existe e é utilizável ANTES da primeira conexão.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO.</b> O SQLite cria o <b>arquivo</b> do banco sozinho, mas
 * <b>nunca o diretório</b> que o contém. Num clone novo do repositório, onde a pasta de
 * dados ainda não existe, a primeira conexão falha com {@code SQLITE_CANTOPEN} e a
 * aplicação não sobe — sintoma que não diz nada sobre a causa. Esta porta existe para que
 * esse caso vire "o diretório foi criado" em vez de "não consegui abrir o banco".</p>
 *
 * <p><b>Por que é PORTA, e não uma chamada direta:</b> quem sabe interpretar uma URL JDBC
 * é a infraestrutura. A camada de aplicação precisa apenas que <i>alguém</i> garanta a
 * disponibilidade antes de ela abrir a primeira conexão — e a guarda de fronteira reprova
 * o build se {@code application} enxergar {@code infrastructure}.</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO.</b></p>
 * <ol>
 *   <li><b>É idempotente.</b> Chamar duas vezes não muda o resultado nem falha — dois
 *       processos subindo ao mesmo tempo é caso normal, não corrida.</li>
 *   <li><b>Falha FECHADA.</b> Diretório impossível de criar ou sem permissão de escrita
 *       derruba o arranque. Subir com banco inacessível troca um erro claro no boot por
 *       um erro obscuro na primeira requisição de quem estiver usando.</li>
 *   <li><b>Não cria, apaga nem migra dado.</b> Prepara o continente, nunca o conteúdo.</li>
 * </ol>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA.</b> Lança
 * {@code ArmazenamentoIndisponivelException} (causa-raiz {@code ARQUIVO_INACESSIVEL}) com
 * o <b>caminho absoluto</b> resolvido no campo alvo — sem o caminho absoluto, "não
 * consegui criar o diretório" manda procurar no lugar errado quando o processo roda com
 * outro diretório de trabalho.</p>
 */
public interface PreparacaoDoArmazenamentoPort {

    /**
     * Garante o armazenamento utilizável.
     *
     * @return o que foi feito — {@code criouDiretorio} distingue AGIU de ABSTEVE, que é a
     *         diferença entre "primeira execução aqui" e "já estava tudo pronto"
     */
    Local garantirDisponibilidade();

    /**
     * O resultado da preparação.
     *
     * @param descricao      caminho absoluto do arquivo, ou a descrição do modo em memória
     * @param criouDiretorio {@code true} se o diretório foi criado AGORA
     */
    record Local(String descricao, boolean criouDiretorio) {
    }
}
