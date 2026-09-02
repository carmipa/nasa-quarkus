package org.nasa.evento.presentation.web;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.nasa.evento.application.EventosProximosUseCase;
import org.nasa.evento.domain.EventoNatural;
import org.nasa.evento.domain.ports.RepositorioDeEventosPort;
import org.nasa.geo.domain.CaixaDelimitadora;
import org.nasa.geo.domain.Coordenada;
import org.nasa.geo.domain.Geodesia;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Prova de que a proximidade tem DUAS etapas — e de que a segunda faz trabalho.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO.</b> É o cálculo que decide quem é avisado de um desastre.
 * Parar na caixa delimitadora é o erro tentador, porque "já filtrou": a consulta responde,
 * a tela desenha, e ninguém percebe que gente a 141 km está sendo avisada de um evento com
 * raio de 100 km. A pessoa avisada à toa é a que desliga a notificação antes do evento que
 * importava.</p>
 *
 * <p><b>O caso do CANTO é o controle positivo deste teste:</b> um evento colocado de
 * propósito no canto da caixa, dentro do retângulo e fora do círculo. Sem ele, um filtro
 * que só usasse a caixa passaria em todos os outros casos.</p>
 */
@QuarkusTest
@DisplayName("proximidade — a caixa reduz, a geodesia DECIDE")
class EventoProximidadeTest {

    /** Praça da Sé, São Paulo. O centro do teste. */
    private static final Coordenada SE = new Coordenada(-23.5505, -46.6333);
    private static final double RAIO_KM = 100.0;

    @Inject
    RepositorioDeEventosPort repositorio;

    @Inject
    EventosProximosUseCase proximos;

    private String gravar(String sufixo, double latitude, double longitude, Instant quando) {
        String eonetId = "EONET_TESTE_" + sufixo + "_" + (System.nanoTime() % 1_000_000_000L);
        repositorio.gravarOuAtualizar(EventoNatural.lidoDaNasa(
                eonetId, "Evento de teste " + sufixo, "wildfires",
                quando, new Coordenada(latitude, longitude), "{}", null));
        return eonetId;
    }

    @Test
    @DisplayName("CONTROLE POSITIVO: o evento no CANTO da caixa e DESCARTADO pela geodesia")
    void oCantoDaCaixaEhDescartado() {
        Instant agora = Instant.now();
        CaixaDelimitadora caixa = CaixaDelimitadora.emVoltaDe(SE, RAIO_KM);

        // O canto NORDESTE da caixa: dentro do retangulo por construcao, e a ~41% alem
        // do raio, porque o canto fica a raio x raiz(2) do centro.
        Coordenada canto = new Coordenada(caixa.norte() - 0.01, caixa.leste() - 0.01);
        double distanciaDoCanto = Geodesia.distanciaEmKm(SE, canto);
        System.out.printf("[PROX] canto da caixa esta a %.0f km (raio pedido: %.0f km)%n",
                distanciaDoCanto, RAIO_KM);
        assertTrue(distanciaDoCanto > RAIO_KM,
                "o cenario exige que o canto esteja FORA do raio; medi " + distanciaDoCanto);
        assertTrue(caixa.contem(canto), "e DENTRO da caixa — e por isso o SQL vai traze-lo");

        String noCanto = gravar("CANTO", canto.latitude(), canto.longitude(), agora);
        String pertinho = gravar("PERTO", SE.latitude() + 0.05, SE.longitude() + 0.05, agora);

        List<EventosProximosUseCase.EventoProximo> dentro = proximos.executar(SE, RAIO_KM, 30);
        List<String> ids = dentro.stream().map(p -> p.evento().eonetId()).toList();

        assertTrue(ids.contains(pertinho), "o evento perto tinha de entrar");
        assertFalse(ids.contains(noCanto),
                "o evento no CANTO da caixa entrou: a geodesia nao esta filtrando, e o "
                        + "alerta esta avisando gente a " + Math.round(distanciaDoCanto) + " km");
    }

    @Test
    @DisplayName("ordenado pelo MAIS PROXIMO — quem esta em cima aparece primeiro")
    void ordenadoPeloMaisProximo() {
        Instant agora = Instant.now();
        gravar("LONGE", SE.latitude() + 0.6, SE.longitude(), agora);
        gravar("MEIO", SE.latitude() + 0.3, SE.longitude(), agora);
        gravar("COLADO", SE.latitude() + 0.01, SE.longitude(), agora);

        var dentro = proximos.executar(SE, RAIO_KM, 30);
        assertTrue(dentro.size() >= 3, "esperava ao menos os tres gravados");

        for (int i = 1; i < dentro.size(); i++) {
            assertTrue(dentro.get(i - 1).distanciaKm() <= dentro.get(i).distanciaKm(),
                    "fora de ordem: quem le a lista precisa ver primeiro o que esta em cima "
                            + "dele, nao o mais recente a 90 km");
        }
        System.out.printf("[PROX] mais proximo: %.1f km%n", dentro.get(0).distanciaKm());
    }

    @Test
    @DisplayName("evento ENCERRADO nao entra, mesmo estando colado")
    void encerradoNaoEntra() {
        Instant agora = Instant.now();
        String encerrado = "EONET_ENCERRADO_" + (System.nanoTime() % 1_000_000_000L);
        repositorio.gravarOuAtualizar(EventoNatural.lidoDaNasa(
                encerrado, "Incendio ja apagado", "wildfires", agora,
                new Coordenada(SE.latitude() + 0.001, SE.longitude()), "{}",
                agora.minusSeconds(3600)));

        var ids = proximos.executar(SE, RAIO_KM, 30).stream()
                .map(p -> p.evento().eonetId()).toList();
        assertFalse(ids.contains(encerrado),
                "um incendio apagado ha uma hora nao pode avisar ninguem");
    }

    @Test
    @DisplayName("a gravacao e IDEMPOTENTE e ATUALIZA a posicao — nao congela nem duplica")
    void gravacaoAtualizaAPosicao() {
        String eonetId = "EONET_MOVEL_" + (System.nanoTime() % 1_000_000_000L);
        Instant agora = Instant.now();

        var primeira = repositorio.gravarOuAtualizar(EventoNatural.lidoDaNasa(
                eonetId, "Tempestade em movimento", "severeStorms", agora.minusSeconds(7200),
                new Coordenada(-20.0, -40.0), "{}", null));
        assertTrue(primeira.inserido(), "a primeira gravacao INSERE");

        var segunda = repositorio.gravarOuAtualizar(EventoNatural.lidoDaNasa(
                eonetId, "Tempestade em movimento", "severeStorms", agora,
                new Coordenada(-21.5, -41.5), "{}", null));
        assertFalse(segunda.inserido(), "a segunda ATUALIZA — se inserisse, o mapa duplicaria");

        var lido = repositorio.porEonetId(eonetId).orElseThrow();
        assertEquals(-21.5, lido.coordenada().latitude(), 0.001,
                "a posicao NAO foi atualizada: `DO NOTHING` congelaria a tempestade no "
                        + "primeiro dia, e o alerta decidiria sobre onde ela estava");
        assertEquals(segunda.evento().id(), primeira.evento().id(),
                "e a MESMA linha, nao uma copia");
    }
}
