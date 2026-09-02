package org.nasa.arquitetura;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.library.dependencies.SlicesRuleDefinition;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guarda de fronteira — congela a arquitetura de fatias verticais.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO.</b> O desenho descrito em {@code docs/PLANO-MESTRE.md} §5
 * é uma <i>intenção</i> até existir um teste que reprove o build quando alguém cruzar a
 * fronteira. Combinado verbal não sobrevive a troca de desenvolvedor nem a troca de
 * modelo de IA; teste vermelho sobrevive. Esta classe é o mecanismo.</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO.</b> Cada {@code @Test} abaixo congela uma:</p>
 * <ol>
 *   <li>o kernel ({@code core}) não conhece peer nem fatia;</li>
 *   <li>peer não conhece fatia;</li>
 *   <li>fatia não conhece fatia — <b>allowlist vazia</b>;</li>
 *   <li>{@code ..domain..} é puro: nenhum framework atravessa essa linha;</li>
 *   <li>{@code ..application..} não depende de {@code ..infrastructure..} — o adaptador
 *       é injetado, e é isso que torna o caso de uso testável sem rede e sem disco.</li>
 * </ol>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA.</b> O build reprova nomeando a classe e a
 * dependência exata que apareceu. Se uma dependência entre fatias for mesmo necessária,
 * a resposta certa é extrair um peer ou declarar uma porta — <b>nunca</b> afrouxar a
 * regra. Afrouxar aqui não conserta o acoplamento, só apaga o alarme.</p>
 *
 * <p><b>ANTI-CEGUEIRA.</b> {@link #oAlvoNaoEstaVazio()} roda primeiro e reprova se o
 * importador trouxer menos classes que o piso. Sem ele, apagar o diretório de classes
 * faria as cinco regras passarem por vacuidade — "nenhuma violação encontrada" e "não
 * havia o que examinar" imprimem exatamente igual, e essa confusão já custou meses de
 * guarda verde e cega em outro projeto.</p>
 *
 * <p><b>⚠️ RODAR COM {@code --rerun-tasks}.</b> O cache do Gradle produz <b>falso-verde</b>
 * em teste de arquitetura: a task é considerada atualizada e o teste nem executa,
 * enquanto o relatório mostra verde. Cicatriz medida no KRONOS.</p>
 */
@DisplayName("fronteira arquitetural (fatias verticais / peers / kernel)")
class FronteiraArquiteturaTest {

    private static final String RAIZ = "org.nasa";

    /**
     * Piso de classes analisadas. Abaixo disto, o veredito das regras não vale.
     *
     * <p>O número é 5 e não 8 porque {@code package-info.java} <b>só gera
     * {@code .class} quando tem anotação</b> — os três daqui têm apenas Javadoc, então
     * o javac não emite nada para eles. Medido: a primeira versão deste teste usava
     * piso 6 contando os package-info, e reprovou dizendo "5 classes". A causa não era
     * o código medido; era o instrumento contando o que não existe.</p>
     */
    private static final int PISO_DE_CLASSES = 5;

    private static JavaClasses classes;

    @BeforeAll
    static void importar() {
        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(RAIZ);
    }

    /** Há alguma classe neste pacote? É o que separa "regra verde" de "regra vazia". */
    private static boolean existeClasseEm(String pacote) {
        return classes.stream().anyMatch(c -> c.getPackageName().startsWith(pacote));
    }

    @Test
    @DisplayName("0. o alvo nao esta vazio — sem isto as regras passariam por vacuidade")
    void oAlvoNaoEstaVazio() {
        System.out.println("[FRONTEIRA] classes analisadas: " + classes.size());
        assertTrue(classes.size() >= PISO_DE_CLASSES,
                "o importador trouxe " + classes.size() + " classes, abaixo do piso de "
                        + PISO_DE_CLASSES + ". As regras de fronteira NAO foram verificadas — "
                        + "isto e NAO VERIFICOU, nunca aprovacao.");

        // Contagem certa não prova conjunto certo: 5 classes todas no `core` fariam as
        // regras 2 e 3 passarem por vacuidade com o piso satisfeito. O que importa é
        // que cada categoria de módulo tenha alvo.
        for (String modulo : new String[] { RAIZ + ".core", RAIZ + ".peer", RAIZ + ".fatia" }) {
            assertTrue(existeClasseEm(modulo),
                    "nenhuma classe em `" + modulo + "` — a regra que governa esse modulo "
                            + "passaria sem examinar nada");
        }
        System.out.println("[FRONTEIRA] core, peer e fatia: os tres tem classe — nenhuma regra e vazia");
    }

    @Test
    @DisplayName("1. o kernel `core` nao conhece peer nem fatia")
    void kernelNaoConheceNinguem() {
        ArchRule regra = noClasses()
                .that().resideInAPackage(RAIZ + ".core..")
                .should().dependOnClassesThat().resideInAnyPackage(RAIZ + ".peer..", RAIZ + ".fatia..")
                .because("se o core precisou importar peer ou fatia, aquilo nao era kernel: "
                        + "era regra de negocio disfarcada de utilitario, e o lugar dela e na fatia");
        regra.check(classes);
    }

    @Test
    @DisplayName("2. peer nao conhece fatia")
    void peerNaoConheceFatia() {
        ArchRule regra = noClasses()
                .that().resideInAPackage(RAIZ + ".peer..")
                .should().dependOnClassesThat().resideInAPackage(RAIZ + ".fatia..")
                .because("peer que importa fatia inverte a seta e a fronteira acaba: "
                        + "a partir dai as fatias passam a se enxergar atraves do peer");
        regra.check(classes);
    }

    @Test
    @DisplayName("3. fatia nao conhece fatia — allowlist VAZIA")
    void fatiaNaoConheceFatia() {
        ArchRule regra = SlicesRuleDefinition.slices()
                .matching(RAIZ + ".fatia.(*)..")
                .should().notDependOnEachOther()
                .because("se uma fatia precisa de outra, o que ela quer e um peer ou uma porta; "
                        + "afrouxar esta regra nao conserta o acoplamento, so apaga o alarme");
        regra.check(classes);
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
                .because("dominio anotado deixa de ser testavel sem container e passa a ser "
                        + "refem do framework; no KRONOS um unico record anotado quebrou um peer inteiro");
        regra.check(classes);
    }

    /**
     * <p><b>Esta regra é reportada como PULADA — "NÃO VERIFICADO" — enquanto não existir
     * nenhuma classe em {@code ..application..}.</b> É deliberado, e é a tradução do
     * terceiro estado da guarda para o vocabulário do JUnit: passou / reprovou /
     * <b>não verificou</b>.</p>
     *
     * <p>O ArchUnit já recusa dar veredito sobre conjunto vazio (foi ele que reprovou
     * esta regra na primeira execução, e estava certo). A saída errada seria
     * {@code allowEmptyShould(true)}, que transformaria "não havia o que examinar" em
     * verde — exatamente a cegueira que o teste 0 existe para impedir. A saída certa é
     * dizer que não verificou, e voltar a verificar sozinho assim que a primeira fatia
     * ganhar um caso de uso.</p>
     */
    @Test
    @DisplayName("5. `..application..` nao depende de `..infrastructure..`")
    void aplicacaoNaoDependeDeInfraestrutura() {
        Assumptions.assumeTrue(existeClasseEm(RAIZ) && classes.stream()
                        .anyMatch(c -> c.getPackageName().contains(".application")),
                "NAO VERIFICADO: ainda nao existe classe em `..application..`. "
                        + "Isto nao e aprovacao — a regra volta a valer sozinha quando a "
                        + "primeira fatia ganhar caso de uso (item 6 do plano-mestre).");

        ArchRule regra = noClasses()
                .that().resideInAPackage("..application..")
                .should().dependOnClassesThat().resideInAPackage("..infrastructure..")
                .because("o caso de uso depende da PORTA e recebe o adaptador injetado — "
                        + "e isso que o deixa testavel sem rede e sem disco");
        regra.check(classes);
    }
}
