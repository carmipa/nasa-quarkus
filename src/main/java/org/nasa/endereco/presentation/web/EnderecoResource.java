package org.nasa.endereco.presentation.web;

import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.nasa.endereco.application.ConsultarCepUseCase;
import org.nasa.endereco.application.CadastrarEnderecoUseCase;
import org.nasa.endereco.domain.Endereco;
import org.nasa.endereco.domain.ports.RepositorioDeEnderecosPort;

import java.net.URI;
import java.util.List;
import org.nasa.endereco.domain.ports.ConsultaCepPort;

/**
 * A API de endereços.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO.</b> Reproduz o {@code /api/enderecos/consultar-cep/{cep}} do
 * legado, com uma diferença que o operador percebe: a resposta diz <b>se aquele endereço
 * participa do alerta de proximidade</b>. No legado, endereço sem coordenada era salvo
 * exatamente como os outros, e ninguém descobria que ele nunca geraria aviso.</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO.</b></p>
 * <ol>
 *   <li><b>CEP inexistente é 404; provedor fora é 503.</b> São coisas diferentes e o
 *       operador reage de forma diferente a cada uma: no primeiro caso ele confere o que
 *       digitou, no segundo ele tenta de novo. Devolver 404 quando o provedor caiu faz
 *       ele apagar um CEP que estava certo.</li>
 *   <li><b>A resposta declara o estado da coordenada</b>, com o motivo. "Sem coordenada"
 *       e "não procurei" não podem produzir a mesma tela.</li>
 * </ol>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA.</b> As exceções sobem para o mapeador da borda,
 * que traduz por causa-raiz e registra log e telemetria uma vez.</p>
 */
@Path("/api/enderecos")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Transactional
public class EnderecoResource {

    @Inject
    ConsultarCepUseCase consultarCep;

    @Inject
    CadastrarEnderecoUseCase cadastrar;

    @Inject
    RepositorioDeEnderecosPort repositorio;

    /**
     * O endereço de um CEP.
     *
     * @return 200 com o endereço, ou 404 quando o CEP não existe em nenhum provedor
     */
    @GET
    @Path("/consultar-cep/{cep}")
    public Response consultarCep(@PathParam("cep") String cep) {
        return consultarCep.executar(cep)
                .map(e -> Response.ok(CepResposta.de(e)).build())
                .orElseGet(() -> Response.status(Response.Status.NOT_FOUND)
                        .entity(new CepNaoEncontrado(cep,
                                "nenhum provedor conhece este CEP"))
                        .build());
    }

    /**
     * Cadastra um endereço, preenchendo pelo CEP o que não foi digitado.
     *
     * <p>Se {@code clienteId} vier, o endereço já fica ligado ao cliente — que é o que o
     * torna elegível ao alerta de proximidade.</p>
     */
    @POST
    public Response cadastrar(EnderecoPedido pedido) {
        Endereco criado = cadastrar.executar(pedido.cep(), pedido.numero(), pedido.logradouro(),
                pedido.bairro(), pedido.localidade(), pedido.uf(), pedido.complemento(),
                pedido.clienteId());
        return Response.created(URI.create("/api/enderecos/" + criado.id()))
                .entity(EnderecoResposta.de(criado)).build();
    }

    @GET
    public List<EnderecoResposta> listar(@QueryParam("pagina") @DefaultValue("0") int pagina,
                                         @QueryParam("tamanho") @DefaultValue("20") int tamanho) {
        return repositorio.listar(Math.max(0, pagina), Math.min(Math.max(1, tamanho), 100))
                .stream().map(EnderecoResposta::de).toList();
    }

    @GET
    @Path("/{id}")
    public Response porId(@PathParam("id") long id) {
        return repositorio.porId(id)
                .map(e -> Response.ok(EnderecoResposta.de(e)).build())
                .orElseGet(() -> Response.status(Response.Status.NOT_FOUND).build());
    }

    /** Os endereços de um cliente — a base do alerta de proximidade dele. */
    @GET
    @Path("/cliente/{clienteId}")
    public List<EnderecoResposta> doCliente(@PathParam("clienteId") long clienteId) {
        return repositorio.doCliente(clienteId).stream().map(EnderecoResposta::de).toList();
    }

    @POST
    @Path("/{id}/vincular/{clienteId}")
    public Response vincular(@PathParam("id") long id, @PathParam("clienteId") long clienteId) {
        repositorio.vincularAoCliente(id, clienteId);
        return Response.noContent().build();
    }

    @DELETE
    @Path("/{id}")
    public Response excluir(@PathParam("id") long id) {
        return repositorio.remover(id)
                ? Response.noContent().build()
                : Response.status(Response.Status.NOT_FOUND).build();
    }

    /**
     * O endereço como a API recebe.
     *
     * <p>Só o CEP é obrigatório: o resto o próprio CEP preenche, e o que for digitado
     * prevalece — quem corrige o nome da rua sabe algo que a base do CEP ainda não sabe.</p>
     */
    public record EnderecoPedido(String cep, Integer numero, String logradouro, String bairro,
                                 String localidade, String uf, String complemento,
                                 Long clienteId) {
    }

    /**
     * O endereço como a API devolve.
     *
     * <p>Com {@code participaDoAlertaDeProximidade} e o motivo — 1 de cada 6 CEPs medidos
     * volta sem coordenada, e um endereço assim nunca gera aviso. No legado esse silêncio
     * era total.</p>
     */
    public record EnderecoResposta(Long id, String cep, String cepFormatado, Integer numero,
                                   String logradouro, String bairro, String localidade,
                                   String uf, String complemento, Double latitude,
                                   Double longitude, boolean participaDoAlertaDeProximidade,
                                   String motivoSemCoordenada, String criadoEm) {

        static EnderecoResposta de(Endereco e) {
            boolean tem = e.coordenada().isPresent();
            return new EnderecoResposta(e.id(), e.cep().digitos(), e.cep().formatado(),
                    e.numero(), e.logradouro(), e.bairro(), e.localidade(), e.uf(),
                    e.complemento(),
                    tem ? e.coordenada().get().latitude() : null,
                    tem ? e.coordenada().get().longitude() : null,
                    tem, tem ? null : "sem coordenada: este endereco NAO entra no alerta "
                            + "de proximidade",
                    e.criadoEm() == null ? null : e.criadoEm().toString());
        }
    }

    /** Corpo do 404: diz o que aconteceu, e não uma página vazia. */
    public record CepNaoEncontrado(String cep, String motivo) {
    }

    /**
     * O endereço como a API devolve.
     *
     * <p><b>O campo que não existia no legado:</b> {@code participaDoAlertaDeProximidade}.
     * Sem ele, a tela não tem como avisar que aquele endereço nunca vai gerar alerta — e o
     * silêncio aqui é o pior tipo, porque a pessoa acha que está coberta.</p>
     */
    public record CepResposta(String cep, String cepFormatado, String logradouro, String bairro,
                              String localidade, String uf, Double latitude, Double longitude,
                              boolean participaDoAlertaDeProximidade, String motivoSemCoordenada,
                              String provedor) {

        static CepResposta de(ConsultaCepPort.EnderecoDoCep e) {
            boolean tem = e.coordenada().isPresent();
            return new CepResposta(
                    e.cep().digitos(),
                    e.cep().formatado(),
                    e.logradouro(),
                    e.bairro(),
                    e.localidade(),
                    e.uf(),
                    tem ? e.coordenada().get().latitude() : null,
                    tem ? e.coordenada().get().longitude() : null,
                    tem,
                    tem ? null : "coordenada nao encontrada: este endereco nao entra no "
                            + "alerta de proximidade",
                    e.provedor());
        }
    }
}
