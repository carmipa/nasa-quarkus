package org.nasa.contato.domain;

import org.nasa.contato.domain.exceptions.TipoDeContatoDesconhecidoException;

/**
 * Para que serve este contato — e é um conjunto FECHADO, de propósito.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO.</b> É por este campo que a fatia de alerta decide
 * <b>quem avisar</b> quando um evento natural acontece perto do endereço de um cliente.
 * A pergunta que ele responde é "quais são os contatos de emergência desta pessoa?", e
 * ela precisa ter resposta confiável — porque o custo de errar é alguém não ser avisado
 * de um desastre.</p>
 *
 * <p><b>O QUE O LEGADO FAZIA.</b> {@code tipoContato} era um {@code <input type="text">}
 * livre, com "Principal" preenchido por padrão no código. Texto livre num campo de
 * classificação produz, em pouco tempo, "Principal", "principal", "PRINCIPAL" e
 * "Pincipal" na mesma coluna — todos parecendo certos numa tela que mostra um contato de
 * cada vez. Um contato gravado como "emergencia" sem acento simplesmente não apareceria
 * na busca por emergência, e o silêncio seria idêntico ao de "esta pessoa não tem
 * contato de emergência".</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO.</b></p>
 * <ol>
 *   <li><b>O conjunto é fechado aqui E no banco.</b> A restrição
 *       {@code contato_tipo_conhecido} da V002 é o que impede alguém gravar um valor
 *       novo por outro caminho — pelo console do banco, por uma carga, por um script.
 *       Enum só no Java protege apenas quem passa pelo Java.</li>
 *   <li><b>{@link #EMERGENCIA} é escolha explícita, nunca padrão nem inferência.</b>
 *       Promover alguém a contato de emergência por engano faz uma pessoa receber aviso
 *       de desastre que ela não pediu — e, pior, faz parecer que a cobertura existe.</li>
 *   <li><b>{@link #PRINCIPAL} é o padrão de quem não escolhe</b>, porque é o mais
 *       conservador: serve para falar com a pessoa, e não a inscreve em nada.</li>
 * </ol>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA.</b> Valor desconhecido lança
 * {@link TipoDeContatoDesconhecidoException}, com a lista do que é aceito na mensagem —
 * quem topa com o erro não deve ter de procurar os valores válidos no código.</p>
 */
public enum TipoContato {

    /** O contato de todo dia. Padrão de quem não escolhe. */
    PRINCIPAL,

    /** Um segundo caminho, quando o principal não responde. */
    ALTERNATIVO,

    /**
     * Recebe o alerta de desastre.
     *
     * <p>É o único valor com consequência operacional: quem está aqui é avisado quando
     * um evento natural acontece perto de um endereço do cliente.</p>
     */
    EMERGENCIA,

    /** Contato de trabalho. Não recebe alerta pessoal. */
    COMERCIAL;

    /**
     * Lê o tipo a partir do que veio do formulário ou do banco.
     *
     * <p>Aceita a caixa que vier — "principal", "Principal" e "PRINCIPAL" são a mesma
     * escolha para quem digita, e recusar por causa disso seria rigor sem propósito. O
     * que não aceita é <b>valor que não existe</b>.</p>
     *
     * @param texto o valor recebido; nulo ou em branco vira {@link #PRINCIPAL}
     * @throws TipoDeContatoDesconhecidoException quando o texto não é um tipo conhecido
     */
    public static TipoContato de(String texto) {
        if (texto == null || texto.isBlank()) {
            return PRINCIPAL;
        }
        String limpo = texto.strip().toUpperCase();
        for (TipoContato t : values()) {
            if (t.name().equals(limpo)) {
                return t;
            }
        }
        throw new TipoDeContatoDesconhecidoException(texto);
    }

    /** Como a tela escreve. */
    public String rotulo() {
        return switch (this) {
            case PRINCIPAL -> "Principal";
            case ALTERNATIVO -> "Alternativo";
            case EMERGENCIA -> "Emergência";
            case COMERCIAL -> "Comercial";
        };
    }

    /** Se este contato entra na lista de quem recebe alerta de desastre. */
    public boolean recebeAlerta() {
        return this == EMERGENCIA;
    }
}
