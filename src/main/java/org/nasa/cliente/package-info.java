/**
 * FATIA {@code cliente} — quem deve ser avisado quando um desastre acontece por perto.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO.</b> O cadastro é a lista de pessoas do sistema. Sem ele o
 * alerta não tem destinatário; com ele duplicado, a mesma pessoa recebe duas vezes — ou,
 * pior, um cadastro fantasma recebe e o real não.</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO.</b></p>
 * <ol>
 *   <li><b>INV-CLIENTE-001 — o documento identifica UM cliente.</b> Protegido por
 *       {@code UNIQUE (documento)} no banco, não apenas no Java. E o documento é
 *       <b>normalizado</b> antes de comparar: no legado, {@code "111.222.333-44"} e
 *       {@code "11122233344"} eram duas pessoas diferentes, e a unicidade não pegava.</li>
 *   <li><b>Não fala com outra fatia.</b> Contato e endereço se ligam a ela pelas tabelas
 *       de junção, e quem monta essa ligação é o caso de uso de cada fatia através de
 *       porta — nunca um import direto.</li>
 *   <li><b>{@code application} depende de {@code domain/ports}</b>, nunca de
 *       {@code infrastructure}: os casos de uso são testáveis sem banco.</li>
 *   <li><b>Exclusão é decisão explícita.</b> A tabela de junção cai em cascata, mas o
 *       cliente só sai quando alguém pede — e a tela nomeia quem está sendo apagado.</li>
 * </ol>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA.</b> Documento repetido ⇒
 * {@code DocumentoJaCadastradoException} (o banco recusa, e a fatia traduz para uma
 * mensagem que diz <b>qual</b> documento). Cliente inexistente ⇒
 * {@code ClienteNaoEncontradoException}. Nenhuma das duas é 500: são respostas
 * previsíveis a pedidos previsíveis.</p>
 */
package org.nasa.cliente;
