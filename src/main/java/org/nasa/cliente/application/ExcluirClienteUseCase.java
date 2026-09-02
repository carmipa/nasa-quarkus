package org.nasa.cliente.application;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;
import org.nasa.cliente.domain.Cliente;
import org.nasa.cliente.domain.exceptions.ClienteNaoEncontradoException;
import org.nasa.cliente.domain.ports.RepositorioDeClientesPort;
import org.nasa.core.log.Registro;

/**
 * Remove um cliente do cadastro.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO.</b> Pessoa que pediu para sair do sistema, cadastro
 * duplicado, teste que virou dado real. É a operação mais destrutiva da fatia: leva junto
 * os vínculos com endereço e contato, e os alertas registrados para aquela pessoa.</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO.</b></p>
 * <ol>
 *   <li><b>O cliente é lido ANTES de ser apagado</b>, e o nome dele vai para o log. Sem
 *       isso, a auditoria fica com "apagado id=42" e ninguém consegue dizer <b>quem</b>
 *       era — que é exatamente a pergunta que se faz quando alguém apaga o errado.</li>
 *   <li><b>Apagar o que não existe é 404</b>, não sucesso silencioso. "Apaguei" e "não
 *       havia nada" precisam ser distinguíveis: a segunda resposta pode significar que o
 *       operador está olhando para a tela errada.</li>
 *   <li><b>A cascata é do banco</b> ({@code ON DELETE CASCADE} nas tabelas de junção),
 *       não um laço em Java: laço deixa órfão quando falha no meio.</li>
 * </ol>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA.</b> Cliente inexistente ⇒
 * {@link ClienteNaoEncontradoException} (404). Falha do banco sobe com causa-raiz. Não há
 * exclusão parcial: ou a linha e seus vínculos saem juntos, ou nada sai.</p>
 *
 * <p><b>A defesa contra apagar o errado é da INTERFACE</b>, e está declarada aqui de
 * propósito: a confirmação da tela nomeia a pessoa ({@code hx-confirm="Apagar Ana
 * Souza?"}), nunca um "Tem certeza?" genérico. Recurso errado escolhido por semelhança
 * visual é o dano de boa-fé mais comum que existe, e nenhuma validação de servidor o
 * impede — quem clicou tinha permissão e quis clicar.</p>
 */
@ApplicationScoped
public class ExcluirClienteUseCase {

    private static final Logger LOG = Logger.getLogger(ExcluirClienteUseCase.class);

    @Inject
    RepositorioDeClientesPort repositorio;

    public void executar(long id) {
        Cliente alvo = repositorio.porId(id)
                .orElseThrow(() -> new ClienteNaoEncontradoException("id=" + id));

        boolean removeu = repositorio.remover(id);
        if (!removeu) {
            // Corrida: alguém apagou entre a leitura e a remoção. Não é erro do sistema,
            // é o mesmo 404 — e o log registra que houve a corrida.
            LOG.warn(Registro.recusa("excluir-cliente", String.valueOf(id), "JA_REMOVIDO"));
            throw new ClienteNaoEncontradoException("id=" + id);
        }
        LOG.info(Registro.de("excluir-cliente", String.valueOf(id),
                "cliente removido: " + alvo.nomeCompleto()));
    }
}
