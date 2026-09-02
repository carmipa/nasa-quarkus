/**
 * PEERS COMPARTILHADOS — um conceito de domínio com dono único.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO.</b> Conceitos que várias fatias consomem e que precisam
 * de <i>uma</i> definição, não de várias parecidas: coordenada geográfica, acesso ao
 * banco, modelo do evento natural. Peer é a <b>exceção</b> ao princípio raiz
 * ("duplicação consciente &gt; acoplamento") e por isso a lista é curta e deliberada —
 * cada peer aqui é uma decisão de que divergir seria <i>bug</i>, não evolução.</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO.</b></p>
 * <ol>
 *   <li><b>Peer não depende de fatia funcional.</b> Só do JDK, de bibliotecas técnicas,
 *       do {@code core} e dele mesmo. Peer que importa fatia inverte a seta e a
 *       fronteira acaba.</li>
 *   <li><b>{@code peer..domain} é puro</b> — sem anotação de framework, sem
 *       serialização, sem I/O. No KRONOS <i>um único record anotado</i> quebrou o peer
 *       de cache; a regra existe por causa disso.</li>
 *   <li>{@code peer..application} não depende de {@code peer..infrastructure}.</li>
 * </ol>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA.</b> {@code FronteiraArquiteturaTest} reprova o
 * build nomeando a classe e a dependência proibida. Criar um peer novo é decisão de
 * arquitetura: exige entrada neste Javadoc e no plano-mestre, porque um peer a mais é
 * uma amarra a mais entre fatias que antes eram independentes.</p>
 */
package org.nasa.peer;
