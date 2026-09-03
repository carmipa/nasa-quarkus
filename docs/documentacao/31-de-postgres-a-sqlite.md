# De PostgreSQL a SQLite

O projeto nasceu em SQLite, passou para PostgreSQL, e voltou. As duas trocas foram medidas
antes de acontecer, e esta página registra o que cada uma custou — inclusive onde a
recomendação foi ignorada, e por quê isso está certo.

## A pergunta que abriu a volta

> *"Em termos de portabilidade e funcionamento fácil e imediato, totalmente dockerizado,
> vale mais a pena usarmos o SQLite?"*

A resposta exigiu contar as construções específicas do PostgreSQL no código. O primeiro
instrumento deu **87** — e estava sujo: `FILTER (` casava com `.filter(` do Java, e `now()`
com `Instant.now()`. Medindo **só dentro de SQL**, deram 35. E separando honestamente:

| | quantas |
|---|---|
| o SQLite **moderno faz** — tradução mecânica | 20 |
| o SQLite **não faz** — exige repensar | 15 |

`FILTER (WHERE)` existe desde a 3.30; `ON CONFLICT DO UPDATE` desde a 3.24; `RETURNING`
desde a 3.35. `LEAST`/`GREATEST` viram `MIN`/`MAX` com vários argumentos. `date_trunc` e
`EXTRACT` viram `substr` da string ISO.

Sobraram três: `TIMESTAMPTZ` (11 usos), `AT TIME ZONE` (3) e `xmax = 0` (1).

**E todo o `ILIKE` morava em `cliente` e `contato`** — as fatias que saíram no mesmo
trabalho. Esse custo desapareceu sozinho.

## A recomendação foi outra, e está registrado

A recomendação era **ficar no PostgreSQL** e resolver a dor real por outro caminho — a dor
não era o motor, era o Dev Services perdendo o contêiner (21.542 eventos, duas vezes num
dia). `docker-compose` mais volume nomeado dariam um comando, portabilidade e dado que não
some, sem reescrita alguma.

A decisão foi do Paulo, e foi pelo SQLite. Registrar a divergência importa: quem ler este
código daqui a um ano precisa saber que a troca foi **escolha informada**, não desconhecimento
do custo.

## O que se perdeu, e o que substituiu

**O SQLite não tem tipo de data.** Datas são TEXT, REAL ou INTEGER, e ele aceita qualquer
coisa em qualquer coluna. A disciplina de UTC deste projeto morava no **tipo** da coluna — e
tipo é mecanismo: não depende de ninguém lembrar.

No lugar, três coisas, e as três são do banco:

1. **instante é texto ISO-8601 terminado em `Z`** — `2026-09-03T01:23:45Z`, sempre 20
   caracteres;
2. **`CHECK (coluna LIKE '%Z')` em toda coluna de instante** — gravação em hora local é
   **recusada pelo banco**, não aceita em silêncio;
3. **largura fixa faz a ordem alfabética coincidir com a cronológica** — `ORDER BY`, `MIN` e
   `MAX` continuam corretos sem função de data, e `substr(x,1,4)` é o ano.

O truncamento em segundos não é detalhe: `Instant.toString()` **omite** a fração quando ela é
zero, e a largura variável inverteria a ordem de dois eventos separados por um décimo de
segundo.

## Dois defeitos que a troca produziu, e como apareceram

### A migração aplicou 1 objeto de 9 e disse "aplicada"

O driver do SQLite executa o **primeiro** comando de um script e ignora o resto — **sem
erro**. O log dizia `aplicadas=1`, e o banco tinha uma tabela de nove.

O pior não foi a aplicação parcial: foi a migração ter ficado **marcada como aplicada**. O
segundo arranque não a repetiria, e o banco ficaria pela metade para sempre, com a evidência
disponível afirmando que estava tudo certo.

A correção divide o script sabendo quando o `;` está dentro de literal, de identificador
entre aspas ou de comentário — o comentário anterior avisava que `split(";")` é bomba-relógio,
e estava certo. **A contagem é verificada**: zero comando é defeito, não migração vazia.

### A detecção de inseriu-vs-atualizou errava dentro do mesmo segundo

O `xmax = 0` do PostgreSQL não tem equivalente. A primeira substituição comparava
`criado_em = sincronizado_em` — e o instante trunca em segundos, então duas sincronizações no
mesmo segundo gravavam o mesmo texto e a comparação dizia "inseriu".

**O teste de idempotência pegou na primeira execução.** Virou um contador `versao`, que não
depende de resolução de relógio nenhuma — e de quebra responde algo que ninguém sabia:
quantas vezes a NASA republicou cada evento.

## O que ficou provado, rodando

```
CEP 01310100 ........ -23,5617698  -46,6553299   (Avenida Paulista)
instante gravado .... 2026-09-03T12:11:40Z
e-mail repetido ..... "você já está inscrito", 200, uma linha só
sincronizar 2023 .... 271 trazidos · 271 novos · 0 atualizados
a MESMA de novo ..... 271 trazidos · 0 novos · 271 atualizados
duplicatas .......... 0
instantes fora do formato ... 0
```

## O que continua valendo da primeira troca

O defeito que motivou a passagem original para PostgreSQL — `SQLITE_CANTOPEN` no arranque —
**não era do SQLite**. Foi medido na época: zero dos três defeitos daquele dia eram do motor.
A causa era o SQLite criar o **arquivo** e nunca a **pasta**, e a correção
(`PreparadorDoArquivoSqlite`) voltou junto com ele.
