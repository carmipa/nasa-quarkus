package org.nasa.persistencia.infrastructure.adapters;

import jakarta.enterprise.context.ApplicationScoped;
import org.nasa.core.erro.CausaRaiz;
import org.nasa.core.erro.ErroDePipeline;
import org.nasa.persistencia.domain.Migracao;
import org.nasa.persistencia.domain.ports.FonteDeMigracoesPort;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Lê as migrações de um ÍNDICE declarado no classpath.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO.</b> Entregar as migrações na ordem certa, com a mesma
 * resposta na IDE e dentro do jar.</p>
 *
 * <p><b>POR QUE ÍNDICE E NÃO VARREDURA.</b> Varrer o diretório parece mais prático e é o
 * caminho para um defeito com data marcada: a ordem de listagem de recursos <b>não é
 * garantida</b> e muda entre sistema de arquivos e jar. Ordem de DDL que depende do
 * empacotamento produz bancos diferentes a partir do mesmo código. O índice é declarado,
 * versionado e revisável — e um arquivo esquecido nele falha <b>alto</b>, em vez de
 * simplesmente não ser aplicado.</p>
 *
 * <p><b>INVARIANTES.</b></p>
 * <ol>
 *   <li>Linhas em branco e comentários ({@code #}) são ignorados; o resto é nome de
 *       arquivo obrigatório.</li>
 *   <li>O nome segue {@code V<numero>__<descricao>.sql} — o número é a versão.</li>
 *   <li>A saída sai <b>ordenada por versão</b>, independentemente da ordem do índice:
 *       ordem correta não pode depender de alguém ter digitado certo.</li>
 * </ol>
 *
 * <p><b>FALHA.</b> Índice ausente ou arquivo listado que não existe ⇒
 * {@link ErroDePipeline} com causa {@link CausaRaiz#ARQUIVO_INACESSIVEL}. Nunca lista
 * vazia calada: "nenhuma migração" e "não achei o índice" não podem ter a mesma cara.</p>
 */
@ApplicationScoped
public class FonteDeMigracoesNoClasspath implements FonteDeMigracoesPort {

    static final String INDICE = "db/migracao/indice.txt";
    static final String PASTA = "db/migracao/";

    @Override
    public List<Migracao> disponiveis() {
        List<String> nomes = lerIndice();
        List<Migracao> migracoes = new ArrayList<>(nomes.size());
        for (String nome : nomes) {
            migracoes.add(new Migracao(versaoDe(nome), descricaoDe(nome), lerRecurso(PASTA + nome)));
        }
        migracoes.sort(Comparator.comparingInt(Migracao::versao));
        return List.copyOf(migracoes);
    }

    private List<String> lerIndice() {
        String conteudo = lerRecurso(INDICE);
        List<String> nomes = new ArrayList<>();
        for (String linha : conteudo.split("\\R")) {
            String limpa = linha.trim();
            if (limpa.isEmpty() || limpa.startsWith("#")) {
                continue;
            }
            nomes.add(limpa);
        }
        return nomes;
    }

    private String lerRecurso(String caminho) {
        try (InputStream in = Thread.currentThread().getContextClassLoader()
                .getResourceAsStream(caminho)) {
            if (in == null) {
                throw new RecursoDeMigracaoAusenteException(caminho);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RecursoDeMigracaoAusenteException(caminho, e);
        }
    }

    static int versaoDe(String nomeDoArquivo) {
        int fim = nomeDoArquivo.indexOf("__");
        if (!nomeDoArquivo.startsWith("V") || fim < 2) {
            throw new NomeDeMigracaoInvalidoException(nomeDoArquivo);
        }
        try {
            return Integer.parseInt(nomeDoArquivo.substring(1, fim));
        } catch (NumberFormatException e) {
            throw new NomeDeMigracaoInvalidoException(nomeDoArquivo);
        }
    }

    static String descricaoDe(String nomeDoArquivo) {
        int inicio = nomeDoArquivo.indexOf("__") + 2;
        String resto = nomeDoArquivo.substring(inicio);
        return resto.endsWith(".sql") ? resto.substring(0, resto.length() - 4) : resto;
    }
}
