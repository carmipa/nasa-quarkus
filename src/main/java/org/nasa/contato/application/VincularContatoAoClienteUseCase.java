package org.nasa.contato.application;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;
import org.nasa.contato.domain.Contato;
import org.nasa.contato.domain.exceptions.ContatoNaoEncontradoException;
import org.nasa.contato.domain.ports.RepositorioDeContatosPort;
import org.nasa.core.log.Registro;

/**
 * Liga um contato a um cliente.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO.</b> É este vínculo que faz o alerta existir. A varredura
 * parte dos <b>endereços do cliente</b> e caminha até os contatos dele; um contato de
 * emergência sem cliente nunca é alcançado, e portanto nunca recebe aviso — sem erro
 * nenhum, porque ele existe e está correto, só não está ligado a ninguém.</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO.</b></p>
 * <ol>
 *   <li><b>Idempotente.</b> Vincular duas vezes não duplica nem falha: repetir é o
 *       resultado normal de um clique duplo, e transformar isso em erro seria punir o
 *       reflexo humano numa rede lenta.</li>
 *   <li><b>O contato tem de existir.</b> Vincular um identificador inexistente é 404, não
 *       sucesso silencioso — que faria quem chamou acreditar que a ligação foi feita.</li>
 *   <li><b>Vincular um contato de EMERGÊNCIA é registrado em WARN.</b> É o momento em que
 *       alguém passa a receber aviso de desastre, e esse fato merece rastro — inclusive
 *       para responder depois "por que esta pessoa foi avisada?".</li>
 * </ol>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA.</b> {@link ContatoNaoEncontradoException} (404)
 * quando o contato não existe. A existência do CLIENTE é garantida pela chave estrangeira
 * do banco: inventar aqui uma consulta à fatia de cliente violaria a regra de fronteira, e
 * a proteção do banco é a que vale de qualquer forma.</p>
 */
@ApplicationScoped
public class VincularContatoAoClienteUseCase {

    private static final Logger LOG = Logger.getLogger(VincularContatoAoClienteUseCase.class);
    private static final String OPERACAO = "vincular-contato";

    @Inject
    RepositorioDeContatosPort repositorio;

    public void executar(long contatoId, long clienteId) {
        Contato contato = repositorio.porId(contatoId)
                .orElseThrow(() -> new ContatoNaoEncontradoException(String.valueOf(contatoId)));

        repositorio.vincularAoCliente(contatoId, clienteId);

        if (contato.recebeAlerta()) {
            LOG.warn(Registro.de(OPERACAO, contatoId + "->" + clienteId,
                    "contato de EMERGENCIA ligado ao cliente: passa a receber alerta de desastre"));
        } else {
            LOG.info(Registro.de(OPERACAO, contatoId + "->" + clienteId, "contato ligado ao cliente"));
        }
    }
}
