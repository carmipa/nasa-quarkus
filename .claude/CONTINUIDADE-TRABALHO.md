# CONTINUIDADE-TRABALHO — nasa-quarkus

TAREFA ORIGINAL
  (1) `.gitignore` de bloqueio de segurança para nenhuma chave de API vazar;
  (2) auditoria bit a bit do `gs/` (GS FIAP 2025/1 — NASA);
  (3) plano de reconstrução em Quarkus/Java 25 + HTMX + SQLite, com arquitetura
      de desacoplamento total (padrão KRONOS / ASPM / Framework Net / binmapper).

OBJETIVO FINAL
  Sistema de consulta e alerta de desastres naturais reescrito do zero: 33 rotas
  e 12 telas em paridade, sem Node, sem Oracle, sem Spring.

CRITÉRIO DE ENCERRAMENTO
  Fila do `docs/PLANO-MESTRE.md` §8 fechada item a item, cada um com artefato.

BRANCH / COMMIT BASE
  main @ 89feabf ("Initial commit"). **Nada commitado nesta sessão** — ver
  "DECISÃO PENDENTE" abaixo.

SESSÃO / PORTÃO
  Sessão 0554bf29 · portão de arranque rc=0 (comprovante desta sessão).
  Máquina medida: DESKTOP-QDNQHL1 (desktop AMD).

---

## FILA ORDENADA DO ESCOPO

- [x] **0. Bloqueio de segredos** — `.gitignore` + `guardas/guarda-segredos.ps1`
      + hook `pre-commit`
- [x] **1. Prova de viabilidade do SQLite** em Quarkus 3.39.1 / Java 25
- [x] **1-bis. `gs/` fora do versionamento** — `.gitignore` + guarda de caminhos
- [x] **2. Esqueleto arquitetural** (`core` / `peer` / `fatia`) + guarda ArchUnit
      calibrada contra 3 violações plantadas
- [ ] 3. Peer `persistencia` (migração, WAL, PRAGMA, transação)
- [ ] 4. Esquema + invariantes no banco (UNIQUE de A4, UTC, complemento opcional)
- [ ] 5. Peer `geo` (bounding box e distância)
- [ ] 6. Fatia `cliente`
- [ ] 7. Fatias `contato` e `endereco`
- [ ] 8. Fatia `eventoEonet`
- [ ] 9. Fatia `estatistica`
- [ ] 10. Fatia `alerta` (outbox + idempotência)
- [ ] 11. Painel: 12 telas em Qute + HTMX
- [ ] 12. Guardas restantes no endereço único

---

## FECHADO COM ARTEFATO

**Item 0 — bloqueio de segredos**

```
$ git check-ignore -v gs/.../gsApi/appsettings.json
.gitignore:200:gs/Advanced_Business_Development_with.NET/gsApi/gsApi/appsettings.json

Caso-controle do .gitignore: 9/9 devem-ignorar corretos, 8/8 não-devem-ignorar
corretos (a 1ª rodada acusou next-env.d.ts, corrigido: em .gitignore vence a
ÚLTIMA regra que casa, e a exceção estava acima da regra genérica).

Controle POSITIVO em produção (commit real com chave sintética):
  [ACHADO] CONTROLE-POSITIVO-TEMP.md:2 - Google API key (AIza.)
  [X] GUARDA DE SEGREDOS REPROVOU - 1 achado(s).
  pre-commit: COMMIT BLOQUEADO
  $ git log --oneline -1  ->  89feabf Initial commit   (HEAD intacto)

Varredura da árvore: 292 arquivos, 46.815 linhas, 0 achados.
Calibração: 8 casos doentes reprovados, 12 casos sãos aprovados.
```

**Item 1 — viabilidade do SQLite**

```
$ ./gradlew test --tests '*SqliteViabilidade*' --rerun-tasks
tests="5" skipped="0" failures="0" errors="0"

[VIABILIDADE] banco=SQLite versao=3.50.1 driver=SQLite JDBC 3.50.1.0
[VIABILIDADE] PRAGMA foreign_keys = 1
[VIABILIDADE] gravou e leu: EONET_1001 / Queimada sintetica
[VIABILIDADE] duplicata recusada pelo banco:
    [SQLITE_CONSTRAINT_UNIQUE] A UNIQUE constraint failed (spike_unico.eonet_id)
[VIABILIDADE] orfao recusado pelo banco:
    [SQLITE_CONSTRAINT_FOREIGNKEY] A foreign key constraint failed

Suíte completa: GreetingResourceTest 1/0/0 + SqliteViabilidadeTest 5/0/0.
```

**Item 1-bis — `gs/` fora do versionamento**

```
$ git check-ignore -v gs/README.md
.gitignore:249:gs/     gs/README.md

CONTROLE POSITIVO: `git add -f` para contornar o .gitignore, e commit real:
  [PROIBIDO] gs/Java_Advanced/README.md
  [PROIBIDO] gs/README.md
  [X] CAMINHO PROIBIDO no indice — 2 arquivo(s).
  pre-commit: COMMIT BLOQUEADO
  $ git log --oneline -1  ->  89feabf Initial commit   (HEAD intacto)

CONTROLE NEGATIVO: 23 arquivos legitimos staged -> as duas guardas PASSARAM.
Calibracao: 3 proibidos recusados, 6 legitimos aprovados (inclui `src/.../logs/`,
que contem "gs" mas nao comeca com "gs/" — a ancora `^` existe por causa disso).
```

**Item 2 — esqueleto + guarda de fronteira**

```
$ ./gradlew test --tests '*Fronteira*' --rerun-tasks
[FRONTEIRA] classes analisadas: 5
[FRONTEIRA] core, peer e fatia: os tres tem classe — nenhuma regra e vazia
tests="6" skipped="1" failures="0"

CALIBRACAO — 3 violacoes plantadas de proposito:
  regra 1 (core -> fatia) ............ FAILED  <- correto
  regra 3 (fatia -> fatia) ........... FAILED  <- correto
  regra 4 (domain -> jakarta) ........ FAILED  <- correto
  regra 2 (peer -> fatia) ............ PASSED  <- correto: nao plantei esta,
                                                  e a guarda NAO reprovou em bloco
Violacoes removidas -> tudo verde de novo.

Regra 5 fica SKIPPED, declarada "NAO VERIFICADO: ainda nao existe classe em
`..application..`". O ArchUnit recusa julgar conjunto vazio e esta certo; a saida
errada seria allowEmptyShould(true), que viraria "nao havia o que examinar" em verde.

SUITE: 20 testes · 1 pulado (declarado) · 0 falhas
  GreetingResourceTest 1 · SqliteViabilidadeTest 5 · Fronteira 6 · Coordenada 8
```

**Dois defeitos meus nesta etapa, os dois pegos pelo proprio instrumento:**
o piso de classes contava `package-info.class` que **o javac nao emite** quando o
arquivo so tem Javadoc (reprovou dizendo "5 classes" — a causa era o instrumento,
nao o codigo medido); e a regra 5 reprovava por conjunto vazio, que virou o
terceiro estado em vez de virar `allowEmptyShould`.

---

## EM ANDAMENTO AGORA

Nada em andamento. Itens 0, 1, 1-bis e 2 fechados com artefato.

## PRÓXIMA AÇÃO EXECUTÁVEL EXATA

**Item 3 — peer `persistencia`.** Na ordem:

1. Criar `org.nasa.peer.persistencia` com o gerenciador de migração: arquivos
   `V001__esquema.sql` em `src/main/resources/db/migracao/`, aplicados na ordem,
   com tabela de controle e **checksum** (migração aplicada é imutável).
2. Confirmar em runtime, com teste, que a conexão chega com
   `journal_mode=WAL`, `foreign_keys=1` e `busy_timeout` — os três já estão na
   URL do datasource, mas *estar na configuração não é estar na conexão*, e o
   `SqliteViabilidadeTest` só provou o `foreign_keys`.
3. Rodar `--rerun-tasks` sempre (falso-verde de cache no teste de arquitetura).

Comando de conferência antes de qualquer commit:
`pwsh -NoProfile -File guardas/guardas.ps1 -Modo arvore`

## TESTES / GUARDAS

- Endereço único: `pwsh -NoProfile -File guardas/guardas.ps1 -Modo arvore`
- Hook instalado: `git config core.hooksPath .githooks` (feito nesta árvore;
  **cada clone novo precisa refazer** — hook não viaja no clone).
- Suíte: `./gradlew test --rerun-tasks`

## GAPS E BLOQUEIOS REAIS

- 🟡 **A chave do Google já estava invalidada havia tempo** — informado por Paulo
  em 2026-09-02. `Inferência a partir de informação do dono, não evidência
  direta`: não testei a chave, e de propósito (testá-la exigiria transmiti-la ao
  Google). Continua bloqueada para o git; o bloqueio não existe por causa dela,
  existe por causa da próxima.
- 🟡 **A credencial Oracle da FIAP também está encerrada** — curso de TDS já
  concluído, fora de uso, senha trocada várias vezes desde então (informado por
  Paulo em 02/09). Não testada, e por dois motivos: é informação do dono, e
  testá-la seria conectar a sistema de terceiro sem autorização.
  **A1 fechado: nenhuma das duas credenciais está viva.**
- 🟡 O legado **não é executável** (sem `application.properties`, achado A3):
  paridade em runtime contra o legado é impossível; a paridade se prova contra o
  DDL, o código e os DTOs.
- 🟡 `Agroal does not support detecting if a connection is still usable after an
  exception for database kind: other` — mitigado com `validation-query-sql`,
  **não eliminado**. Se aparecer conexão morta no pool, é aqui.

## DECISÃO PENDENTE DO PAULO

1. ~~Revogar a chave do Google~~ — **FECHADO 02/09**: já estava invalidada.
2. ~~Provedor de geocodificação~~ — **DECIDIDO 02/09**: *"ao invés do Google,
   alternativas de código aberto para a mesma função ou melhor"*. Pilha medida em
   `docs/PLANO-MESTRE.md` §6.4. O Google sai do projeto inteiro.
3. Alerta por e-mail (padrão assumido: adaptador de log até haver SMTP).
4. Paridade total (assumida) × escopo reduzido.

## MEDIÇÕES QUE NÃO SE REFAZEM (02/09, `curl` ao vivo)

```
BrasilAPI CEP v2   200 · 297 bytes · 0,23s · endereço + COORDENADA + IBGE + fuso
Nominatim search   200 · 826 bytes · 0,82s
Nominatim reverse  200 · 782 bytes · 0,39s
Photon (Komoot)    200 · 478 bytes · 1,04s
ViaCEP             200 ·           · 1,04s · SEM coordenada  <- por isso o legado
                                                                chamava o Google

Amostra de 6 CEPs na BrasilAPI: 5 com coordenada, 1 SEM (69900000, provedor
`correios`, e sem nem a cidade). O campo `location` NÃO é garantido.
```

## NÃO REPETIR

- Não colocar exceção de `.gitignore` **antes** da regra genérica que ela quer
  anular: vence a última regra que casa. Custou o `next-env.d.ts`.
- Não usar sequência óbvia (`abcdef`, `0123456789`) como caso-controle de chave:
  a lista de placeholders da própria guarda as anula, e o caso doente deixa de
  ser doente.
- Não confiar em `BUILD SUCCESSFUL` como prova de teste executado: ler
  `tests="N"` no XML. Filtro que não casa nada também "passa".
- Não commitar `gs/` sem decisão do Paulo: o `origin` é **público**.
