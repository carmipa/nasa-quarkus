package org.nasa.persistencia.infrastructure.adapters;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import org.nasa.core.log.Registro;
import org.nasa.persistencia.domain.exceptions.ArmazenamentoIndisponivelException;
import org.nasa.persistencia.domain.ports.PreparacaoDoArmazenamentoPort;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;

/**
 * Cria o diretório do arquivo SQLite antes da primeira conexão.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO.</b> O SQLite cria o arquivo do banco sozinho e
 * <b>nunca o diretório</b>. Em qualquer lugar onde a pasta de dados ainda não exista — um
 * clone novo, um contêiner com volume vazio, um servidor recém-provisionado — a primeira
 * conexão morre com {@code SQLITE_CANTOPEN}, mensagem que não menciona diretório nenhum.
 * Esta classe transforma esse caso em "criei a pasta e segui".</p>
 *
 * <p><b>O PREJUÍZO QUE A ORIGINOU</b> (02/09/2026): a suíte passava com 122 testes verdes
 * e o {@code quarkusDev} não subia. O perfil de teste apontava para {@code build/}, que o
 * Gradle cria; o de produção para {@code data/}, que ninguém criava. O teste passava
 * porque exercitava o <b>único caminho que já existia</b> — a diferença entre os perfis
 * era exatamente a dimensão do defeito.</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO.</b></p>
 * <ol>
 *   <li><b>Idempotente:</b> se o diretório já existe, não faz nada e relata ABSTEVE.</li>
 *   <li><b>Falha FECHADA</b>, com o <b>caminho absoluto</b> no erro. Caminho relativo em
 *       mensagem manda procurar no lugar errado quando o processo roda com outro
 *       diretório de trabalho — e {@code quarkusDev}, {@code systemd} e contêiner são
 *       justamente três casos assim.</li>
 *   <li><b>Banco em memória não tem diretório</b>, e pedir um seria erro inventado.</li>
 *   <li><b>Nunca toca no conteúdo:</b> prepara o continente, jamais cria, apaga ou
 *       migra dado.</li>
 * </ol>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA.</b> Diretório impossível de criar, caminho ocupado
 * por um arquivo comum, ou pasta sem permissão de escrita ⇒
 * {@link ArmazenamentoIndisponivelException}, que derruba o arranque. A checagem de escrita
 * é <b>inferência, não evidência direta</b>: {@link Files#isWritable(Path)} responde pelo
 * que o sistema de arquivos declara, e no Windows nem sempre reflete a ACL efetiva. A prova
 * definitiva continua sendo a conexão logo em seguida — esta checagem existe para
 * antecipar o caso comum com uma mensagem melhor, não para substituí-la.</p>
 */
@ApplicationScoped
public class PreparadorDoArquivoSqlite implements PreparacaoDoArmazenamentoPort {

    private static final Logger LOG = Logger.getLogger(PreparadorDoArquivoSqlite.class);
    private static final String OPERACAO = "preparar-armazenamento";
    private static final String PREFIXO_JDBC = "jdbc:sqlite:";

    @ConfigProperty(name = "quarkus.datasource.jdbc.url")
    Optional<String> urlConfigurada;

    @Override
    public Local garantirDisponibilidade() {
        String url = urlConfigurada.orElseThrow(() -> new ArmazenamentoIndisponivelException(
                Registro.NAO_INFORMADO, "quarkus.datasource.jdbc.url nao esta configurada", null));

        String caminho = caminhoDoArquivo(url);
        if (caminho == null) {
            // Banco em memoria: nao existe diretorio a preparar, e inventar um seria erro.
            LOG.info(Registro.de(OPERACAO, "memoria", "banco em memoria, sem diretorio"));
            return new Local("em memoria", false);
        }
        return prepararDiretorioDe(caminho);
    }

    /**
     * Extrai o caminho do arquivo de uma URL JDBC de SQLite.
     *
     * <p>Costura {@code static} de propósito: é a parte com regra, e o teste a exercita
     * sem CDI, sem banco e sem disco.</p>
     *
     * @return o caminho, ou {@code null} quando o banco é em memória (que não tem arquivo)
     */
    static String caminhoDoArquivo(String url) {
        if (url == null || !url.startsWith(PREFIXO_JDBC)) {
            return null;
        }
        String resto = url.substring(PREFIXO_JDBC.length());

        int interrogacao = resto.indexOf('?');
        if (interrogacao >= 0) {
            resto = resto.substring(0, interrogacao);   // fora os parametros da URL
        }
        if (resto.startsWith("file:")) {
            resto = resto.substring("file:".length());
        }
        if (resto.isBlank() || resto.startsWith(":memory:")) {
            return null;
        }
        return resto;
    }

    private Local prepararDiretorioDe(String caminho) {
        Path arquivo;
        try {
            arquivo = Paths.get(caminho).toAbsolutePath().normalize();
        } catch (InvalidPathException e) {
            throw new ArmazenamentoIndisponivelException(caminho,
                    "o caminho configurado nao e valido neste sistema de arquivos", e);
        }

        Path pasta = arquivo.getParent();
        if (pasta == null) {
            return new Local(arquivo.toString(), false);   // raiz: nao ha o que criar
        }

        boolean jaExistia = Files.isDirectory(pasta);
        if (!jaExistia) {
            try {
                Files.createDirectories(pasta);
            } catch (IOException e) {
                // Inclui o caso em que ja existe um ARQUIVO COMUM naquele caminho, que
                // e o engano de digitacao mais provavel numa variavel de ambiente.
                throw new ArmazenamentoIndisponivelException(pasta.toString(),
                        "nao foi possivel criar o diretorio", e);
            }
        }
        if (!Files.isWritable(pasta)) {
            throw new ArmazenamentoIndisponivelException(pasta.toString(),
                    "o diretorio existe mas nao aceita escrita", null);
        }

        // O caminho ABSOLUTO no log e o que responde "afinal, onde esta o banco?" sem
        // depender de adivinhar o diretorio de trabalho do processo.
        LOG.info(Registro.de(OPERACAO, arquivo.toString(),
                jaExistia ? "diretorio ja existia" : "diretorio CRIADO agora"));
        return new Local(arquivo.toString(), !jaExistia);
    }
}
