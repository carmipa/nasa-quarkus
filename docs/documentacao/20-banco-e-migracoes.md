# Banco e migrações

**SQLite 3.50**, arquivo único em `data/nasa.db`. O esquema é construído por migrações
versionadas, aplicadas no arranque, com **checksum imutável**.

Era PostgreSQL 17 até a troca de agosto de 2026. O porquê, o que se perdeu e a recomendação
que **não** venceu estão em [De PostgreSQL a SQLite](/documentacao/postgres-sqlite).

## As migrações

| versão | o que fez |
|---|---|
| **V001** | esquema inicial — 4 tabelas e 7 índices, com as invariantes dentro do banco |
| **V002** | `DROP` de `alerta_enviado` e `inscrito` — o sistema deixou de guardar gente |

Sobraram **três tabelas**: `evento_natural`, `telemetria_operacao` e a própria
`esquema_migracao`. Conferido no banco em 03/09/2026:

```
sqlite> select name from sqlite_master where type='table';
esquema_migracao
evento_natural
telemetria_operacao

sqlite> select versao, nome from esquema_migracao;
1|esquema_inicial
2|sem_cadastro_nem_fila
```

A ordem dos `DROP` na V002 importa: `alerta_enviado` primeiro. A chave estrangeira aponta
naquele sentido, e a ordem inversa **falha com o banco cheio e passa com o banco vazio** — o
pior tipo de migração, a que só quebra em produção. O motivo da remoção está em
[Sem cadastro](/documentacao/sem-cadastro).

### Uma inconsistência conhecida, e por que ela fica

A coluna `esquema_migracao.aplicada_em` é gravada com `Instant.toString()` cru, e não pelo
`InstanteEmTexto` que padroniza todas as outras datas do sistema. O resultado tem
nanossegundos e **largura variável**:

```
aplicada_em em esquema_migracao:  2026-09-03T12:20:05.992346500Z   (30 caracteres)
o padrão do projeto:              2026-09-03T12:20:05Z             (20, fixos)
```

**Não foi corrigida, de propósito.** Duas razões, e a segunda é a que decide:

1. **Nada ordena essa coluna** — a ordem das migrações é o `versao INTEGER PRIMARY KEY`. O
   valor continua sendo UTC e continua terminando em `Z`, então nada lê errado hoje.
2. **Trocar o formato agora deixaria a mesma coluna com duas larguras.** As linhas já
   gravadas ficariam em nanossegundos e as novas em segundos — e aí a comparação como texto
   passaria a ser realmente traiçoeira, porque `...05.992Z` ordena **antes** de `...05Z`. Uma
   coluna uniformemente verbosa é melhor que uma coluna misturada.

Fica como **risco residual declarado**: quem for ordenar ou comparar `aplicada_em` como texto
precisa saber disto antes, e é para isso que está escrito aqui.

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

**Não há credencial de banco para guardar.** O SQLite é um arquivo no disco: quem pode ler
o arquivo tem o banco, e quem não pode não tem — a permissão do sistema de arquivos é a
autenticação. Isso apagou uma classe inteira de risco (senha em `application.properties`,
em variável de ambiente, no log de arranque, no histórico do shell) e criou outra, que fica
declarada: **um arquivo não tem controle de acesso por usuário nem por tabela**.

Continuam valendo, para as demais credenciais do sistema:

- **nenhum valor padrão em produção.** Faltando qualquer uma, o arranque cai. Subir com
  segredo adivinhável é pior que não subir.
- **a guarda de segredos** varre o repositório a cada execução, e **nunca imprime o
  conteúdo** do que encontra — só arquivo, linha e tipo. Guarda que ecoa o segredo o copia
  para o log de CI, que costuma ser mais exposto que o repositório.
