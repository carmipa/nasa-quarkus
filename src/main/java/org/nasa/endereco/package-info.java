/**
 * FATIA {@code endereco} — CEP, geocodificação e cadastro de endereço.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO.</b> Um endereço só serve ao alerta de desastre quando tem
 * coordenada: é a distância até o evento da NASA que decide se o cliente é avisado. Esta
 * fatia leva o endereço de "CEP digitado" a "ponto no mapa", e diz com todas as letras
 * quando não conseguiu.</p>
 *
 * <p><b>AS QUATRO CAMADAS</b> (§5.2 da planta):</p>
 * <pre>
 * endereco/
 * ├── domain/            PURO — sem framework, sem I/O, sem Jackson
 * │   ├── ports/         interfaces do que a fatia precisa do mundo
 * │   └── exceptions/    exceções de negócio da fatia
 * ├── application/       casos de uso e guardas de invariante
 * ├── infrastructure/    adaptadores: BrasilAPI, ViaCEP, Nominatim, persistência
 * │   ├── adapters/  ├── config/  ├── dtos/  └── telemetria/
 * └── presentation/web/  entrada HTTP e página Qute+HTMX
 * </pre>
 *
 * <p><b>INVARIANTES DO DOMÍNIO.</b></p>
 * <ol>
 *   <li><b>Não fala com outra fatia.</b> Se precisar, o que ela quer é um peer ou porta.</li>
 *   <li><b>{@code application} depende de {@code domain/ports}</b>, nunca de
 *       {@code infrastructure} — o adaptador é injetado.</li>
 *   <li><b>Toda saída passa por porta declarada</b>: CEP, geocodificação, persistência e
 *       telemetria.</li>
 *   <li><b>Coordenada ausente é ausente</b>, nunca {@code (0,0)}. Medido: 1 de 6 CEPs
 *       volta da BrasilAPI sem {@code location}, e o par {@code 0,0} — o null island, no
 *       Golfo da Guiné — poria o endereço do cliente no oceano com o mapa desenhando o
 *       pino lá e <b>nenhum erro aparecendo</b>. Esta é a regra que justifica a fatia ter
 *       {@code CHECK} próprio no banco, e é regra <b>do endereço</b>: o peer
 *       {@code geo} aceita {@code (0,0)} de propósito, porque evento natural pode
 *       legitimamente ocorrer em alto-mar sobre aquele ponto.</li>
 * </ol>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA.</b> Provedor de CEP ou de geocodificação fora do
 * ar ⇒ o adaptador lança a exceção de indisponibilidade da fatia; o caso de uso decide
 * entre degradar (salvar sem coordenada, <b>marcado</b>, e a tela informa que o endereço
 * não entra no alerta de proximidade) ou abortar. O que ele <b>não</b> faz é inventar
 * um ponto.</p>
 */
package org.nasa.endereco;
