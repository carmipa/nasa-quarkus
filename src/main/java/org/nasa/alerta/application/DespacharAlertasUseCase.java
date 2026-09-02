package org.nasa.alerta.application;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;
import org.nasa.alerta.domain.Alerta;
import org.nasa.alerta.domain.ports.EnvioDeAlertaPort;
import org.nasa.alerta.domain.ports.RepositorioDeAlertasPort;
import org.nasa.core.erro.ErroDePipeline;
import org.nasa.core.erro.RegistradorDeFalha;
import org.nasa.core.log.Registro;
import org.nasa.core.tempo.Relogio;

import java.time.Duration;
import java.util.List;

/**
 * Envia os avisos que estão na fila.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO.</b> É a segunda metade do padrão <i>outbox</i>: pega o que a
 * varredura registrou como {@code PENDENTE} e tenta entregar, marcando cada linha com o
 * que aconteceu.</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO.</b></p>
 * <ol>
 *   <li><b>A falha de UM aviso não para os demais.</b> Cada um é marcado individualmente:
 *       um endereço inválido no meio da fila não pode impedir que as outras pessoas sejam
 *       avisadas de um desastre.</li>
 *   <li><b>Nenhum aviso desaparece.</b> Falhou vira {@code FALHOU} com a causa-raiz
 *       gravada — nunca some, nunca volta a {@code PENDENTE} sozinho. Voltar a pendente
 *       automaticamente criaria uma tentativa infinita sobre um erro permanente.</li>
 *   <li><b>{@code tentativas} sempre incrementa</b>, tenha dado certo ou não. É o que
 *       separa "falhou uma vez" de "falha sempre", e as duas pedem reações diferentes.</li>
 *   <li><b>O meio de entrega é DECLARADO no log.</b> Enquanto o adaptador for o de log,
 *       ninguém recebe nada — e essa frase precisa aparecer, senão "ENVIADO" na tela vira
 *       cobertura imaginária.</li>
 * </ol>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA.</b> Falha do <b>envio</b> é capturada por aviso,
 * marcada e registrada com causa-raiz. Falha do <b>banco</b> ao marcar sobe e interrompe o
 * lote — porque continuar sem conseguir gravar produziria envios cujo registro se perdeu,
 * que é exatamente o que o outbox existe para impedir.</p>
 */
@ApplicationScoped
public class DespacharAlertasUseCase {

    private static final Logger LOG = Logger.getLogger(DespacharAlertasUseCase.class);
    private static final String OPERACAO = "despachar-alertas";

    @Inject
    RepositorioDeAlertasPort repositorio;

    @Inject
    EnvioDeAlertaPort envio;

    @Inject
    Relogio relogio;

    /**
     * O que o despacho fez.
     *
     * @param tentados         quantos estavam na fila
     * @param enviados         quantos foram marcados como ENVIADO
     * @param falhos           quantos foram marcados como FALHOU
     * @param entregaDeVerdade se o meio em uso realmente entrega a alguém
     * @param meio             descrição do meio, para a tela não mentir
     * @param duracao          quanto levou
     */
    public record Resultado(int tentados, int enviados, int falhos,
                            boolean entregaDeVerdade, String meio, Duration duracao) {
    }

    public Resultado executar(int limite) {
        var inicio = relogio.agora();
        List<Alerta> fila = repositorio.pendentes(Math.max(1, limite));

        if (!envio.entregaDeVerdade() && !fila.isEmpty()) {
            // Esta linha precisa existir no log. Sem ela, "ENVIADO" na tela vira cobertura
            // imaginaria — e a descoberta viria no dia do desastre.
            LOG.warn(Registro.recusa(OPERACAO, envio.descricaoDoMeio(),
                    "MEIO_NAO_ENTREGA_DE_VERDADE"));
        }

        int enviados = 0;
        int falhos = 0;
        for (Alerta alerta : fila) {
            try {
                envio.enviar(alerta, ASSUNTO, corpoDe(alerta));
                repositorio.atualizar(alerta.entregue(relogio.agora()));
                enviados++;
            } catch (ErroDePipeline falha) {
                // A falha de UM aviso nao pode impedir os outros: um endereco invalido no
                // meio da fila deixaria as demais pessoas sem saber do desastre.
                RegistradorDeFalha.registrar(falha);
                repositorio.atualizar(alerta.falho(relogio.agora(), falha.causaRaiz().name()));
                falhos++;
            } catch (RuntimeException inesperada) {
                LOG.error(Registro.recusa(OPERACAO, String.valueOf(alerta.id()),
                        "FALHA_NAO_CLASSIFICADA"), inesperada);
                repositorio.atualizar(alerta.falho(relogio.agora(), "NAO_CLASSIFICADA"));
                falhos++;
            }
        }

        var duracao = Duration.between(inicio, relogio.agora());
        LOG.info(Registro.de(OPERACAO, envio.descricaoDoMeio(),
                "tentados=" + fila.size() + " enviados=" + enviados + " falhos=" + falhos,
                duracao));
        return new Resultado(fila.size(), enviados, falhos,
                envio.entregaDeVerdade(), envio.descricaoDoMeio(), duracao);
    }

    private static final String ASSUNTO = "Alerta de desastre natural proximo ao seu endereco";

    private static String corpoDe(Alerta a) {
        return "Um evento natural foi registrado perto de um endereco cadastrado. "
                + "Evento #" + a.eventoId() + ". Consulte o painel para os detalhes.";
    }
}
