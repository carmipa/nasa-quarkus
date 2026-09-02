package org.nasa.contato.application;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;
import org.nasa.contato.domain.Contato;
import org.nasa.contato.domain.exceptions.ContatoNaoEncontradoException;
import org.nasa.contato.domain.ports.RepositorioDeContatosPort;
import org.nasa.core.log.Registro;

/**
 * Exclui um contato.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO.</b> Tirar um canal de aviso do ar. Se o contato era de
 * EMERGENCIA, alguém deixa de ser avisado de desastres a partir de agora — e essa é a
 * consequência que precisa ficar registrada.</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO.</b></p>
 * <ol>
 *   <li><b>Excluir o que não existe é 404, não sucesso silencioso.</b> Devolver "ok" para
 *       um identificador inexistente faz quem chamou acreditar que apagou algo — e a
 *       segunda tentativa, com o identificador certo, nunca acontece.</li>
 *   <li><b>A perda de cobertura de alerta é registrada em WARN.</b> Excluir um contato de
 *       emergência não é erro, mas é o tipo de mudança cuja consequência só aparece
 *       muito depois, quando o aviso não chega.</li>
 * </ol>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA.</b> {@link ContatoNaoEncontradoException} (404)
 * quando não existe. Falha de banco sobe como 500 com causa-raiz.</p>
 */
@ApplicationScoped
public class ExcluirContatoUseCase {

    private static final Logger LOG = Logger.getLogger(ExcluirContatoUseCase.class);
    private static final String OPERACAO = "excluir-contato";

    @Inject
    RepositorioDeContatosPort repositorio;

    public void executar(long id) {
        Contato alvo = repositorio.porId(id)
                .orElseThrow(() -> new ContatoNaoEncontradoException(String.valueOf(id)));

        if (!repositorio.remover(id)) {
            // Existia na leitura e sumiu antes da exclusao: outra requisicao chegou
            // primeiro. O resultado desejado ja aconteceu, mas quem chamou precisa saber
            // que nao foi ele.
            LOG.warn(Registro.recusa(OPERACAO, String.valueOf(id), "SUMIU_ENTRE_LER_E_EXCLUIR"));
            throw new ContatoNaoEncontradoException(String.valueOf(id));
        }

        if (alvo.recebeAlerta()) {
            LOG.warn(Registro.de(OPERACAO, String.valueOf(id),
                    "contato de EMERGENCIA excluido: este canal deixa de receber alerta"));
        } else {
            LOG.info(Registro.de(OPERACAO, String.valueOf(id), "contato excluido"));
        }
    }
}
