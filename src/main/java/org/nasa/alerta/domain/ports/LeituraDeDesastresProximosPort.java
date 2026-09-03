package org.nasa.alerta.domain.ports;

import org.nasa.alerta.domain.DesastreProximo;
import org.nasa.geo.domain.Coordenada;

import java.time.Instant;
import java.util.List;

/**
 * Os desastres perto de um ponto — o modelo de leitura desta fatia.
 *
 * <p><b>POR QUE UMA PORTA PRÓPRIA, E NÃO O CASO DE USO DA FATIA `evento`.</b> A regra de
 * arquitetura é dura: <b>fatia não conhece fatia</b>. Se `alerta` importasse
 * {@code ConsultarEventosUseCase}, a guarda de fronteira reprovaria o build — corretamente,
 * porque é assim que duas fatias começam a se enrolar e nenhuma pode mais ser mudada
 * sozinha.</p>
 *
 * <p>O adaptador desta porta lê a <b>tabela</b> {@code evento_natural} direto, com SQL
 * próprio. Ele conhece o <b>esquema</b>, não o código da outra fatia — e esquema é contrato
 * do banco, não acoplamento entre módulos. É a mesma solução que o modelo anterior já
 * usava.</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO.</b></p>
 * <ol>
 *   <li><b>A busca é em DUAS ETAPAS, e as duas importam.</b> Uma caixa em graus reduz o
 *       conjunto pelo índice; a geodésia decide quem fica. Caixa é retângulo e raio é
 *       círculo: o canto de uma caixa de 100 km fica a <b>141 km</b>. Parar na caixa
 *       alertaria gente 40% além do raio pedido — foi o defeito do projeto original.</li>
 *   <li><b>Só eventos COM coordenada.</b> Sem posição não há distância, e um item sem
 *       distância numa lista ordenada por proximidade vai para um lugar arbitrário.</li>
 *   <li><b>Ordenado do MAIS PRÓXIMO para o mais distante.</b> É a ordem em que a informação
 *       é útil: quem lê o alerta quer saber primeiro o que está em cima dele.</li>
 * </ol>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA.</b> Erro de banco vira exceção da fatia, com
 * causa-raiz. Lista vazia é <b>resultado</b>, não falha — e é a resposta mais comum.</p>
 */
public interface LeituraDeDesastresProximosPort {

    /**
     * Os desastres dentro do raio, do mais próximo ao mais distante.
     *
     * @param onde   o ponto de onde medir
     * @param raioKm o raio, em quilômetros
     * @param desde  só eventos ocorridos a partir daqui — um desastre de dois anos atrás
     *               não é alerta, é história
     * @param limite teto de resultados
     * @return lista possivelmente vazia, <b>nunca</b> {@code null}
     */
    List<DesastreProximo> proximos(Coordenada onde, double raioKm, Instant desde, int limite);
}
