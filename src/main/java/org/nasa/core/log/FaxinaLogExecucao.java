package org.nasa.core.log;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import org.nasa.core.tempo.Relogio;
import jakarta.inject.Inject;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Mantém a pasta de log de crescer sem fim — e recusa faxinar pasta que não é só dela.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO.</b> Um arquivo de log por execução (§9.1) resolve o
 * problema de execuções misturadas e cria outro: a pasta cresce a cada `quarkusDev`, a
 * cada teste, a cada boot. Sem faxina, o disco enche; com faxina descuidada, ela apaga o
 * que não devia.</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO.</b></p>
 * <ol>
 *   <li><b>A pasta é EXCLUSIVA de log.</b> Se houver ali um arquivo que não casa com
 *       {@code nasa-*.log}, a faxina <b>não roda</b> e registra anomalia. A planta diz
 *       isso com todas as letras: <i>faxina apontada para pasta com outras coisas apaga
 *       as outras coisas</i>. Falha fechada.</li>
 *   <li><b>O log DESTA execução nunca é apagado</b>, mesmo que a política de retenção o
 *       alcançasse — apagar o arquivo que está sendo escrito é o pior momento possível.</li>
 *   <li><b>Conta o que agiu E o que se absteve.</b> "Nada a apagar" e "não pude apagar"
 *       não podem produzir o mesmo silêncio (§10.4).</li>
 * </ol>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA.</b> Pasta inexistente ⇒ {@link Resultado} com
 * {@code executou=false} e motivo {@code PASTA_AUSENTE}: é o estado normal do primeiro
 * boot, não um erro. Arquivo estranho na pasta ⇒ {@code PASTA_NAO_EXCLUSIVA}, e nada é
 * apagado. Falha de I/O ao apagar um arquivo é contada em {@code falhas} e <b>não
 * interrompe</b> os demais — mas aparece no log com o motivo. A faxina nunca derruba o
 * boot: log é apoio, não função.</p>
 */
@ApplicationScoped
public class FaxinaLogExecucao {

    private static final Logger LOG = Logger.getLogger(FaxinaLogExecucao.class);

    /**
     * Folga antes de chamar um carimbo de "futuro".
     *
     * <p>Sistema de arquivos e relógio podem divergir por segundos sem que nada esteja
     * errado. Uma hora é folga suficiente para não gritar por ruído, e curta o bastante
     * para pegar relógio realmente trocado.</p>
     */
    private static final Duration TOLERANCIA_DE_RELOGIO = Duration.ofHours(1);

    /** O nome que a faxina reconhece como seu. Qualquer outro torna a pasta não-exclusiva. */
    private static final String PREFIXO = "nasa-";
    private static final String SUFIXO = ".log";

    @ConfigProperty(name = "nasa.log.pasta", defaultValue = "logs/execucoes")
    String pasta;

    /**
     * Retenção por TEMPO — a ordem de Paulo (2026-09-02): apagar log com mais de 30 dias.
     * É o critério que casa com a pergunta real ("o que aconteceu no último mês?").
     */
    @ConfigProperty(name = "nasa.log.manter-dias", defaultValue = "30")
    int manterDias;

    /**
     * Teto de segurança por CONTAGEM, além da idade.
     *
     * <p>Idade sozinha não limita nada quando há muitas execuções por dia — em modo dev,
     * cada reinício cria um arquivo, e trinta dias disso enchem a pasta mesmo com todos
     * "novos". As duas réguas juntas fecham as duas dimensões: nenhuma sozinha impede a
     * pasta de crescer sem fim.</p>
     */
    @ConfigProperty(name = "nasa.log.manter-execucoes", defaultValue = "200")
    int manter;

    @Inject
    Relogio relogio;

    @ConfigProperty(name = "nasa.log.execucao", defaultValue = "manual")
    String carimboAtual;

    /**
     * O que a faxina fez — e o que deixou de fazer, com o motivo.
     *
     * @param executou    a varredura chegou a rodar
     * @param motivo      quando não executou, a causa; {@code null} quando executou
     * @param examinados  arquivos de log encontrados
     * @param apagados    o que AGIU
     * @param preservados o que se ABSTEVE de apagar (dentro da retenção, ou o log desta run)
     * @param falhas      arquivos que deveriam sair e não saíram
     * @param relogioSuspeito arquivos com data no FUTURO — relógio dessincronizado
     */
    public record Resultado(boolean executou, String motivo,
                            int examinados, int apagados, int preservados, int falhas,
                            int relogioSuspeito) {

        static Resultado naoExecutou(String motivo) {
            return new Resultado(false, motivo, 0, 0, 0, 0, 0);
        }
    }

    void aoIniciar(@Observes StartupEvent evento) {
        Resultado r = executar();
        if (!r.executou()) {
            // Não executar é informação, não silêncio.
            LOG.warn(Registro.recusa("faxina-log", pasta, r.motivo()));
            return;
        }
        LOG.info(Registro.de("faxina-log", pasta,
                "examinados=" + r.examinados() + " apagados=" + r.apagados()
                        + " preservados=" + r.preservados() + " falhas=" + r.falhas()
                        + " relogioSuspeito=" + r.relogioSuspeito()
                        + " retencao=" + manterDias + "d/" + manter + "arq"));
    }

    /**
     * Roda a faxina.
     *
     * <p><b>FALHA:</b> nunca lança. Toda impossibilidade vira {@link Resultado} com motivo
     * — quem chama decide o que fazer, e o boot não cai por causa de log.</p>
     */
    public Resultado executar() {
        Path raiz = Path.of(pasta);
        if (!Files.isDirectory(raiz)) {
            return Resultado.naoExecutou("PASTA_AUSENTE");
        }

        List<Path> tudo;
        try (Stream<Path> s = Files.list(raiz)) {
            tudo = s.filter(Files::isRegularFile).toList();
        } catch (IOException e) {
            return Resultado.naoExecutou("LEITURA_FALHOU");
        }

        // Invariante 1: a pasta é só de log. Um arquivo estranho aborta TUDO.
        boolean exclusiva = tudo.stream().allMatch(FaxinaLogExecucao::ehArquivoDeLog);
        if (!exclusiva) {
            return Resultado.naoExecutou("PASTA_NAO_EXCLUSIVA");
        }

        String meuArquivo = PREFIXO + carimboAtual + SUFIXO;
        List<Path> candidatos = tudo.stream()
                // Invariante 2: o log desta execução nunca entra na fila de exclusão.
                .filter(p -> !p.getFileName().toString().equals(meuArquivo))
                .sorted(Comparator.comparing(FaxinaLogExecucao::modificadoEm).reversed())
                .toList();

        int examinados = tudo.size();
        int preservados = examinados - candidatos.size();   // começa com o desta run
        int apagados = 0;
        int falhas = 0;
        int relogioSuspeito = 0;

        Instant agora = relogio.agora();
        Instant corte = agora.minus(manterDias, ChronoUnit.DAYS);

        for (int i = 0; i < candidatos.size(); i++) {
            Path arquivo = candidatos.get(i);
            Instant modificado = modificadoEm(arquivo);

            // RELÓGIO DESSINCRONIZADO — cenário da revisão de falha operacional.
            // Arquivo com data no FUTURO significa que o relógio do host andou para trás
            // (ou o arquivo veio de outra máquina). Apagar por idade nesse estado apagaria
            // o acervo inteiro de uma vez, porque tudo pareceria velho. Falha FECHADA:
            // não apaga, conta e declara.
            if (modificado.isAfter(agora.plus(TOLERANCIA_DE_RELOGIO))) {
                relogioSuspeito++;
                preservados++;
                LOG.warn(Registro.recusa("faxina-log-apagar",
                        arquivo.getFileName().toString(), "RELOGIO_DESSINCRONIZADO"));
                continue;
            }

            boolean velhoDemais = modificado.isBefore(corte);      // régua de IDADE (30 dias)
            boolean acimaDoTeto = i >= manter;                     // régua de CONTAGEM
            if (!velhoDemais && !acimaDoTeto) {
                preservados++;
                continue;
            }

            try {
                Files.delete(arquivo);
                apagados++;
            } catch (IOException e) {
                falhas++;
                LOG.warn(Registro.recusa("faxina-log-apagar",
                        arquivo.getFileName().toString(), "IO_FALHOU"));
            }
        }
        return new Resultado(true, null, examinados, apagados, preservados, falhas, relogioSuspeito);
    }

    private static boolean ehArquivoDeLog(Path p) {
        String nome = p.getFileName().toString();
        return nome.startsWith(PREFIXO) && nome.endsWith(SUFIXO);
    }

    /**
     * Data de modificação como {@link Instant} — UTC por construção.
     *
     * <p><b>FALHA:</b> ilegível devolve {@link Instant#EPOCH}, que é o mais velho possível
     * e portanto o primeiro candidato a sair. Arquivo que nem dá para consultar não vai
     * ficar ocupando espaço para sempre — mas note que ele sai por ser ilegível, não por
     * idade real, e é por isso que a contagem de {@code falhas} existe.</p>
     */
    private static Instant modificadoEm(Path p) {
        try {
            return Files.getLastModifiedTime(p).toInstant();
        } catch (IOException e) {
            return Instant.EPOCH;
        }
    }
}
