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
