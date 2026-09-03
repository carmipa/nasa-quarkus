package org.nasa.persistencia.infrastructure.adapters;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import org.nasa.core.log.Registro;
import org.nasa.persistencia.domain.exceptions.BancoIndisponivelException;
import org.nasa.persistencia.domain.exceptions.ConexaoComOBancoIndisponivelException;
import org.nasa.persistencia.domain.ports.PreparacaoDoArmazenamentoPort;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Prepara o arquivo do SQLite e confere que ele responde.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO.</b> O SQLite cria o <b>arquivo</b> do banco sozinho, mas
 * <b>nunca cria a pasta</b> onde ele deveria ficar. Se o diretório não existe, o driver
 * devolve {@code SQLITE_CANTOPEN} — que a aplicação inteira interpretava como "o banco
 * recusou a migração", mandando investigar DDL enquanto o problema era uma pasta ausente.
 * Este defeito custou uma sessão inteira de diagnóstico neste projeto, e esta classe é a
 * correção dele em forma de mecanismo.</p>
 *
 * <p><b>POR QUE ELE ENGANAVA TÃO BEM.</b> Em teste o caminho era {@code build/}, que o
 * Gradle cria antes de qualquer coisa; em execução era {@code data/}, que ninguém cria. A
 * suíte passava inteira e a aplicação não subia — o pior formato de defeito, porque a
 * evidência disponível dizia que estava tudo certo.</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO.</b></p>
 * <ol>
 *   <li><b>A pasta é criada ANTES da primeira conexão.</b> Não adianta criá-la depois: a
 *       primeira coisa que a aplicação faz é migrar, e a migração já precisa do arquivo.</li>
 *   <li><b>Não apaga nem recria nada.</b> Criar diretório é idempotente e seguro; apagar
 *       arquivo de banco em nome de "preparar" seria a operação mais destrutiva possível
 *       disfarçada de arranque.</li>
 *   <li><b>Nenhuma credencial na descrição.</b> A URL do SQLite é um caminho de arquivo, e
 *       caminho absoluto vaza o nome de usuário do sistema — que é dado pessoal, e já
 *       custou reescrita de histórico de repositório neste projeto. Só o nome do arquivo
 *       aparece.</li>
 *   <li><b>Confere que o banco RESPONDE</b>, não só que o arquivo existe. Arquivo vazio,
 *       arquivo corrompido e disco cheio são todos "o arquivo está lá".</li>
 * </ol>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA.</b> {@link BancoIndisponivelException}, que derruba
 * o arranque — com a causa nomeada. Subir com banco inacessível apenas adia a falha para o
 * primeiro visitante, e aí ela aparece como erro de tela em vez de erro de arranque.</p>
 */
@ApplicationScoped
public class PreparadorDoArquivoSqlite implements PreparacaoDoArmazenamentoPort {

    private static final Logger LOG = Logger.getLogger(PreparadorDoArquivoSqlite.class);
    private static final String OPERACAO = "verificar-banco";

    /** O prefixo da URL do SQLite. O que vem depois dele é caminho de arquivo. */
    private static final String PREFIXO = "jdbc:sqlite:";

    /**
     * Bancos que NÃO são arquivo, e para os quais não há pasta a criar.
     *
     * <p>{@code :memory:} e {@code file::memory:} vivem só na RAM. Tentar criar um
     * diretório chamado {@code :memory:} falharia no Windows, onde {@code :} é proibido em
     * nome de arquivo — e falharia no arranque, por causa de um caso que nem precisava de
     * preparação.</p>
     */
    private static final String[] SEM_ARQUIVO = { ":memory:", "file::memory:" };

    @Inject
    DataSource dataSource;

    @ConfigProperty(name = "quarkus.datasource.jdbc.url")
    String urlDoBanco;

    @Override
    public Local garantirDisponibilidade() {
        Path arquivo = criarPastaSeNecessario();
        return new Local(conferirQueResponde(arquivo));
    }

    /**
     * Cria a pasta do arquivo, se houver pasta e se ela faltar.
     *
     * @return o caminho do arquivo, ou {@code null} para banco em memória
     */
    private Path criarPastaSeNecessario() {
        String caminho = caminhoDoArquivo(urlDoBanco);
        if (caminho == null) {
            LOG.debug(Registro.de(OPERACAO, "memoria", "banco em memoria — sem pasta a criar"));
            return null;
        }
        Path arquivo = Path.of(caminho).toAbsolutePath().normalize();
        Path pasta = arquivo.getParent();
        if (pasta == null || Files.isDirectory(pasta)) {
            return arquivo;
        }
        try {
            Files.createDirectories(pasta);
            // Em INFO, e nao em DEBUG: criar a pasta acontece uma vez na vida da maquina, e
            // e exatamente a linha que se procura quando alguem pergunta "onde foi parar o
            // banco?".
            LOG.info(Registro.de(OPERACAO, arquivo.getFileName().toString(),
                    "pasta do banco criada"));
            return arquivo;
        } catch (IOException naoCriou) {
            // Disco cheio, permissao negada, caminho invalido. Nomear e melhor que deixar
            // virar SQLITE_CANTOPEN mais adiante, que manda investigar o lugar errado.
            throw new BancoIndisponivelException(pasta.getFileName().toString(),
                    "nao foi possivel criar a pasta do banco", naoCriou);
        }
    }

    /**
     * Abre uma conexão e pergunta a versão.
     *
     * <p>Arquivo existir não é o mesmo que banco funcionar: arquivo vazio, arquivo
     * corrompido e disco sem espaço são todos "o arquivo está lá". A única prova é uma
     * consulta que volta.</p>
     */
    private String conferirQueResponde(Path arquivo) {
        try (Connection c = dataSource.getConnection();
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT sqlite_version()")) {

            String versao = rs.next() ? rs.getString(1) : "?";
            String nome = arquivo == null ? "memoria" : arquivo.getFileName().toString();
            String descricao = nome + " (SQLite " + versao + ")";
            LOG.info(Registro.de(OPERACAO, nome, "banco respondeu — " + descricao));
            return descricao;

        } catch (SQLException naoRespondeu) {
            // O motivo legivel vai no LOG antes de subir: a excecao carrega a causa
            // tecnica, e a traducao e o que diz o que fazer.
            LOG.error(Registro.recusa(OPERACAO, "sqlite", motivoLegivel(naoRespondeu)));
            throw new ConexaoComOBancoIndisponivelException("sqlite", naoRespondeu);
        }
    }

    /**
     * O caminho de arquivo dentro da URL do JDBC.
     *
     * @return {@code null} quando o banco é em memória — não há pasta a criar
     */
    static String caminhoDoArquivo(String url) {
        if (url == null || !url.startsWith(PREFIXO)) {
            return null;
        }
        String resto = url.substring(PREFIXO.length());
        // Parametros depois de `?` nao fazem parte do caminho.
        int interrogacao = resto.indexOf('?');
        if (interrogacao >= 0) {
            resto = resto.substring(0, interrogacao);
        }
        if (resto.isBlank()) {
            return null;
        }
        for (String memoria : SEM_ARQUIVO) {
            if (resto.equals(memoria) || resto.startsWith(memoria)) {
                return null;
            }
        }
        return resto;
    }

    /**
     * Traduz a falha do SQLite em algo que diga o que fazer.
     *
     * <p>São menos casos que no PostgreSQL — não há servidor, rede nem credencial — e é
     * essa a diferença que motivou a troca de motor. Os que sobram são todos sobre o
     * <b>arquivo</b>.</p>
     */
    private static String motivoLegivel(SQLException falha) {
        String texto = falha.getMessage() == null ? "" : falha.getMessage().toUpperCase();
        if (texto.contains("SQLITE_CANTOPEN") || texto.contains("UNABLE TO OPEN")) {
            return "o arquivo do banco nao pode ser aberto — confira a pasta e a permissao";
        }
        if (texto.contains("SQLITE_READONLY") || texto.contains("READONLY")) {
            return "o arquivo do banco esta somente leitura";
        }
        if (texto.contains("SQLITE_NOTADB") || texto.contains("NOT A DATABASE")) {
            return "o arquivo existe mas NAO e um banco SQLite — pode estar corrompido";
        }
        if (texto.contains("SQLITE_FULL") || texto.contains("DISK")) {
            return "sem espaco em disco para o banco";
        }
        if (texto.contains("SQLITE_BUSY") || texto.contains("LOCKED")) {
            return "o banco esta travado por outro processo — ha outra instancia rodando?";
        }
        // Desconhecido tambem derruba, COM o texto original: sem ele, a proxima pessoa nao
        // tem por onde comecar.
        return "falha nao classificada do SQLite: " + falha.getMessage();
    }
}
