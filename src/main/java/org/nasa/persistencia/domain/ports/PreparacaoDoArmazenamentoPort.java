package org.nasa.persistencia.domain.ports;

/**
 * Garante que o banco está utilizável ANTES da primeira operação de negócio.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO.</b> Existe para que "o banco não está pronto" seja uma frase
 * dita no arranque, com a correção junto, em vez de um erro obscuro na primeira requisição
 * de quem estiver usando o sistema.</p>
 *
 * <p><b>A LIÇÃO QUE ESTA PORTA CARREGA</b> (02/09/2026). Nasceu no SQLite, onde a falha era
 * "o diretório {@code data/} não existe" — o SQLite cria o arquivo do banco, nunca a pasta.
 * Sobreviveu à troca para PostgreSQL <b>de propósito</b>: a causa concreta mudou, a classe
 * não. Onde havia uma forma de não estar pronto agora há quatro — servidor fora, credencial
 * errada, base inexistente, servidor recusando —, e a lição vale mais depois da troca do
 * que antes.</p>
 *
 * <p><b>Por que é PORTA, e não uma chamada direta:</b> quem sabe interpretar SQLSTATE e URL
 * JDBC é a infraestrutura. A camada de aplicação precisa apenas que <i>alguém</i> garanta a
 * disponibilidade antes de ela começar — e a guarda de fronteira reprova o build se
 * {@code application} enxergar {@code infrastructure}.</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO.</b></p>
 * <ol>
 *   <li><b>É idempotente.</b> Chamar duas vezes não muda nada — dois processos subindo ao
 *       mesmo tempo é caso normal, não corrida.</li>
 *   <li><b>Falha FECHADA.</b> Banco inacessível derruba o arranque.</li>
 *   <li><b>Não cria, apaga nem migra dado.</b> Verifica o continente, nunca o conteúdo.
 *       Criar a base automaticamente pareceria conveniente e transformaria um erro de
 *       digitação na variável de ambiente numa base vazia recém-criada — que sobe limpa,
 *       sem nenhum dado, e sem nada acusando o engano.</li>
 * </ol>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA.</b> Lança
 * {@code BancoIndisponivelException} com a causa nomeada e host/porta/base no alvo —
 * <b>nunca</b> usuário ou senha, que vazariam para o log.</p>
 */
public interface PreparacaoDoArmazenamentoPort {

    /**
     * Garante o banco utilizável.
     *
     * @return descrição do que foi verificado, para o log do arranque responder
     *         "afinal, contra qual banco este processo está falando?"
     */
    Local garantirDisponibilidade();

    /**
     * O que foi verificado.
     *
     * @param descricao identificação legível do banco — host, porta, base e versão.
     *                  Nunca credencial.
     */
    record Local(String descricao) {
    }
}
