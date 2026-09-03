package org.nasa.painel.presentation.web;

import io.quarkus.qute.Location;
import io.quarkus.qute.Template;
import io.quarkus.qute.TemplateInstance;
import jakarta.inject.Inject;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import org.nasa.core.presentation.web.MolduraDaPagina;
import org.nasa.core.tempo.Relogio;
import org.nasa.core.telemetria.Telemetria;
import org.nasa.telemetria.infrastructure.adapters.RepositorioDeTelemetria;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A tela de telemetria — o que o sistema mediu de si mesmo.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO.</b> O log responde <i>"o que aconteceu naquele momento"</i>.
 * Esta tela responde as perguntas de agregado que o log não responde: com que frequência
 * cada operação roda, qual está lenta, o que está falhando e desde quando. São as perguntas
 * que aparecem às três da manhã, e respondê-las por {@code grep} com aritmética à mão é o
 * que ninguém faz naquela hora.</p>
 *
 * <p><b>ELA MEDE A SI MESMA.</b> A rota desta página é medida pelo mesmo filtro que mede
 * todas as outras — e isso é deliberado. Uma tela de telemetria que se exclui da telemetria
 * não pode ser usada para verificar se a telemetria funciona; abrir a página e ver a própria
 * visita contada é o controle positivo mais barato que existe.</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO.</b></p>
 * <ol>
 *   <li><b>Horas sem registro APARECEM, com zero.</b> O banco só devolve o que existe;
 *       desenhar apenas as horas retornadas encurtaria a linha do tempo e faria duas horas
 *       de atividade separadas por um dia parecerem consecutivas.</li>
 *   <li><b>RECUSA e FALHA são mostradas separadamente.</b> 404 é o sistema funcionando;
 *       500 é o sistema quebrado. Somá-las num "erros" faria um rastreador varrendo URLs
 *       inexistentes parecer uma pane.</li>
 *   <li><b>Base vazia é DECLARADA como "ainda não mediu"</b>, nunca desenhada como zero
 *       tranquilo. Gráfico zerado por falta de dado é indistinguível de gráfico zerado por
 *       sistema parado — e são situações opostas.</li>
 * </ol>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA.</b> Falha ao ler a telemetria não derruba a página:
 * ela mostra o aviso e o resto do que conseguiu ler. É a mesma disciplina do coletor —
 * telemetria é apoio, e apoio quebrado não pode virar tela de erro.</p>
 */
@Path("/telemetria")
@Produces(MediaType.TEXT_HTML)
public class TelemetriaResource {

    /** Formato do rótulo horário: dia/mês e hora, curto o bastante para caber sob a coluna. */
    private static final DateTimeFormatter ROTULO_HORA =
            DateTimeFormatter.ofPattern("dd/MM HH'h'").withZone(ZoneOffset.UTC);

    private static final DateTimeFormatter INSTANTE_LEGIVEL =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneOffset.UTC);

    @Inject
    MolduraDaPagina moldura;

    @Inject
    Telemetria telemetria;

    @Inject
    RepositorioDeTelemetria repositorio;

    @Inject
    Relogio relogio;

    @Inject
    @Location("paginas/telemetria/pagina.html")
    Template tela;

    @GET
    public TemplateInstance painel(@QueryParam("horas") @DefaultValue("24") int horas) {
        // Entre 1 hora e 30 dias. Abaixo de 1 nao ha o que agregar; acima de 720 a serie
        // horaria viraria 720+ colunas, que nao cabem numa tela nem informam nada.
        int janela = Math.max(1, Math.min(horas, 720));
        Instant desde = relogio.agora().minus(janela, ChronoUnit.HOURS)
                .truncatedTo(ChronoUnit.HOURS);

        var resumos = repositorio.resumo(desde);
        var serie = completarHoras(repositorio.porHora(desde, null), desde, janela);

        long totalChamadas = resumos.stream().mapToLong(r -> r.chamadas()).sum();
        long totalRecusas = resumos.stream().mapToLong(r -> r.recusas()).sum();
        long totalFalhas = resumos.stream().mapToLong(r -> r.falhas()).sum();

        // A media geral vem de SOMA ÷ CONTAGEM, nunca de media das medias: agregar uma
        // operacao com 1 chamada e outra com 10.000 dando peso igual mentiria.
        long somaPonderada = resumos.stream()
                .mapToLong(r -> r.mediaMs() * r.chamadas()).sum();
        long mediaGeralMs = totalChamadas == 0 ? 0 : somaPonderada / totalChamadas;

        long pico = serie.stream().mapToLong(p -> p.chamadas()).max().orElse(1L);
        List<ColunaHoraria> colunas = serie.stream()
                .map(p -> new ColunaHoraria(
                        ROTULO_HORA.format(p.hora()),
                        p.chamadas(),
                        p.problemas(),
                        p.mediaMs(),
                        // Hora com ZERO fica em zero e NAO ganha altura minima: o vazio e
                        // a informacao, e um tracinho falsificaria atividade numa hora em
                        // que nao houve nenhuma.
                        p.chamadas() == 0 ? 0 : Math.max(2, p.chamadas() * 100 / pico),
                        // A fatia de problemas dentro da coluna, para o erro ser VISIVEL
                        // no grafico e nao so na tabela.
                        p.chamadas() == 0 ? 0 : p.problemas() * 100 / p.chamadas()))
                .toList();

        // A maior media decide a escala da barra de latencia — o mesmo criterio das
        // outras telas: com o total, uma operacao lenta achataria as demais.
        long piorMedia = resumos.stream().mapToLong(r -> r.mediaMs()).max().orElse(1L);
        List<LinhaDeOperacao> linhas = resumos.stream()
                .map(r -> new LinhaDeOperacao(r,
                        piorMedia == 0 ? 0 : Math.max(1, r.mediaMs() * 100 / piorMedia),
                        INSTANTE_LEGIVEL.format(r.ultimaHora())))
                .toList();

        return moldura.vestir(tela
                .data("horas", janela)
                .data("colunas", colunas)
                .data("linhas", linhas)
                .data("pico", pico)
                .data("totalChamadas", totalChamadas)
                .data("totalRecusas", totalRecusas)
                .data("totalFalhas", totalFalhas)
                .data("totalSucessos", totalChamadas - totalRecusas - totalFalhas)
                .data("percentualDeSucesso",
                        totalChamadas == 0 ? 100 : (totalChamadas - totalRecusas - totalFalhas)
                                * 100 / totalChamadas)
                .data("mediaGeralMs", mediaGeralMs)
                .data("operacoesDistintas", resumos.size())
                .data("pendentesEmMemoria", telemetria.pendentes())
                .data("linhasNaTabela", repositorio.contarLinhas())
                .data("tabelas", tamanhoDasTabelas())
                // `vazio` separa "ainda nao mediu" de "mediu e deu zero", que sao
                // situacoes opostas com a mesma aparencia num grafico.
                .data("vazio", resumos.isEmpty()), "telemetria");
    }

    /**
     * Completa as horas sem registro com zero.
     *
     * <p>O banco devolve só as horas que tiveram atividade. Desenhar apenas elas encurtaria
     * a linha do tempo: duas horas de trabalho separadas por um dia inteiro apareceriam
     * lado a lado, como se fossem consecutivas.</p>
     */
    private List<RepositorioDeTelemetria.PontoPorHora> completarHoras(
            List<RepositorioDeTelemetria.PontoPorHora> medidos, Instant desde, int janela) {

        Map<Instant, RepositorioDeTelemetria.PontoPorHora> porHora = new HashMap<>();
        for (var p : medidos) {
            porHora.put(p.hora().truncatedTo(ChronoUnit.HOURS), p);
        }
        List<RepositorioDeTelemetria.PontoPorHora> completa = new ArrayList<>(janela);
        Instant inicio = desde.truncatedTo(ChronoUnit.HOURS);
        for (int i = 0; i <= janela; i++) {
            Instant hora = inicio.plus(i, ChronoUnit.HOURS);
            completa.add(porHora.getOrDefault(hora,
                    new RepositorioDeTelemetria.PontoPorHora(hora, 0, 0, 0)));
        }
        return completa;
    }

    /** O tamanho de cada tabela; falha de leitura não derruba a página. */
    private List<TamanhoDaTabela> tamanhoDasTabelas() {
        try {
            return repositorio.tamanhoDasTabelas().entrySet().stream()
                    .map(e -> new TamanhoDaTabela(e.getKey(), e.getValue()))
                    .toList();
        } catch (RuntimeException naoLeu) {
            // A pagina continua inteira sem esta secao. Perder a tela toda por causa de
            // uma contagem seria a troca errada.
            return List.of();
        }
    }

    /**
     * Uma coluna do gráfico horário.
     *
     * @param rotulo    dia/mês e hora, curto para caber sob a coluna
     * @param chamadas  quantas naquela hora
     * @param problemas recusas + falhas
     * @param mediaMs   latência média da hora
     * @param altura    0 a 100, relativa ao pico da janela. ZERO significa hora sem
     *                  atividade, e não ganha altura mínima — o vazio é a informação
     * @param fatiaDeProblema percentual da coluna que foi problema, para o erro aparecer
     *                        no desenho e não só na tabela
     */
    public record ColunaHoraria(String rotulo, long chamadas, long problemas, long mediaMs,
                                long altura, long fatiaDeProblema) {
    }

    /**
     * Uma linha da tabela de operações.
     *
     * @param resumo         o agregado vindo do banco
     * @param larguraDaMedia 1 a 100, relativa à operação mais lenta
     * @param ultimaVez      a última hora com registro, legível
     */
    public record LinhaDeOperacao(RepositorioDeTelemetria.ResumoDaOperacao resumo,
                                  long larguraDaMedia, String ultimaVez) {
    }

    /** Quantas linhas uma tabela de negócio tem. */
    public record TamanhoDaTabela(String tabela, long linhas) {
    }
}
