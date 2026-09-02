package org.nasa.persistencia.infrastructure.adapters;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;
import org.nasa.core.log.Registro;
import org.nasa.persistencia.domain.exceptions.BancoIndisponivelException;
import org.nasa.persistencia.domain.exceptions.ConexaoComOBancoIndisponivelException;
import org.nasa.persistencia.domain.ports.PreparacaoDoArmazenamentoPort;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Confere que o PostgreSQL responde, e traduz cada forma de não responder.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO.</b> Substitui o preparador de diretório que existia no
 * SQLite. A causa concreta mudou — não há mais pasta a criar — mas a classe de falha é a
 * mesma e ficou <b>maior</b>: onde havia uma forma de o ambiente não estar pronto, agora há
 * quatro. Esta classe existe para que cada uma tenha nome e correção próprios no arranque,
 * em vez de virar um {@code SQLException} genérico que manda testar tudo na mão.</p>
 *
 * <p><b>AS QUATRO, E O QUE CADA UMA PEDE:</b></p>
 * <ul>
 *   <li><b>servidor fora</b> ({@code 08001}, {@code 08004}, {@code 08006}) — subir o
 *       PostgreSQL, ou corrigir host/porta em {@code NASA_DB_URL};</li>
 *   <li><b>credencial errada</b> ({@code 28P01}, {@code 28000}) — conferir
 *       {@code NASA_DB_USER} e {@code NASA_DB_PASSWORD};</li>
 *   <li><b>base inexistente</b> ({@code 3D000}) — {@code CREATE DATABASE}. Este é o que
 *       mais engana: o servidor está no ar e responde, então "o banco está de pé" é
 *       verdade e inútil ao mesmo tempo;</li>
 *   <li><b>servidor iniciando</b> ({@code 57P03}) — esperar. É o caso do contêiner que
 *       subiu junto com a aplicação e ainda está abrindo.</li>
 * </ul>
 *
 * <p><b>INVARIANTES DO DOMÍNIO.</b></p>
 * <ol>
 *   <li><b>Nenhuma credencial na descrição nem na mensagem de erro.</b> Este texto vai para
 *       o log, para a tela e para o print que alguém cola num chat. A URL é higienizada
 *       antes de aparecer: some o que vem depois de {@code ?} e o {@code usuario:senha@}.</li>
 *   <li><b>Não cria a base.</b> Criar automaticamente transformaria um erro de digitação na
 *       variável de ambiente numa base vazia que sobe limpa, sem dado nenhum e sem nada
 *       acusando o engano.</li>
 *   <li><b>Idempotente e somente leitura.</b></li>
 * </ol>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA.</b> {@link BancoIndisponivelException}, que derruba
 * o arranque. SQLSTATE desconhecido também derruba — com o código no texto, para que a
 * próxima pessoa saiba exatamente o que procurar.</p>
 */
@ApplicationScoped
public class VerificadorDoBancoPostgres implements PreparacaoDoArmazenamentoPort {

    private static final Logger LOG = Logger.getLogger(VerificadorDoBancoPostgres.class);
    private static final String OPERACAO = "verificar-banco";

    @Inject
    DataSource dataSource;

    @Override
    public Local garantirDisponibilidade() {
        try (Connection c = Conexoes.abrir(dataSource, "arranque");
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT current_database(), current_user, version()")) {

            String base = rs.next() ? rs.getString(1) : "?";
            String endereco = semCredencial(c.getMetaData().getURL());
            String versao = c.getMetaData().getDatabaseProductVersion();

            String descricao = base + " em " + endereco + " (PostgreSQL " + versao + ")";
            LOG.info(Registro.de(OPERACAO, descricao, "banco respondeu"));
            return new Local(descricao);

        } catch (ConexaoComOBancoIndisponivelException naoAbriu) {
            throw traduzir(naoAbriu);
        } catch (SQLException e) {
            // Conectou e falhou na consulta: e outra coisa, e o SQLSTATE vai no texto.
            throw new BancoIndisponivelException("consulta-de-verificacao",
                    "o banco conectou mas recusou a consulta de verificacao (SQLSTATE "
                    + e.getSQLState() + ")", e);
        }
    }

    /**
     * Dá nome à forma de o banco não estar disponível.
     *
     * <p>Cada ramo devolve a <b>correção</b>, não só o sintoma: quem topa com isto está no
     * meio de outra coisa e não deve ter de descobrir o significado de um código.</p>
     */
    private static BancoIndisponivelException traduzir(ConexaoComOBancoIndisponivelException falha) {
        SQLException sql = (falha.getCause() instanceof SQLException s) ? s : null;
        String estado = sql == null ? null : sql.getSQLState();
        String alvo = "postgresql";

        String motivo = switch (estado == null ? "" : estado) {
            case "08001", "08004", "08006" -> "o servidor PostgreSQL nao respondeu. "
                    + "Suba o banco, ou corrija host e porta em NASA_DB_URL. "
                    + "Em dev e teste o Dev Services cuida disso — confira se o Docker esta de pe";
            case "28P01", "28000" -> "o servidor respondeu e RECUSOU a credencial. "
                    + "Confira NASA_DB_USER e NASA_DB_PASSWORD (a senha nao aparece aqui de proposito)";
            case "3D000" -> "o servidor esta no ar, mas a base indicada NAO EXISTE. "
                    + "Crie com `CREATE DATABASE <nome>;` ou corrija o nome em NASA_DB_URL. "
                    + "Este caso engana: o servidor responde, entao 'o banco esta de pe' e "
                    + "verdadeiro e inutil ao mesmo tempo";
            case "57P03" -> "o servidor esta iniciando e ainda nao aceita conexao. "
                    + "E o caso do conteiner que subiu junto com a aplicacao: tente de novo em segundos";
            case "" -> "nao foi possivel abrir conexao, e o driver nao informou SQLSTATE";
            default -> "nao foi possivel abrir conexao (SQLSTATE " + estado + ")";
        };
        return new BancoIndisponivelException(alvo, motivo, falha);
    }

    /**
     * Remove credencial da URL antes de ela ir para o log.
     *
     * <p>Duas formas carregam senha: o {@code ?password=...} da query e o
     * {@code usuario:senha@host} do estilo URI. As duas somem aqui. Costura
     * {@code static} para o teste provar isso sem banco.</p>
     */
    static String semCredencial(String url) {
        if (url == null || url.isBlank()) {
            return "endereco-nao-informado";
        }
        String limpa = url;
        int query = limpa.indexOf('?');
        if (query >= 0) {
            limpa = limpa.substring(0, query);
        }
        int arroba = limpa.lastIndexOf('@');
        int aposEsquema = limpa.indexOf("//");
        if (arroba > 0 && aposEsquema > 0 && arroba > aposEsquema) {
            limpa = limpa.substring(0, aposEsquema + 2) + limpa.substring(arroba + 1);
        }
        return limpa;
    }
}
