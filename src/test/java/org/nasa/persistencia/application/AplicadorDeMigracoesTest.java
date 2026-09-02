package org.nasa.persistencia.application;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nasa.persistencia.domain.Migracao;
import org.nasa.persistencia.domain.exceptions.MigracaoAlteradaException;
import org.nasa.persistencia.domain.ports.FonteDeMigracoesPort;
import org.nasa.persistencia.domain.ports.PreparacaoDoArmazenamentoPort;
import org.nasa.persistencia.domain.ports.RegistroDeMigracoesPort;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Prova das regras de migração — e principalmente da que <b>aborta</b>.
 *
 * <p><b>PROPÓSITO.</b> O caso que importa não é o do caminho feliz: é o da migração já
 * aplicada que alguém editou. Esse é o defeito que produz dois bancos diferentes com o
 * mesmo número de versão, e que só aparece quando uma consulta encontra coluna que existe
 * numa máquina e não na outra.</p>
 *
 * <p>Os dublês são de propósito: o caso de uso depende só de <b>portas</b>, então ele é
 * testável sem banco, sem disco e sem contêiner. É o que a regra
 * "{@code application} nunca depende de {@code infrastructure}" compra.</p>
 */
@DisplayName("AplicadorDeMigracoes — aplica na ordem, e aborta quando editaram o passado")
class AplicadorDeMigracoesTest {

    /** Registro em memória, com a lista de aplicações na ordem em que aconteceram. */
    static final class RegistroFalso implements RegistroDeMigracoesPort {
        final Map<Integer, String> banco = new LinkedHashMap<>();
        final List<String> aplicadasNestaRodada = new ArrayList<>();
        /** Ordem REAL das chamadas — sem isto, "antes" e "depois" nao sao observaveis. */
        final List<String> trilha = new ArrayList<>();
        boolean controlePreparado;

        @Override public void prepararControle() {
            controlePreparado = true;
            trilha.add("prepararControle");
        }

        @Override public Map<Integer, String> checksumsAplicados() { return new LinkedHashMap<>(banco); }

        @Override public void aplicarERegistrar(Migracao m) {
            aplicadasNestaRodada.add(m.identificacao());
            banco.put(m.versao(), m.checksum());
        }
    }

    private static AplicadorDeMigracoes aplicador(RegistroFalso registro, List<Migracao> declaradas) {
        AplicadorDeMigracoes a = new AplicadorDeMigracoes();
        a.registro = registro;
        a.fonte = (FonteDeMigracoesPort) () -> declaradas;
        a.relogio = () -> Instant.parse("2026-09-02T12:00:00Z");
        a.armazenamento = () -> {
            registro.trilha.add("garantirDisponibilidade");
            return new PreparacaoDoArmazenamentoPort.Local("duble");
        };
        return a;
    }

    private static Migracao m(int versao, String sql) {
        return new Migracao(versao, "teste", sql);
    }

    @Test
    @DisplayName("banco vazio: aplica todas, na ordem crescente")
    void aplicaTodasNaOrdem() {
        var registro = new RegistroFalso();
        var r = aplicador(registro, List.of(
                m(1, "CREATE TABLE a(x INT);"),
                m(2, "CREATE TABLE b(x INT);"),
                m(3, "CREATE TABLE c(x INT);"))).executar();

        assertTrue(registro.controlePreparado, "a tabela de controle tem de ser preparada antes");
        assertEquals(3, r.disponiveis());
        assertEquals(3, r.aplicadas());
        assertEquals(0, r.jaEstavam());
        assertEquals(List.of("V1__teste", "V2__teste", "V3__teste"), registro.aplicadasNestaRodada,
                "a ordem importa: DDL fora de ordem produz esquema diferente");
    }

    @Test
    @DisplayName("IDEMPOTENTE: a segunda execucao aplica ZERO — e diz isso com numero")
    void idempotente() {
        var registro = new RegistroFalso();
        var declaradas = List.of(m(1, "CREATE TABLE a(x INT);"), m(2, "CREATE TABLE b(x INT);"));

        aplicador(registro, declaradas).executar();
        registro.aplicadasNestaRodada.clear();
        var segunda = aplicador(registro, declaradas).executar();

        assertEquals(0, segunda.aplicadas(), "reaplicar DDL falharia com 'tabela ja existe'");
        assertEquals(2, segunda.jaEstavam(), "'nada a fazer' precisa aparecer como numero");
        assertTrue(registro.aplicadasNestaRodada.isEmpty());
    }

    @Test
    @DisplayName("aplica so o que falta quando chega migracao nova")
    void aplicaSoOQueFalta() {
        var registro = new RegistroFalso();
        var v1 = m(1, "CREATE TABLE a(x INT);");
        aplicador(registro, List.of(v1)).executar();
        registro.aplicadasNestaRodada.clear();

        var r = aplicador(registro, List.of(v1, m(2, "CREATE TABLE b(x INT);"))).executar();

        assertEquals(1, r.aplicadas());
        assertEquals(1, r.jaEstavam());
        assertEquals(List.of("V2__teste"), registro.aplicadasNestaRodada);
    }

    @Test
    @DisplayName("ABORTA quando uma migracao JA APLICADA foi editada")
    void abortaQuandoEditaramOPassado() {
        var registro = new RegistroFalso();
        aplicador(registro, List.of(m(1, "CREATE TABLE a(x INT);"))).executar();

        // Alguém "corrigiu" a V1 depois de ela já ter rodado.
        var editada = m(1, "CREATE TABLE a(x INT, y TEXT);");

        var erro = assertThrows(MigracaoAlteradaException.class,
                () -> aplicador(registro, List.of(editada)).executar());

        System.out.println("[MIGRACAO] " + erro.linhaDeLog());
        assertTrue(erro.getMessage().contains("migracao JA APLICADA foi editada"));
        assertTrue(erro.getMessage().contains("escreva uma migracao NOVA"),
                "a mensagem tem de dizer qual e a correcao, nao so que deu errado");
    }

    @Test
    @DisplayName("a verificacao vem INTEIRA antes de aplicar qualquer coisa")
    void verificaTudoAntesDeAplicarQualquerCoisa() {
        // V1 já aplicada e depois editada; V2 é nova. Se o aplicador conferisse e
        // aplicasse no mesmo laço, a V2 entraria antes de a V1 ser reprovada — e o banco
        // ficaria num estado que ninguém pediu, com a versão errada registrada.
        var registro = new RegistroFalso();
        aplicador(registro, List.of(m(1, "CREATE TABLE a(x INT);"))).executar();
        registro.aplicadasNestaRodada.clear();

        assertThrows(MigracaoAlteradaException.class, () -> aplicador(registro, List.of(
                m(1, "CREATE TABLE a(x INT, y TEXT);"),   // editada
                m(2, "CREATE TABLE b(x INT);"))).executar());

        assertTrue(registro.aplicadasNestaRodada.isEmpty(),
                "NADA pode ter sido aplicado: abortar no meio deixaria o banco pela metade");
    }

    @Test
    @DisplayName("indice vazio nao e 'nada a fazer' — e indice quebrado, e vira alerta")
    void indiceVazioEhAnomalia() {
        var registro = new RegistroFalso();
        var r = aplicador(registro, List.of()).executar();

        assertEquals(0, r.disponiveis());
        // O alarme sai no log como recusa com motivo NENHUMA_MIGRACAO_DECLARADA; aqui
        // provamos que o caso é distinguível pelo número, e não some no silêncio.
        assertEquals(0, r.aplicadas());
        assertEquals(0, r.jaEstavam());
    }
    @Test
    @DisplayName("ORDEM: o armazenamento e preparado ANTES da primeira conexao")
    void preparaOArmazenamentoAntesDeTudo() {
        // Em 02/09/2026 esta ordem nao existia, e o quarkusDev morria com SQLITE_CANTOPEN
        // num clone onde `data/` ainda nao tinha sido criada — enquanto 122 testes passavam,
        // porque o perfil de teste apontava para `build/`, que o Gradle cria.
        var registro = new RegistroFalso();
        aplicador(registro, List.of(m(1, "CREATE TABLE a(x INT);"))).executar();

        assertEquals(List.of("garantirDisponibilidade", "prepararControle"), registro.trilha,
                "preparar depois de abrir a conexao nao prepara nada: a conexao ja falhou");
    }

}
