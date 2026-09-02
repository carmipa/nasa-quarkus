package org.nasa.arquitetura;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaConstructorCall;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.conditions.ArchConditions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Catraca do tempo: <b>UTC no sistema inteiro, e um único relógio</b>.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO.</b> Ordem de Paulo (2026-09-02): UTC no log, na telemetria
 * <b>e no próprio sistema</b> — <i>"que não existia à época"</i>. O legado guardava o
 * evento da NASA em coluna Oracle {@code TIMESTAMP WITH LOCAL TIME ZONE}, que grava no
 * fuso do servidor; num sistema que decide <b>se um desastre está perto do endereço de
 * alguém</b>, errar por horas é errar exatamente na virada do dia, que é quando a janela
 * de "últimos N dias" muda de resposta.</p>
 *
 * <p><b>O QUE ELE CONGELA.</b></p>
 * <ol>
 *   <li><b>Nenhum uso de API de hora LOCAL</b>: {@code LocalDateTime.now()},
 *       {@code LocalDate.now()}, {@code ZonedDateTime.now()}, {@code new Date()},
 *       {@code Calendar.getInstance()}. São as que adotam o fuso do host em silêncio — a
 *       mesma linha sai {@code -03:00} aqui e {@code +00:00} no contêiner, e ninguém
 *       percebe até comparar dois ambientes.</li>
 *   <li><b>Um único leitor do relógio.</b> {@code Instant.now()} e
 *       {@code System.currentTimeMillis()} só em {@code RelogioSistema}. Não é dogma: é o
 *       que permite testar a virada do dia sem esperar a meia-noite, e o teste que
 *       ninguém consegue rodar é o teste que não existe.</li>
 * </ol>
 *
 * <p><b>Por que {@code Instant} não é proibido, só o {@code now()}.</b> {@link
 * java.time.Instant} <i>é</i> UTC por construção — o problema nunca foi o tipo, foi
 * <b>quem lê o relógio</b>. Proibir o tipo mataria a cura junto com a doença.</p>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA.</b> Reprova nomeando o método e a chamada exata.
 * A correção é injetar {@code Relogio} e chamar {@code relogio.agora()} — nunca afrouxar
 * a lista, e nunca acrescentar uma classe à isenção sem escrever o motivo.</p>
 */
@DisplayName("catraca: UTC no sistema inteiro, com um unico relogio")
class CatracaRelogioUtcTest {

    private static final String RAIZ = "org.nasa";

    /**
     * A ÚNICA classe autorizada a ler o relógio do sistema.
     *
     * <p>Isenção nominal com motivo: alguém tem de perguntar as horas ao mundo, e é ela.
     * Toda outra classe recebe o {@code Relogio} injetado.</p>
     */
    private static final String RELOGIO_DO_SISTEMA = RAIZ + ".core.tempo.RelogioSistema";

    /** Hora LOCAL: proibida em qualquer lugar, sem isenção. */
    private static final Set<String> HORA_LOCAL = Set.of(
            "java.time.LocalDateTime.now",
            "java.time.LocalDate.now",
            "java.time.LocalTime.now",
            "java.time.ZonedDateTime.now",
            "java.time.OffsetDateTime.now",
            "java.time.OffsetTime.now",
            "java.time.Year.now",
            "java.time.YearMonth.now",
            "java.util.Calendar.getInstance",
            "java.util.TimeZone.getDefault");

    /** Leitura do relógio: permitida SÓ no RelogioSistema. */
    private static final Set<String> LEITURA_DO_RELOGIO = Set.of(
            "java.time.Instant.now",
            "java.time.Clock.systemUTC",
            "java.lang.System.currentTimeMillis");

    private static JavaClasses classes;

    @BeforeAll
    static void importar() {
        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(RAIZ);
    }

    private static String alvo(JavaMethodCall c) {
        return c.getTargetOwner().getName() + "." + c.getName();
    }

    @Test
    @DisplayName("0. o relogio existe e ha classes para examinar")
    void oAlvoNaoEstaVazio() {
        assertTrue(classes.size() >= 5,
                "menos de 5 classes: as regras nao examinariam nada — NAO VERIFICOU");
        assertTrue(classes.stream().anyMatch(c -> c.getName().equals(RELOGIO_DO_SISTEMA)),
                "RelogioSistema nao existe: a isencao nominal apontaria para o vazio e a "
                        + "regra 2 passaria sem ter o que permitir");
        System.out.println("[UTC] classes analisadas: " + classes.size());
    }

    @Test
    @DisplayName("1. nenhuma API de hora LOCAL e usada — em lugar nenhum")
    void nadaDeHoraLocal() {
        DescribedPredicate<JavaMethodCall> local =
                new DescribedPredicate<>("leitura de hora LOCAL (adota o fuso do host)") {
                    @Override
                    public boolean test(JavaMethodCall c) {
                        return HORA_LOCAL.contains(alvo(c));
                    }
                };

        ArchRule regra = noClasses()
                .that().resideInAPackage(RAIZ + "..")
                .should(ArchConditions.callMethodWhere(local))
                .because("hora local adota o fuso do host em silencio: a mesma linha sai -03:00 "
                        + "aqui e +00:00 no conteiner, e a janela de 'ultimos N dias' erra na "
                        + "virada. Injete Relogio e use relogio.agora(), que e Instant (UTC)");
        regra.check(classes);
    }

    @Test
    @DisplayName("2. so o RelogioSistema le o relogio do sistema")
    void soORelogioSistemaLeORelogio() {
        DescribedPredicate<JavaMethodCall> leitura =
                new DescribedPredicate<>("leitura direta do relogio do sistema") {
                    @Override
                    public boolean test(JavaMethodCall c) {
                        return LEITURA_DO_RELOGIO.contains(alvo(c));
                    }
                };

        ArchRule regra = noClasses()
                .that().resideInAPackage(RAIZ + "..")
                .and().doNotHaveFullyQualifiedName(RELOGIO_DO_SISTEMA)
                .should(ArchConditions.callMethodWhere(leitura))
                .because("um unico leitor do relogio e o que permite testar a virada do dia sem "
                        + "esperar a meia-noite; receba o Relogio injetado");
        regra.check(classes);
    }

    @Test
    @DisplayName("3. `new java.util.Date()` nao existe neste projeto")
    void nadaDeDateLegado() {
        DescribedPredicate<JavaConstructorCall> dateLegado =
                new DescribedPredicate<>("construtor de java.util.Date") {
                    @Override
                    public boolean test(JavaConstructorCall c) {
                        return "java.util.Date".equals(c.getTargetOwner().getName());
                    }
                };

        ArchRule regra = noClasses()
                .that().resideInAPackage(RAIZ + "..")
                .should(ArchConditions.callConstructorWhere(dateLegado))
                .because("java.util.Date carrega fuso implicito e e mutavel; o tipo do sistema "
                        + "e java.time.Instant, que e UTC por construcao");
        regra.check(classes);
    }
}
