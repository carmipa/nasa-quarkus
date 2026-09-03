package org.nasa.core.log;

import io.quarkus.test.junit.QuarkusTest;
import org.eclipse.microprofile.config.ConfigProvider;
import org.jboss.logging.Logger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Prova que o log por execução existe <b>no disco</b> — não só na configuração.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO.</b> A planta (§9.1) registra uma cicatriz específica: sem
 * {@code quarkus.log.file.enable=true}, {@code path} e {@code rotation} configurados não
 * escrevem <b>nada</b>, e a aplicação roda cinquenta minutos sem gravar log nenhum, em
 * silêncio. É uma falha que só aparece quando alguém vai procurar o log — ou seja, no
 * pior momento. Configuração presente e arquivo ausente são estados diferentes, e este
 * teste é o que os separa.</p>
 *
 * <p><b>INVARIANTES VERIFICADAS.</b></p>
 * <ol>
 *   <li>o arquivo da execução corrente existe e recebeu conteúdo;</li>
 *   <li>a linha carrega o <b>execucaoId</b> — é ele que correlaciona a linha com esta run;</li>
 *   <li>a linha carrega <b>operação</b>, <b>alvo</b> e <b>duração</b> no formato canônico;</li>
 *   <li>o carimbo <b>chegou</b> ao JVM de teste: se ele cair no default {@code "teste"},
 *       a lista nominal do {@code build.gradle} quebrou, e o teste <b>diz isso</b> em vez
 *       de rodar calado sobre um log que não é o desta execução.</li>
 * </ol>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA.</b> Reprova nomeando o caminho esperado. Não há
 * degradação silenciosa possível: ou o arquivo tem a linha, ou o mecanismo de log está
 * quebrado.</p>
 */
@QuarkusTest
@DisplayName("log por execucao — o arquivo existe no disco, com o carimbo desta run")
class LogPorExecucaoTest {

    private static final Logger LOG = Logger.getLogger(LogPorExecucaoTest.class);

    private static String config(String chave, String padrao) {
        return ConfigProvider.getConfig().getOptionalValue(chave, String.class).orElse(padrao);
    }

    @Test
    @DisplayName("o carimbo desta execucao chegou ao JVM de teste")
    void carimboChegou() {
        String carimbo = config("nasa.log.execucao", "teste");
        System.out.println("[LOG] nasa.log.execucao = " + carimbo);
        assertTrue(carimbo.startsWith("teste-"),
                "o carimbo caiu no default ('" + carimbo + "'): a lista nominal de "
                        + "systemProperty do build.gradle nao entregou a chave, e o log "
                        + "desta execucao nao esta separado do das outras.");
    }

    @Test
    @DisplayName("a linha escrita aparece no arquivo, com execucaoId, alvo e duracao")
    void aLinhaChegaNoArquivo() throws Exception {
        String carimbo = config("nasa.log.execucao", "teste");
        String pasta = config("nasa.log.pasta", "build/logs-teste");
        Path arquivo = Path.of(pasta, "nasa-" + carimbo + ".log");

        String marca = "prova-de-log-" + System.nanoTime();
        LOG.info(Registro.de("prova-log", marca, "linha de prova", Duration.ofMillis(1234)));

        assertTrue(Files.exists(arquivo),
                "o arquivo de log da execucao nao existe: " + arquivo.toAbsolutePath()
                        + " — confira `quarkus.log.file.enable=true`, que e a linha sem a qual "
                        + "path e rotation nao escrevem nada.");

        String conteudo = Files.readString(arquivo, StandardCharsets.UTF_8);
        System.out.println("[LOG] arquivo=" + arquivo + " bytes=" + conteudo.length());

        assertTrue(conteudo.contains(marca), "a linha recem-escrita nao chegou ao arquivo");
        assertTrue(conteudo.contains("[" + carimbo + "]"),
                "a linha nao carrega o execucaoId — sem ele nao da para correlacionar a run");
        assertTrue(conteudo.contains("prova-log alvo=" + marca),
                "a linha nao esta no formato canonico `operacao alvo=<alvo>`");
        assertTrue(conteudo.contains("(1.2s)") || conteudo.contains("(1,2s)"),
                "a linha nao carrega a duracao — achar a lentidao exige o numero, nao a suspeita");
    }

    @Test
    @DisplayName("a JVM roda em UTC e a linha de log carrega o Z — mecanismo, nao afirmacao")
    void aLinhaDeLogEstaEmUtc() throws Exception {
        // "UTC enforcado" precisa de MECANISMO nomeado. O mecanismo e
        // `-Duser.timezone=UTC` no build.gradle (testes) e TZ=UTC no conteiner.
        // Comparar o OFFSET, nao o ID: `ZoneId.systemDefault().getId()` devolve "UTC"
        // e `ZoneOffset.UTC.getId()` devolve "Z" — sao a mesma coisa com nomes
        // diferentes, e a primeira versao deste teste reprovou por isso. Defeito do
        // instrumento, nao do codigo medido; o offset e o que realmente importa.
        var deslocamento = java.time.ZoneId.systemDefault().getRules()
                .getOffset(java.time.Instant.EPOCH);
        System.out.println("[LOG] fuso da JVM: " + java.time.ZoneId.systemDefault()
                + " offset=" + deslocamento);
        assertEquals(java.time.ZoneOffset.UTC, deslocamento,
                "a JVM NAO esta em UTC: a mesma linha sairia -03:00 aqui e +00:00 no "
                        + "conteiner, e log de duas maquinas deixa de ser comparavel");

        String carimbo = config("nasa.log.execucao", "teste");
        String pasta = config("nasa.log.pasta", "build/logs-teste");
        Path arquivo = Path.of(pasta, "nasa-" + carimbo + ".log");
        assertTrue(Files.exists(arquivo), "sem arquivo nao ha o que auditar: " + arquivo);

        String conteudo = Files.readString(arquivo, StandardCharsets.UTF_8);
        var linha = conteudo.lines().filter(l -> l.startsWith("20")).findFirst();
        assertTrue(linha.isPresent(), "nenhuma linha com carimbo de tempo no arquivo");
        System.out.println("[LOG] primeira linha: " + linha.get());
        // Sem regex: o instante ISO-8601 UTC tem forma fixa — 'T' na posicao 10 e 'Z'
        // fechando o carimbo. Verificar por posicao e mais legivel que escapar barras,
        // e nao tem como o proprio teste errar a expressao.
        String carimboDaLinha = linha.get().substring(0, 24);
        assertEquals('T', carimboDaLinha.charAt(10),
                "carimbo fora do formato ISO-8601: " + carimboDaLinha);
        assertEquals('Z', carimboDaLinha.charAt(23),
                "o instante nao termina em Z, entao NAO esta em UTC: " + carimboDaLinha);
    }

    @Test
    @DisplayName("o arquivo NAO carrega segredo: nem chave, nem token, nem senha")
    void oLogNaoCarregaSegredo() throws Exception {
        String carimbo = config("nasa.log.execucao", "teste");
        String pasta = config("nasa.log.pasta", "build/logs-teste");
        Path arquivo = Path.of(pasta, "nasa-" + carimbo + ".log");
        assertTrue(Files.exists(arquivo), "sem arquivo nao ha o que auditar: " + arquivo);

        String conteudo = Files.readString(arquivo, StandardCharsets.UTF_8);
        // Controle positivo do instrumento: ele SABE achar o que procura.
        assertTrue(conteudo.contains("nasa"), "o instrumento nao esta lendo o arquivo certo");

        for (String proibido : new String[] { "AIza", "ghp_", "AKIA", "PRIVATE KEY", "Password=" }) {
            assertFalse(conteudo.contains(proibido),
                    "o log carrega padrao de credencial: " + proibido);
        }
    }
}
