# Guardas executáveis

Regra escrita em documento é combinado; regra que reprova o build é mecanismo. Este
projeto tem sete famílias de guarda, e **todas já reprovaram alguma coisa de verdade**.

## O princípio que vale para todas

> Guarda que nunca foi vista reprovando não é guarda — é esperança.

Toda guarda aqui é **calibrada**: existe um caso doente conhecido que ela precisa recusar,
e esse caso roda junto. Três estados, nunca dois: `0` passou · `1` reprovou · `2` **NÃO
VERIFICOU**, que não é aprovação.

## Fronteira arquitetural

Sete regras ArchUnit. Reprovou o build duas vezes, e nas duas estava certa:

- `ConsultarCepUseCase` importava adaptadores direto — `application` não pode depender de
  `infrastructure`. Virou uma porta;
- a fatia `alerta` precisaria importar quatro outras — virou modelo de leitura próprio.

A regra 0 existe para impedir vacuidade: sem alvo, todas as outras passariam sem julgar
nada.

## Segredos

Varredura por **conteúdo**, antes de cada commit. Calibrada com 8 casos doentes e 12 sãos.
**Nunca imprime o segredo encontrado** — só arquivo, linha e tipo; imprimir faria o log
carregar a chave.

Ela **bloqueou um commit meu**, corretamente: uma URL com credencial embutida num teste
de SSRF <!-- SEGREDO-FALSO-POSITIVO-AUTORIZADO: texto de documentacao sobre a propria guarda; nao ha credencial aqui -->
casava com "credencial em URL". Selado com o motivo, porque a credencial é inventada e o
teste existe para provar que aquela URL é *recusada*.

## Caminhos proibidos

Impede que a pasta `gs/` — o acervo do projeto original — entre no versionamento. O
repositório é **público**, e aquele acervo tem credenciais antigas. Calibrada com 3
caminhos proibidos e 6 legítimos. Hoje: **0 arquivos de `gs/` rastreados**.

## UTC no sistema inteiro

Só uma classe pode ler o relógio do sistema. A catraca **me pegou** quando usei
`Instant.now()` direto no cache do noticiário — e estava certa.

Há também uma catraca de arranque: se a JVM não estiver em UTC de offset **zero e fixo**, o
sistema **não sobe**. `Europe/London` é recusado de propósito: rende `Z` no inverno e
`+01:00` no verão, e um invariante que vale metade do ano não é invariante.

## Exceção específica por classe

Nenhuma classe lança exceção genérica. Cada uma tem a sua, com causa-raiz, e o registro
acontece **uma vez**, num só lugar.

## Todas as telas renderizam

Template não é código compilado: uma expressão errada dentro de um `{#if}` fica invisível
até aquele ramo acontecer.

Esta guarda nasceu de um defeito real — uma extensão inexistente na paginação, escondida
porque a base tinha só quatro clientes e o bloco nunca era desenhado. Por isso o teste
**cria 22 registros antes de olhar**, para a paginação ser realmente renderizada.

Calibração: o defeito foi reintroduzido de propósito, e a guarda **reprovou**.

## Log por execução, com faxina

Cada execução escreve o próprio arquivo, carimbado. A faxina apaga por idade (30 dias) e
por teto de contagem, **recusa pasta não exclusiva**, nunca apaga o arquivo da execução
corrente, e conta arquivos com data no futuro como relógio suspeito.

## Guarda de CSS em porcentagem

A regra do projeto é largura em `%`, conteúdo preenchendo a tela, `px` só em borda fina,
sombra e breakpoint de `@media`. O comentário no topo do `base.css` enuncia isso — e
comentário é intenção: ninguém o executa, e a próxima folha nasce sem ele.

A guarda impõe três invariantes: nenhum `px` em largura/altura/espaçamento/posição, nenhum
`auto-fill` em grid, e nenhum `max-width` + `margin: 0 auto` centralizando container.

**A primeira versão acusou três violações que eram o texto da regra dentro de comentários.**
Um instrumento que lê comentário não está medindo código — ele teria mandado "corrigir" a
documentação da própria regra. A varredura passou a remover comentários antes de medir.

Depois de calibrada, a contagem real do projeto foi **3 ocorrências**, todas fios de cabelo
de `2px` no gráfico, convertidas para `rem` — que escala com a fonte do leitor. As demais 73
ocorrências de `px` são a exceção declarada, e continuam onde estão.

A guarda se calibra em toda execução, nos dois sentidos: precisa **reprovar** os três
defeitos num CSS sintético doente, e **não acusar nada** num CSS são que contém borda,
sombra, breakpoint e o texto da regra em comentário. Sem as duas metades, o `0` dela é
indistinguível de uma expressão regular quebrada.
