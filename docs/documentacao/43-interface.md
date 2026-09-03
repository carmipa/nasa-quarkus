# Interface — ícones, dicas e a regra da porcentagem

Três decisões de tela que valem ser ditas, porque cada uma descartou a escolha mais comum
por um motivo medido.

## Ícones: catálogo em código, SVG embutido

40 ícones vivem em `Icones.java`, como constantes. Nenhuma requisição, nenhum pacote,
nenhum CDN.

As três alternativas usuais foram descartadas, cada uma por uma razão própria:

| alternativa | por que não |
|---|---|
| fonte de ícones por CDN | o projeto não fala com CDN. Uma tela de alerta que depende de servidor de terceiro no ar para desenhar o ícone de perigo tem dependência externa **no pior momento possível** |
| emoji | muda de desenho por sistema operacional, e alguns viram retângulo vazio. Ícone de aviso que aparece como caixinha não avisa nada |
| arquivos `.svg` soltos | cada ícone vira uma requisição, e um ícone renomeado some da tela sem erro — exatamente como o documento que sumia do índice da documentação |

Duas propriedades fazem o CSS por ícone ser desnecessário: `stroke: currentColor` faz o
desenho herdar a **cor** do texto ao redor, e `width/height: 1em` faz herdar o **tamanho**
da fonte. Um ícone dentro de um botão de perigo fica vermelho sozinho.

**Ícone desconhecido não é silêncio.** Um nome com erro de digitação devolve um triângulo
de aviso visível, nunca string vazia — porque um ícone que some é indistinguível de um
layout correto, e ninguém conta os ícones de uma tela.

**Nenhum ícone aparece sozinho.** Todos têm `aria-hidden="true"` e acompanham texto: ícone
sem palavra é adivinhação para quem enxerga e silêncio para quem usa leitor de tela.

## Dicas: por que não o atributo `title`

Cada campo do sistema explica o que faz ao receber o mouse ou o foco. A implementação é
CSS puro sobre `data-dica`, e não o `title` nativo, que parece resolver e não resolve:

1. leva ~1,5 s para aparecer, e some sozinho depois de alguns segundos;
2. **não aparece nunca no teclado** — quem navega por Tab jamais o vê;
3. **não aparece em toque nenhum** no celular;
4. não se pode estilizar, e fica ilegível sobre tema escuro.

Os itens 2 e 3 não são estética: são gente que simplesmente não recebe a informação. Por
isso toda dica responde a `:hover` **e** a `:focus-visible`, e o portador é sempre um
elemento focável.

O texto da dica diz o que o campo **faz**, nunca repete o nome dele. "Documento: documento"
não informa; "é a chave única do cadastro, e é por ele que a busca encontra a pessoa"
informa.

## A regra da porcentagem

Largura, altura, espaçamento e posição em `%`, `rem`, `em` ou `vmin`. `px` só em borda
fina, sombra e breakpoint de `@media` — as exceções declaradas pela própria regra.

Medido no projeto depois de calibrar o instrumento: **3 ocorrências** fora das exceções,
todas fios de `2px` no gráfico, convertidas para `rem`, que escala com a fonte do leitor.

Há [guarda executável](/documentacao/guardas) para isso, e ela se calibra em toda execução.

## O que a tela recusa fazer

**Nenhum gráfico usa biblioteca.** O legado carregava Chart.js e react-chartjs-2 — dois
pacotes, ~200 KB — para desenhar barras. Barra é um retângulo com largura proporcional: o
CSS faz numa linha, o servidor já sabe o maior valor, e o resultado é legível sem
JavaScript, imprimível e navegável por teclado. Um `<canvas>` é, para um leitor de tela, um
retângulo mudo.

**Nenhuma conta acontece no template.** O Qute não faz aritmética em expressão — medido,
`Method "*(100)" not found` — e isso é uma sorte: conta dentro de template é regra
escondida num lugar que ninguém testa. Toda porcentagem de barra é calculada no servidor.

**Zero é desenhado, e diferente de "não sei".** Na série de 30 dias, um dia sem evento tem
altura zero e aparece: o buraco é a informação, e falsificar um tracinho apagaria a calmaria
que o gráfico existe para mostrar. No histórico por ano, ao contrário, um ano vazio pode
significar duas coisas opostas — ano calmo, ou ano nunca sincronizado — e a tela as pinta
diferente, com legenda dizendo qual é qual. Desenhá-las igual seria mentir com um gráfico,
que é a pior forma de mentir porque parece medição.

## Cores por tipo de desastre

As 13 categorias da EONET têm **nome em português, cor e ícone** próprios, num catálogo único
(`CategoriasDeDesastre`) usado pelo filtro, pelos gráficos, pelos selos das listas e pelos
pinos do mapa. Uma lista só: duas listas de cores em dois arquivos divergem no primeiro dia
em que alguém mexe numa delas.

**O filtro tinha 8 de 13.** Faltavam poeira, origem humana, neve, extremos de temperatura e
cor da água. Um filtro incompleto não erra — ele simplesmente **nunca mostra** o que ficou de
fora, e ninguém procura o que não sabe que existe. Agora ele vem do catálogo, e há teste que
confere as 13 contra a lista medida na API.

**Cor e ícone, nunca só cor.** Cerca de 8% dos homens não distinguem verde de vermelho. Um
mapa que codifica o tipo apenas na cor não informa essa parcela — e num sistema de alerta,
"não informa" é o defeito inteiro. Cada categoria tem cor **e** ícone; a legenda traz os dois.

**No mapa, a cor passou a dizer o TIPO.** Antes ela dizia em curso ou encerrado, e todos os
pinos ativos eram laranja — um mapa com trezentos pontos idênticos não responde à pergunta
que se faz olhando um mapa. O estado agora é dito pelo **tamanho** e pelo **traço** (cheio em
curso, tracejado encerrado), que são dimensões livres.

A legenda do mapa lista **só as categorias presentes**, não as 13: legenda é chave de leitura
do que está desenhado, não catálogo do possível — e listar vulcão quando não há nenhum é
afirmar algo falso.

Guardas: nenhuma cor se repete (duas categorias da mesma cor são indistinguíveis no mapa),
toda cor é `#rrggbb` válido (o mapa valida por lista de permissão e trocaria uma cor torta
por cinza **sem erro**), e todo ícone declarado existe no catálogo de ícones.

## O que a tela mostrava errado, visto na tela

Estas foram encontradas **abrindo as páginas e olhando**, não lendo código:

| defeito | causa |
|---|---|
| tracejado da dica atravessava o campo inteiro, parecendo borda quebrada | `<label>` é bloco; a `border-bottom` esticava pela coluna toda |
| título e contadores corriam juntos | faltava margem entre o nome da tela e o dado dela |
| barras do gráfico diário fundidas num bloco | espaço de `0.15rem` entre colunas |
| botão "Recalcular" flutuando fora de linha | `align-items: center` centralizava na altura total, que cresce quando um campo tem texto de ajuda |
| atribuição do OpenStreetMap em páginas sem mapa | rodapé fixo no layout, sem condição |
| painel de desastres abrindo vazio com 21.542 eventos na base | a aba que abria era o formulário de sincronização |

## Filtro de tipos no mapa

Chips marcáveis, um por categoria com eventos desenháveis, cada um com ícone, cor e
**contagem**. Nenhum marcado significa **todos** — um mapa que abrisse vazio esperando
escolha seria uma tela em branco pedindo trabalho antes de mostrar qualquer coisa.

**As 13 aparecem sempre**, inclusive as que não têm nada para desenhar. A primeira versão
listava só as 10 com evento desenhável — e as três de fora não estavam vazias: medido,
neve, extremos de temperatura e origem humana têm 3, 14 e 5 eventos na base, todos **sem
coordenada publicada pela NASA**.

Escondê-las repetia, na mesma tela, o defeito que esta documentação já descrevia dois
parágrafos acima: filtro incompleto não erra, ele simplesmente nunca mostra o que ficou de
fora. Quem procurasse "neve" concluiria que a NASA não publica neve — quando ela publica e o
que falta é a posição. O chip inerte diz qual das duas coisas é, e essa é toda a diferença.

**O filtro é do servidor, e essa é a decisão que importa.** Filtrar no navegador filtraria
só os eventos já carregados, e o mapa desenha no máximo 500 de 21.542. Medido em 02/09/2026:

```
vulcões entre os eventos mais recentes ......    0
vulcões na base, com coordenada ............   547
```

Um filtro no navegador teria mostrado mapa vazio e produzido a conclusão de que não há
vulcão nenhum. **Filtro que mente sobre ausência é pior que filtro nenhum** — ele produz uma
conclusão, e a conclusão está errada.

Sendo um `<form method="get">`, o recorte vira URL compartilhável e funciona sem JavaScript.
Cada chip é um `<label>` com `<input type="checkbox">` dentro, e não um botão com JavaScript:
assim ele já é focável, marcável por teclado e anunciado como caixa de seleção — de graça,
por ser o elemento certo. Marcado se distingue por **três** coisas ao mesmo tempo (cor, fundo
e um ✓), nunca só por cor.

Valor inválido na URL é **descartado na borda**: `?categoria=xpto` não vira consulta ao banco
por um valor que nunca casa; o mapa se comporta como se não tivesse sido pedido.

O mapa também **diz quando está no teto** — "desenha no máximo 500 por vez, estes são os mais
recentes". Sem esse aviso, um recorte truncado pareceria o conjunto inteiro.

## O seletor de camadas que estava invisível

Ruas e satélite sempre existiram, e o controle **não aparecia**. Medido: o CSS do Leaflet pede
`images/layers.png`, `layers-2x.png` e `marker-icon.png`, e as três respondem **404** — só o
`.css` e o `.js` foram vendorizados. Fechado, o controle dependia desse ícone e desenhava um
quadrado branco vazio no canto.

Passou a abrir expandido, com os dois rótulos escritos. Duas palavras visíveis valem mais que
um ícone que exige descobrir que dá para clicar — mesmo se o ícone estivesse lá. E os
controles do Leaflet ganharam as cores do sistema: eles nascem claros, para mapas claros, e
sobre o tema escuro ficavam como retângulos berrantes.

## Telemetria

O sistema mede a si mesmo. O log responde *"o que aconteceu naquele momento"*; a telemetria
responde as perguntas de agregado que o log não responde — com que frequência cada operação
roda, qual está lenta, o que está falhando e desde quando.

**Toda requisição é medida por um filtro**, não por instrumentação espalhada. São 40+ métodos
de resource: instrumentar um a um significaria 40 lugares para esquecer, e o primeiro
esquecido seria invisível — uma rota sem telemetria não aparece como zero, ela **não existe**
no gráfico, e ninguém procura o que não sabe que falta.

O nome da operação vem do **padrão da rota**, nunca da URL crua: `/desastres/[id]`, não
`/desastres/15320`. Sem isso haveria uma linha de telemetria por evento do banco — 21.542
para uma tela só.

**Recusa e falha são contadas separadamente.** 4xx é o sistema funcionando (pediram o que não
existe); 5xx é o sistema quebrado. Somá-las num "erros" faria um rastreador varrendo URLs
inexistentes parecer uma pane, e mandaria investigar infraestrutura quando o problema é o
pedido.

**Acumula em memória, descarrega de minuto em minuto.** Uma gravação por operação medida
poria latência de banco dentro de cada chamada observada — e apoio que cobra pedágio da
função que observa acaba desligado no dia em que o sistema fica lento, que é o dia em que ele
mais serve. A descarga também acontece no desligamento, senão todo restart perderia o último
minuto.

O agregado guarda **soma, mínimo e máximo — nunca média**. Média de médias está errada:
agregar uma hora com 1 chamada e outra com 1000 dando peso igual mente. Com soma e contagem,
a média de qualquer janela sai certa, e o máximo revela o caso ruim que a média esconde.

**A página mede a si mesma**, e isso é deliberado: uma tela de telemetria que se excluísse da
telemetria não poderia ser usada para verificar se a telemetria funciona.

### O primeiro defeito que ela encontrou

Nas primeiras horas no ar:

```
GET /                   media 1340 ms   pior 4015 ms
GET /desastres          media   27 ms
GET /clientes/listar    media    9 ms
```

A home era **duas ordens de grandeza** mais lenta que qualquer outra tela. A causa: ela
buscava o noticiário do GDACS dentro da própria requisição — 1 MB e 348 itens, com cache de
10 minutos. Todo primeiro visitante depois de um reinício, e um a cada dez minutos, pagava a
busca inteira antes de ver qualquer coisa.

O noticiário passou a chegar depois, por HTMX. Uma fonte externa lenta, ou fora do ar, deixou
de segurar a porta de entrada do sistema.

## A lista abaixo do mapa: filtro e paginação

Com 500 eventos desenhados, a lista tinha 500 cartões e metros de rolagem. Ninguém percorre
500 cartões procurando um. Agora ela tem **filtro por tipo** e **paginação de 50**.

**Os dois são do navegador, e essa é a decisão que define o bloco.** A lista tem dois papéis
ao mesmo tempo: é a **fonte de dados dos pinos** — o desenhador lê `data-latitude` de cada
item — e é a versão legível do mapa para quem está sem JavaScript.

Paginar no servidor mandaria 50 itens, e o mapa passaria a desenhar **50 pinos em vez de
500**, em silêncio. O mapa é o produto da tela; encolhê-lo para arrumar a lista seria
consertar o menor problema quebrando o maior. Há teste que reprova essa troca, calibrado com
o defeito reintroduzido — e ele cria 60 eventos com coordenada, porque a versão anterior
comparava zero com zero e passava **por vacuidade**.

A alternativa seria mandar os 500 duas vezes: uma escondida para o mapa, outra paginada para
ler. Duas cópias do mesmo dado no mesmo HTML divergem no primeiro dia em que alguém mexer
numa delas.

**São dois filtros diferentes, e a tela diz qual é qual.** O de cima recarrega a página e
muda o que o servidor manda; o de baixo recorta só os cartões, e traz escrito *"só os cartões
— o mapa continua com todos os pinos"*. Eles respondem a momentos diferentes: um escolhe o
recorte, o outro serve a quem já está lendo a lista e não quer voltar ao topo.

O filtro de baixo lista **só os tipos presentes na lista**, ao contrário do de cima, que
mostra as 13. Também de propósito: o de cima é um **controle** ("posso pedir isto ao
servidor"); o de baixo é um **recorte** do que já chegou, e oferecer um tipo ausente daria um
clique que leva a lista vazia.

## O vão preto acima e abaixo do mundo

Com pinos espalhados pelo globo, o `fitBounds` escolhia um zoom baixo — e nesse zoom o
mapa-múndi fica **mais baixo que o contêiner**. O que sobrava não era mar: era o fundo da
caixa, uma faixa preta acima e abaixo do planeta, que parecia mapa não carregado.

Horizontalmente o Leaflet repete o mundo e o problema não existe. Verticalmente ele não pode
repetir — o planeta acaba nos polos. A conta: no zoom `z` o mundo tem `256 × 2^z` pixels de
altura, então preencher exige `z ≥ log2(altura ÷ 256)`. Tomando o maior entre esse e o zoom
escolhido, o mundo sempre preenche e o mapa nunca se afasta mais do que o `fitBounds` queria.
