/**
 * PEER {@code persistencia} — o esquema do banco e como ele evolui.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO.</b> Toda fatia guarda dado, e todas guardam no mesmo
 * arquivo SQLite. Se cada uma criasse a própria tabela do seu jeito, o esquema deixaria de
 * ter dono e a primeira divergência apareceria como dado inconsistente — não como erro de
 * compilação. Este peer é o dono único do esquema e da ordem em que ele muda.</p>
 *
 * <p><b>POR QUE É PEER</b> (as três perguntas de §3.3 da planta): tem dono único (o
 * esquema é um só), é conceito e não utilidade (migração é modelo de domínio: versão,
 * ordem, imutabilidade), e divergir seria <b>bug</b> — duas fatias aplicando DDL na mesma
 * tabela em ordens diferentes produzem bancos diferentes na mesma versão do código.</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO.</b></p>
 * <ol>
 *   <li><b>Migração aplicada é IMUTÁVEL.</b> O conteúdo de cada arquivo é somado em
 *       SHA-256 e guardado. Editar uma migração já aplicada muda o checksum e <b>aborta o
 *       boot</b> — porque o banco de quem já rodou a versão antiga não vai ser corrigido
 *       por mágica, e seguir em frente produziria dois esquemas com o mesmo número.</li>
 *   <li><b>A ordem é declarada, não deduzida.</b> As migrações são listadas num índice
 *       versionado; varredura de classpath muda de resultado entre a IDE e o jar, e
 *       ordem de DDL que muda com o empacotamento é defeito esperando data.</li>
 *   <li><b>Tudo ou nada, por migração.</b> Cada arquivo roda dentro da própria transação
 *       e só é registrado se completou. Migração pela metade é o pior estado possível:
 *       nem aplicada, nem por aplicar.</li>
 *   <li><b>Instante em UTC</b>, como todo o resto do sistema.</li>
 * </ol>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA.</b> <b>Falha fechada e alto.</b> Checksum
 * divergente, arquivo do índice ausente ou DDL com erro derrubam o boot com exceção
 * específica e causa-raiz. É deliberado: subir a aplicação sobre esquema que ninguém sabe
 * qual é dá erro muito mais tarde, muito mais longe da causa, e possivelmente já com dado
 * gravado errado.</p>
 */
package org.nasa.persistencia;
