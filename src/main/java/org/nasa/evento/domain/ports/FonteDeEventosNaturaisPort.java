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

    /**
     * Todos os eventos de um ANO — o arquivo histórico.
     *
     * <p><b>Por que um método próprio, e não `dias` grande.</b> Medido em 02/09/2026:
     * sem filtro de data, a EONET devolve <b>os mais recentes primeiro</b> — pedir
     * {@code limit=2000} traz 2000 eventos, todos de 2026, e nenhum de 2015. O arquivo
     * histórico só é alcançável recortando por {@code start} e {@code end}.</p>
     *
     * <p><b>O volume por ano é desigual, e isso importa</b> (medido):</p>
     * <pre>
     * 2015: 342   2019: 476   2023:  271   2026: 5000 (bateu o teto)
     * 2017: 632   2021: 732   2025: 4612
     * </pre>
     * <p>O salto de 2023 para 2025 não é ruído: é a NASA publicando mais. Um limite fixo
     * pequeno truncaria os anos recentes <b>em silêncio</b>, e o gráfico mostraria uma
     * queda que não existe.</p>
     *
     * @param ano    o ano civil, em UTC
     * @param limite teto de eventos; o adaptador avisa em WARN quando o teto é atingido,
     *               porque atingir o teto significa que há mais e não se sabe quantos
     */
    List<EventoNatural> buscarDoAno(int ano, int limite);
}
