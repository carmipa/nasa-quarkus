package org.nasa.persistencia.infrastructure.adapters;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.nasa.persistencia.domain.exceptions.ArmazenamentoIndisponivelException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Prova do preparo do diretório do banco — o defeito que 122 testes verdes não pegaram.
 *
 * <p><b>PROPÓSITO.</b> Em 02/09/2026 a suíte inteira passava e o {@code quarkusDev} não
 * subia: o perfil de teste apontava para {@code build/}, que o Gradle cria, e o de
 * produção para {@code data/}, que ninguém criava. O teste exercitava o <b>único caminho
 * que já existia</b>. Estes testes fecham exatamente essa lacuna: partem de um diretório
 * que <b>não existe</b>.</p>
 *
 * <p><b>O caso 4 é o controle positivo do instrumento:</b> um cenário em que a guarda
 * <b>tem</b> de reprovar. Sem ele, os outros três provariam apenas que nada explodiu — e
 * um método vazio passaria em todos.</p>
 */
@DisplayName("preparo do diretorio do banco — SQLite cria o arquivo, nunca a pasta")
class PreparadorDoArquivoSqliteTest {

    private static PreparadorDoArquivoSqlite comUrl(String url) {
        var p = new PreparadorDoArquivoSqlite();
        p.urlConfigurada = Optional.of(url);
        return p;
    }

    // ------------------------------------------------ leitura da URL (sem disco)

    @Test
    @DisplayName("a URL real do projeto: tira os parametros e sobra o caminho")
    void tiraOsParametrosDaUrl() {
        assertEquals("data/nasa.db", PreparadorDoArquivoSqlite.caminhoDoArquivo(
                "jdbc:sqlite:data/nasa.db?foreign_keys=on&busy_timeout=5000&journal_mode=WAL"));
        assertEquals("build/teste-nasa.db", PreparadorDoArquivoSqlite.caminhoDoArquivo(
                "jdbc:sqlite:build/teste-nasa.db?foreign_keys=on"));
        assertEquals("data/nasa.db", PreparadorDoArquivoSqlite.caminhoDoArquivo(
                "jdbc:sqlite:data/nasa.db"), "URL sem parametro nenhum tambem vale");
        assertEquals("data/nasa.db", PreparadorDoArquivoSqlite.caminhoDoArquivo(
                "jdbc:sqlite:file:data/nasa.db?cache=shared"), "a forma com `file:`");
    }

    @Test
    @DisplayName("banco em memoria NAO tem diretorio — e pedir um seria erro inventado")
    void memoriaNaoTemDiretorio() {
        assertNull(PreparadorDoArquivoSqlite.caminhoDoArquivo("jdbc:sqlite::memory:"));
        assertNull(PreparadorDoArquivoSqlite.caminhoDoArquivo("jdbc:sqlite:"));
        assertNull(PreparadorDoArquivoSqlite.caminhoDoArquivo("jdbc:postgresql://host/base"));
    }

    // ------------------------------------------------------- o disco de verdade

    @Test
    @DisplayName("diretorio INEXISTENTE: cria, e diz que AGIU")
    void criaODiretorioQueFaltava(@TempDir Path base) {
        // Este e o cenario exato do quarkusDev quebrado: pasta que nunca existiu.
        Path alvo = base.resolve("data").resolve("nasa.db");
        assertFalse(Files.exists(alvo.getParent()), "o cenario exige que a pasta NAO exista");

        var local = comUrl("jdbc:sqlite:" + alvo + "?journal_mode=WAL").garantirDisponibilidade();

        assertTrue(Files.isDirectory(alvo.getParent()), "a pasta tinha de ter sido criada");
        assertTrue(local.criouDiretorio(), "criar e ABSTER-SE nao podem produzir o mesmo relato");
        assertTrue(Path.of(local.descricao()).isAbsolute(),
                "caminho relativo no relato manda procurar no diretorio errado: " + local.descricao());
        System.out.println("[BANCO] " + local);
    }

    @Test
    @DisplayName("segunda chamada: idempotente, e relata que se ABSTEVE")
    void idempotente(@TempDir Path base) {
        Path alvo = base.resolve("data").resolve("nasa.db");
        var preparador = comUrl("jdbc:sqlite:" + alvo);

        assertTrue(preparador.garantirDisponibilidade().criouDiretorio());
        assertFalse(preparador.garantirDisponibilidade().criouDiretorio(),
                "dois processos subindo juntos e caso normal, nao corrida");
    }

    @Test
    @DisplayName("CONTROLE POSITIVO: pasta ocupada por um ARQUIVO falha FECHADA")
    void falhaFechadaQuandoOCaminhoEstaOcupado(@TempDir Path base) throws IOException {
        // O engano de digitacao mais provavel numa variavel de ambiente: apontar para
        // dentro de algo que e um arquivo comum. Sem este caso, os testes acima passariam
        // ate com um metodo vazio.
        Path arquivoNoLugarDaPasta = base.resolve("data");
        Files.writeString(arquivoNoLugarDaPasta, "isto e um arquivo, nao uma pasta");

        var preparador = comUrl("jdbc:sqlite:" + arquivoNoLugarDaPasta.resolve("nasa.db"));

        var erro = assertThrows(ArmazenamentoIndisponivelException.class,
                preparador::garantirDisponibilidade);

        System.out.println("[BANCO] " + erro.linhaDeLog());
        assertEquals("ARQUIVO_INACESSIVEL", erro.causaRaiz().name(),
                "diretorio inacessivel nao e 'o banco recusou o comando'");
        assertTrue(erro.alvo().contains("data"), "o alvo precisa dizer QUAL caminho: " + erro.alvo());
    }

    @Test
    @DisplayName("sem URL configurada: falha FECHADA, nunca segue em frente")
    void semUrlFalhaFechada() {
        var p = new PreparadorDoArquivoSqlite();
        p.urlConfigurada = Optional.empty();
        assertThrows(ArmazenamentoIndisponivelException.class, p::garantirDisponibilidade);
    }
}
