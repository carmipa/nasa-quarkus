package org.nasa.core.telemetria;

/**
 * O veredito de uma execução — o número que acusa sozinho.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO.</b> Log é texto para humano ler <i>quando já suspeita</i>
 * de algo. Veredito é estrutura que acusa <b>sem</b> ninguém suspeitar. A diferença
 * aparece no caso real que a planta registra (§10.1): uma varredura "bem-sucedida" — 13
 * enviadas, 0 falhas — escondia <b>9 traduções degradadas</b>, porque cada passo
 * individual teve sucesso. Só o veredito por execução pegou.</p>
 *
 * <p><b>POR QUE MORA NO KERNEL.</b> Passa no teste das três perguntas ao contrário: se
 * cada fatia escolhesse seu vocabulário — uma diz {@code OK}, outra {@code SUCESSO},
 * outra {@code 200} — o conjunto de telemetria vira ilegível e não dá para perguntar
 * "quantas execuções anômalas houve hoje" sem traduzir três dialetos. Divergir aqui é
 * bug, então é fonte única. E é vocabulário técnico sem regra de negócio, que é a
 * definição de kernel.</p>
 *
 * <p><b>INVARIANTE.</b> Três estados, nunca dois — a mesma disciplina das guardas.
 * {@link #ANOMALIA} existe porque "não fiz nada" e "não consegui medir" não podem
 * produzir o mesmo pixel: <i>"não rodou" é mais difícil de perceber que "vazou"</i>.</p>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA.</b> Não há caminho de falha: é um enum. Quem
 * decide o veredito é a regra da própria fatia, e ela é obrigada a acompanhar um
 * <b>motivo</b> quando o veredito não é {@link #OK}.</p>
 */
public enum Veredito {

    /** Fez o que prometeu, na quantidade esperada. */
    OK,

    /** Fez, mas degradado — parte do trabalho não aconteceu, e o motivo está declarado. */
    ATENCAO,

    /**
     * O resultado não é confiável: zero processado sem motivo conhecido, medição ausente,
     * ou contradição entre os contadores. <b>Nunca é sucesso silencioso.</b>
     */
    ANOMALIA
}
