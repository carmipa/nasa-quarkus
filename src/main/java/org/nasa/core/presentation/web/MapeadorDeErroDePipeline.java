package org.nasa.core.presentation.web;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import org.nasa.core.erro.CausaRaiz;
import org.nasa.core.erro.ErroDePipeline;
import org.nasa.core.erro.RegistradorDeFalha;

/**
 * Traduz toda falha do sistema para o status HTTP que diz o que aconteceu — e registra
 * o incidente <b>uma vez</b>.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO.</b> Sem este mapeador, toda recusa vira 500. O efeito não é
 * cosmético: o painel de erro passa a contar digitação errada junto com queda de banco, e
 * o número de incidentes perde o sentido — ninguém consegue dizer se o sistema está mal
 * ou se as pessoas estão errando o CPF. Além disso, um cliente HTTP não tem como decidir
 * se vale a pena repetir a requisição: 409 nunca vai melhorar sozinho, 503 talvez.</p>
 *
 * <p><b>POR QUE O REGISTRO ACONTECE AQUI.</b> Este é o ponto em que a falha <b>venceu</b>:
 * ninguém mais vai tratá-la, e ela está virando resposta. Registrar antes — no construtor
 * da exceção, por exemplo — contaria o mesmo incidente a cada reembrulho e marcaria ERROR
 * em exceção que alguém capturou e resolveu.</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO.</b></p>
 * <ol>
 *   <li><b>Cada causa-raiz tem um status</b>, e a tabela é explícita — não há
 *       {@code default} silencioso que jogue tudo em 500 sem ninguém decidir.</li>
 *   <li><b>A resposta NUNCA carrega detalhe técnico.</b> Vai a mensagem de negócio e a
 *       causa-raiz; rastro de pilha, SQL e caminho de arquivo ficam no log. Mensagem de
 *       erro é superfície: caminho absoluto nela vaza a estrutura da máquina.</li>
 *   <li><b>4xx é recusa esperada, 5xx é defeito.</b> Só o segundo grupo conta como
 *       incidente do sistema.</li>
 * </ol>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA.</b> Este mapeador não falha: recebe uma exceção
 * que já carrega tudo de que precisa. Exceção que <b>não</b> desce de
 * {@link ErroDePipeline} não passa por aqui — e a catraca
 * {@code CatracaExcecaoEspecificaTest} garante que ela não existe.</p>
 */
@Provider
public class MapeadorDeErroDePipeline implements ExceptionMapper<ErroDePipeline> {

    /** O corpo da resposta de erro. Negócio, nunca detalhe técnico. */
    public record Problema(String erro, String causa, String alvo, String operacao) {
    }

    @Override
    public Response toResponse(ErroDePipeline falha) {
        // Log + telemetria, uma vez, no ponto em que a falha venceu.
        RegistradorDeFalha.registrar(falha);

        Response.Status status = statusPara(falha.causaRaiz());
        return Response.status(status)
                .type(MediaType.APPLICATION_JSON)
                .entity(new Problema(falha.getMessage(), falha.causaRaiz().name(),
                        falha.alvo(), falha.operacao()))
                .build();
    }

    /**
     * A tabela de tradução — explícita, sem {@code default} que esconda decisão.
     *
     * <p>Um {@code default} genérico faria toda causa nova cair em 500 sem que ninguém
     * escolhesse isso; aqui, causa nova exige uma linha e, portanto, uma decisão.</p>
     */
    static Response.Status statusPara(CausaRaiz causa) {
        return switch (causa) {
            // Recusa esperada: o pedido está errado, e quem pediu consegue corrigir.
            case DADO_INVALIDO -> Response.Status.BAD_REQUEST;
            case DADO_AUSENTE -> Response.Status.NOT_FOUND;
            case CONFLITO_DE_ESTADO -> Response.Status.CONFLICT;

            // Indisponibilidade: pode melhorar sozinha, e repetir faz sentido.
            case PROVEDOR_INDISPONIVEL, TEMPO_ESGOTADO, CONCORRENCIA, INTERROMPIDO ->
                    Response.Status.SERVICE_UNAVAILABLE;
            case PROVEDOR_RECUSOU -> Response.Status.BAD_GATEWAY;

            // Defeito nosso: nada que o cliente faça muda o resultado.
            case CONFIGURACAO_AUSENTE, PERSISTENCIA_FALHOU, ARQUIVO_CORROMPIDO,
                 ARQUIVO_INACESSIVEL, NAO_CLASSIFICADA -> Response.Status.INTERNAL_SERVER_ERROR;
        };
    }
}
