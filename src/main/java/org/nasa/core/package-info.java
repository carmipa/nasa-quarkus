/**
 * KERNEL TÉCNICO — utilidade transversal, sem nenhuma regra de negócio.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO.</b> Reunir o que <i>toda</i> fatia precisa e que não
 * pertence a nenhuma delas: relógio, erro-raiz, apoio de apresentação, fila de
 * execução. Existir aqui é o que evita que a mesma utilidade nasça três vezes com três
 * comportamentos ligeiramente diferentes.</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO.</b></p>
 * <ol>
 *   <li><b>O kernel não conhece fatia funcional.</b> Se o {@code core} precisou importar
 *       {@code org.nasa.fatia..}, aquilo não era kernel — era regra de negócio
 *       disfarçada de utilitário, e o lugar dela é na fatia.</li>
 *   <li><b>O kernel não conhece peer.</b> A seta aponta sempre fatia → peer → kernel.</li>
 *   <li>Aqui pode haver framework (CDI, Quarkus): kernel é infraestrutura. A exigência
 *       de pureza vale para {@code ..domain..}, não para este pacote.</li>
 * </ol>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA.</b> As duas primeiras invariantes são congeladas
 * por {@code FronteiraArquiteturaTest}, que <b>reprova o build</b> listando a aresta
 * exata que apareceu. Não é combinado verbal: um {@code CLAUDE.md} pode ser ignorado
 * pela próxima IA; um teste vermelho, não.</p>
 */
package org.nasa.core;
