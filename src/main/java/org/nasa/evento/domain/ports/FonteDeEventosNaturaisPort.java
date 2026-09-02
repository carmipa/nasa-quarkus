package org.nasa.evento.domain.ports;

import org.nasa.evento.domain.EventoNatural;
import org.nasa.geo.domain.CaixaDelimitadora;

import java.util.List;
import java.util.Optional;

/**
 * De onde os eventos naturais vem.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO.</b> Isola o caso de uso da API da NASA. É o que permite
 * provar a sincronização — inclusive a escolha da geometria, que é onde mora o defeito
 * mais caro desta fatia — <b>sem rede</b> e sem depender de haver uma tempestade
 * acontecendo no momento do teste.</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO.</b></p>
 * <ol>
 *   <li><b>Devolve evento já com a posição MAIS RECENTE.</b> Traduzir a trajetória em uma
 *       posição é trabalho do adaptador, não do caso de uso — e é onde o legado errava por
 *       456 km ao usar o primeiro ponto.</li>
 *   <li><b>Lista vazia é resposta legítima</b> ("não há eventos assim"); provedor fora é
 *       <b>exceção</b>. Confundir os dois faria uma queda da NASA parecer um mundo calmo.</li>
 * </ol>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA.</b> Rede fora ⇒ exceção de indisponibilidade
 * (503). Corpo ilegível ⇒ exceção própria (502), que manda olhar o contrato e não a rede.</p>
 */
public interface FonteDeEventosNaturaisPort {

    /**
     * Busca eventos na fonte.
     *
     * @param limite   quantos no máximo
     * @param dias     janela para trás, em dias; nulo usa o padrão da fonte
     * @param apenasAtivos {@code true} pede só os que ainda não encerraram
     * @param caixa    recorte geográfico opcional
     */
    List<EventoNatural> buscar(int limite, Integer dias, boolean apenasAtivos,
                               Optional<CaixaDelimitadora> caixa);
}
