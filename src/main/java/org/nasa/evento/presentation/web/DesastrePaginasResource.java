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

    @GET
    @Path("/mapa")
    public TemplateInstance mapa(@QueryParam("dias") @DefaultValue("30") int dias,
                                 @QueryParam("apenasAtivos") @DefaultValue("true")
                                 boolean apenasAtivos) {
        // 500 eventos e o teto do que um mapa mostra sem virar uma mancha de pinos.
        var eventos = consultar.listar(0, 100);
        return moldura.vestir(telaMapa
                .data("eventos", eventos)
                .data("dias", dias)
                .data("apenasAtivos", apenasAtivos)
                .data("total", consultar.contar())
                .data("ativos", consultar.contarAtivos()), "desastres");
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

        return moldura.vestir(telaEstatisticas
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

    // ----------------------------------------------------------------- detalhe

    @GET
    @Path("/{id}")
    public TemplateInstance detalhe(@PathParam("id") long id) {
        return moldura.vestir(telaDetalhe
                .data("evento", consultar.exigirPorId(id)), "desastres");
    }
}
