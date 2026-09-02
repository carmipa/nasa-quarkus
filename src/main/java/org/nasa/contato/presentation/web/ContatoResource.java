package org.nasa.contato.presentation.web;

import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.nasa.contato.application.AlterarContatoUseCase;
import org.nasa.contato.application.CadastrarContatoUseCase;
import org.nasa.contato.application.ConsultarContatosUseCase;
import org.nasa.contato.application.ExcluirContatoUseCase;
import org.nasa.contato.domain.Contato;

import java.net.URI;
import java.util.List;

/**
 * A API de contatos.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO.</b> Reproduz e corrige o {@code /api/contatos} do legado.
 * A correção que mais importa está em {@code GET /api/contatos/email/...}: no legado esse
 * endpoint devolvia UM contato sem que nada garantisse existir apenas um, e a resposta
 * dependia da ordem que o banco resolvesse devolver. Agora a unicidade é do banco, e a
 * pergunta tem resposta.</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO.</b></p>
 * <ol>
 *   <li><b>201 traz {@code Location}.</b> Sem ele quem criou precisa adivinhar a URL do
 *       que acabou de criar.</li>
 *   <li><b>A resposta declara se o contato RECEBE ALERTA</b>, com o motivo. É a
 *       informação que muda a decisão de quem cadastra, e no legado ela não existia em
 *       lugar nenhum — cadastrava-se um contato achando que estava coberto.</li>
 *   <li><b>Nenhuma regra aqui.</b> Este resource traduz JSON em chamada de caso de uso.</li>
 * </ol>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA.</b> As exceções sobem para o mapeador de borda,
 * que traduz por causa-raiz e registra log e telemetria uma vez: 400 para campo torto,
 * 404 para inexistente, 409 para e-mail repetido, 500 para falha de banco.</p>
 */
@Path("/api/contatos")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Transactional
public class ContatoResource {

    @Inject
    CadastrarContatoUseCase cadastrar;

    @Inject
    ConsultarContatosUseCase consultar;

    @Inject
    AlterarContatoUseCase alterar;

    @Inject
    ExcluirContatoUseCase excluir;

    @POST
    public Response criar(ContatoPedido pedido) {
        Contato criado = cadastrar.executar(pedido.ddd(), pedido.telefone(), pedido.celular(),
                pedido.whatsapp(), pedido.email(), pedido.tipoContato());
        return Response.created(URI.create("/api/contatos/" + criado.id()))
                .entity(ContatoResposta.de(criado))
                .build();
    }

    @GET
    public List<ContatoResposta> listar(@QueryParam("pagina") @DefaultValue("0") int pagina,
                                        @QueryParam("tamanho") @DefaultValue("10") int tamanho) {
        return ContatoResposta.de(consultar.listar(pagina, tamanho));
    }

    @GET
    @Path("/pesquisar")
    public List<ContatoResposta> pesquisar(@QueryParam("termo") @DefaultValue("") String termo,
                                           @QueryParam("pagina") @DefaultValue("0") int pagina,
                                           @QueryParam("tamanho") @DefaultValue("10") int tamanho) {
        return ContatoResposta.de(consultar.pesquisar(termo, pagina, tamanho));
    }

    @GET
    @Path("/tipo/{tipo}")
    public List<ContatoResposta> porTipo(@PathParam("tipo") String tipo,
                                         @QueryParam("pagina") @DefaultValue("0") int pagina,
                                         @QueryParam("tamanho") @DefaultValue("10") int tamanho) {
        return ContatoResposta.de(consultar.porTipo(tipo, pagina, tamanho));
    }

    @GET
    @Path("/{id}")
    public Response porId(@PathParam("id") long id) {
        return consultar.porId(id)
                .map(c -> Response.ok(ContatoResposta.de(c)).build())
                .orElseGet(() -> Response.status(Response.Status.NOT_FOUND).build());
    }

    /**
     * O contato de um e-mail.
     *
     * <p>No legado este endpoint era ambíguo por construção: devolvia UM contato sem que
     * nada garantisse existir apenas um. A restrição {@code contato_email_unico} é o que
     * transforma a pergunta em respondível.</p>
     */
    @GET
    @Path("/email/{email}")
    public Response porEmail(@PathParam("email") String email) {
        return consultar.porEmail(email)
                .map(c -> Response.ok(ContatoResposta.de(c)).build())
                .orElseGet(() -> Response.status(Response.Status.NOT_FOUND).build());
    }

    /**
     * Quem recebe o alerta de desastre deste cliente.
     *
     * <p>É a consulta que a fatia de alerta vai usar. Não é paginada de propósito: quem
     * vai ser avisado tem de ser avisado inteiro.</p>
     */
    @GET
    @Path("/emergencia/cliente/{clienteId}")
    public List<ContatoResposta> deEmergencia(@PathParam("clienteId") long clienteId) {
        return ContatoResposta.de(consultar.deEmergenciaDoCliente(clienteId));
    }

    @PUT
    @Path("/{id}")
    public ContatoResposta alterar(@PathParam("id") long id, ContatoPedido pedido) {
        return ContatoResposta.de(alterar.executar(id, pedido.ddd(), pedido.telefone(),
                pedido.celular(), pedido.whatsapp(), pedido.email(), pedido.tipoContato()));
    }

    @DELETE
    @Path("/{id}")
    public Response excluir(@PathParam("id") long id) {
        excluir.executar(id);
        return Response.noContent().build();
    }
}
