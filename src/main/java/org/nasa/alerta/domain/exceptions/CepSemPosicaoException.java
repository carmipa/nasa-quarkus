package org.nasa.alerta.domain.exceptions;

import org.nasa.core.erro.CausaRaiz;
import org.nasa.core.erro.ErroDePipeline;

/**
 * O CEP não virou posição — e diz <b>qual</b> das duas coisas aconteceu.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO.</b> São dois estados diferentes com a mesma consequência
 * imediata (não há alerta a mostrar) e orientações <b>opostas</b>:</p>
 * <ul>
 *   <li><b>o CEP não existe</b> — a pessoa digitou errado, e precisa corrigir;</li>
 *   <li><b>o CEP existe, mas nenhum provedor soube a posição</b> — não há nada que ela
 *       possa fazer, e mandá-la "conferir o CEP" seria pedir que corrija o que está certo.</li>
 * </ul>
 *
 * <p>Uma mensagem só para os dois casos faria metade das pessoas procurar um erro que não
 * cometeu.</p>
 *
 * <p><b>POR QUE ABORTA E NÃO DEGRADA.</b> É o oposto do que o cadastro fazia: lá a inscrição
 * valia mesmo sem coordenada, porque podia ser completada depois. Aqui não há depois — sem
 * posição não existe alerta nenhum para mostrar, e devolver uma tela vazia fingindo
 * resultado seria a pior resposta possível.</p>
 *
 * <p><b>INVARIANTE.</b> A mensagem carrega o CEP, que identifica região e não pessoa —
 * nunca o e-mail, que identifica pessoa.</p>
 */
public class CepSemPosicaoException extends ErroDePipeline {

    private final boolean cepInexistente;

    /**
     * @param cep            só dígitos
     * @param cepInexistente {@code true} quando nenhum provedor conhece o CEP;
     *                       {@code false} quando ele existe e ninguém soube a posição
     */
    public CepSemPosicaoException(String cep, boolean cepInexistente) {
        super("montar-alerta", cep, CausaRaiz.DADO_AUSENTE,
              cepInexistente
                      ? "este CEP nao foi encontrado em nenhum provedor — confira os digitos"
                      : "este CEP existe, mas nenhum provedor soube a posicao dele; sem "
                        + "posicao nao da para medir a distancia ate o desastre");
        this.cepInexistente = cepInexistente;
    }

    /** Se o CEP não existe. A tela usa isto para escolher a orientação certa. */
    public boolean cepInexistente() {
        return cepInexistente;
    }
}
