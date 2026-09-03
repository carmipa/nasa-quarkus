package org.nasa.evento.application;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.nasa.evento.domain.EventoNatural;
import org.nasa.evento.domain.exceptions.EventoNaoEncontradoException;
import org.nasa.evento.domain.ports.RepositorioDeEventosPort;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

    /**
     * A série histórica: quantos eventos em CADA dia da janela.
     *
     * <p><b>Os dias sem evento são preenchidos com zero aqui</b>, e essa é a única coisa
     * que este método faz além de consultar. O banco devolve apenas os dias que tiveram
     * eventos; um gráfico desenhado só com eles <b>encurta a linha do tempo</b> e faz três
     * eventos espalhados por três semanas parecerem três dias seguidos de atividade
     * intensa. O buraco é a informação mais importante de uma série histórica — é ele que
     * mostra que houve calmaria.</p>
     *
     * <p>A janela é contada do <b>relógio injetado</b>, e os dias são em <b>UTC</b>: agrupar
     * no fuso local faria a virada do dia cair em hora diferente por máquina, e o mesmo
     * gráfico mudaria conforme quem o abrisse.</p>
     */
    public List<PontoDaSerie> serieHistorica(int dias) {
        int janela = Math.max(1, Math.min(dias, 365));
        Instant desde = inicioDaJanela(janela);

        Map<java.time.LocalDate, Long> porDia = new HashMap<>();
        for (var c : repositorio.contarPorDia(desde)) {
            porDia.put(c.dia(), c.quantos());
        }

        java.time.LocalDate hoje = relogio.agora().atZone(java.time.ZoneOffset.UTC).toLocalDate();
        List<PontoDaSerie> serie = new ArrayList<>(janela);
        for (int i = janela - 1; i >= 0; i--) {
            java.time.LocalDate dia = hoje.minusDays(i);
            serie.add(new PontoDaSerie(dia, porDia.getOrDefault(dia, 0L)));
        }
        return serie;
    }

    /**
     * Um ponto da série histórica.
     *
     * @param dia     o dia, em UTC
     * @param quantos quantos eventos; ZERO é um valor legítimo e informativo
     */
    public record PontoDaSerie(java.time.LocalDate dia, long quantos) {
    }

    /**
     * O ANO EM QUE OS REGISTROS DA EONET COMEÇAM.
     *
     * <p>Medido em 02/09/2026 consultando a própria API: 2015 devolve eventos, e é o
     * primeiro ano com arquivo consistente. <b>Está aqui como constante, e não deduzido do
     * banco</b>, de propósito: deduzir do menor ano gravado faria a série começar em 2024
     * numa base recém-criada, e a página diria que a EONET começou em 2024.</p>
     */
    public static final int PRIMEIRO_ANO_EONET = 2015;

    /**
     * O histórico ano a ano, do começo dos registros da EONET até hoje.
     *
     * <p><b>PROPÓSITO DE NEGÓCIO.</b> Responde "o que aconteceu no planeta em cada ano" —
     * a visão que o sistema original tinha e que a janela de 30 dias não alcança.</p>
     *
     * <p><b>A DECISÃO QUE DEFINE ESTA TELA: ano sem linha no banco NÃO é ano sem
     * desastre.</b> É ano que ainda não foi sincronizado. São dois estados com a mesma
     * aparência — coluna vazia — e significados opostos: um diz "o planeta esteve calmo",
     * o outro diz "não perguntamos à NASA ainda". Uma tela que os desenhasse igual estaria
     * <b>mentindo com um gráfico</b>, que é a pior forma de mentir porque parece medição.
     * Por isso {@link AnoDoHistorico#sincronizado()} existe e a tela os pinta diferente.</p>
     *
     * <p>É o mesmo raciocínio do dia vazio na série de 30 dias, com a conclusão invertida:
     * lá, o zero é informação e o gráfico o mostra; aqui, o zero é <b>ausência de
     * informação</b> e o gráfico precisa dizer isso.</p>
     */
    public List<AnoDoHistorico> historicoPorAno() {
        Map<Integer, RepositorioDeEventosPort.ContagemPorAno> porAno = new HashMap<>();
        for (var c : repositorio.contarPorAno()) {
            porAno.put(c.ano(), c);
        }

        int anoAtual = relogio.agora().atZone(java.time.ZoneOffset.UTC).getYear();
        List<AnoDoHistorico> historico = new ArrayList<>();
        for (int ano = PRIMEIRO_ANO_EONET; ano <= anoAtual; ano++) {
            var c = porAno.get(ano);
            historico.add(c == null
                    // NAO sincronizado: quantos=0 e sincronizado=false. A tela precisa dos
                    // dois campos para nao pintar "sem dado" como "sem desastre".
                    ? new AnoDoHistorico(ano, 0, 0, false)
                    : new AnoDoHistorico(ano, c.quantos(), c.categorias(), true));
        }
        return historico;
    }

    /**
     * Um ano do histórico.
     *
     * @param ano          o ano civil, em UTC
     * @param quantos      eventos gravados daquele ano
     * @param categorias   categorias distintas — 400 eventos de uma categoria e 400 de
     *                     doze são anos diferentes, e só este campo os separa
     * @param sincronizado se o ano já foi buscado na NASA. <b>Sem este campo, um ano nunca
     *                     sincronizado seria indistinguível de um ano sem desastres.</b>
     */
    public record AnoDoHistorico(int ano, long quantos, long categorias, boolean sincronizado) {
    }

    /**
     * O ano corrente, em UTC, pelo <b>relógio injetado</b>.
     *
     * <p>Existe para que a camada web não precise chamar {@code Year.now()} — que é leitura
     * estática de relógio e adota o fuso do host. A catraca de UTC reprova esse chamado em
     * qualquer lugar de {@code org.nasa..}, e reprovou este em 02/09/2026, mesmo com o fuso
     * passado explicitamente: o problema não é só o fuso, é o relógio não ser injetável —
     * um teste não consegue fixar a data, e a virada do ano vira um caso que ninguém testa.</p>
     */
    public int anoAtual() {
        return relogio.agora().atZone(java.time.ZoneOffset.UTC).getYear();
    }

    /** O detalhe de um ano: quantos eventos de cada categoria. */
    public List<RepositorioDeEventosPort.ContagemPorCategoria> categoriasDoAno(int ano) {
        return repositorio.contarPorCategoriaNoAno(ano);
    }

    public long contarDoAno(int ano) {
        return repositorio.contarDoAno(ano);
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
