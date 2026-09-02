package org.nasa.painel.presentation.web;

/**
 * A versão dos arquivos estáticos, para invalidar cache do navegador.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO.</b> CSS e JS são servidos com cache longo — é o que faz a
 * segunda visita ser instantânea. O preço é que, sem uma marca na URL, o navegador
 * continua servindo o arquivo <b>velho</b> depois de um deploy, e o operador vê a tela
 * antiga com o comportamento novo. A planta é direta: <b>sem o {@code ?v=} o cache não
 * vale</b>, e com ele a regra pode marcar {@code immutable}.</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO.</b></p>
 * <ol>
 *   <li><b>Uma única constante</b>, injetada no chrome e usada por todas as páginas —
 *       nunca digitada por página. Versão por página diverge, e a que ficar para trás
 *       serve arquivo velho sem ninguém perceber.</li>
 *   <li><b>Muda a cada alteração de asset.</b> Não mudar é o mesmo que não ter versão.</li>
 * </ol>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA.</b> Não falha: é uma constante. O risco real é
 * humano — esquecer de incrementá-la — e por isso ela mora sozinha, num arquivo cujo
 * único propósito é ser lembrado.</p>
 */
public final class VersaoDosAssets {

    /** Incremente a cada mudança em {@code META-INF/resources/estatico/}. */
    public static final String ATUAL = "1";

    private VersaoDosAssets() {
    }
}
