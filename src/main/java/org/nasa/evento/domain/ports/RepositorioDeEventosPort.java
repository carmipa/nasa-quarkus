package org.nasa.evento.domain.ports;

import org.nasa.evento.domain.EventoNatural;
import org.nasa.geo.domain.CaixaDelimitadora;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * O que a fatia de evento precisa guardar e recuperar.
 *
 * <p><b>INVARIANTES DO DOMÍNIO.</b></p>
 * <ol>
 *   <li><b>{@link #gravarOuAtualizar(EventoNatural)} é IDEMPOTENTE por {@code eonetId}.</b>
 *       É a operação da sincronização, que roda repetidamente sobre os mesmos eventos: um
 *       evento aberto reaparece em toda chamada, com posição nova. Inserir sempre criaria
 *       cópias; ignorar o repetido congelaria a posição no primeiro dia.</li>
 *   <li><b>A busca por caixa é um FILTRO GROSSEIRO.</b> Ela reduz o conjunto; quem decide
 *       a distância de verdade é a geodésia, porque uma caixa é um retângulo e um raio é
 *       um círculo — os cantos da caixa ficam mais longe que o raio.</li>
 *   <li><b>Toda listagem é paginada, exceto a de alerta.</b></li>
 * </ol>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA.</b> O adaptador traduz erro de banco para
 * exceção da fatia, com causa-raiz.</p>
 */
public interface RepositorioDeEventosPort {

    /**
     * Grava o evento, ou atualiza o que já existe com o mesmo {@code eonetId}.
     *
     * @return o evento gravado, e {@code true} em {@link Resultado#inserido()} quando ele
     *         não existia antes — é o que separa AGIU de ABSTEVE na contagem
     */
    Resultado gravarOuAtualizar(EventoNatural evento);

    /** O que a gravação fez. */
    record Resultado(EventoNatural evento, boolean inserido) {
    }

    Optional<EventoNatural> porId(long id);

    Optional<EventoNatural> porEonetId(String eonetId);

    List<EventoNatural> listar(int pagina, int tamanho);

    List<EventoNatural> porCategoria(String categoria, int pagina, int tamanho);

    /** Eventos ATIVOS com coordenada dentro da caixa. Filtro grosseiro, por índice. */
    List<EventoNatural> ativosNaCaixa(CaixaDelimitadora caixa, Instant desde, int limite);

    /** Contagem por categoria, na janela pedida — a base da tela de estatísticas. */
    List<ContagemPorCategoria> contarPorCategoria(Instant desde);

    /**
     * Quantos eventos de cada categoria.
     *
     * @param categoria o identificador da EONET, ex.: {@code severeStorms}
     * @param quantos   quantos eventos naquela categoria
     */
    record ContagemPorCategoria(String categoria, long quantos) {
    }

    /**
     * Quantos eventos por DIA, na janela — a série histórica.
     *
     * <p><b>Dias sem evento NÃO aparecem</b> no resultado do banco, e isso é uma armadilha:
     * um gráfico que desenha só os dias retornados encurta a linha do tempo e faz três
     * eventos em três semanas parecerem três dias seguidos de atividade. Quem completa os
     * dias vazios é o caso de uso, com o relógio na mão.</p>
     */
    List<ContagemPorDia> contarPorDia(Instant desde);

    /**
     * Um dia e quantos eventos ocorreram nele.
     *
     * @param dia     a data em UTC — o agrupamento por dia depende do fuso, e usar o
     *                local faria a virada do dia cair em hora diferente por máquina
     * @param quantos quantos eventos naquele dia
     */
    record ContagemPorDia(java.time.LocalDate dia, long quantos) {
    }

    long contar();

    long contarAtivos();
}
