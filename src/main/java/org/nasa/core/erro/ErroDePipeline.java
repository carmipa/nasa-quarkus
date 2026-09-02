package org.nasa.core.erro;

import org.nasa.core.log.Registro;

/**
 * Raiz de <b>toda</b> exceção deste projeto — e o contrato que faz log e telemetria
 * acontecerem sem ninguém lembrar.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO.</b> Ordem de Paulo (2026-09-02): <i>"para cada classe do
 * projeto, uma exceção específica, com log e telemetria"</i>. Esta classe é o mecanismo
 * que torna isso verdade em vez de intenção: ela é <b>abstrata</b>, então não existe
 * {@code throw new ErroDePipeline(...)} genérico — quem falha é obrigado a nomear a
 * falha; e ela <b>carrega</b> os três campos que o log canônico (§9.2) e o KPI causal
 * (§10.4) exigem, então não existe falha que chegue à borda sem operação, alvo e causa.</p>
 *
 * <p><b>POR QUE A EXCEÇÃO NÃO LOGA A SI MESMA.</b> Logar no construtor parece atender o
 * pedido e o atende <b>mal</b>: a exceção é criada uma vez e pode ser capturada, tratada
 * e reembrulhada — o log sai duas, três vezes, e o painel conta o mesmo incidente várias
 * vezes. Pior: exceção capturada e resolvida vira ERROR no log de quem só olha o volume.
 * A exceção <b>carrega</b> o que é preciso; {@link RegistradorDeFalha} emite <b>uma</b>
 * linha e <b>um</b> evento de telemetria, no ponto em que a falha realmente venceu.</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO.</b></p>
 * <ol>
 *   <li><b>Abstrata.</b> Toda falha tem nome próprio. Congelado por
 *       {@code CatracaExcecaoEspecificaTest}, que reprova o build quando alguém lança
 *       {@link RuntimeException}, {@link IllegalArgumentException},
 *       {@link IllegalStateException} ou {@link UnsupportedOperationException}.</li>
 *   <li><b>Causa-raiz obrigatória.</b> Nula vira {@link CausaRaiz#NAO_CLASSIFICADA}, que
 *       é greppável e contável — nunca some.</li>
 *   <li><b>Operação e alvo obrigatórios.</b> Vazios viram {@link Registro#NAO_INFORMADO}
 *       pelo mesmo motivo: um erro de preenchimento não pode derrubar o tratamento da
 *       falha que ele estava descrevendo.</li>
 * </ol>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA.</b> É a própria falha. Nunca lança de dentro do
 * construtor: uma exceção que explode ao ser construída troca um defeito diagnosticável
 * por um rastro incompreensível.</p>
 */
public abstract class ErroDePipeline extends RuntimeException {

    private final String operacao;
    private final String alvo;
    private final CausaRaiz causaRaiz;

    /**
     * @param operacao   o que estava sendo feito, em kebab-case ({@code geocodificar-endereco})
     * @param alvo       o identificador de NEGÓCIO afetado — nunca caminho absoluto de
     *                   máquina, que vaza PII
     * @param causaRaiz  a categoria que o painel vai agrupar
     * @param mensagem   o que aconteceu, em português, sem segredo nem token
     * @param causaTecnica a exceção de baixo nível, quando houver; pode ser nula
     */
    protected ErroDePipeline(String operacao, String alvo, CausaRaiz causaRaiz,
                             String mensagem, Throwable causaTecnica) {
        super(mensagem, causaTecnica);
        this.operacao = (operacao == null || operacao.isBlank()) ? Registro.NAO_INFORMADO : operacao;
        this.alvo = (alvo == null || alvo.isBlank()) ? Registro.NAO_INFORMADO : alvo;
        this.causaRaiz = causaRaiz == null ? CausaRaiz.NAO_CLASSIFICADA : causaRaiz;
    }

    protected ErroDePipeline(String operacao, String alvo, CausaRaiz causaRaiz, String mensagem) {
        this(operacao, alvo, causaRaiz, mensagem, null);
    }

    public String operacao() {
        return operacao;
    }

    public String alvo() {
        return alvo;
    }

    public CausaRaiz causaRaiz() {
        return causaRaiz;
    }

    /**
     * A linha de log canônica desta falha.
     *
     * <p><b>FALHA:</b> não falha. Campo ausente já virou {@code NAO_INFORMADO} no
     * construtor, então a linha sempre sai completa e sempre é greppável.</p>
     */
    public String linhaDeLog() {
        return Registro.recusa(operacao, alvo, causaRaiz.name())
                + " — " + getMessage();
    }
}
