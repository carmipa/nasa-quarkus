/**
 * FATIA {@code contato} — por onde a pessoa é avisada.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO.</b> É o destino do alerta. Quando um evento natural
 * acontece perto de um endereço cadastrado, é daqui que sai para quem avisar. O defeito
 * característico desta fatia não produz erro: produz <b>silêncio</b> na hora do desastre,
 * e ninguém percebe até acontecer.</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO.</b></p>
 * <ol>
 *   <li><b>INV-CONTATO-001 — o e-mail identifica UM contato.</b> Protegido por
 *       {@code UNIQUE (email)} no banco. O legado expunha
 *       {@code GET /api/contatos/email/...} devolvendo UM contato: sem unicidade, esse
 *       endpoint é ambíguo por construção.</li>
 *   <li><b>INV-CONTATO-002 — o tipo é um conjunto FECHADO</b>, garantido pela restrição
 *       {@code contato_tipo_conhecido} da V002. No legado era texto livre com padrão
 *       "Principal", e um contato gravado como "emergencia" sem acento não apareceria na
 *       busca por emergência — o silêncio seria idêntico ao de "não tem contato de
 *       emergência".</li>
 *   <li><b>O e-mail é obrigatório; os telefones não.</b> É o único canal que o sistema
 *       sabe usar hoje. Contato só com telefone pareceria completo e não avisaria
 *       ninguém.</li>
 *   <li><b>Telefone guarda só dígitos.</b> Mesma cicatriz do documento do cliente:
 *       guardar como digitado faz o mesmo número existir em quatro formas, e nenhuma
 *       busca encontra as outras três.</li>
 *   <li><b>Não fala com outra fatia.</b> A ligação com {@code cliente} é feita pela
 *       tabela de junção {@code cliente_contato}, por porta — nunca por import direto.</li>
 *   <li><b>A consulta de emergência NÃO é paginada.</b> Quem vai ser avisado tem de ser
 *       avisado inteiro; paginar avisaria a primeira página de pessoas e esqueceria as
 *       demais, sem erro nenhum.</li>
 * </ol>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA.</b> Duplicata de e-mail é 409 e resolvível por
 * quem opera. Campo torto é 400 com o nome do campo. Falha de banco é 500 com causa-raiz.
 * Mudança que entra ou sai de {@code EMERGENCIA} é registrada em WARN — é a única
 * alteração daqui cuja consequência é alguém deixar de ser avisado.</p>
 */
package org.nasa.contato;
