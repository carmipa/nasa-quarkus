package org.nasa.telemetria.infrastructure.adapters;

import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;
import org.nasa.core.erro.ErroDePipeline;
import org.nasa.core.log.Registro;
import org.nasa.core.telemetria.Telemetria;

/**
 * Leva o que está acumulado em memória para o banco, de tempos em tempos.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO.</b> É o que faz a telemetria sobreviver a reinício — e
 * reinício é o que acontece logo depois de um incidente, que é quando se quer ver o
 * histórico. Sem esta classe, o coletor seria um contador que zera no pior momento.</p>
 *
 * <p><b>POR QUE PERIÓDICO, E NÃO A CADA MEDIÇÃO.</b> Uma gravação por operação medida
 * poria latência de banco dentro de cada chamada observada. Telemetria é apoio: apoio que
 * cobra pedágio da função que observa acaba desligado no dia em que o sistema fica lento —
 * que é o dia em que ela mais serve.</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO.</b></p>
 * <ol>
 *   <li><b>Falha de descarga NUNCA sobe.</b> O agendador continua, e a próxima tentativa
 *       leva o que aparecer depois. Uma exceção que escapa daqui derruba o agendador
 *       inteiro do Quarkus — e junto dele qualquer outra tarefa agendada.</li>
 *   <li><b>Descarrega também no DESLIGAMENTO.</b> Sem isso, o último minuto de medição se
 *       perde em todo restart — e restart é frequente em desenvolvimento e obrigatório em
 *       deploy, que são justamente os momentos que se quer poder comparar.</li>
 *   <li><b>Nada a gravar não é erro nem log.</b> Um sistema parado descarrega vazio de
 *       minuto em minuto; registrar isso encheria o log de "nada aconteceu" e treinaria
 *       quem lê a ignorar a linha.</li>
 * </ol>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA.</b> Banco fora ⇒ WARN com causa-raiz, e as medidas
 * <b>daquele ciclo se perdem</b> — declarado, não escondido: elas já foram retiradas da
 * memória quando a gravação falhou. A alternativa (devolvê-las à memória) faria a memória
 * crescer sem limite enquanto o banco estivesse fora, trocando perda de telemetria por
 * queda da aplicação.</p>
 */
@ApplicationScoped
public class DescarregadorDeTelemetria {

    private static final Logger LOG = Logger.getLogger(DescarregadorDeTelemetria.class);
    private static final String OPERACAO = "descarregar-telemetria";

    @Inject
    Telemetria telemetria;

    @Inject
    RepositorioDeTelemetria repositorio;

    /**
     * De minuto em minuto.
     *
     * <p>Um minuto é o intervalo em que a perda máxima (um ciclo) ainda é pequena e a
     * carga no banco continua desprezível: são poucas linhas somadas, não uma por chamada.
     * Menos que isso não melhora nada — o agregado é por HORA, então descarregar de
     * segundo em segundo produziria as mesmas linhas com mais viagens.</p>
     */
    @Scheduled(every = "60s", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void descarregarPeriodicamente() {
        descarregar("agendado");
    }

    /** No desligamento, para não perder o último ciclo. */
    void aoDesligar(@Observes ShutdownEvent evento) {
        descarregar("desligamento");
    }

    /**
     * A descarga.
     *
     * <p>O {@code catch} pega {@link ErroDePipeline} <b>e</b> {@link RuntimeException}: o
     * primeiro é a falha esperada de banco; o segundo é tudo o que não se previu. Deixar o
     * segundo escapar derrubaria o agendador do Quarkus inteiro, e a próxima falha seria
     * "as tarefas agendadas pararam", que manda investigar o lugar errado.</p>
     */
    private void descarregar(String motivo) {
        try {
            var medidas = telemetria.retirarTudo();
            if (medidas.isEmpty()) {
                // Nada a gravar nao e erro nem log: um sistema parado descarregaria
                // "nada aconteceu" de minuto em minuto, e quem le aprenderia a ignorar.
                return;
            }
            int linhas = repositorio.somar(medidas);
            LOG.debug(Registro.de(OPERACAO, motivo, linhas + " linha(s) somada(s)"));
        } catch (ErroDePipeline falhou) {
            // As medidas deste ciclo se PERDEM, e isso e declarado. Devolve-las a memoria
            // faria ela crescer sem limite enquanto o banco estivesse fora — trocaria
            // perda de telemetria por queda da aplicacao.
            LOG.warn(Registro.recusa(OPERACAO, motivo, "CICLO_PERDIDO_" + falhou.causaRaiz()));
        } catch (RuntimeException inesperado) {
            LOG.warn(Registro.recusa(OPERACAO, motivo, "CICLO_PERDIDO_INESPERADO"), inesperado);
        }
    }
}
