/**
 * FATIA {@code evento} — o que a NASA publica, e o que dispara o alerta.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO.</b> É a razão do sistema existir. Um incêndio, uma
 * tempestade, um vulcão: quando um deles acontece perto de um endereço cadastrado, alguém
 * precisa ser avisado. Todo o resto serve a esta comparação.</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO.</b></p>
 * <ol>
 *   <li><b>INV-EONET-001 — o {@code eonetId} identifica UM evento.</b> Protegido por
 *       {@code UNIQUE} no banco. No legado a garantia morava só no Java
 *       ({@code findByEonetIdApi().orElse(new)}), e duas sincronizações simultâneas liam
 *       "não existe" e inseriam as duas.</li>
 *   <li><b>INV-EONET-002 — a posição é a do ponto MAIS RECENTE.</b> A EONET devolve a
 *       trajetória inteira; o legado usava o primeiro ponto, que é onde o evento COMEÇOU.
 *       Medido em 02/09/2026 no evento {@code EONET_23800}: <b>456 km</b> entre o primeiro
 *       e o último ponto, num alerta de raio 100 km.</li>
 *   <li><b>INV-EONET-003 — GeoJSON é {@code [longitude, latitude]}.</b> Ordem inversa da
 *       intuitiva, e trocá-la põe o evento do outro lado do planeta sem exceção nenhuma
 *       quando os dois números estão na faixa válida.</li>
 *   <li><b>INV-EONET-004 — proximidade tem DUAS etapas.</b> Caixa por índice, geodésia
 *       decidindo. Parar na caixa avisaria gente a até 41% além do raio, porque o canto do
 *       retângulo fica a {@code raio × √2} do centro.</li>
 *   <li><b>Encerrado não alerta; sem coordenada não alerta.</b> As duas ausências são
 *       declaradas na resposta, com o motivo — são invisíveis de outra forma.</li>
 *   <li><b>Sincronizar é idempotente pelo banco</b> ({@code ON CONFLICT DO UPDATE}), e
 *       atualiza a posição. {@code DO NOTHING} congelaria o evento no primeiro dia.</li>
 * </ol>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA.</b> NASA fora ⇒ 503, e a base local continua
 * válida — sincronizar é atualizar, não é a fonte de verdade em tempo real. Contrato
 * mudado ⇒ 502 com "o contrato pode ter mudado", que manda olhar o formato em vez da rede.
 * Um evento torto é contado, registrado e pulado: perder o lote inteiro por causa de um
 * evento trocaria um problema pequeno por um apagão de dados.</p>
 */
package org.nasa.evento;
