package org.nasa.core.erro;

/**
 * A causa-raiz de uma falha — o vocabulário fechado que transforma contagem em diagnóstico.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO.</b> Saber que 37 endereços falharam não diz o que fazer.
 * Saber que 31 falharam por {@link #PROVEDOR_INDISPONIVEL} e 6 por {@link #DADO_INVALIDO}
 * diz: o primeiro conserta sozinho, o segundo exige olhar os dados. É o <b>KPI causal</b>
 * da planta (§10.4) — o número que responde <i>por que</i>, e não só <i>quanto</i>.</p>
 *
 * <p><b>POR QUE É ENUM E NÃO STRING.</b> Causa em texto livre vira
 * {@code "provedor fora"}, {@code "provedor indisponível"} e {@code "PROVIDER_DOWN"} na
 * mesma base, e agrupar por causa deixa de funcionar exatamente quando é preciso. Com
 * enum, o compilador impede o quarto dialeto.</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO.</b></p>
 * <ol>
 *   <li><b>Toda falha declara sua causa.</b> É a regra que mais acha bug: um motivo
 *       calculado e jogado fora cegou 996 pendências no projeto de origem.</li>
 *   <li><b>{@link #NAO_CLASSIFICADA} é defeito, não categoria.</b> Ela existe para ser
 *       <b>greppável</b> e para a catraca conseguir contá-la — não para ser usada. Uma
 *       falha que chega nela é uma falha que ninguém classificou ainda.</li>
 * </ol>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA.</b> Não há: é enum. Quem falha em escolher a
 * causa certa cai em {@link #NAO_CLASSIFICADA}, e isso aparece no painel como anomalia,
 * não como sucesso.</p>
 */
public enum CausaRaiz {

    /** Falta configuração para a operação sequer tentar. Falha fechada, não degradação. */
    CONFIGURACAO_AUSENTE,

    /** Serviço externo fora do ar, recusando conexão ou sem resposta. Costuma ser transitório. */
    PROVEDOR_INDISPONIVEL,

    /** Serviço externo respondeu, e a resposta foi uma recusa explícita (4xx, status de erro). */
    PROVEDOR_RECUSOU,

    /** Estourou o tempo. Diferente de indisponível: pode ter acontecido do outro lado. */
    TEMPO_ESGOTADO,

    /** O dado chegou, e está errado — formato, faixa, coerência. */
    DADO_INVALIDO,

    /** O dado que a operação exigia não veio. Distinto de inválido: ausência não é erro de forma. */
    DADO_AUSENTE,

    /** O estado atual não permite a transição pedida. */
    CONFLITO_DE_ESTADO,

    /** A escrita no banco falhou. */
    PERSISTENCIA_FALHOU,

    /** Arquivo existe e está ilegível. O original é PRESERVADO, nunca apagado. */
    ARQUIVO_CORROMPIDO,

    /** Arquivo ausente, sem permissão, ou disco recusando. */
    ARQUIVO_INACESSIVEL,

    /** Duas operações disputaram o mesmo recurso. */
    CONCORRENCIA,

    /** Parada cooperativa pedida pelo operador. Não é defeito — é o botão "Parar". */
    INTERROMPIDO,

    /**
     * Ninguém classificou esta falha.
     *
     * <p><b>É defeito, não categoria.</b> Existe para ser greppável e contável: uma
     * catraca que a encontre subindo sabe que o vocabulário ficou para trás do código.</p>
     */
    NAO_CLASSIFICADA
}
