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

    /**
     * Eventos <b>com coordenada</b> das categorias pedidas — o que o mapa desenha.
     *
     * <p><b>Por que existe, em vez de filtrar a lista já carregada no navegador.</b> O mapa
     * carrega um teto de eventos, e a base tem 21.542. Filtrar no navegador filtraria
     * apenas <b>esse teto</b>: pedir "vulcões" entre os 100 eventos mais recentes devolveria
     * vazio, e a tela diria que não há vulcão nenhum — quando há. O filtro precisa alcançar
     * a base inteira, e por isso é consulta, não JavaScript.</p>
     *
     * <p><b>Só eventos COM coordenada.</b> Evento sem posição não pode ser desenhado, e
     * incluí-lo gastaria o teto com pontos que nunca aparecem — o mapa mostraria menos
     * quanto mais eventos sem posição a NASA publicasse.</p>
     *
     * @param categorias os identificadores da EONET; <b>vazio significa TODAS</b>, que é o
     *                   estado inicial do mapa
     * @param limite     teto de eventos
     */
    List<EventoNatural> comCoordenadaNasCategorias(java.util.Collection<String> categorias,
                                                   int limite);

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

    /**
     * Quantos eventos por ANO — o arquivo histórico inteiro, sem janela.
     *
     * <p><b>Por que não reusar {@link #contarPorDia(Instant)} com uma janela de dez anos.</b>
     * Aquela devolve uma linha por dia; dez anos são ~3650 linhas para desenhar doze
     * colunas. Pior: ela existe para mostrar <b>dias vazios</b>, e o caso de uso os
     * completa um a um — trabalho inútil quando a pergunta é por ano.</p>
     *
     * <p><b>Ano sem evento NÃO aparece</b>, pela mesma razão do dia: o banco só devolve o
     * que existe. Aqui, porém, quem completa a lacuna é esta camada de cima <b>de outro
     * jeito</b> — um ano vazio no meio da série significa "não sincronizado ainda", não
     * "não houve desastre no planeta em 2019". Confundir os dois é o defeito que esta
     * página existe para não ter.</p>
     */
    List<ContagemPorAno> contarPorAno();

    /**
     * Um ano e o que houve nele.
     *
     * @param ano        o ano civil em UTC
     * @param quantos    quantos eventos
     * @param categorias quantas categorias distintas — um ano com 400 eventos de uma só
     *                   categoria conta uma história diferente de um com 400 de doze
     */
    record ContagemPorAno(int ano, long quantos, long categorias) {
    }

    /**
     * Contagem por categoria <b>entre os eventos com coordenada</b> — os chips do mapa.
     *
     * <p><b>Por que não reusar {@link #contarPorCategoria(Instant)}.</b> Aquela conta tudo,
     * inclusive evento sem posição. O número no chip do mapa tem de bater com o que o mapa
     * <b>desenha</b>: um chip dizendo "Vulcões (54)" que acende 41 pinos faz a pessoa
     * procurar os 13 que faltam, e não há o que achar. Número ao lado de um filtro é uma
     * promessa do que ele vai mostrar.</p>
     */
    List<ContagemPorCategoria> contarPorCategoriaComCoordenada();

    /** Contagem por categoria DENTRO de um ano — o detalhe de uma coluna do histórico. */
    List<ContagemPorCategoria> contarPorCategoriaNoAno(int ano);

    /** Quantos eventos naquele ano — usado para saber se já foi sincronizado. */
    long contarDoAno(int ano);

    long contar();

    long contarAtivos();
}
