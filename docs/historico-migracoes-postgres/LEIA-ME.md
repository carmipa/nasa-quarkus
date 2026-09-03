# As migrações da era PostgreSQL

Estes quatro arquivos **não rodam mais**. Estão aqui como registro, não como código.

## Por que foram substituídos, e não corrigidos

Migração aplicada é **imutável** — é uma regra dura deste projeto, e ela não foi
quebrada por descuido. Duas coisas mudaram ao mesmo tempo, e as duas invalidam o
histórico anterior:

1. **O motor do banco.** Passou de PostgreSQL para SQLite (decisão do Paulo,
   03/09/2026, por portabilidade). `TIMESTAMPTZ`, `GENERATED ALWAYS AS IDENTITY` e
   `AT TIME ZONE` não existem no SQLite — nenhuma destas migrações executa lá.
2. **O modelo.** As fatias `cliente`, `contato` e `endereco` saíram do projeto e
   foram substituídas por `inscrito`. Cinco das nove tabelas deixaram de existir.

Um histórico que não pode ser reproduzido do zero não é histórico: é decoração. A
regra da imutabilidade protege bancos **em produção**, e este ainda não está em
nenhum — não há uma linha de dado real para migrar.

## O que se preservou

O raciocínio. Cada invariante que estes arquivos documentavam foi reescrita no
esquema novo, com o mesmo motivo e a mesma cicatriz:

- `evento_eonet_id_unico` — a garantia que no legado morava só no Java, e deixava
  duas sincronizações simultâneas inserirem o mesmo evento duas vezes;
- `evento_coordenada_na_terra` — coordenada fora do planeta recusada pelo banco;
- `alerta_terminal_tem_instante` — alerta "ENVIADO" sem saber quando não fecha auditoria;
- a contagem de telemetria agregada por hora, com soma/mínimo/máximo em vez de média.

O que **não** se preservou está declarado no esquema novo: o `AT TIME ZONE` saiu,
e no lugar dele há `CHECK` exigindo que todo instante termine em `Z`.
