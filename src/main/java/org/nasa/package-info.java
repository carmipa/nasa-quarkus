/**
 * RAIZ DO PROJETO — a taxonomia dos módulos de topo.
 *
 * <p><b>PROPÓSITO DE NEGÓCIO.</b> Declarar, num lugar só, qual pacote de topo é kernel,
 * qual é peer e qual é fatia. A árvore canônica (§5.1 da planta) põe peers e fatias
 * <b>lado a lado na raiz</b>, sem agrupador — o que é mais legível para quem procura um
 * caso de uso, e deixa a categoria de cada módulo <i>invisível no caminho</i>. Por isso
 * a lista existe aqui e é lida pela guarda de fronteira: categoria declarada, nunca
 * deduzida do nome da pasta.</p>
 *
 * <p><b>A TAXONOMIA</b> (mantenha em sincronia com {@code FronteiraArquiteturaTest}):</p>
 * <pre>
 * org.nasa
 * ├── core/        KERNEL TÉCNICO — utilidade transversal, zero regra de negócio
 * ├── config/      bootstrap da aplicação
 * │
 * ├── geo/           PEER — coordenada, distância, caixa delimitadora
 * ├── persistencia/  PEER — esquema do banco e as migrações
 * │
 * ├── endereco/    FATIA — CEP, geocodificação, CRUD de endereço
 * └── painel/      FATIA — as telas, em Qute + HTMX
 * </pre>
 *
 * <p><b>INVARIANTES DO DOMÍNIO.</b> A seta aponta sempre <b>fatia → peer → kernel</b>.
 * Nunca ao contrário, nunca lateral entre fatias. As três categorias e a matriz de
 * dependência estão em §3.1 da planta.</p>
 *
 * <p><b>COMPORTAMENTO EM CASO DE FALHA.</b> Módulo de topo que não estiver declarado na
 * lista de {@code FronteiraArquiteturaTest} <b>reprova o build</b> — e é de propósito:
 * pacote novo sem categoria é pacote que nenhuma regra governa, e regra que não governa
 * nada é o começo do acoplamento que esta planta existe para impedir.</p>
 *
 * <p><b>NUNCA criar aqui:</b> {@code shared/}, {@code common/}, {@code util/} funcional,
 * nem pacote por camada ({@code controllers/}, {@code services/}, {@code models/}).
 * Utilidade técnica pura vai ao {@code core}; regra de negócio duplica-se
 * conscientemente dentro da fatia.</p>
 */
package org.nasa;
