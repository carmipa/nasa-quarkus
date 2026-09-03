# Defeitos medidos

Cada item aqui foi **medido**, não deduzido. O número é o que separa uma opinião sobre
código de um defeito.

## 456 km — a posição errada do evento

A EONET devolve a trajetória inteira de um evento. O projeto original usava o **primeiro**
ponto, que é onde o evento começou.

```
EONET_23800 (Tropical Storm Marie, 6 pontos)
primeiro ponto  2026-09-01T06:00Z  lat 14.10  lon -108.10
último ponto    2026-09-02T12:00Z  lat 16.80  lon -111.30
distância ..................................  456 km
```

Num alerta de raio 100 km. Avisa quem está longe, cala para quem está perto, e não produz
erro nenhum.

## 140 km — o canto da caixa

Filtrar por caixa delimitadora e parar aí entrega eventos a `raio × √2` do centro. Medido
com raio de 100 km: **o canto está a 140 km**.

## A busca que devolvia a base inteira

Termo sem dígitos produzia `'%%'` no filtro de documento, e `LIKE '%%'` casa com **toda
linha**.

```
antes:  zzzzzz→4   @@@→4   teste→4   ana→4      (a base tinha 4)
depois: zzzzzz→0   @@@→0   teste→1   ana→1
```

A caixa de busca aceitava o texto, respondia rápido, e simplesmente não filtrava.

## `LIKE` sensível a caixa

No SQLite o `LIKE` ignora maiúsculas; no PostgreSQL **não**. Portar literalmente faria
`pesquisar?termo=paulo` parar de encontrar "Paulo" — sem exceção, sem log, só uma lista
vazia que parece "não existe".

## O log em `-03:00`

O invariante "tudo em UTC" valia **só nos testes** — o único lugar onde a flag já estava.

```
produção (jar)  2026-09-02T09:06:13.599-03:00
teste  (flag)   2026-09-02T15:04:19.138Z
```

A API respondia `criadoEm: ...T15:05:51Z` e a linha de log do mesmo instante dizia
`12:05:51-03:00`. Quem cruzasse os dois caçaria três horas de defeito inexistente.

## `SQLITE_CANTOPEN` — a aplicação não subia

O SQLite cria o *arquivo* do banco, nunca o *diretório*. O perfil de teste apontava para
`build/`, que o Gradle cria; o de produção para `data/`, que ninguém criava. **A suíte
exercitava o único caminho que já existia.**

## O BOM que ninguém vê

O feed do GDACS começa com um BOM de UTF-8. O parser recusa com *"o conteúdo não é
permitido no prólogo"* — mensagem que não menciona BOM e manda procurar erro de sintaxe num
XML válido.

**A lição foi sobre o teste, não sobre o código:** o fixture escrito à mão era *mais limpo
que a realidade*, e passava enquanto a home mostrava "noticiário indisponível".

## A expressão escondida num ramo

`{termo.urlEncoded}` — extensão que o Qute não tem — vivia dentro do bloco de paginação,
que só renderiza com mais de uma página. Com quatro clientes na base, o bloco **nunca foi
desenhado**: passou nos testes, passou no uso à mão, e ficou esperando o quinto cadastro.

## O nível do alerta que o título não conta

O item **"Orange flood alert in Nepal"** tem `<gdacs:alertlevel>Red</gdacs:alertlevel>`. O
GDACS **elevou** o nível e não reescreveu o título. Quem confiasse no texto mostraria
laranja para o que a fonte já classifica como vermelho — subestimando o evento exatamente
quando ele piorou.

## Uma fonte que simplesmente morreu

```
api.reliefweb.int/v1   HTTP 410  "v1 has been decommissioned"
api.reliefweb.int/v2   HTTP 403  "not using an approved appname"
```

O carrossel de notícias do projeto original **não funciona hoje**, e não é por causa da
reescrita.

## E um engano meu

Achei que `99999999` fosse um CEP falso e que a BrasilAPI estivesse errada ao responder
`200`. Medi: é **real** — Sarandi/PR. Quase "corrigi" um comportamento correto.

## O teto que truncava 903 eventos, com o aviso calado

A sincronização por ano tinha um teto de 6000 eventos e um aviso para quando ele fosse
atingido. Medido em 02/09/2026, contra a API real:

```
o que a EONET tem de 2026 ....... 6900 eventos
o que o sistema gravou .......... 5997 eventos
o aviso de truncamento .......... nao disparou
```

O aviso comparava `lidos.size() >= limite` — a lista **já filtrada**. Como um evento torto
é pulado durante a leitura, a lista sai menor que o corpo: a API devolveu 6000 (truncando
900), três vieram tortos, a lista ficou com 5997, e `5997 >= 6000` é falso.

O que torna este defeito pior que a truncagem em si: **a guarda falhava exatamente no caso
em que ela existe para servir**. Quanto mais dado a API tem, mais provável que algum venha
torto — e mais provável que o alarme de excesso de dado se cale.

A correção compara contra `recebidos`, o número de eventos no corpo antes de filtrar. O
teto subiu para 20.000. Ressincronizando 2026: **883 eventos novos** entraram, e 6014
foram atualizados sem duplicar.

O teste que trava isso foi calibrado reintroduzindo o defeito — reprovou; restaurado —
passou.

## A expressão que virou texto na tela, com status 200

O ícone foi escrito como `{'historico'.icone.raw}`, apostando numa `@TemplateExtension`.
O Qute **não reconhece expressão que começa por aspas**. Ele não falhou: imprimiu

```
{'historico'.icone.raw}
```

como texto literal na página, com **HTTP 200** e sem uma linha de log.

Nenhum dos testes existentes pegaria — todos conferem `statusCode == 200`, e o 200 estava
lá. É a mesma lição, terceira vez neste projeto: **200 não prova que a página está certa**.
A verificação passou a medir o HTML, e há guarda para as duas metades — que o `<svg>` seja
tag e não texto escapado, e que nenhuma expressão de template vaze para a página.

## A dica que eu mesmo esqueci de ligar

O componente de dica exigia `class="dica"` **junto** de `data-dica="..."`. No primeiro
arquivo em que usei o componente — o menu do layout —, escrevi o atributo e esqueci a
classe. A explicação estava escrita, e nada aparecia. Sem erro, sem aviso.

Se quem acabou de escrever o componente esquece a metade, todo mundo esquece. O seletor
passou a ser `[data-dica]`: escrever a explicação **é** ligá-la, e não sobra segunda metade
para esquecer. O tracejado que anuncia a dica virou classe separada e opcional, porque um
item de menu não pode receber sublinhado por baixo da borda de seção ativa.
