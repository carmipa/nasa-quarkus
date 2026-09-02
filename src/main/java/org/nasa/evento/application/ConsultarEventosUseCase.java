package org.nasa.evento.application;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.nasa.evento.domain.EventoNatural;
import org.nasa.evento.domain.exceptions.EventoNaoEncontradoException;
import org.nasa.evento.domain.ports.RepositorioDeEventosPort;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

/**
 * As leituras de evento natural.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO.</b> Serve a tela de desastres e a de estatísticas. O teto de
 * paginação é UM, declarado aqui, e não uma decisão repetida em cada chamada.</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO.</b></p>
 * <ol>
 *   <li><b>Teto de {@value #TAMANHO_MAXIMO} por página.</b> Pedir um milhão não carrega a
 *       base na memória — e cada evento carrega o {@code jsonOriginal}, que pode ter
 *       quilobytes. Aqui o teto protege mais que na fatia de cliente.</li>
 *   <li><b>A janela de dias é medida contra o RELÓGIO INJETADO</b>, nunca contra uma data
 *       anotada. Estado calculado contra o relógio é o que faz o teste conseguir provar a
 *       borda da janela sem esperar um dia passar.</li>
 * </ol>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA.</b> Consulta vazia devolve lista vazia ou
 * {@link Optional#empty()} — "não existe" é resposta legítima.
 * {@link #exigirPorId(long)} é a exceção declarada, para os caminhos em que a ausência já
 * é erro.</p>
 */
@ApplicationScoped
public class ConsultarEventosUseCase {

    public static final int TAMANHO_MAXIMO = 100;

    @Inject
    RepositorioDeEventosPort repositorio;

    @Inject
    org.nasa.core.tempo.Relogio relogio;

    public List<EventoNatural> listar(int pagina, int tamanho) {
        return repositorio.listar(Math.max(0, pagina), limitar(tamanho));
    }

    public List<EventoNatural> porCategoria(String categoria, int pagina, int tamanho) {
        return repositorio.porCategoria(categoria, Math.max(0, pagina), limitar(tamanho));
    }

    public Optional<EventoNatural> porId(long id) {
        return repositorio.porId(id);
    }

    public Optional<EventoNatural> porEonetId(String eonetId) {
        return repositorio.porEonetId(eonetId);
    }

    public EventoNatural exigirPorId(long id) {
        return repositorio.porId(id)
                .orElseThrow(() -> new EventoNaoEncontradoException(String.valueOf(id)));
    }

    /** Contagem por categoria na janela pedida — a base da tela de estatísticas. */
    public List<RepositorioDeEventosPort.ContagemPorCategoria> contarPorCategoria(int dias) {
        return repositorio.contarPorCategoria(inicioDaJanela(dias));
    }

    public long contar() {
        return repositorio.contar();
    }

    public long contarAtivos() {
        return repositorio.contarAtivos();
    }

    /** O começo da janela, contado do relógio injetado — nunca de data anotada. */
    public Instant inicioDaJanela(int dias) {
        return relogio.agora().minus(Math.max(1, dias), ChronoUnit.DAYS);
    }

    private static int limitar(int tamanho) {
        if (tamanho <= 0) {
            return 10;
        }
        return Math.min(tamanho, TAMANHO_MAXIMO);
    }
}
