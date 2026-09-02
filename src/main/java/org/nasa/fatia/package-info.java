/**
 * FATIAS VERTICAIS — cada uma é um caso de uso completo, de ponta a ponta.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO.</b> Uma fatia contém tudo de que um recorte funcional
 * precisa — domínio, caso de uso, adaptador e entrada HTTP — para que mexer em
 * "cliente" não exija entender "evento" nem quebrá-lo. É o oposto do legado, onde
 * `controller/service/repository` obrigava a atravessar o sistema inteiro para alterar
 * uma regra só.</p>
 *
 * <p><b>INVARIANTES DO DOMÍNIO.</b></p>
 * <ol>
 *   <li><b>Fatia não fala com fatia.</b> Se precisou, o que ela quer é um peer ou uma
 *       porta. Esta é a invariante que o desenho inteiro existe para sustentar.</li>
 *   <li><b>{@code application} depende de {@code domain.ports}, nunca de
 *       {@code infrastructure}.</b> O adaptador é injetado — e é isso que torna o caso
 *       de uso testável sem rede e sem disco.</li>
 *   <li><b>{@code domain} é puro</b>: sem framework, sem I/O, sem serialização.</li>
 *   <li><b>Toda saída da fatia passa por porta declarada</b> — rede, banco, e-mail,
 *       relógio. Sem exceção "só desta vez".</li>
 * </ol>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA.</b> {@code FronteiraArquiteturaTest} reprova o
 * build. A regra 1 usa <i>allowlist vazia</i>: qualquer aresta entre fatias é erro, e a
 * mensagem nomeia as duas pontas. Se uma dependência entre fatias for mesmo necessária,
 * a resposta certa é extrair um peer ou declarar uma porta — nunca afrouxar a guarda.</p>
 *
 * <p><b>Rodar sempre com {@code --rerun-tasks}:</b> o cache do Gradle produz
 * <b>falso-verde</b> em teste de arquitetura. Cicatriz medida no KRONOS, e a que mais
 * engana, porque o verde parece exatamente igual ao verde legítimo.</p>
 */
package org.nasa.fatia;
