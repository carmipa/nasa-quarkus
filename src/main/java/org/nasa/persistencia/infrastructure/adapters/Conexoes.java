package org.nasa.persistencia.infrastructure.adapters;

import org.nasa.persistencia.domain.exceptions.ConexaoComOBancoIndisponivelException;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * O único lugar do sistema que abre conexão com o banco.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO.</b> Separar, em <b>todos</b> os pontos de acesso a dados,
 * "não consegui chegar ao banco" de "o banco recusou o meu comando". São duas causas com
 * correções diferentes — diretório e permissão numa, SQL na outra — e o log precisa dizer
 * qual das duas foi.</p>
 *
 * <p><b>O PREJUÍZO QUE A ORIGINOU</b> (02/09/2026): {@code prepararControle()} abria a
 * conexão e executava o DDL dentro do mesmo {@code try}, com um único {@code catch}. Um
 * diretório inexistente gerou no log <i>"o banco recusou o DDL desta migracao"</i>: as
 * duas metades falsas, porque nenhum comando chegou a ser enviado. Havia <b>onze</b>
 * pontos com a mesma estrutura; corrigir só o que doeu deixaria os outros dez esperando.</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO.</b></p>
 * <ol>
 *   <li><b>Falha de abertura NUNCA vira {@link SQLException}</b> para quem chama. Vira
 *       exceção própria, de tipo diferente do erro de comando — assim o {@code catch
 *       (SQLException)} do chamador não consegue, nem por engano, atribuir a si uma falha
 *       que aconteceu antes dele.</li>
 *   <li><b>Devolve conexão aberta ou lança.</b> Nunca {@code null}: {@code null} viraria
 *       NullPointerException dentro do {@code try-with-resources}, longe da causa.</li>
 * </ol>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA.</b>
 * {@link ConexaoComOBancoIndisponivelException}, causa-raiz {@code PERSISTENCIA_FALHOU} —
 * o mesmo mapeamento HTTP (500) que os erros de comando já tinham, então a troca não muda
 * o que o cliente da API recebe: muda o que o log diz.</p>
 */
public final class Conexoes {

    private Conexoes() {
    }

    /**
     * Abre uma conexão do pool.
     *
     * @param alvo o que se ia fazer — entra no campo {@code alvo} do log, e é o que
     *             responde "conexão para quê?" quando o erro aparece sozinho
     */
    public static Connection abrir(DataSource dataSource, String alvo) {
        try {
            Connection c = dataSource.getConnection();
            if (c == null) {
                throw new ConexaoComOBancoIndisponivelException(alvo, null);
            }
            return c;
        } catch (SQLException e) {
            throw new ConexaoComOBancoIndisponivelException(alvo, e);
        }
    }
}
