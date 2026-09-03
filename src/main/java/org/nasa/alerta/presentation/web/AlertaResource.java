package org.nasa.alerta.presentation.web;

import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.nasa.alerta.application.MontarAlertaUseCase;
import org.nasa.alerta.domain.MensagemDeAlerta;

import java.util.List;

/**
 * A API de alerta — monta a mensagem e não guarda nada.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO.</b> É a mesma função da tela, em JSON: dado um CEP e um raio,
 * quais desastres a NASA publicou por perto e a que distância. Serve a quem quer integrar o
 * alerta em outro sistema sem raspar HTML.</p>
 *
 * <p><b>É POST, e não GET, mesmo sem escrever nada.</b> O e-mail vai no corpo; num GET ele
 * iria na URL — onde fica no log de acesso do servidor, no histórico do cliente e no
 * cabeçalho {@code Referer}. Três lugares onde um endereço de e-mail não deveria estar.
 * <b>Idempotência não é o critério aqui; onde o dado sensível trafega é.</b></p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO.</b></p>
 * <ol>
 *   <li><b>O e-mail é OPCIONAL nesta API.</b> Na tela ele serve para a pessoa ver a mensagem
 *       endereçada a ela; numa integração isso não tem uso, e exigi-lo forçaria quem integra
 *       a inventar um endereço — que é pior que não ter.</li>
 *   <li><b>A resposta NÃO ecoa o e-mail recebido.</b> Devolver o que chegou faz o endereço
 *       aparecer no log de quem chamou, e ele já o tem.</li>
 *   <li><b>Nada é gravado.</b> Nem o e-mail, nem o CEP, nem a consulta.</li>
 * </ol>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA.</b> CEP inexistente ou sem posição vira 404 pelo
 * mapeador de borda, com a explicação — nunca uma lista vazia, que afirmaria "não há
 * desastre perto" quando o que houve foi não saber onde é.</p>
 */
@Path("/api/alertas")
@Produces(MediaType.APPLICATION_JSON)
public class AlertaResource {

    @Inject
    MontarAlertaUseCase montar;

    /**
     * O que se manda.
     *
     * @param email  opcional; só a tela precisa dele
     * @param cep    obrigatório, só dígitos
     * @param raioKm nulo usa o padrão de 100 km
     * @param dias   nulo usa o padrão de 30 dias
     */
    public record ConsultaPedido(String email, String cep, Double raioKm, Integer dias) {
    }

    /**
     * O que se recebe.
     *
     * @param assunto     a linha que a mensagem teria
     * @param onde        o lugar de onde se mediu, para conferência
     * @param raioKm      o raio efetivamente usado, já limitado à faixa aceita
     * @param quantos     quantos desastres entraram
     * @param desastres   do mais próximo ao mais distante
     * @param montadaEm   quando, em UTC — a resposta tem validade
     * @param guardado    sempre {@code false}. Está aqui de propósito: quem integra precisa
     *                    poder afirmar, no próprio contrato, que nada foi persistido
     */
    public record ConsultaResposta(String assunto, LocalResposta onde, double raioKm,
                                   int quantos, List<DesastreResposta> desastres,
                                   String montadaEm, boolean guardado) {

        static ConsultaResposta de(MensagemDeAlerta m) {
            return new ConsultaResposta(
                    m.assunto(),
                    new LocalResposta(m.ondeVoceEsta().cep(), m.ondeVoceEsta().descricao(),
                            m.ondeVoceEsta().coordenada().latitude(),
                            m.ondeVoceEsta().coordenada().longitude()),
                    m.raioKm(),
                    m.desastres().size(),
                    m.desastres().stream().map(DesastreResposta::de).toList(),
                    m.montadaEm().toString(),
                    false);
        }
    }

    /** De onde se mediu. */
    public record LocalResposta(String cep, String descricao, double latitude,
                                double longitude) {
    }

    /**
     * Um desastre próximo.
     *
     * @param distanciaKm a <b>geodésica</b>, medida sobre a curvatura da Terra — não a de
     *                    uma caixa no mapa. O canto de uma caixa de 100 km fica a 141 km
     */
    public record DesastreResposta(String eonetId, String titulo, String categoria,
                                   String ocorridoEm, double latitude, double longitude,
                                   double distanciaKm, boolean ativo) {

        static DesastreResposta de(org.nasa.alerta.domain.DesastreProximo d) {
            return new DesastreResposta(d.eonetId(), d.titulo(), d.categoria(),
                    d.ocorridoEm().toString(), d.coordenada().latitude(),
                    d.coordenada().longitude(),
                    // Uma casa decimal: km com seis decimais e falsa precisao, porque a
                    // propria coordenada da NASA nao tem essa resolucao.
                    Math.round(d.distanciaKm() * 10) / 10.0,
                    d.ativo());
        }
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    public ConsultaResposta consultar(ConsultaPedido pedido) {
        return ConsultaResposta.de(montar.executar(
                // O e-mail e OPCIONAL aqui. Numa integracao ele nao tem uso, e exigi-lo
                // forcaria quem integra a inventar um endereco — pior que nao ter.
                pedido.email() == null || pedido.email().isBlank()
                        ? "integracao@exemplo.invalid" : pedido.email(),
                pedido.cep(), pedido.raioKm(), pedido.dias()));
    }
}
