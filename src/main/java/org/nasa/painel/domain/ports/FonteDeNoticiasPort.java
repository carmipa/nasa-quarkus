package org.nasa.painel.domain.ports;

import org.nasa.painel.domain.Noticia;

import java.util.List;

/**
 * De onde vem o noticiario de desastres da home.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO.</b> Isola a tela da fonte. Foi essa separação que permitiu
 * descobrir, sem quebrar nada, que a fonte do legado morreu: trocar a implementação é
 * escrever um adaptador novo, não mexer na home.</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO.</b></p>
 * <ol>
 *   <li><b>Lista vazia é resposta legítima</b> — significa "sem notícias agora". Fonte
 *       fora é <b>exceção</b>. Confundir as duas faria uma queda parecer um mundo em paz.</li>
 *   <li><b>Já vem ordenado por gravidade e recência.</b> Ordenar na tela espalharia a
 *       regra por templates, e a home não é o lugar de decidir o que é mais grave.</li>
 * </ol>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA.</b> Fonte fora ⇒ exceção de indisponibilidade. A
 * home trata isso como <b>degradação</b>: mostra o resto da página e diz que o noticiário
 * está indisponível — nunca devolve erro 500 por causa de uma vitrine.</p>
 */
public interface FonteDeNoticiasPort {

    /**
     * As noticias mais relevantes agora.
     *
     * @param limite quantas no máximo
     */
    List<Noticia> maisRecentes(int limite);
}
