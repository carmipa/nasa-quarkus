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
- [x] **2. Esqueleto CANÔNICO** + `core` do dia zero + guarda ArchUnit calibrada
      contra 4 violações plantadas
- [x] **2-bis. Log por execução com carimbo** (§9 da planta)
- [x] **2-ter. Telemetria: porta da fatia + contador agiu/absteve** (§10 da planta)
- [x] **2-quater. Exceção específica por classe, com log e telemetria** — ordem de
      Paulo em 02/09, com catraca calibrada
- [x] **2-quinquies. UTC no sistema inteiro** — log, telemetria e domínio, com
      catraca calibrada; retenção de log por 30 dias
- [x] **2-sexies. Home em Qute + HTMX** — relógio de página (UTC + local) e i18n
      automática com bandeiras BR/EUA/Espanha, com guarda de i18n
- [x] **3. Peer `persistencia`** — migração com checksum imutável, WAL/PRAGMA
      provados NA CONEXÃO
- [x] **4. Esquema V001** com as invariantes no banco (UNIQUE de A4, UTC,
      complemento opcional, CHECK do null island)
- [x] **5. Peer `geo`** — haversine conferido contra distâncias reais + caixa
      delimitadora com polo, antimeridiano e locale
- [x] **6. Fatia `cliente`** completa, com concorrência provada (8 simultâneos → 1)
- [x] **7a. Fatia `endereco`** — CEP e geocodificação por provedores abertos
- [ ] 7b. Fatia `contato` (CRUD, espelha `cliente`)
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

**PLANTA CANÔNICA (chegou 2026-09-02, 1366 linhas, lida integral)**

`C:\cerebro_de_ia\cerebro_de_ia\instrucoes\regra-arquitetura-desacoplamento-total-kronos.md`
manda neste projeto. O que ela mudou no que já estava feito:

- **peers e fatias vão na RAIZ do pacote**, sem agrupador `peer.`/`fatia.` (§5.1).
  Reestruturado: `org.nasa.{core,config,geo,endereco}`. Feito com 5 classes no
  disco — é quando custa nada.
- **categoria passa a ser DECLARADA**, porque sumiu do caminho: lista em
  `org/nasa/package-info.java` + `FronteiraArquiteturaTest`, com a regra nova
  **0-bis** reprovando pacote de topo fora da lista.
- **três classes de `core` antes da primeira regra de negócio** (§1-bis Passo 4):
  `ArquivoAtomicoUtil`, `FilaExecucaoPipeline`, `ErroDePipeline`. Feitas.
- **entram na fila**: log por execução (§9) e telemetria com porta da fatia (§10),
  os dois ANTES da segunda fatia existir.
- **régua §6.1 confirma Qute+HTMX** para este projeto (há banco, há página derivada
  do estado do servidor) — linha 1 da régua.
- `GreetingResource` do gerador foi **removido**: o canon diz que a raiz do pacote
  só tem módulos, e `/hello` não é um deles.

**Item 2 — esqueleto canônico + guarda de fronteira**

```
$ ./gradlew test --rerun-tasks
[FRONTEIRA] classes analisadas: 11
[FRONTEIRA] kernel, peer e fatia: os tres tem classe — nenhuma regra e vazia
[FRONTEIRA] modulos de topo no disco: [core, endereco, geo]
[FRONTEIRA] regra 3: 1 fatia declarada — trivialmente satisfeita, vira exigivel
            quando a segunda nascer

CALIBRACAO — 4 violacoes plantadas de proposito:
  regra 0-bis (modulo de topo `relatorio` sem categoria) ... FAILED  <- correto
  regra 1     (core -> fatia) ............................. FAILED  <- correto
  regra 2     (peer -> fatia) ............................. FAILED  <- correto
  regra 4     (domain -> jakarta) ......................... FAILED  <- correto
  regra 3     (fatia -> fatia) ............................ PASSED  <- correto:
              nao plantei esta, e a guarda NAO reprovou em bloco

Mensagem da 0-bis, colada:
  "pacote(s) de topo sem categoria declarada: [relatorio]. Declare em KERNEL,
   PEERS ou FATIAS aqui e em org/nasa/package-info.java — pacote que nenhuma
   regra governa e o comeco do acoplamento."

Violacoes removidas -> tudo verde de novo.

Regra 5 fica SKIPPED, declarada "NAO VERIFICADO: ainda nao existe classe em
`..application..`". O ArchUnit recusa julgar conjunto vazio e esta certo; a saida
errada seria allowEmptyShould(true), que viraria "nao havia o que examinar" em verde.

SUITE: 20 testes · 1 pulado (declarado) · 0 falhas
  SqliteViabilidade 5 · Fronteira 7 · Coordenada 8
```

**Dois defeitos meus nesta etapa, os dois pegos pelo proprio instrumento:**
o piso de classes contava `package-info.class` que **o javac nao emite** quando o
arquivo so tem Javadoc (reprovou dizendo "5 classes" — a causa era o instrumento,
nao o codigo medido); e a regra 5 reprovava por conjunto vazio, que virou o
terceiro estado em vez de virar `allowEmptyShould`.

**Itens 2-bis / 2-ter / 2-quater — log, telemetria e exceção específica**

```
LOG POR EXECUCAO — a linha real, do disco (nao da configuracao):
2026-09-02T08:42:39.029-03:00 INFO  [teste-20260902-084231]
    [or.na.co.lo.LogPorExecucaoTest] prova-log alvo=prova-de-log-7526856708900
    — linha de prova (1.2s)
                     ^instante ISO  ^execucaoId  ^origem  ^operacao ^alvo ^duracao

A faxina no boot, com os contadores de agiu E absteve:
    faxina-log alvo=build/logs-teste — examinados=1 apagados=0 preservados=1 falhas=0

TELEMETRIA — o arquivo gravado, com os quatro tipos de numero:
{ "versaoDoEsquema" : 1, "registros" : { "resolver-lote" : {
    "registradoEm" : "2026-09-02T12:00:00Z",   <- ISO, nunca numero
    "resolvidos" : 38,                          <- AGIU
    "semCoordenada" : 4,                        <- ABSTEVE
    "recusasPorCausa" : { "DADO_AUSENTE" : 4 }, <- KPI causal
    "veredito" : "ATENCAO",
    "motivo" : "ENDERECOS_SEM_COORDENADA=4" } } }

EXCECAO ESPECIFICA — catraca calibrada com 2 violacoes plantadas:
  regra 1 (lanca generica) ... FAILED, acusando:
      Method <...CalibGenerica.falhar()> calls constructor
      <java.lang.IllegalStateException.<init>(java.lang.String)>
  regra 2 (excecao orfa) ..... FAILED, acusando:
      Class <...CalibExcecaoOrfa> is not assignable to
      org.nasa.core.erro.ErroDePipeline
  Removidas -> verde. 7 excecoes no disco, todas descendo da base.

SUITE: 49 testes · 1 pulado (declarado) · 0 falhas
GUARDAS: 2 passaram · 0 reprovaram · 0 sem veredito
```

**Dois defeitos meus nesta etapa, os dois pegos por LER O ARTEFATO em vez de
confiar no verde:**

1. A catraca de exceção reprovou o **próprio `ErroDePipeline`**, que chama
   `super(...)` de `RuntimeException` — inevitável e legítimo. Virou exceção
   NOMINAL com motivo escrito, não afrouxamento da regra. É a guarda estreando
   contra o código real, como o canon manda.
2. A telemetria gravou `"registradoEm": 1788350400.000000000` — timestamp
   numérico — porque **o `ObjectMapper` do teste não era o de produção**.
   Instrumento diferente do código medido, que é o que a regra da medição
   proíbe. Agora os dois chamam `mapeadorDeTelemetria()`, e o teste afirma o
   ISO-8601 literal.

**Itens 2-quinquies / 2-sexies — UTC, retenção e a home**

```
UTC — mecanismo, nao afirmacao (ordem de Paulo, 02/09):
  ANTES: 2026-09-02T08:55:36.032-03:00   <- hora LOCAL, medido
  AGORA: 2026-09-02T12:13:58.327Z        <- UTC, com o Z
  Mecanismo em tres pontas: -Duser.timezone=UTC no build.gradle (test e JavaExec),
  TZ=UTC + -Duser.timezone=UTC nos Dockerfiles, e a asserçao no teste.
  [LOG] fuso da JVM: UTC offset=Z

  CatracaRelogioUtcTest, calibrada com 3 violacoes plantadas:
    LocalDateTime.now() ... FAILED   correto
    Instant.now() fora do RelogioSistema ... FAILED   correto
    new java.util.Date() ... FAILED   correto
  Unica isencao nominal: RelogioSistema, o unico que le o relogio do mundo.

  DE QUEBRA: `quarkus.log.file.enable` esta DEPRECIADO no Quarkus 3.39 — o proprio
  log acusava. Trocado por `quarkus.log.file.enabled`, e o teste confirmou que o
  arquivo continua sendo escrito (a troca as cegas seria a cicatriz da planta).

RETENCAO DE LOG — 30 dias (ordem de Paulo) + teto de 200 arquivos:
  faxina-log alvo=build/logs-teste — examinados=19 apagados=0 preservados=19
      falhas=0 relogioSuspeito=0 retencao=1d/200arq
  Idade sozinha nao limita nada com muitas execucoes por dia; as duas reguas
  juntas fecham as duas dimensoes.
  E arquivo com data no FUTURO nao e apagado: relogio para tras faria tudo
  parecer velho e a faxina apagaria o acervo inteiro. Falha fechada, contada.

HOME (Qute + HTMX, zero Node):
  GET / -> 200 text/html
  Relogio de pagina com DUAS horas rotuladas: UTC do servidor (que anda a partir
  do instante do servidor, nao do relogio do aparelho) e a local do visitante.
  i18n no padrao canonico: widget escondido, cookie googtrans, reload, bandeiras
  BR/EUA/Espanha em SVG inline, `translate="no"` nas ilhas tecnicas.
  10 testes de guarda de i18n, incluindo a ORDEM dos dois <script> — trocada, o
  widget nao inicializa e NADA traduz, sem erro visivel.
  Assets servidos de verdade (200 + bytes), HTMX LOCAL, todos com ?v=.

SUITE: 66 testes · 1 pulado (declarado) · 0 falhas
```

**Dois defeitos meus nesta etapa, os dois no INSTRUMENTO:**

1. O teste de UTC comparava `ZoneId.systemDefault().getId()` (`"UTC"`) com
   `ZoneOffset.UTC.getId()` (`"Z"`) — mesma coisa, nomes diferentes. Reprovou
   código correto. Agora compara o **offset**, que é o que importa.
2. O `@BeforeAll` do teste da home rodava **antes** de o Quarkus configurar a
   porta do RestAssured: `Connection refused`. Busca sob demanda resolve sem
   depender de ordem de extensão.

**🟡 Não executado:** conferência VISUAL da home em navegador real. O conteúdo e
os assets estão provados por HTTP, mas a planta exige olho na tela — rode
`./gradlew quarkusDev` e abra `http://localhost:8080`.

**Itens 3 a 7a — do banco à primeira integração externa**

```
BRANCH: main @ 8f2e104, EMPURRADO para origin (autorizado por Paulo em 02/09).
SUITE: 122 testes · 0 falhas · 0 sem veredito nas guardas.

ITEM 3+4 — persistencia e esquema
  registro: 1|5ed9ff98855c36cf...|2026-09-02T12:59:55.402904400Z
  journal_mode=wal · busy_timeout=5000ms · PRAGMA foreign_keys=1
  9 testes NEGATIVOS: cada invariante provada tentando viola-la.
  MEDIDO E CORRIGIDO: o teste dos PRAGMA acusou `journal_mode=delete` — faltava
  `journal_mode=WAL` no perfil de TESTE, que media configuracao que ninguem roda.

ITEM 5 — geo
  SP->Rio 357km · SP->Manaus 2.689km (a longa pega erro proporcional)
  antipodas sem NaN · polo e antimeridiano abrem o globo em vez de caixa invalida
  Locale.US provado com a JVM em pt-BR

ITEM 6 — cliente (a primeira fatia completa, molde das outras)
  [API] concorrencia: criados=1 recusados=7   <- 8 cadastros SIMULTANEOS
  O defeito do legado consertado: "111.222.333-44" e "11122233344" eram DUAS
  pessoas; agora colidem no UNIQUE, provado por HTTP.

ITEM 7a — endereco
  As duas respostas que enganam, provadas com o corpo REAL medido:
    BrasilAPI sem `location` -> AUSENCIA, nunca (0,0)
    ViaCEP com erro          -> HTTP 200 e {"erro":"true"}
```

**A GUARDA DE FRONTEIRA REPROVOU O BUILD, e estava certa.** O `ConsultarCepUseCase`
injetava os adaptadores direto para declarar a ordem dos provedores — violando
"`application` não depende de `infrastructure`". A ordem virou
`CadeiaDeProvedoresDeCepPort`. Ceder ali teria custado exatamente o que a regra
compra: o caso de uso rodar em teste **sem rede**.

**Três defeitos meus nesta rodada, todos de instrumento ou de teste:**
1. `journal_mode` divergindo entre teste e produção (acima).
2. Sombreamento de nome em classe anônima: `List.of(primario, reserva)` pegava o
   campo herdado **nulo** em vez do parâmetro, e o NPE não dizia nada sobre isso.
   Curado tirando a subclasse anônima e injetando a porta como lambda — o teste
   passou a exercitar o caminho real.
3. Asserção contraditória no teste de locale (`não contém vírgula` num parâmetro
   que tem 3 vírgulas separadoras). Virou contagem, que diz a coisa certa.

---

## EM ANDAMENTO AGORA

Nada em andamento. Itens 0, 1, 1-bis, 2, 2-bis, 2-ter e 2-quater fechados com
artefato.

**PADRÃO PERMANENTE DO PROJETO (ordem de Paulo, 02/09):** *uma exceção específica
por classe, com log e telemetria.* Como está implementado:

- `ErroDePipeline` é **abstrata** e carrega `operacao`, `alvo` e `CausaRaiz` —
  não existe genérica para lançar;
- `RegistradorDeFalha` emite **uma** linha de log e **uma** contagem causal, no
  ponto em que a falha venceu. A exceção **não** loga no construtor: assim ela
  não vira ERROR quando alguém a captura e trata, nem é contada três vezes ao
  ser reembrulhada na subida da pilha;
- `CatracaExcecaoEspecificaTest` reprova o build quando alguém lança genérica ou
  cria exceção fora da base.

Exceção nova nasce em `<modulo>/domain/exceptions/` (ou `core/erro/` no kernel),
com os 3 pilares no Javadoc e a `CausaRaiz` escolhida — nunca
`NAO_CLASSIFICADA`, que existe para ser greppável, não para ser usada.

## PRÓXIMA AÇÃO EXECUTÁVEL EXATA

**Item 7b — fatia `contato`**, espelhando `cliente` (é CRUD puro, e o molde já
existe). Depois, na ordem:

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
