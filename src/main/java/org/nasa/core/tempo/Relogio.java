package org.nasa.core.tempo;

import java.time.Instant;

/**
 * A fonte de tempo do sistema.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO.</b> Existe para que "agora" seja uma dependência
 * injetada, e não uma chamada estática espalhada pelo código. Sem isso, testar a virada
 * do dia operacional exigiria esperar a meia-noite — e o teste que ninguém consegue
 * rodar é o teste que não existe.</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO.</b></p>
 * <ol>
 *   <li><b>Sempre UTC.</b> O tipo é {@link Instant}, que não carrega fuso — é
 *       impossível devolver hora local por engano. {@code LocalDateTime.now()} é local
 *       e gravá-lo quebra o dia operacional <i>em silêncio</i>, errando por horas
 *       justamente na janela da virada.</li>
 *   <li>O fuso da apresentação é decidido na borda, com {@code ZoneId} IANA, nunca no
 *       armazenamento. Grava-se UTC, decide-se local.</li>
 * </ol>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA.</b> Não falha: a implementação de produção
 * delega ao relógio da JVM. Relógio dessincronizado do host é risco operacional
 * declarado no plano-mestre — a data de um evento natural vem da API da NASA, não
 * daqui, exatamente para não depender do relógio desta máquina.</p>
 */
public interface Relogio {

    /** O instante atual, em UTC. */
    Instant agora();
}
