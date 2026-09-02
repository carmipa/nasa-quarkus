/**
 * FATIA {@code alerta} — a saída de todo o sistema.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO.</b> Cadastro, endereço, contato, sincronização com a NASA e
 * geodésia existem para produzir uma coisa: o aviso de que um desastre aconteceu perto de
 * alguém. Esta fatia é essa coisa.</p>
 *
 * <p><b>COMO ELA FALA COM AS OUTRAS SEM CONHECÊ-LAS.</b> A regra é dura e está certa —
 * fatia não conhece fatia. Esta fatia <b>não importa</b> nenhuma classe de {@code cliente},
 * {@code contato}, {@code endereco} ou {@code evento}. Ela tem o próprio <b>modelo de
 * leitura</b>, montado com SQL sobre o esquema, que é compartilhado e pertence ao peer
 * {@code persistencia}. O ganho não é burocrático: se o cadastro de cliente mudar de forma
 * amanhã, o alerta continua compilando, e o que muda é uma consulta — não uma cascata por
 * quatro fatias.</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO.</b></p>
 * <ol>
 *   <li><b>INV-ALERTA-001 — o mesmo evento não avisa o mesmo cliente duas vezes.</b> A
 *       chave de idempotência é {@code (cliente_id, evento_id)} e mora no <b>banco</b>,
 *       não na memória de um processo que reinicia. Uma tempestade que dura cinco dias
 *       aparece em cinco varreduras.</li>
 *   <li><b>OUTBOX: registrar antes de enviar.</b> A ordem inversa perde o registro se o
 *       processo cair entre as duas coisas — a pessoa recebeu e o sistema não sabe, então
 *       avisa de novo. Gravar primeiro troca "avisar duas vezes" por "avisar com atraso".</li>
 *   <li><b>Duas etapas de filtro.</b> SQL em graus reduz por índice; a geodésia decide em
 *       quilômetros. Parar na primeira avisaria gente além do raio, e quem é avisado à toa
 *       desliga a notificação antes do evento que importava.</li>
 *   <li><b>Só contato de EMERGENCIA vira destinatário.</b> Ninguém entra nessa lista sem
 *       ter sido inscrito nela.</li>
 *   <li><b>Nenhum aviso desaparece.</b> Falhou vira {@code FALHOU} com causa-raiz, e a
 *       linha fica para auditoria.</li>
 * </ol>
 *
 * <p><b>LACUNA DECLARADA — e é a mais importante desta fatia.</b> Não há servidor de
 * e-mail configurado. O adaptador em uso <b>registra no log</b> e não entrega a ninguém;
 * ele diz isso em WARN a cada envio, e a API expõe {@code entregaDeVerdade: false} com a
 * ressalva por escrito. Um adaptador que fingisse sucesso silencioso seria pior que não
 * ter alerta nenhum, porque a tela mostraria cobertura que não existe.</p>
 */
package org.nasa.alerta;
