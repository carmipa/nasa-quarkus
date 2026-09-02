package org.nasa.endereco.presentation.web;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.nasa.endereco.application.ConsultarCepUseCase;
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
public class EnderecoResource {

    @Inject
    ConsultarCepUseCase consultarCep;

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
