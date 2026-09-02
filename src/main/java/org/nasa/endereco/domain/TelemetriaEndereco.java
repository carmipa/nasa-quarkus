package org.nasa.endereco.domain;

import org.nasa.core.erro.CausaRaiz;
import org.nasa.core.telemetria.Veredito;

import java.time.Instant;
import java.util.Map;

/**
 * O que a fatia {@code endereco} fez numa execução — e o que ela <b>deixou</b> de fazer.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO.</b> Resolver endereço é a etapa de que o alerta de desastre
 * depende: sem coordenada, o endereço não entra no cálculo de proximidade e o cliente
 * simplesmente não é avisado — <b>sem erro nenhum aparecer</b>. Este registro existe para
 * que esse silêncio tenha número: quantos resolveram, quantos ficaram sem coordenada, e
 * por quê.</p>
 *
 * <p><b>OS QUATRO TIPOS DE NÚMERO</b> (§10.4 da planta), todos presentes:</p>
 * <ol>
 *   <li><b>AGIU</b> — {@code resolvidos}: o número que todo mundo já coleta;</li>
 *   <li><b>ABSTEVE</b> — {@code semCoordenada}: o que faz <i>"não havia trabalho"</i>
 *       deixar de parecer <i>"eu estava cego"</i>;</li>
 *   <li><b>KPI causal</b> — {@code recusasPorCausa}: responde <b>por que</b>, não só
 *       quanto;</li>
 *   <li><b>Veredito</b> — {@link Veredito} com motivo: acusa no instante.</li>
 * </ol>
 *
 * <p><b>INVARIANTES DO DOMÍNIO.</b></p>
 * <ol>
 *   <li><b>{@link Integer}, nunca {@code int}.</b> {@code null} significa <i>"não medi"</i>
 *       e {@code 0} significa <i>"medi e deu zero"</i>. Com primitivo os dois viram
 *       {@code 0} e a auditoria perde a distinção — e <b>{@code 0} não é prova</b>.</li>
 *   <li><b>Zero processado sem motivo conhecido é {@link Veredito#ANOMALIA}</b>, nunca
 *       sucesso silencioso. É o alarme do job silencioso (§10.5): falha fechada troca o
 *       vazamento barulhento por não fazer nada em silêncio, e <i>"não rodou" é mais
 *       difícil de perceber que "vazou"</i>.</li>
 *   <li><b>Veredito diferente de OK exige motivo.</b> Sem ele, o painel acusa e ninguém
 *       sabe o que investigar.</li>
 *   <li>Este record é <b>puro</b>: sem anotação de framework, sem serialização. Quem
 *       serializa é o adaptador.</li>
 * </ol>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA.</b> {@link #avaliar} nunca lança: entrada
 * incoerente produz {@link Veredito#ANOMALIA} com o motivo, que é justamente o que se quer
 * ver no painel. Uma medição que se recusa a existir é pior que uma medição que diz
 * "não confio em mim".</p>
 *
 * @param operacao        chave de negócio da execução — é por ela que o arquivo deduplica
 * @param registradoEm    instante UTC
 * @param resolvidos      AGIU — endereços que ganharam coordenada
 * @param semCoordenada   ABSTEVE — endereços salvos sem coordenada, de propósito
 * @param recusasPorCausa KPI causal
 * @param veredito        OK · ATENCAO · ANOMALIA
 * @param motivo          obrigatório quando o veredito não é OK
 */
public record TelemetriaEndereco(
        String operacao,
        Instant registradoEm,
        Integer resolvidos,
        Integer semCoordenada,
        Map<CausaRaiz, Integer> recusasPorCausa,
        Veredito veredito,
        String motivo) {

    /** Versão do formato gravado. Sem ela, leitor novo lendo arquivo velho inventa zero. */
    public static final int VERSAO_DO_ESQUEMA = 1;

    public TelemetriaEndereco {
        recusasPorCausa = recusasPorCausa == null ? Map.of() : Map.copyOf(recusasPorCausa);
    }

    /**
     * Monta o registro <b>já com o veredito calculado</b> — a regra do job silencioso em
     * código, e não em comentário.
     *
     * @param haviaTrabalho o chamador sabia que existia trabalho a fazer? É o dado que
     *                      separa "não havia nada" de "eu estava cego"; sem ele, zero
     *                      processado é ambíguo por construção
     */
    public static TelemetriaEndereco avaliar(String operacao, Instant quando,
                                             Integer resolvidos, Integer semCoordenada,
                                             Map<CausaRaiz, Integer> recusas,
                                             boolean haviaTrabalho) {
        Veredito v;
        String motivo;

        if (resolvidos == null || semCoordenada == null) {
            // "Não medi" nunca é "deu zero".
            v = Veredito.ANOMALIA;
            motivo = "CONTADOR_NAO_MEDIDO";
        } else if (resolvidos == 0 && semCoordenada == 0 && haviaTrabalho) {
            // O alarme do job silencioso: havia trabalho e nada aconteceu.
            v = Veredito.ANOMALIA;
            motivo = "ZERO_PROCESSADO_COM_TRABALHO_DISPONIVEL";
        } else if (semCoordenada > 0) {
            // Degradação real: o endereço existe e não entra no alerta de proximidade.
            v = Veredito.ATENCAO;
            motivo = "ENDERECOS_SEM_COORDENADA=" + semCoordenada;
        } else {
            v = Veredito.OK;
            motivo = null;
        }
        return new TelemetriaEndereco(operacao, quando, resolvidos, semCoordenada, recusas, v, motivo);
    }
}
