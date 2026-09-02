package org.nasa.endereco.domain.ports;

import org.nasa.core.telemetria.ContadorDeCausaRaiz;
import org.nasa.endereco.domain.TelemetriaEndereco;

import java.util.Optional;

/**
 * Porta de saída da telemetria da fatia {@code endereco}.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO.</b> É o que permite a fatia medir tudo <b>sem importar uma
 * fatia de telemetria</b> — a aresta que a guarda de fronteira proíbe. A fatia declara a
 * porta no próprio domínio e implementa em {@code infrastructure/telemetria}; é assim que
 * "zero aresta funcional de saída" deixa de ser slogan (§10.2 da planta).</p>
 *
 * <p><b>Por que a porta nasce agora, antes do primeiro caso de uso.</b> A planta é
 * explícita (§1-bis, Passo 7): a porta é <b>grátis</b> neste momento e vira refatoração
 * depois. Quem começa medindo só o que agiu descobre tarde demais que não sabe distinguir
 * <i>"não havia trabalho"</i> de <i>"eu estava cego"</i>.</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO.</b></p>
 * <ol>
 *   <li><b>A porta não conhece formato nem disco.</b> Nada aqui menciona JSON, arquivo ou
 *       banco — quem conhece é o adaptador.</li>
 *   <li><b>Registrar nunca falha para quem chama.</b> Telemetria é apoio, não função:
 *       uma medição quebrada não pode derrubar a operação que ela estava medindo.</li>
 *   <li><b>Contar causa-raiz vem de {@link ContadorDeCausaRaiz}</b>, que é do kernel — é o
 *       que deixa {@code RegistradorDeFalha} registrar qualquer falha desta fatia sem
 *       conhecê-la.</li>
 * </ol>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA.</b> {@link #ultimo(String)} devolve
 * {@link Optional#empty()} quando não há registro — ausência é ausência, não zero.
 * Distinguir os dois é o ponto inteiro desta camada.</p>
 */
public interface TelemetriaEnderecoPort extends ContadorDeCausaRaiz {

    /** Grava o registro da execução. Não lança. */
    void registrar(TelemetriaEndereco evento);

    /** O último registro daquela operação, se houver. */
    Optional<TelemetriaEndereco> ultimo(String operacao);
}
