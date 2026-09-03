package org.nasa.arquitetura;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guarda de fronteira — congela a arquitetura de fatias verticais.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO.</b> O desenho da planta canônica
 * ({@code instrucoes/regra-arquitetura-desacoplamento-total-kronos.md}) é <i>intenção</i>
 * até existir um teste que reprove o build quando alguém cruzar a fronteira. Um documento
 * pode ser ignorado pela próxima troca de modelo de IA; um teste vermelho, não.</p>
 *
 * <p><b>POR QUE A TAXONOMIA É DECLARADA AQUI.</b> A árvore canônica (§5.1) põe peers e
 * fatias <b>lado a lado na raiz do pacote</b>, sem agrupador — {@code org.nasa.geo} e
 * {@code org.nasa.endereco} são irmãos, e o caminho <b>não diz</b> qual é qual. Categoria
 * deduzida do nome da pasta seria adivinhação; então ela é declarada, e o teste
 * {@link #todoModuloDeTopoTemCategoriaDeclarada()} reprova o build quando nasce um pacote
 * de topo fora da lista. Pacote sem categoria é pacote que nenhuma regra governa.</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO</b> — uma por teste, todas de §2 da planta:</p>
 * <ol>
 *   <li>o kernel não conhece peer nem fatia;</li>
 *   <li>peer não conhece fatia;</li>
 *   <li>fatia não conhece fatia;</li>
 *   <li>{@code ..domain..} é puro — nenhum framework atravessa;</li>
 *   <li>{@code ..application..} não depende de {@code ..infrastructure..}.</li>
 * </ol>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA.</b> O build reprova nomeando a classe e a aresta
 * exata. Se a dependência for mesmo necessária, a resposta é extrair um peer ou declarar
 * uma porta — <b>nunca</b> afrouxar a regra: afrouxar não conserta o acoplamento, apaga
 * o alarme.</p>
 *
 * <p><b>⚠️ RODAR COM {@code --rerun-tasks}.</b> O cache do Gradle produz <b>falso-verde</b>
 * em teste de arquitetura: a task é considerada atualizada, o teste não executa e o
 * relatório mostra verde. Cicatriz medida no projeto de origem — e lá uma IA com
 * permissão de commit chegou a baixar a catraca porque a própria refatoração cegou o
 * scanner.</p>
 */
@DisplayName("fronteira arquitetural (fatias verticais / peers / kernel)")
class FronteiraArquiteturaTest {

    private static final String RAIZ = "org.nasa";

    // ---------------------------------------------------------------------
    // A TAXONOMIA DECLARADA — mantenha em sincronia com org/nasa/package-info.java
    // ---------------------------------------------------------------------
    /** Kernel técnico e bootstrap: podem ser usados por todos, não usam ninguém. */
    private static final Set<String> KERNEL = Set.of("core", "config");

    /** Peers: conceito de domínio com dono único. Não conhecem fatia. */
    private static final Set<String> PEERS = Set.of("geo", "persistencia", "telemetria");

    /** Fatias: recorte vertical de um caso de uso. Não conhecem outra fatia. */
    private static final Set<String> FATIAS = Set.of("endereco", "painel", "cliente", "contato", "evento", "alerta");

    /**
     * Piso de classes analisadas.
     *
     * <p>Conta só o que o javac <b>emite</b>: {@code package-info.java} sem anotação não
     * gera {@code .class}. Medido — a primeira versão deste teste contava os
     * {@code package-info} e reprovou dizendo "5 classes"; a causa era o instrumento, não
     * o código medido.</p>
     */
    private static final int PISO_DE_CLASSES = 5;

    private static JavaClasses classes;

    @BeforeAll
    static void importar() {
        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(RAIZ);
    }

    /** Nome do módulo de topo de uma classe: `org.nasa.geo.domain.X` -> `geo`. */
    private static String moduloDeTopo(String nomeDoPacote) {
        if (!nomeDoPacote.startsWith(RAIZ + ".")) {
            return "";
        }
        String resto = nomeDoPacote.substring(RAIZ.length() + 1);
        int ponto = resto.indexOf('.');
        return ponto < 0 ? resto : resto.substring(0, ponto);
    }

    private static Set<String> modulosDeTopoNoDisco() {
        return classes.stream()
                .map(c -> moduloDeTopo(c.getPackageName()))
                .filter(m -> !m.isEmpty())
                .collect(Collectors.toCollection(TreeSet::new));
    }

    private static boolean existeClasseNoModulo(String modulo) {
        return classes.stream().anyMatch(c -> moduloDeTopo(c.getPackageName()).equals(modulo));
    }

    private static String[] pacotesDe(Set<String> modulos) {
        return modulos.stream().map(m -> RAIZ + "." + m + "..").toArray(String[]::new);
    }

    // =====================================================================
    // 0. ANTI-CEGUEIRA — sem estes dois, tudo abaixo pode passar por vacuidade
    // =====================================================================

    @Test
    @DisplayName("0. o alvo nao esta vazio — sem isto as regras passariam por vacuidade")
    void oAlvoNaoEstaVazio() {
        System.out.println("[FRONTEIRA] classes analisadas: " + classes.size());
        assertTrue(classes.size() >= PISO_DE_CLASSES,
                "o importador trouxe " + classes.size() + " classes, abaixo do piso de "
                        + PISO_DE_CLASSES + ". As regras NAO foram verificadas — "
                        + "isto e NAO VERIFICOU, nunca aprovacao.");

        // Contagem certa não prova conjunto certo: todas as classes no `core` fariam as
        // regras de peer e fatia passarem por vacuidade com o piso satisfeito.
        for (String categoria : List.of("core")) {
            assertTrue(existeClasseNoModulo(categoria),
                    "nenhuma classe no kernel `" + categoria + "` — a regra 1 nao examinaria nada");
        }
        assertTrue(PEERS.stream().anyMatch(FronteiraArquiteturaTest::existeClasseNoModulo),
                "nenhuma classe em peer algum — a regra 2 nao examinaria nada");
        assertTrue(FATIAS.stream().anyMatch(FronteiraArquiteturaTest::existeClasseNoModulo),
                "nenhuma classe em fatia alguma — a regra 3 nao examinaria nada");
        System.out.println("[FRONTEIRA] kernel, peer e fatia: os tres tem classe — nenhuma regra e vazia");
    }

    @Test
    @DisplayName("0-bis. todo modulo de topo tem categoria declarada")
    void todoModuloDeTopoTemCategoriaDeclarada() {
        Set<String> declarados = new LinkedHashSet<>();
        declarados.addAll(KERNEL);
        declarados.addAll(PEERS);
        declarados.addAll(FATIAS);

        Set<String> noDisco = modulosDeTopoNoDisco();
        System.out.println("[FRONTEIRA] modulos de topo no disco: " + noDisco);

        Set<String> semCategoria = new TreeSet<>(noDisco);
        semCategoria.removeAll(declarados);

        assertEquals(Set.of(), semCategoria,
                "pacote(s) de topo sem categoria declarada: " + semCategoria
                        + ". Declare em KERNEL, PEERS ou FATIAS aqui e em org/nasa/package-info.java — "
                        + "pacote que nenhuma regra governa e o comeco do acoplamento.");
    }

    // =====================================================================
    // AS CINCO INVARIANTES
    // =====================================================================

    @Test
    @DisplayName("1. o kernel nao conhece peer nem fatia")
    void kernelNaoConheceNinguem() {
        Set<String> funcionais = new LinkedHashSet<>();
        funcionais.addAll(PEERS);
        funcionais.addAll(FATIAS);

        ArchRule regra = noClasses()
                .that().resideInAnyPackage(pacotesDe(KERNEL))
                .should().dependOnClassesThat().resideInAnyPackage(pacotesDe(funcionais))
                .because("se o kernel precisou importar peer ou fatia, aquilo nao era kernel: "
                        + "era regra de negocio disfarcada de utilitario, e o lugar dela e na fatia");
        regra.check(classes);
    }

    @Test
    @DisplayName("2. peer nao conhece fatia")
    void peerNaoConheceFatia() {
        ArchRule regra = noClasses()
                .that().resideInAnyPackage(pacotesDe(PEERS))
                .should().dependOnClassesThat().resideInAnyPackage(pacotesDe(FATIAS))
                .because("peer que importa fatia inverte a seta e a fronteira acaba: "
                        + "a partir dai as fatias passam a se enxergar atraves do peer");
        regra.check(classes);
    }

    @Test
    @DisplayName("3. fatia nao conhece fatia — allowlist VAZIA")
    void fatiaNaoConheceFatia() {
        if (FATIAS.size() < 2) {
            // Com UMA fatia a regra é trivialmente verdadeira. Dizer "passou" aqui seria
            // o mesmo erro do alvo vazio: a guarda existe e ainda não teve o que julgar.
            // Ela vira exigível sozinha quando a segunda fatia nascer.
            System.out.println("[FRONTEIRA] regra 3: 1 fatia declarada — trivialmente satisfeita, "
                    + "vira exigivel quando a segunda nascer");
        }
        for (String fatia : FATIAS) {
            Set<String> outras = new LinkedHashSet<>(FATIAS);
            outras.remove(fatia);
            if (outras.isEmpty()) {
                continue;
            }
            ArchRule regra = noClasses()
                    .that().resideInAPackage(RAIZ + "." + fatia + "..")
                    .should().dependOnClassesThat().resideInAnyPackage(pacotesDe(outras))
                    .because("se a fatia `" + fatia + "` precisa de outra, o que ela quer e um peer "
                            + "ou uma porta; afrouxar nao conserta o acoplamento, so apaga o alarme");
            regra.check(classes);
        }
    }

    @Test
    @DisplayName("4. `..domain..` e puro — nenhum framework atravessa")
    void dominioEhPuro() {
        ArchRule regra = noClasses()
                .that().resideInAPackage("..domain..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "jakarta..",
                        "javax..",
                        "io.quarkus..",
                        "io.smallrye..",
                        "org.hibernate..",
                        "com.fasterxml..",
                        "org.eclipse.microprofile..")
                .because("dominio anotado deixa de ser testavel sem container e vira refem do "
                        + "framework; na origem um unico record anotado quebrou um peer inteiro");
        regra.check(classes);
    }

    /**
     * <p><b>Reportada como PULADA — "NÃO VERIFICADO" — enquanto não houver classe em
     * {@code ..application..}.</b> É o terceiro estado da guarda traduzido para o
     * vocabulário do JUnit: passou / reprovou / não verificou.</p>
     *
     * <p>O ArchUnit recusa julgar conjunto vazio, e está certo. A saída errada seria
     * {@code allowEmptyShould(true)}, que transformaria "não havia o que examinar" em
     * verde — a cegueira que o teste 0 existe para impedir.</p>
     */
    @Test
    @DisplayName("5. `..application..` nao depende de `..infrastructure..`")
    void aplicacaoNaoDependeDeInfraestrutura() {
        Assumptions.assumeTrue(
                classes.stream().anyMatch(c -> c.getPackageName().contains(".application")),
                "NAO VERIFICADO: ainda nao existe classe em `..application..`. Isto nao e "
                        + "aprovacao — a regra volta a valer sozinha quando a primeira fatia "
                        + "ganhar caso de uso.");

        ArchRule regra = noClasses()
                .that().resideInAPackage("..application..")
                .should().dependOnClassesThat().resideInAPackage("..infrastructure..")
                .because("o caso de uso depende da PORTA e recebe o adaptador injetado — "
                        + "e isso que o deixa testavel sem rede e sem disco");
        regra.check(classes);
    }
}
