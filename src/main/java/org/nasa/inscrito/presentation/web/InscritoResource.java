package org.nasa.inscrito.presentation.web;

import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.nasa.inscrito.application.ConsultarInscritosUseCase;
import org.nasa.inscrito.application.InscreverUseCase;
import org.nasa.inscrito.domain.Inscrito;

import java.util.List;

/**
 * A API de inscrições.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO.</b> Fecha a superfície REST do sistema: {@code evento} e
 * {@code alerta} já a tinham, e a falta desta era uma inconsistência — quem integra
 * consegue ler eventos e alertas por API, mas precisaria de um formulário HTML para
 * inscrever alguém.</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO.</b></p>
 * <ol>
 *   <li><b>O e-mail NÃO aparece na listagem.</b> É dado pessoal, e uma API pública que
 *       devolve a lista de e-mails inscritos é uma lista de e-mails para spam servida de
 *       bandeja. A listagem mostra id, nome, cidade aproximada e estado — o suficiente para
 *       operar, e insuficiente para colher.</li>
 *   <li><b>E-mail repetido é 409</b>, não 500: a pessoa já está inscrita e o sistema
 *       funcionou. O status é o que integrações leem.</li>
 *   <li><b>Cancelar é DELETE e é idempotente</b>: cancelar duas vezes devolve 204 nas duas,
 *       porque o resultado pedido — "esta inscrição não recebe mais alerta" — foi alcançado
 *       nas duas. Devolver 404 na segunda faria um cliente que repete por timeout tratar
 *       sucesso como erro.</li>
 * </ol>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA.</b> Dado inválido é 400 pelo mapeador de borda, com
 * o campo nomeado. Nenhuma exceção vaza com rastro de pilha.</p>
 */
@Path("/api/inscritos")
@Produces(MediaType.APPLICATION_JSON)
public class InscritoResource {

    @Inject
    InscreverUseCase inscrever;

    @Inject
    ConsultarInscritosUseCase consultar;

    /**
     * O que a API devolve de uma inscrição.
     *
     * <p><b>Sem e-mail e sem telefone, deliberadamente.</b> Uma API que lista os contatos
     * dos inscritos é uma lista de contatos para spam. O que fica é o que permite operar:
     * quem é, se recebe alerta, e onde aproximadamente.</p>
     *
     * @param id          identificador
     * @param nome        como a pessoa se identificou
     * @param cep         o CEP informado — é público por natureza, identifica uma região,
     *                    não uma pessoa
     * @param raioKm      a que distância ela quer ser avisada
     * @param recebeAlerta se ela está ativa E tem coordenada. <b>Os dois estados de "não"
     *                    são diferentes</b>, e o campo abaixo separa
     * @param situacao    {@code ATIVA}, {@code SEM_POSICAO} ou {@code CANCELADA}
     */
    public record InscritoResposta(long id, String nome, String cep, double raioKm,
                                   boolean recebeAlerta, String situacao) {

        static InscritoResposta de(Inscrito i) {
            String situacao = !i.ativo() ? "CANCELADA"
                    : i.coordenada() == null ? "SEM_POSICAO" : "ATIVA";
            return new InscritoResposta(i.id(), i.nome(), i.cep().digitos(), i.raioKm(),
                    i.recebeAlertaDeProximidade(), situacao);
        }
    }

    /** O que se manda para inscrever. */
    public record InscricaoPedido(String nome, String email, String telefone, String cep,
                                  Double raioKm) {
    }

    @GET
    public List<InscritoResposta> listar(@QueryParam("pagina") @DefaultValue("0") int pagina,
                                         @QueryParam("tamanho") @DefaultValue("20") int tamanho) {
        return consultar.listar(pagina, tamanho).stream()
                .map(InscritoResposta::de)
                .toList();
    }

    @GET
    @Path("/{id}")
    public InscritoResposta porId(@PathParam("id") long id) {
        return InscritoResposta.de(consultar.exigirPorId(id));
    }

    /**
     * Inscreve.
     *
     * <p><b>201 com o cabeçalho {@code Location}</b> — é o que permite a quem integrou saber
     * onde a inscrição ficou sem adivinhar a URL.</p>
     */
    @POST
    @Transactional
    public Response inscrever(InscricaoPedido pedido) {
        var r = inscrever.executar(pedido.nome(), pedido.email(), pedido.telefone(),
                pedido.cep(), pedido.raioKm());
        var corpo = InscritoResposta.de(r.inscrito());
        return Response.created(java.net.URI.create("/api/inscritos/" + r.inscrito().id()))
                .entity(corpo)
                .build();
    }

    /**
     * Cancela — e é IDEMPOTENTE.
     *
     * <p>Cancelar duas vezes devolve 204 nas duas. O resultado pedido ("esta inscrição não
     * recebe mais alerta") foi alcançado nas duas, e devolver 404 na segunda faria um
     * cliente que repete por timeout tratar sucesso como erro — o erro de boa-fé mais comum
     * numa integração.</p>
     */
    @DELETE
    @Path("/{id}")
    @Transactional
    public Response cancelar(@PathParam("id") long id) {
        // `exigirPorId` primeiro: id que NUNCA existiu e 404, e isso e diferente de
        // "ja estava cancelada". O primeiro e engano de quem chama; o segundo e repeticao.
        consultar.exigirPorId(id);
        consultar.cancelar(id);
        return Response.noContent().build();
    }
}
