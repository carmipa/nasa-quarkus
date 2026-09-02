package org.nasa.arquitetura;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaConstructorCall;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.conditions.ArchConditions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Catraca da ordem de Paulo: <b>uma exceção específica por classe, com log e telemetria</b>.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO.</b> A ordem (2026-09-02) só vale se for mecanismo. Escrita
 * em documento, ela sobrevive até a próxima pressa: alguém lança
 * {@code new IllegalStateException("nao devia acontecer")}, o painel conta aquilo como
 * uma falha sem causa, e o KPI causal — que é o número que diz <i>por quê</i> — passa a
 * ter um balde chamado "não sei". Este teste reprova o build antes disso.</p>
 *
 * <p><b>O QUE ELE CONGELA.</b></p>
 * <ol>
 *   <li><b>Nenhuma exceção genérica é construída</b> em {@code org.nasa}:
 *       {@link RuntimeException}, {@link IllegalArgumentException},
 *       {@link IllegalStateException}, {@link UnsupportedOperationException},
 *       {@link Exception} e {@link Error}. Falha sem nome próprio é falha sem causa-raiz,
 *       e causa-raiz é o que separa contagem de diagnóstico.</li>
 *   <li><b>Toda exceção nossa desce de {@code ErroDePipeline}</b> — que é abstrata, carrega
 *       operação, alvo e causa-raiz, e produz a linha de log canônica. É por aí que log e
 *       telemetria deixam de depender de alguém lembrar.</li>
 * </ol>
 *
 * <p><b>ANTI-CEGUEIRA.</b> {@link #existeExcecaoParaExaminar()} roda primeiro: sem
 * nenhuma exceção no disco, as duas regras passariam por vacuidade, e "nenhuma violação"
 * teria a mesma cara de "não havia o que examinar".</p>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA.</b> Reprova nomeando a classe e o construtor
 * exato. A correção é criar a exceção específica em {@code <modulo>/domain/exceptions/}
 * (ou {@code core/erro/} quando for do kernel) — <b>nunca</b> afrouxar a lista.</p>
 */
@DisplayName("catraca: uma excecao especifica por classe, com causa-raiz")
class CatracaExcecaoEspecificaTest {

    private static final String RAIZ = "org.nasa";
    private static final String BASE = RAIZ + ".core.erro.ErroDePipeline";

    /**
     * As genéricas. Lista fechada e por <b>nome exato</b> — não por hierarquia: usar
     * "tudo que desce de RuntimeException" pegaria as nossas, que descem dela de propósito.
     */
    private static final Set<String> PROIBIDAS = Set.of(
            "java.lang.RuntimeException",
            "java.lang.Exception",
            "java.lang.Error",
            "java.lang.IllegalArgumentException",
            "java.lang.IllegalStateException",
            "java.lang.UnsupportedOperationException");

    private static JavaClasses classes;

    @BeforeAll
    static void importar() {
        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(RAIZ);
    }

    @Test
    @DisplayName("0. ha excecao no disco para examinar — senao as regras passam por vacuidade")
    void existeExcecaoParaExaminar() {
        List<String> nossas = classes.stream()
                .filter(c -> c.isAssignableTo(Throwable.class))
                .map(c -> c.getName())
                .sorted()
                .toList();
        System.out.println("[EXCECAO] classes de excecao encontradas: " + nossas.size() + " -> " + nossas);
        assertTrue(nossas.size() >= 2,
                "menos de 2 excecoes no disco (" + nossas.size() + "): as regras abaixo nao "
                        + "examinariam nada. Isto e NAO VERIFICOU, nunca aprovacao.");
    }

    @Test
    @DisplayName("1. nenhuma excecao GENERICA e construida no projeto")
    void nadaDeExcecaoGenerica() {
        DescribedPredicate<JavaConstructorCall> genericas =
                new DescribedPredicate<>("construtor de excecao generica (sem causa-raiz)") {
                    @Override
                    public boolean test(JavaConstructorCall chamada) {
                        return PROIBIDAS.contains(chamada.getTargetOwner().getName());
                    }
                };

        ArchRule regra = noClasses()
                .that().resideInAPackage(RAIZ + "..")
                // ÚNICA exceção nominal, e ela é estrutural: a própria base precisa chamar
                // `super(mensagem, causa)` de RuntimeException — é assim que ela É uma
                // exceção. Medido na estreia desta catraca: foi a única violação em todo o
                // projeto, e é a que prova que a regra funciona sem afrouxar nada.
                // "Guarda estreia contra o código real existente antes de ser confiada."
                .and().doNotHaveFullyQualifiedName(BASE)
                .should(ArchConditions.callConstructorWhere(genericas))
                .because("falha sem nome proprio e falha sem causa-raiz, e o painel passa a ter "
                        + "um balde chamado 'nao sei'. Crie a excecao especifica em "
                        + "<modulo>/domain/exceptions/ ou core/erro/ — nunca afrouxe esta lista");
        regra.check(classes);
    }

    @Test
    @DisplayName("2. toda excecao nossa desce de ErroDePipeline — e por isso carrega log e telemetria")
    void todaExcecaoDesceDaBase() {
        ArchRule regra = classes()
                .that().resideInAPackage(RAIZ + "..")
                .and().areAssignableTo(Throwable.class)
                .should().beAssignableTo(BASE)
                .because("e da base que vem operacao, alvo, causa-raiz e a linha de log canonica; "
                        + "excecao fora dela chega a borda sem nada disso");
        regra.check(classes);
    }
}
