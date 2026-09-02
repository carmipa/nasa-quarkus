# Banco e migrações

**PostgreSQL 17.** O esquema é construído por migrações versionadas, aplicadas no
arranque, com **checksum imutável**.

## As migrações

| versão | o que fez |
|---|---|
| **V001** | esquema inicial — 7 tabelas, com as invariantes dentro do banco |
| **V002** | o tipo de contato virou conjunto fechado, com backfill antes do `CHECK` |
| **V003** | o evento passou a saber quando **acabou** (`encerrado_em`) |

## Checksum imutável

Editar uma migração já aplicada **aborta o arranque**. E a verificação roda **inteira**
antes de aplicar qualquer coisa: abortar no meio deixaria o banco num estado que ninguém
pediu.

O índice das migrações é um arquivo **declarado**, não uma varredura de diretório. Varredura
muda de resultado entre a IDE e o jar, e ordem de DDL que depende do empacotamento produz
bancos diferentes a partir do mesmo código. Arquivo esquecido no índice falha alto, em vez
de simplesmente não ser aplicado.

### Prova de idempotência

```
declaradas=3 aplicadas=3 jaEstavam=0   ← primeira execução
declaradas=3 aplicadas=0 jaEstavam=3   ← segunda: aplicou ZERO
```

## O que o esquema garante, e o legado não garantia

O projeto original tinha as regras certas *no Java* e **nenhuma constraint `UNIQUE` além
das chaves primárias** no DDL — conferido nos três arquivos de DDL da entrega de 2025.
Regra que só existe no Java some quando duas execuções acontecem ao mesmo tempo, ou quando
alguém escreve pelo caminho de baixo.

| invariante | como é protegida |
|---|---|
| documento identifica um cliente | `UNIQUE (documento)` |
| e-mail identifica um contato | `UNIQUE (email)` |
| evento da NASA existe uma vez | `UNIQUE (eonet_id)` |
| um aviso por cliente e evento | `UNIQUE (cliente_id, evento_id)` |
| coordenada é indivisível | `CHECK` — ou os dois campos, ou nenhum |
| nada no null island | `CHECK NOT (latitude = 0 AND longitude = 0)` |
| estado terminal tem instante | `CHECK situacao = 'PENDENTE' OR concluido_em IS NOT NULL` |

Cada uma tem **teste negativo**: o teste tenta violar e exige que o banco recuse.

## Tipos, e o que eles compram

- **`TIMESTAMPTZ`** para todo instante. O UTC passa a morar no *tipo* — nem o banco aceita
  hora ambígua.
- **`DATE`** para data de nascimento. O tipo recusa `2026-02-31`, que o `CHECK` de posição
  de hífen do SQLite deixava passar.
- **`BIGINT GENERATED ALWAYS AS IDENTITY`** — o `ALWAYS` impede gravar id à mão e colidir
  com a sequência muitos registros adiante.

### Um tipo que deliberadamente NÃO mudou

`json_original` continua `TEXT`, e não virou `JSONB`. É cópia forense da resposta da NASA:
se um dia ela mandar algo malformado, é exatamente esse payload que se vai querer ler.
`JSONB` recusaria a inserção e descartaria a única prova do problema.

## Credenciais

**Nenhuma neste repositório, por construção.**

- **dev e teste** — Dev Services sobe um contêiner PostgreSQL e gera credencial efêmera;
- **produção** — só variável de ambiente, **sem valor padrão**. Faltando qualquer uma, o
  arranque cai. Subir com senha adivinhada é pior que não subir.
