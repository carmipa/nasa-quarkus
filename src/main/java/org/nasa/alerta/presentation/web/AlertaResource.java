package org.nasa.alerta.presentation.web;

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
import jakarta.ws.rs.core.Response;
import org.nasa.alerta.application.ConsultarAlertasUseCase;
import org.nasa.alerta.application.DespacharAlertasUseCase;
import org.nasa.alerta.application.VarrerEGerarAlertasUseCase;
import org.nasa.alerta.domain.Alerta;
import org.nasa.alerta.domain.ports.RepositorioDeAlertasPort;

import java.util.List;

/**
 * A API de alertas.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO.</b> Expõe as duas metades do <i>outbox</i> — varrer e
 * despachar — e a auditoria: o que foi avisado, para quem, e o que falhou.</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO.</b></p>
 * <ol>
 *   <li><b>Varrer e despachar são POST</b>, nunca GET. As duas ESCREVEM, e um GET que
 *       escreve é executado por rastreador e por pré-carregamento de navegador, sem
 *       ninguém clicar. No caso do despacho, isso enviaria avisos de verdade.</li>
 *   <li><b>{@code /meio-de-entrega} existe para a tela não mentir.</b> Enquanto não houver
 *       servidor de e-mail, {@code entregaDeVerdade} é {@code false} e a ressalva vem por
 *       escrito. É a informação que separa "cobertura" de "cobertura imaginária".</li>
 *   <li><b>As duas operações são seguras de repetir.</b> Varrer é idempotente pela chave
 *       do banco; despachar só toca no que está PENDENTE.</li>
 * </ol>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA.</b> Falha de banco sobe com causa-raiz (500).
 * Falha de envio não sobe: é marcada no próprio alerta, que fica no banco para auditoria.</p>
 */
@Path("/api/alertas")
@Produces(MediaType.APPLICATION_JSON)
@Transactional
public class AlertaResource {

    @Inject
    VarrerEGerarAlertasUseCase varrer;

    @Inject
    DespacharAlertasUseCase despachar;

    @Inject
    ConsultarAlertasUseCase consultar;

    /**
     * Descobre quem precisa ser avisado e REGISTRA os avisos — sem enviar.
     *
     * <p>POST porque escreve. Seguro de repetir: a chave {@code (cliente, evento)} do
     * banco garante que uma tempestade de cinco dias não vire cinco avisos.</p>
     */
    @POST
    @Path("/varrer")
    public VarrerEGerarAlertasUseCase.Resultado varrer(
            @QueryParam("raioKm") @DefaultValue("100") double raioKm,
            @QueryParam("dias") @DefaultValue("30") int dias) {
        return varrer.executar(raioKm, dias);
    }

    /**
     * Envia o que está na fila.
     *
     * <p>POST porque escreve — e porque, quando houver SMTP, isto manda mensagem para
     * pessoas de verdade.</p>
     */
    @POST
    @Path("/despachar")
    public DespacharAlertasUseCase.Resultado despachar(
            @QueryParam("limite") @DefaultValue("50") int limite) {
        return despachar.executar(limite);
    }

    @GET
    public List<AlertaResposta> listar(@QueryParam("pagina") @DefaultValue("0") int pagina,
                                       @QueryParam("tamanho") @DefaultValue("20") int tamanho) {
        return AlertaResposta.de(consultar.listar(pagina, tamanho));
    }

    @GET
    @Path("/situacao/{situacao}")
    public List<AlertaResposta> porSituacao(@PathParam("situacao") String situacao,
                                            @QueryParam("pagina") @DefaultValue("0") int pagina,
                                            @QueryParam("tamanho") @DefaultValue("20") int tamanho) {
        return AlertaResposta.de(consultar.porSituacao(situacao, pagina, tamanho));
    }

    @GET
    @Path("/cliente/{clienteId}")
    public List<AlertaResposta> doCliente(@PathParam("clienteId") long clienteId,
                                          @QueryParam("pagina") @DefaultValue("0") int pagina,
                                          @QueryParam("tamanho") @DefaultValue("20") int tamanho) {
        return AlertaResposta.de(consultar.doCliente(clienteId, pagina, tamanho));
    }

    @GET
    @Path("/{id}")
    public Response porId(@PathParam("id") long id) {
        return consultar.porId(id)
                .map(a -> Response.ok(AlertaResposta.de(a)).build())
                .orElseGet(() -> Response.status(Response.Status.NOT_FOUND).build());
    }

    @GET
    @Path("/resumo")
    public List<RepositorioDeAlertasPort.ContagemPorSituacao> resumo() {
        return consultar.contarPorSituacao();
    }

    /**
     * Por onde os avisos saem — e se saem mesmo.
     *
     * <p>Existe para a tela nunca mentir. Enquanto {@code entregaDeVerdade} for
     * {@code false}, o "ENVIADO" da lista significa "registrado", não "recebido".</p>
     */
    @GET
    @Path("/meio-de-entrega")
    public ConsultarAlertasUseCase.MeioDeEntrega meioDeEntrega() {
        return consultar.meioDeEntrega();
    }

    /**
     * O alerta como a API devolve.
     *
     * <p>O <b>destino aparece mascarado</b>: é o e-mail de uma pessoa, e esta lista é a
     * tela de auditoria, que alguém abre para mostrar a outra pessoa. Dá para conferir
     * para onde foi, e não dá para colher endereços de um print.</p>
     */
    public record AlertaResposta(Long id, long clienteId, long eventoId, String destinoMascarado,
                                 String situacao, String situacaoRotulo, String causaRaiz,
                                 int tentativas, String criadoEm, String concluidoEm) {

        static AlertaResposta de(Alerta a) {
            return new AlertaResposta(a.id(), a.clienteId(), a.eventoId(),
                    a.destinoMascarado(),
                    a.situacao().name(), a.situacao().rotulo(), a.causaRaiz(), a.tentativas(),
                    a.criadoEm() == null ? null : a.criadoEm().toString(),
                    a.concluidoEm() == null ? null : a.concluidoEm().toString());
        }

        static List<AlertaResposta> de(List<Alerta> alertas) {
            return alertas.stream().map(AlertaResposta::de).toList();
        }
    }
}
