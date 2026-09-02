/**
 * PEER {@code geo} — coordenada, distância e caixa delimitadora.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO.</b> É a moeda comum entre o endereço do cliente e o evento
 * natural da NASA. O alerta de proximidade só existe porque os dois lados falam a mesma
 * linguagem de ponto no mapa; se cada fatia calculasse distância do seu jeito, o alerta
 * chegaria para a pessoa errada — ou não chegaria.</p>
 *
 * <p><b>POR QUE É PEER, e não duplicação consciente.</b> Passou nas três perguntas
 * (§3.3 da planta):</p>
 * <ol>
 *   <li><b>Dono único?</b> Sim — um lugar decide o que é uma coordenada válida.</li>
 *   <li><b>É conceito, não util?</b> Sim — é modelo de domínio, não função solta.</li>
 *   <li><b>Divergir seria bug?</b> Sim — duas fórmulas de distância dão dois raios de
 *       alerta diferentes para o mesmo evento, e o erro é silencioso.</li>
 * </ol>
 *
 * <p><b>INVARIANTES DO DOMÍNIO.</b></p>
 * <ol>
 *   <li>Não depende de nenhuma fatia funcional — só de JDK, {@code core} e dele mesmo.</li>
 *   <li>{@code geo.domain} é <b>puro</b>: sem framework, sem serialização, sem I/O.</li>
 *   <li>{@code geo.application} não depende de {@code geo.infrastructure}.</li>
 * </ol>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA.</b> Congelado por {@code FronteiraArquiteturaTest},
 * que reprova o build nomeando a aresta exata. A superfície pública deste peer é
 * <b>declarada</b>, não deduzida do nome da pasta — consumir uma classe de
 * {@code infrastructure} só é violação se ela não estiver na superfície declarada.</p>
 */
package org.nasa.geo;
