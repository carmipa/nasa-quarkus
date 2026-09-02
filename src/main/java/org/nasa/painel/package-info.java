/**
 * FATIA {@code painel} — as telas do sistema, em Qute + HTMX.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO.</b> É a superfície por onde a pessoa usa o sistema:
 * consulta desastres naturais, cadastra endereço e vê se está numa área de risco. A régua
 * §6.1 da planta coloca este projeto na linha 1 — há banco, há autorização e há página
 * derivada do estado do servidor —, então o servidor renderiza e o HTMX troca pedaço de
 * tela, sem SPA e sem Node.
 *
 * <p><b>INVARIANTES DO DOMÍNIO.</b></p>
 * <ol>
 *   <li><b>Backend decide, frontend apresenta.</b> Atributo {@code hx-*} não é
 *       autorização; esconder botão não é autorização; campo {@code readonly} não é
 *       proteção. Toda regra crítica é validada no servidor, com teste chamando a API
 *       com payload adulterado.</li>
 *   <li><b>Nada de CDN.</b> HTMX, CSS e ícones são servidos localmente. Dependência
 *       externa em página do sistema morre junto com a CDN.
 *       <b>Exceção única e declarada:</b> o widget do Google Translate (§6.6), que é de
 *       terceiro por natureza e falha <b>aberto</b> — sem rede, a página fica no idioma
 *       de origem e continua funcionando.</li>
 *   <li><b>Largura cheia e porcentagem.</b> Nunca {@code max-width} + {@code margin:0
 *       auto}, que deixa o conteúdo pingando numa coluna central; nunca {@code 100vw},
 *       que inclui a barra de rolagem e cria barra horizontal.</li>
 *   <li><b>Estado vazio é declarado</b>, com o motivo. "Nada a mostrar" e "eu não
 *       consegui buscar" não podem produzir o mesmo pixel.</li>
 * </ol>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA.</b> Caso de uso indisponível devolve a página de
 * erro com o motivo, nunca tela branca. Tradução indisponível degrada para o idioma de
 * origem — é conforto, não função.</p>
 */
package org.nasa.painel;
