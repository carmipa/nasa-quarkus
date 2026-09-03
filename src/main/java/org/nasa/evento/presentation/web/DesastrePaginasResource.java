package org.nasa.evento.presentation.web;

import io.quarkus.qute.Location;
import io.quarkus.qute.Template;
import io.quarkus.qute.TemplateInstance;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import org.nasa.core.erro.ErroDePipeline;
import org.nasa.core.presentation.web.MolduraDaPagina;
import org.nasa.evento.application.ConsultarEventosUseCase;
import org.nasa.evento.application.EventosProximosUseCase;
import org.nasa.evento.application.SincronizarEventosUseCase;
import org.nasa.geo.domain.Coordenada;

import java.util.List;

/**
 * As telas de desastres — sincronizar com a NASA, listar, ver no mapa e buscar por perto.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO.</b> É a tela onde o sistema mostra o que a NASA publica e
 * responde à pergunta que motiva tudo: "tem desastre perto daqui?".</p>
 *
 * <p><b>O QUE MUDOU EM RELAÇÃO AO LEGADO</b> ({@code desastres/page.tsx}, 757 linhas de
 * React com quatro abas):</p>
 * <ol>
 *   <li><b>A aba "alertar usuário" saiu daqui</b>, e virou tela própria em {@code /alertas}.
 *       Não é organização: a regra da arquitetura proíbe uma fatia conhecer outra, e
 *       misturar alerta com evento na mesma classe faria a guarda de fronteira reprovar o
 *       build — corretamente, porque é assim que duas fatias começam a se enrolar.</li>
 *   <li><b>Os filtros continuam todos</b> — limite, dias, apenas ativos, categoria,
 *       coordenada e raio —, mas quem os aplica é o servidor. No legado, parte deles
 *       filtrava a lista já baixada no navegador.</li>
 *   <li><b>A busca por proximidade agora usa geodésia de verdade.</b> O legado montava uma
 *       caixa delimitadora e parava nela; o canto de uma caixa fica 41% além do raio
 *       pedido.</li>
 * </ol>
 *
 * <p><b>INVARIANTES DO DOMÍNIO.</b></p>
 * <ol>
 *   <li><b>Sincronizar é POST</b>, nunca GET: escreve no banco, e um GET que escreve é
 *       executado por rastreador e por pré-carregamento de navegador.</li>
 *   <li><b>Cada aba troca só o seu pedaço</b>, por HTMX. Recarregar a página inteira
 *       perderia os filtros digitados na aba ao lado.</li>
 *   <li><b>Nenhuma regra vive aqui.</b> Este resource traduz formulário em caso de uso.</li>
 * </ol>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA.</b> NASA fora vira aviso na própria aba, com o
 * texto do erro — e a lista local continua visível, porque ela não depende da NASA estar
 * no ar. Falha inesperada sobe para o mapeador de borda.</p>
 */
@Path("/desastres")
@Produces(MediaType.TEXT_HTML)
@Transactional
public class DesastrePaginasResource {

    private static final int TAMANHO_PAGINA = 20;

    /**
     * Teto de eventos por ano na sincronização histórica.
     *
     * <p>Medido em 02/09/2026: o maior ano é 2026, com <b>6900</b> eventos; 2024 tem 5789 e
     * 2015 tem 342. O teto anterior, de 6000, <b>truncou 900 eventos de 2026 em silêncio</b>
     * — e o aviso de truncamento não disparou porque comparava a lista já filtrada. Os dois
     * defeitos foram corrigidos; este número é a folga, e o aviso é a rede.</p>
     */
    private static final int LIMITE_POR_ANO = 20_000;

    @Inject
    MolduraDaPagina moldura;

    @Inject
    SincronizarEventosUseCase sincronizar;

    @Inject
    ConsultarEventosUseCase consultar;

    @Inject
    EventosProximosUseCase proximos;

    @Inject
    @Location("paginas/desastres/painel/pagina.html")
    Template telaPainel;

    @Inject
    @Location("paginas/desastres/painel/fragmento-sincronizacao.html")
    Template fragmentoSincronizacao;

    @Inject
    @Location("paginas/desastres/painel/fragmento-lista.html")
    Template fragmentoLista;

    @Inject
    @Location("paginas/desastres/painel/fragmento-proximos.html")
    Template fragmentoProximos;

    @Inject
    @Location("paginas/desastres/mapa/pagina.html")
    Template telaMapa;

    @Inject
    @Location("paginas/desastres/estatisticas/pagina.html")
    Template telaEstatisticas;

    @Inject
    @Location("paginas/desastres/detalhe/pagina.html")
    Template telaDetalhe;

    @Inject
    @Location("paginas/desastres/historico/pagina.html")
    Template telaHistorico;

    @Inject
    @Location("paginas/desastres/historico/fragmento-ano.html")
    Template fragmentoAno;

    // ------------------------------------------------------------------ painel

    @GET
    public TemplateInstance painel() {
        return moldura.vestir(telaPainel
                .data("total", consultar.contar())
                .data("ativos", consultar.contarAtivos()), "desastres");
    }

    /** Roda a sincronização e devolve o resultado. POST porque ESCREVE. */
    @POST
    @Path("/sincronizar")
    public TemplateInstance sincronizar(@QueryParam("limite") @DefaultValue("50") int limite,
                                        @QueryParam("dias") @DefaultValue("30") int dias,
                                        @QueryParam("apenasAtivos") @DefaultValue("true")
                                        boolean apenasAtivos) {
        try {
            var r = sincronizar.executar(limite, dias, apenasAtivos);
            return fragmentoSincronizacao
                    .data("resultado", r).data("erro", null)
                    .data("total", consultar.contar())
                    .data("ativos", consultar.contarAtivos());
        } catch (ErroDePipeline falha) {
            // A NASA fora NAO apaga nada: a base local continua valida, e a lista ao lado
            // continua funcionando. O aviso diz isso em vez de sugerir que se perdeu algo.
            return fragmentoSincronizacao
                    .data("resultado", null).data("erro", falha.getMessage())
                    .data("total", consultar.contar())
                    .data("ativos", consultar.contarAtivos());
        }
    }

    @GET
    @Path("/fragmento/lista")
    public TemplateInstance lista(@QueryParam("pagina") @DefaultValue("0") int pagina,
                                  @QueryParam("categoria") String categoria) {
        int p = Math.max(0, pagina);
        var eventos = (categoria == null || categoria.isBlank())
                ? consultar.listar(p, TAMANHO_PAGINA)
                : consultar.porCategoria(categoria, p, TAMANHO_PAGINA);
        return fragmentoLista
                .data("eventos", eventos)
                .data("pagina", p)
                .data("categoria", categoria == null ? "" : categoria)
                .data("temProxima", eventos.size() == TAMANHO_PAGINA)
                .data("vazio", eventos.isEmpty());
    }

    /**
     * Os eventos ativos dentro do raio.
     *
     * <p>Duas etapas: caixa por índice, geodésia decidindo. O fragmento mostra os dois
     * números — <b>candidatos</b> e <b>dentro do raio</b> — porque a diferença entre eles é
     * a prova de que a segunda etapa faz trabalho.</p>
     */
    @GET
    @Path("/fragmento/proximos")
    public TemplateInstance proximos(@QueryParam("latitude") @DefaultValue("0") double latitude,
                                     @QueryParam("longitude") @DefaultValue("0") double longitude,
                                     @QueryParam("raioKm") @DefaultValue("100") double raioKm,
                                     @QueryParam("dias") @DefaultValue("30") int dias,
                                     @QueryParam("procurou") @DefaultValue("false") boolean procurou) {
        if (!procurou) {
            return fragmentoProximos.data("achados", List.of())
                    .data("procurou", false).data("erro", null)
                    .data("raioKm", raioKm).data("latitude", latitude)
                    .data("longitude", longitude);
        }
        try {
            var achados = proximos.executar(new Coordenada(latitude, longitude), raioKm, dias);
            return fragmentoProximos.data("achados", achados)
                    .data("procurou", true).data("erro", null)
                    .data("raioKm", raioKm).data("latitude", latitude)
                    .data("longitude", longitude);
        } catch (ErroDePipeline falha) {
            // Coordenada fora da Terra ou raio invalido: o peer `geo` recusa, e a tela
            // mostra o motivo em vez de uma lista vazia que pareceria "nada por perto".
            return fragmentoProximos.data("achados", List.of())
                    .data("procurou", true).data("erro", falha.getMessage())
                    .data("raioKm", raioKm).data("latitude", latitude)
                    .data("longitude", longitude);
        }
    }

    // -------------------------------------------------------------------- mapa

    /**
     * O mapa, com filtro por tipo de desastre.
     *
     * <p><b>O FILTRO É DO SERVIDOR, e isso é a decisão que importa aqui.</b> Filtrar no
     * navegador filtraria apenas os eventos <b>já carregados</b> — e o mapa carrega um teto,
     * enquanto a base tem 21.542. Pedir "vulcões" entre os mais recentes devolveria vazio, e
     * a tela diria que não há vulcão nenhum. Filtro que mente sobre ausência é pior que
     * filtro nenhum: ele produz uma conclusão, e a conclusão está errada.</p>
     *
     * <p><b>Nenhuma categoria marcada significa TODAS</b>, não nenhuma. É o estado inicial, e
     * um mapa que abrisse vazio esperando escolha seria uma tela em branco pedindo trabalho
     * antes de mostrar qualquer coisa.</p>
     *
     * <p><b>As categorias vão na URL</b>, uma por parâmetro repetido. É o que torna um
     * recorte compartilhável: "olha os incêndios e as enchentes" vira um link.</p>
     */
    @GET
    @Path("/mapa")
    public TemplateInstance mapa(@QueryParam("categoria") List<String> categorias) {
        // So categorias que EXISTEM entram na consulta. Um `?categoria=xpto` digitado na
        // URL nao vira consulta ao banco por um valor que nunca casa — ele e descartado
        // aqui, e o mapa se comporta como se nao tivesse sido pedido.
        var pedidas = categorias == null ? List.<String>of()
                : categorias.stream()
                        .filter(c -> c != null && !c.isBlank())
                        .filter(c -> CategoriasDeDesastre.existe(c))
                        .distinct()
                        .toList();

        var eventos = consultar.paraOMapa(pedidas, ConsultarEventosUseCase.MAXIMO_NO_MAPA);

        // OS CHIPS mostram TODAS as categorias que existem na base com coordenada, com o
        // numero de cada uma — nao so as que estao na tela agora. Um filtro que some
        // depois de usado nao deixa voltar, e e justamente quando o resultado veio vazio
        // que se precisa do caminho de volta.
        var chips = consultar.categoriasDoMapa().stream()
                .map(c -> new ChipDeCategoria(
                        CategoriasDeDesastre.de(c.categoria()),
                        c.quantos(),
                        pedidas.contains(c.categoria())))
                .toList();

        // A LEGENDA mostra so o que esta DESENHADO — e outra coisa dos chips. Chip e
        // controle ("posso pedir isto"); legenda e chave de leitura ("isto esta ai").
        var naTela = eventos.stream()
                .map(e -> CategoriasDeDesastre.de(e.categoria()))
                .distinct()
                .sorted(java.util.Comparator.comparing(CategoriasDeDesastre.Categoria::nome))
                .toList();

        return moldura.vestir(telaMapa
                .data("eventos", eventos)
                .data("legenda", naTela)
                .data("chips", chips)
                .data("filtrando", !pedidas.isEmpty())
                .data("quantosDesenhados", eventos.size())
                .data("noTeto", eventos.size() >= ConsultarEventosUseCase.MAXIMO_NO_MAPA)
                .data("teto", ConsultarEventosUseCase.MAXIMO_NO_MAPA)
                .data("total", consultar.contar())
                .data("ativos", consultar.contarAtivos()), "desastres", true);
    }

    /**
     * Um chip de filtro do mapa.
     *
     * @param categoria a categoria, com nome, cor e ícone
     * @param quantos   quantos eventos <b>desenháveis</b> ela tem. O número é a promessa do
     *                  que o filtro vai mostrar — contar eventos sem coordenada aqui faria
     *                  o chip prometer pinos que nunca aparecem
     * @param marcada   se está no recorte atual
     */
    public record ChipDeCategoria(CategoriasDeDesastre.Categoria categoria, long quantos,
                                  boolean marcada) {
    }

    // ------------------------------------------------------------ estatisticas

    @GET
    @Path("/estatisticas")
    public TemplateInstance estatisticas(@QueryParam("dias") @DefaultValue("30") int dias) {
        var contagens = consultar.contarPorCategoria(dias);
        long soma = contagens.stream().mapToLong(c -> c.quantos()).sum();

        // O MAIOR valor decide a escala. Com o TOTAL, uma categoria dominante achataria
        // as demais a ponto de nao dar para compara-las — e comparar e a unica coisa que
        // este grafico serve para fazer.
        long maior = contagens.stream().mapToLong(c -> c.quantos()).max().orElse(1L);

        // A porcentagem e calculada AQUI, e nao no template. O Qute nao faz aritmetica em
        // expressao — e nem deveria: conta dentro de template e regra escondida num lugar
        // que ninguem testa. Descoberto em 02/09 com `Method "*(100)" not found`.
        List<Barra> barras = contagens.stream()
                .map(c -> new Barra(c.categoria(), c.quantos(),
                        maior == 0 ? 0 : Math.max(1, c.quantos() * 100 / maior)))
                .toList();

        // SERIE HISTORICA. A altura de cada coluna e calculada AQUI, pelo mesmo motivo
        // das barras: o Qute nao faz aritmetica, e conta em template e regra escondida.
        var serie = consultar.serieHistorica(dias);
        long pico = serie.stream().mapToLong(p -> p.quantos()).max().orElse(1L);
        List<Coluna> colunas = serie.stream()
                .map(p -> new Coluna(p.dia().toString(),
                        p.dia().getDayOfMonth() + "/" + p.dia().getMonthValue(),
                        p.quantos(),
                        // Dia com ZERO fica com altura 0 e NAO vira 1%: o vazio e a
                        // informacao mais importante de uma serie historica, e falsificar
                        // um tracinho ali apagaria a calmaria que ele mostra.
                        p.quantos() == 0 ? 0 : Math.max(2, p.quantos() * 100 / pico)))
                .toList();

        return moldura.vestir(telaEstatisticas
                .data("colunas", colunas)
                .data("pico", pico)
                .data("diasComEvento", serie.stream().filter(p -> p.quantos() > 0).count())
                .data("barras", barras)
                .data("dias", dias)
                .data("soma", soma)
                .data("categorias", contagens.size())
                .data("vazio", contagens.isEmpty()), "desastres");
    }

    /**
     * Uma barra do grafico, com a largura ja calculada.
     *
     * <p>A largura mínima é <b>1%</b>, nunca zero: uma categoria com um único evento
     * ainda precisa <b>aparecer</b>. Arredondada para baixo, ela viraria uma barra de
     * largura nenhuma — e a categoria sumiria do gráfico enquanto o número ao lado dizia
     * que ela existe.</p>
     *
     * @param categoria    o identificador da EONET
     * @param quantos      quantos eventos
     * @param porcentagem  largura da barra, de 1 a 100, relativa à maior categoria
     */
    public record Barra(String categoria, long quantos, long porcentagem) {
    }

    /**
     * Uma coluna da serie historica.
     *
     * <p><b>Dia com zero tem altura ZERO</b>, e não um mínimo simbólico. É o oposto da
     * regra das barras por categoria, onde uma categoria com um evento precisa aparecer:
     * ali o mínimo revela algo que existe; aqui um tracinho falsificaria atividade num dia
     * em que não houve nenhuma — e a calmaria é justamente o que a série histórica mostra
     * de mais útil.</p>
     *
     * @param dia      a data completa, para o rótulo acessível
     * @param rotulo   dia/mês, curto, para caber sob a coluna
     * @param quantos  quantos eventos
     * @param altura   0 a 100, relativa ao pico da janela
     */
    public record Coluna(String dia, String rotulo, long quantos, long altura) {
    }

    // --------------------------------------------------------------- historico

    /**
     * O arquivo histórico, ano a ano, desde o começo dos registros da EONET.
     *
     * <p><b>PROPÓSITO DE NEGÓCIO.</b> É a visão que o sistema original tinha e que a janela
     * de 30 dias não alcança: o que aconteceu no planeta em cada ano desde 2015.</p>
     *
     * <p><b>A DECISÃO QUE DEFINE ESTA TELA.</b> Um ano sem eventos no banco e um ano nunca
     * sincronizado têm a MESMA aparência — coluna vazia — e significados opostos. A tela
     * os pinta diferente, e a legenda diz qual é qual. Desenhá-los igual seria mentir com
     * um gráfico, que é a pior forma de mentir porque parece medição.</p>
     */
    @GET
    @Path("/historico")
    public TemplateInstance historico() {
        var anos = consultar.historicoPorAno();

        // O PICO decide a escala, e ele e calculado so sobre anos SINCRONIZADOS: incluir
        // os nao sincronizados (que valem 0) nao mudaria o maximo, mas deixa claro no
        // codigo que a escala fala de dado medido, nao de ausencia de dado.
        long pico = anos.stream().filter(a -> a.sincronizado())
                .mapToLong(a -> a.quantos()).max().orElse(1L);

        List<ColunaDoAno> colunas = anos.stream()
                .map(a -> new ColunaDoAno(a.ano(), a.quantos(), a.categorias(), a.sincronizado(),
                        // Ano sincronizado com ZERO eventos ainda ganha altura minima de 1%,
                        // para se distinguir do NAO sincronizado, que fica em 0. Sao os dois
                        // estados que esta tela existe para separar.
                        !a.sincronizado() ? 0
                                : (a.quantos() == 0 ? 1
                                        : Math.max(2, a.quantos() * 100 / pico))))
                .toList();

        long totalArquivo = anos.stream().mapToLong(a -> a.quantos()).sum();
        long anosSincronizados = anos.stream().filter(a -> a.sincronizado()).count();

        return moldura.vestir(telaHistorico
                .data("colunas", colunas)
                .data("pico", pico)
                .data("totalArquivo", totalArquivo)
                .data("anosSincronizados", anosSincronizados)
                .data("anosTotais", anos.size())
                .data("primeiroAno", ConsultarEventosUseCase.PRIMEIRO_ANO_EONET)
                .data("faltamAnos", anosSincronizados < anos.size()), "desastres");
    }

    /**
     * Uma coluna do gráfico anual.
     *
     * @param ano          o ano civil, em UTC
     * @param quantos      eventos gravados
     * @param categorias   categorias distintas naquele ano
     * @param sincronizado se o ano já foi buscado na NASA. <b>Sem este campo a tela não
     *                     conseguiria distinguir "ano calmo" de "ano não perguntado".</b>
     * @param altura       0 a 100. Zero significa NÃO SINCRONIZADO; um ano sincronizado e
     *                     vazio vale 1, para que os dois estados nunca desenhem igual
     */
    public record ColunaDoAno(int ano, long quantos, long categorias, boolean sincronizado,
                              long altura) {
    }

    /**
     * Sincroniza UM ano com a NASA. POST porque ESCREVE.
     *
     * <p>Um ano por vez, e não todos de uma vez, por uma razão medida: 2025 tem 4612
     * eventos e 2026 passou de 5000. Puxar doze anos numa requisição seria uma requisição
     * de vários minutos que o navegador desiste no meio — e o que já entrou ficaria
     * gravado sem ninguém saber quanto. Um ano por vez é retomável: se o quinto falhar,
     * os quatro anteriores continuam valendo, e a tela mostra exatamente onde parou.</p>
     */
    @POST
    @Path("/historico/sincronizar/{ano}")
    public TemplateInstance sincronizarAno(@PathParam("ano") int ano) {
        // Pelo RELOGIO INJETADO, nunca por `Year.now()`: a catraca de UTC reprova
        // leitura estatica de relogio em todo o `org.nasa..`, e reprovou esta linha.
        int anoAtual = consultar.anoAtual();
        if (ano < ConsultarEventosUseCase.PRIMEIRO_ANO_EONET || ano > anoAtual) {
            // Ano fora do arquivo NAO vira requisicao a NASA: seria uma chamada externa
            // garantidamente inutil, disparada por um numero digitado na URL.
            return fragmentoAno.data("ano", ano).data("resultado", null)
                    .data("erro", "ano fora do arquivo da EONET ("
                            + ConsultarEventosUseCase.PRIMEIRO_ANO_EONET + " a " + anoAtual + ")")
                    .data("quantos", 0L).data("barras", null);
        }
        try {
            // 20000, e nao 6000. Medido em 02/09/2026: 2026 tem 6900 eventos, e o teto
            // anterior de 6000 truncou 900 deles. A folga precisa acompanhar o crescimento
            // da publicacao da NASA — 2015 teve 342 eventos, 2024 teve 5789.
            var r = sincronizar.executarAno(ano, LIMITE_POR_ANO);
            return fragmentoAno.data("ano", ano).data("resultado", r).data("erro", null)
                    .data("quantos", consultar.contarDoAno(ano))
                    // Depois de sincronizar, o detalhe por categoria do ano recem-trazido:
                    // e a prova visivel de que o ano entrou, no mesmo lugar da tela.
                    .data("barras", barrasDoAno(ano));
        } catch (ErroDePipeline falha) {
            return fragmentoAno.data("ano", ano).data("resultado", null)
                    .data("erro", falha.getMessage())
                    .data("quantos", consultar.contarDoAno(ano)).data("barras", null);
        }
    }

    /** O detalhe de um ano: quantos eventos de cada categoria. */
    @GET
    @Path("/historico/{ano}")
    public TemplateInstance detalheDoAno(@PathParam("ano") int ano) {
        return fragmentoAno.data("ano", ano).data("resultado", null).data("erro", null)
                .data("quantos", consultar.contarDoAno(ano))
                .data("barras", barrasDoAno(ano));
    }

    /**
     * As barras por categoria de um ano.
     *
     * <p>Devolve {@code null} quando o ano não tem nada — e o template testa a chave. Uma
     * lista vazia desenharia um cabeçalho "por categoria" com nada embaixo, que parece
     * defeito de renderização em vez de ano sem dados.</p>
     */
    private List<Barra> barrasDoAno(int ano) {
        var categorias = consultar.categoriasDoAno(ano);
        if (categorias.isEmpty()) {
            return null;
        }
        long maior = categorias.stream().mapToLong(c -> c.quantos()).max().orElse(1L);
        return categorias.stream()
                .map(c -> new Barra(c.categoria(), c.quantos(),
                        maior == 0 ? 0 : Math.max(1, c.quantos() * 100 / maior)))
                .toList();
    }

    // ----------------------------------------------------------------- detalhe

    @GET
    @Path("/{id}")
    public TemplateInstance detalhe(@PathParam("id") long id) {
        // `true`: a tela de detalhe tambem desenha mapa, e a atribuicao ODbL e
        // exigida onde o dado aparece.
        return moldura.vestir(telaDetalhe
                .data("evento", consultar.exigirPorId(id)), "desastres", true);
    }
}
