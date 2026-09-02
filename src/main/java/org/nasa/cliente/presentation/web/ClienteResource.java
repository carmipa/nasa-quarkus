package org.nasa.cliente.presentation.web;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.nasa.cliente.application.AlterarClienteUseCase;
import org.nasa.cliente.application.CadastrarClienteUseCase;
import org.nasa.cliente.application.ConsultarClientesUseCase;
import org.nasa.cliente.application.ExcluirClienteUseCase;
import org.nasa.cliente.domain.Cliente;

import java.util.List;

/**
 * A API do cadastro de clientes.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO.</b> Reproduz as sete operações que o legado expunha em
 * {@code /api/clientes}, agora com as invariantes protegidas no banco e as recusas
 * traduzidas para status que dizem o que aconteceu.</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO.</b></p>
 * <ol>
 *   <li><b>A borda só traduz.</b> Nenhuma regra aqui: validação, unicidade e existência
 *       são dos casos de uso e do banco. Esconder botão não é autorização; validar no
 *       cliente não é regra de negócio.</li>
 *   <li><b>Cada recusa tem seu status.</b> 400 para dado inválido, 404 para inexistente,
 *       409 para conflito de documento. Devolver 500 para tudo faz o painel de erro
 *       contar digitação errada junto com queda de banco, e o número perde o sentido.</li>
 *   <li><b>Sem {@code @Transactional} na classe</b>, e é decisão declarada: cada operação
 *       aqui é uma única consulta ou uma única escrita, e o adaptador abre e fecha a
 *       conexão. A anotação passa a ser obrigatória quando um caso de uso fizer duas
 *       escritas que precisam entrar juntas — o que acontece na fatia de alerta.</li>
 * </ol>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA.</b> As exceções sobem e são traduzidas pelo
 * mapeador da borda, que registra log e telemetria <b>uma vez</b>, no ponto em que a
 * falha venceu.</p>
 */
@Path("/api/clientes")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ClienteResource {

    @Inject
    CadastrarClienteUseCase cadastrar;

    @Inject
    ConsultarClientesUseCase consultar;

    @Inject
    AlterarClienteUseCase alterar;

    @Inject
    ExcluirClienteUseCase excluir;

    @GET
    public List<ClienteResposta> listar(@QueryParam("pagina") int pagina,
                                        @QueryParam("tamanho") int tamanho) {
        return consultar.listar(pagina, tamanho).stream().map(ClienteResposta::de).toList();
    }

    @GET
    @Path("/{id}")
    public ClienteResposta porId(@PathParam("id") long id) {
        return ClienteResposta.de(consultar.exigirPorId(id));
    }

    @GET
    @Path("/documento/{documento}")
    public Response porDocumento(@PathParam("documento") String documento) {
        return consultar.porDocumento(documento)
                .map(c -> Response.ok(ClienteResposta.de(c)).build())
                // Ausência aqui é 404 e não lista vazia: a rota promete UM cliente.
                .orElseGet(() -> Response.status(Response.Status.NOT_FOUND).build());
    }

    @GET
    @Path("/pesquisar")
    public List<ClienteResposta> pesquisar(@QueryParam("termo") String termo,
                                           @QueryParam("pagina") int pagina,
                                           @QueryParam("tamanho") int tamanho) {
        return consultar.pesquisar(termo, pagina, tamanho).stream().map(ClienteResposta::de).toList();
    }

    @POST
    public Response cadastrar(ClientePedido pedido) {
        Cliente novo = cadastrar.executar(pedido.nome(), pedido.sobrenome(),
                pedido.dataNascimentoComoData(), pedido.documento());
        // 201 com Location: o padrão para criação, e é o que permite ao cliente HTTP
        // seguir direto para o recurso novo sem adivinhar a URL.
        return Response.created(java.net.URI.create("/api/clientes/" + novo.id()))
                .entity(ClienteResposta.de(novo))
                .build();
    }

    @PUT
    @Path("/{id}")
    public ClienteResposta alterar(@PathParam("id") long id, ClientePedido pedido) {
        return ClienteResposta.de(alterar.executar(id, pedido.nome(), pedido.sobrenome(),
                pedido.dataNascimentoComoData(), pedido.documento()));
    }

    @DELETE
    @Path("/{id}")
    public Response excluir(@PathParam("id") long id) {
        excluir.executar(id);
        return Response.noContent().build();
    }
}
