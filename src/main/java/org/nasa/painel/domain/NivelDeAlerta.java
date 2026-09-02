package org.nasa.painel.domain;

/**
 * A gravidade que o GDACS atribui a um evento.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO.</b> É o que permite a home destacar o que importa. Um feed
 * de 348 itens em que tudo tem o mesmo peso visual não informa nada: quem olha desiste
 * antes de achar o evento vermelho no meio dos verdes.</p>
 *
 * <p><b>A ESCALA É DO GDACS, e não inventada aqui:</b> verde é rotina, laranja pede
 * atenção, vermelho é grave. Traduzir para uma escala própria criaria um número que
 * ninguém consegue conferir contra a fonte.</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO.</b> Valor desconhecido vira {@link #DESCONHECIDO}, e
 * <b>nunca</b> {@link #VERDE}. Assumir "verde" para o que não se entendeu é o erro que
 * esconde um evento grave atrás de uma cor tranquilizadora — e o GDACS pode acrescentar um
 * nível novo sem avisar ninguém.</p>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA.</b> Não lança: nível ilegível é informação
 * degradada, não motivo para descartar a notícia inteira.</p>
 */
public enum NivelDeAlerta {

    VERDE("Verde", 1),
    LARANJA("Laranja", 2),
    VERMELHO("Vermelho", 3),

    /** O feed trouxe algo que não reconhecemos. Nunca é tratado como verde. */
    DESCONHECIDO("Desconhecido", 0);

    private final String rotulo;
    private final int gravidade;

    NivelDeAlerta(String rotulo, int gravidade) {
        this.rotulo = rotulo;
        this.gravidade = gravidade;
    }

    /**
     * Lê o nível como o GDACS escreve.
     *
     * <p>Aceita a caixa que vier. O que não reconhece vira {@link #DESCONHECIDO} — jamais
     * verde, porque um nível novo do GDACS apareceria pintado de "tudo bem".</p>
     */
    public static NivelDeAlerta de(String texto) {
        if (texto == null || texto.isBlank()) {
            return DESCONHECIDO;
        }
        return switch (texto.strip().toLowerCase()) {
            case "green" -> VERDE;
            case "orange" -> LARANJA;
            case "red" -> VERMELHO;
            default -> DESCONHECIDO;
        };
    }

    public String rotulo() {
        return rotulo;
    }

    /** 3 é o mais grave. Serve para ordenar e para escolher a cor na tela. */
    public int gravidade() {
        return gravidade;
    }

    /** A classe CSS da tela, derivada aqui para não haver duas tabelas de cor. */
    public String classeCss() {
        return "nivel-" + name().toLowerCase();
    }
}
