package org.nasa.alerta.presentation.web;

import io.quarkus.qute.Location;
import io.quarkus.qute.Template;
import io.quarkus.qute.TemplateInstance;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import org.nasa.alerta.application.ConsultarAlertasUseCase;
import org.nasa.alerta.application.DespacharAlertasUseCase;
import org.nasa.alerta.application.VarrerEGerarAlertasUseCase;
import org.nasa.core.erro.ErroDePipeline;
import org.nasa.core.presentation.web.MolduraDaPagina;

/**
 * A tela de alertas — varrer, despachar e auditar.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO.</b> É onde se vê se a promessa do sistema foi cumprida:
 * quem foi avisado, de quê, e o que falhou. É a tela de auditoria.</p>
 *
 * <p><b>POR QUE ELA NÃO É UMA ABA DE {@code /desastres}.</b> No legado era: a quarta aba
 * daquela tela alertava um usuário sobre um evento. Aqui não pode ser, e o motivo é
 * estrutural — a regra da arquitetura proíbe uma fatia conhecer outra, e um resource que
 * injetasse casos de uso de {@code evento} e de {@code alerta} ao mesmo tempo faria a
 * guarda de fronteira reprovar o build. Ela estaria certa: é assim que duas fatias começam
 * a se enrolar, e depois não se separam mais.</p>
 *
 * <p><b>A TELA NÃO PODE MENTIR, e isso é a decisão mais importante daqui.</b> Enquanto não
 * houver servidor de e-mail, o alerta marcado como {@code ENVIADO} significa
 * <b>registrado</b>, e ninguém recebeu nada. A tela mostra essa ressalva <b>ao lado</b> da
 * lista, sempre, e não escondida num rodapé — porque a única coisa pior que não ter alerta
 * é acreditar que se tem.</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO.</b></p>
 * <ol>
 *   <li><b>Varrer e despachar são POST.</b> As duas escrevem, e um GET que escreve é
 *       executado por rastreador e por pré-carregamento — no caso do despacho, isso
 *       mandaria avisos de verdade.</li>
 *   <li><b>O destino aparece MASCARADO.</b> Esta é a tela que alguém abre para mostrar a
 *       outra pessoa; dá para conferir para onde foi, não dá para colher endereços.</li>
 * </ol>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA.</b> Falha vira aviso na própria tela, com a
 * mensagem — a lista continua visível, porque ela não depende da varredura ter dado certo.</p>
 */
@Path("/alertas")
@Produces(MediaType.TEXT_HTML)
@Transactional
public class AlertaPaginasResource {

    private static final int TAMANHO_PAGINA = 20;

    @Inject
    MolduraDaPagina moldura;

    @Inject
    VarrerEGerarAlertasUseCase varrer;

    @Inject
    DespacharAlertasUseCase despachar;

    @Inject
    ConsultarAlertasUseCase consultar;

    @Inject
    @Location("paginas/alertas/painel/pagina.html")
    Template telaPainel;

    @Inject
    @Location("paginas/alertas/painel/fragmento-lista.html")
    Template fragmentoLista;

    @Inject
    @Location("paginas/alertas/painel/fragmento-operacao.html")
    Template fragmentoOperacao;

    @GET
    public TemplateInstance painel() {
        return moldura.vestir(telaPainel
                .data("contagens", consultar.contarPorSituacao())
                .data("meio", consultar.meioDeEntrega()), "desastres");
    }

    /** Descobre quem avisar e REGISTRA. POST porque escreve. Seguro de repetir. */
    @POST
    @Path("/varrer")
    public TemplateInstance varrer(@QueryParam("raioKm") @DefaultValue("100") double raioKm,
                                   @QueryParam("dias") @DefaultValue("30") int dias) {
        try {
            var r = varrer.executar(raioKm, dias);
            return fragmentoOperacao
                    .data("varredura", r).data("despacho", null).data("erro", null)
                    .data("contagens", consultar.contarPorSituacao());
        } catch (ErroDePipeline falha) {
            return fragmentoOperacao
                    .data("varredura", null).data("despacho", null)
                    .data("erro", falha.getMessage())
                    .data("contagens", consultar.contarPorSituacao());
        }
    }

    /** Envia o que está na fila. POST porque escreve — e um dia mandará e-mail de verdade. */
    @POST
    @Path("/despachar")
    public TemplateInstance despachar(@QueryParam("limite") @DefaultValue("50") int limite) {
        try {
            var r = despachar.executar(limite);
            return fragmentoOperacao
                    .data("varredura", null).data("despacho", r).data("erro", null)
                    .data("contagens", consultar.contarPorSituacao());
        } catch (ErroDePipeline falha) {
            return fragmentoOperacao
                    .data("varredura", null).data("despacho", null)
                    .data("erro", falha.getMessage())
                    .data("contagens", consultar.contarPorSituacao());
        }
    }

    @GET
    @Path("/fragmento/lista")
    public TemplateInstance lista(@QueryParam("situacao") String situacao,
                                  @QueryParam("pagina") @DefaultValue("0") int pagina) {
        int p = Math.max(0, pagina);
        var alertas = (situacao == null || situacao.isBlank())
                ? consultar.listar(p, TAMANHO_PAGINA)
                : consultar.porSituacao(situacao, p, TAMANHO_PAGINA);
        return fragmentoLista
                .data("alertas", AlertaResource.AlertaResposta.de(alertas))
                .data("situacao", situacao == null ? "" : situacao)
                .data("pagina", p)
                .data("temProxima", alertas.size() == TAMANHO_PAGINA)
                .data("vazio", alertas.isEmpty())
                .data("meio", consultar.meioDeEntrega());
    }
}
