package org.nasa.core.erro;

/**
 * A JVM não está em UTC, e por isso os carimbos do log sairiam em fuso local.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO.</b> Existe para impedir a única forma de o sistema mentir
 * sobre <i>quando</i> as coisas aconteceram. O relógio da aplicação é UTC por mecanismo
 * ({@code Instant} em tudo, formatação com zona explícita), mas o <b>carimbo do log</b> é
 * escrito pelo framework de logging usando o fuso padrão da JVM — e esse não obedece a
 * nenhuma regra do código.</p>
 *
 * <p><b>O PREJUÍZO QUE A ORIGINOU</b> (02/09/2026). Medido lado a lado, no mesmo commit:</p>
 * <pre>
 * producao (jar, sem flag)   2026-09-02T09:06:13.599-03:00
 * teste    (com a flag)      2026-09-02T15:04:19.138Z
 * </pre>
 * <p>O invariante "log em UTC" valia <b>só nos testes</b> — e a catraca que devia protegê-lo
 * rodava apenas dentro do JVM de teste, ou seja, foi calibrada no único ambiente onde já
 * era verdade. O dano concreto: a API responde {@code criadoEm: ...T15:05:51Z} enquanto a
 * linha de log do mesmo instante diz {@code 12:05:51-03:00}. Quem cruza os dois vê três
 * horas de diferença e conclui que existe um defeito de gravação que não existe.</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO.</b> Fuso de offset zero <b>e fixo</b>. {@code Europe/London}
 * é recusado de propósito: rende {@code Z} no inverno e {@code +01:00} no verão, e um
 * invariante que vale seis meses por ano não é um invariante.</p>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA.</b> Causa-raiz {@link CausaRaiz#CONFIGURACAO_AUSENTE}
 * e o arranque <b>cai</b>. É deliberado: log com fuso errado não avisa que está errado —
 * ele parece perfeitamente normal, e o engano só aparece muito depois, na hora de
 * investigar outra coisa. A mensagem carrega o comando exato da correção.</p>
 */
public class FusoHorarioNaoUtcException extends ErroDePipeline {

    public FusoHorarioNaoUtcException(String zonaEncontrada) {
        super("verificar-fuso", zonaEncontrada, CausaRaiz.CONFIGURACAO_AUSENTE,
              "a JVM esta em '" + zonaEncontrada + "', e o carimbo do log sairia neste fuso "
              + "em vez de UTC. Suba com -Duser.timezone=UTC "
              + "(ex.: java -Duser.timezone=UTC -jar build/quarkus-app/quarkus-run.jar), "
              + "ou use `gradlew rodar`, que ja passa a flag");
    }
}
