# De SQLite a PostgreSQL

O projeto começou em SQLite, por decisão explícita: trocar o Oracle do original por algo
sem servidor, sem credencial e sem cota.

Meio caminho andado, a pergunta voltou — e a resposta mudou por um motivo que não era
técnico.

## Os três problemas que o SQLite *não* causou

No primeiro `quarkusDev` de verdade, três defeitos apareceram juntos. A pergunta natural
foi: o PostgreSQL teria evitado?

| problema | evitaria? |
|---|---|
| `data/` não existia → `SQLITE_CANTOPEN` | **Não.** Trocaria por `connection refused`, `database does not exist` ou `password authentication failed`. A classe é a mesma — ambiente não pronto com mensagem que não ensina a arrumar — e o PostgreSQL tem **mais** maneiras de não estar pronto. |
| erro de conexão reportado como "o banco recusou o DDL" | **Não.** 100% meu: um `try` juntando abertura e comando. |
| log em `-03:00` com a API em `Z` | **Não.** Nada a ver com banco. |

**Zero dos três.**

## O que realmente decidiu

A pergunta certa não era sobre defeitos, era sobre destino: **onde isto vai rodar?**

VPS, com usuários autenticados. E SQLite impede mais de um processo escrevendo. Foi isso.

## O custo, medido

```
código específico de SQLite:  4 arquivos (~670 linhas)
domínio + casos de uso:       0 linhas
```

As únicas menções a SQLite em `domain` e `application` eram **Javadoc**. Foi exatamente
isso que a arquitetura de portas comprou.

E o custo **cresce a cada migração nova**: havia uma; com cinco ou seis, cada uma
precisaria de gêmea. Se era para trocar, aquele era o momento mais barato que existiria.

## As duas diferenças que não dão erro nenhum

Estas são as perigosas — a portabilidade "literal" as atravessa sem reclamar:

**`LIKE` virou `ILIKE`.** No SQLite o `LIKE` ignora caixa; no PostgreSQL não. `paulo`
deixaria de achar "Paulo" — sem exceção, sem log, só lista vazia que parece "não existe".

**Duplicata deixou de ser detectada por texto.** Procurar a palavra `UNIQUE` na mensagem
depende do fornecedor: muda de versão para versão e é traduzida pelo idioma do servidor.
Agora é `SQLSTATE 23505` mais o **nome** da restrição.

## O que se ganhou de tipo

- **`TIMESTAMPTZ`** — o UTC passa a morar no tipo, reforçando no armazenamento o mesmo
  invariante que se descobriu quebrado no log naquele dia.
- **`DATE`** — recusa `2026-02-31`, que o `CHECK` de posição de hífen deixava passar.
- **`GENERATED ALWAYS AS IDENTITY`** — ninguém grava id à mão.

## O que se perdeu

**A execução sem dependência nenhuma.** `gradlew test` e `quarkusDev` agora exigem Docker.
É permanente, e é o preço.

## O que NÃO mudou, de propósito

A unicidade de e-mail continua sensível a maiúsculas, como já era. Mudar regra no meio de
uma portabilidade faz com que, diante do próximo defeito, ninguém saiba se veio da troca de
banco ou da regra nova.
