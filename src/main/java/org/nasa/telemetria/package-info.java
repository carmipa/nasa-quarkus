/**
 * PEER de telemetria — o que persiste e lê o que o sistema mediu.
 *
 * <p><b>POR QUE PEER, E NÃO KERNEL.</b> O coletor em memória
 * ({@link org.nasa.core.telemetria.Telemetria}) é kernel: ele não importa nada, só conta.
 * A <b>persistência</b> precisa do peer {@code persistencia}, e a regra 1 da fronteira é
 * dura — <i>o kernel não conhece peer nem fatia</i>. Um kernel que importa peer não era
 * kernel: era infraestrutura disfarçada de utilitário.</p>
 *
 * <p><b>POR QUE PEER, E NÃO FATIA.</b> Telemetria não é um recorte do domínio: ninguém
 * pede "quero cadastrar uma telemetria". Ela é serviço transversal, usado por todas as
 * fatias — que é a definição de peer. Como fatia, ela teria de ser importada pelas outras,
 * e a regra 3 (<i>fatia não conhece fatia</i>) reprovaria o build corretamente.</p>
 *
 * <p>Peer pode usar peer: a regra 2 proíbe apenas <i>peer conhecer fatia</i>. Este peer usa
 * {@code persistencia}, e nenhuma fatia.</p>
 */
package org.nasa.telemetria;
