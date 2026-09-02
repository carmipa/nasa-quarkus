# Plano-Mestre — reconstrução do GS FIAP 2025/1 (NASA) em Quarkus

> **Estado:** EM ANÁLISE — escopo proposto, aguardando o portão de entrada.
> **Sessão:** `0554bf29` · 2026-09-02 · desktop `DESKTOP-QDNQHL1`
> **Portão de arranque:** `rc=0` (cadeia íntegra, comprovante desta sessão).
>
> Este documento é o **portão de entrada** do protocolo AI-OS: o escopo fecha aqui.
> Depois de fechado, é proibido ampliá-lo durante a implementação — dependência nova
> interrompe e recalcula o plano.

---

## 1. Entendimento da missão

Reconstruir do zero o sistema entregue como Global Solution FIAP 2025/1 (tema NASA —
consulta e alerta de desastres naturais), trocando a pilha inteira:

| dimensão | legado (2025) | alvo |
|---|---|---|
| runtime | Spring Boot 3.5.0 · Java 17 | **Quarkus 3.39.1 · Java 25** |
| frontend | Next.js 15.3.2 · React 19 · Node | **Qute + HTMX + CSS + JS puro — zero Node** |
| banco | Oracle 21c (`oracle.fiap.com.br`) | **SQLite** (arquivo local) |
| concorrência | pool de plataforma do Tomcat | **virtual threads** + JIT da JVM |
| arquitetura | camadas horizontais (controller/service/repository) | **fatias verticais + peers + kernel + portas** |

Padrão arquitetural de referência: KRONOS CORE, ASPM Pride Security, Framework Net e
binmapper — consolidado em `instrucoes/regra-arquitetura-desacoplamento` do vault.

---

## 2. O legado, medido (não estimado)

Comandos e saídas no relatório da sessão. Números conferidos em 2026-09-02.

**Inventário:** 308 arquivos em `gs/`, 7 módulos acadêmicos.

| módulo | conteúdo | serve à reconstrução? |
|---|---|---|
| `Java_Advanced` | **API Spring Boot (63 .java) + frontend Next.js (37 .tsx/.ts)** | **SIM — é a fonte** |
| `Advanced_Business_Development_with.NET` | reimplementação do MESMO domínio em C# (52 .cs) + frontend | referência de paridade |
| `Mastering_Relational_and_Non_Relational_Database` | DDL Oracle, diagramas, 16 imagens, documentação | **SIM — o modelo de dados** |
| `Mobile_Application_Development` | só diagramas (3 PDF) + README | não |
| `Compliance_Quality_Assurance_Tests` | só README | não |
| `Deveops_Tools_Cloud_Computing` | só README | não |
| `Disruptive_Architectures_IoT_IOB_Generative_IA` | só README | não |

**Superfície a reproduzir — 33 endpoints em 7 controllers:**

| controller | rota base | operações |
|---|---|---|
| `ClienteController` | `/api/clientes` | listar · por id · por documento · criar · alterar · excluir · pesquisar |
| `ContatoController` | `/api/contatos` | criar · listar · por id · por email · alterar · excluir |
| `EnderecoController` | `/api/enderecos` | listar · por id · **consultar-cep** · **calcular-coordenadas** · criar · alterar · excluir |
| `EonetController` | `/api/eonet` | listar · por id interno · por id da API · criar · alterar · excluir · por-data · **nasa/sincronizar** · **nasa/proximos** |
| `StatsController` | `/api/stats` | eonet/count-by-category |
| `AlertTriggerController` | `/api/alerts` | trigger-user-specific-alert |
| `HealthController` | `/api` | health |

**Telas a reproduzir — 12 rotas do Next.js:**
`/` · `/clientes/listar` · `/clientes/buscar` · `/clientes/cadastrar` · `/clientes/[id]` ·
`/clientes/alterar/[id]` · `/clientes/deletar/[id]` · `/contato` · `/desastres` ·
`/desastres/mapa` · `/desastres/mapa-atuais` · `/desastres/mapa-historico` ·
`/desastres/estatisticas`.

**Modelo de dados — 7 tabelas Oracle**, 4 entidades e 3 tabelas de junção:
`tb_cliente3` · `tb_contato3` · `tb_endereco3` · `tb_eonet3` ·
`tb_clientecontato3` · `tb_clienteendereco3` · `tb_enderecoeventos3`.

**Integrações externas do legado — 3:** NASA EONET v3 · **Google Geocoding** · ViaCEP.
(O Google **não** vai para a reconstrução — decisão do Paulo em 2026-09-02; a pilha
aberta que o substitui está medida em §6.4.)

**Peso do Node que sai:** `package-lock.json` com **484 pacotes resolvidos** em 7.172
linhas, para entregar 12 telas. É o custo que o HTMX elimina.

---

## 3. Achados da auditoria

Gravidade: 🔴 impede · 🟡 corrigir na reconstrução · ⚪ registrado.

### 🔴 A1 — Credencial viva em texto claro, com o repositório de destino PÚBLICO

`gs/Advanced_Business_Development_with.NET/gsApi/gsApi/appsettings.json` carrega:

- **chave do Google Cloud** (formato `AIza…`, 39 caracteres), linha 20;
- **credencial Oracle da FIAP** (`User ID` + `Password`) em texto claro, linha 13.

Medido: `api.github.com/repos/carmipa/nasa-quarkus` devolve `"private": false`. O
`origin` deste diretório é esse repositório. `git ls-files gs/` devolveu **vazio** —
nada está rastreado ainda, então **a chave não foi publicada por este repositório**.
Um `git add -A && git push` publicaria as duas.

**Fechado nesta sessão** por duas camadas (§ Evidências do relatório).

> **Reconciliação, 2026-09-02 — a gravidade caiu de 🔴 para 🟡.** Paulo informou que
> **a chave do Google já estava invalidada havia tempo**. Ele é o dono da credencial e
> a fonte autoritativa sobre o estado dela.
>
> `Inferência a partir de informação do dono, não evidência direta` — eu **não** testei
> a chave, e de propósito: bater com ela no Google significaria transmitir o segredo a
> um terceiro para provar que ele não serve, o que troca um risco por outro.
>
> **A credencial Oracle da linha 13 também está encerrada.** Paulo informou que é do
> curso de TDS que ele já concluiu, não está mais em uso, e a senha mudou várias vezes
> desde então. `Inferência a partir de informação do dono, não evidência direta` — e
> aqui a recusa de medir é dupla: testar aquela credencial seria **conectar a um
> sistema de terceiro (FIAP) sem autorização**, o que não se faz nem para provar que
> ela não funciona.
>
> **A1 fica 🟡 e fechado: nenhuma das duas credenciais está viva.**
>
> **O que NÃO muda**, e é o ponto que importa para o plano: o bloqueio não foi feito
> para estas duas credenciais — foi feito para a **próxima**. O plano prevê
> geocodificação, e se as APIs de `api.nasa.gov` entrarem, `api_key`. Credencial morta
> que vaza é constrangimento; viva que vaza é incidente — e as duas passam pelo mesmo
> `git add`, indistinguíveis no momento em que importa.

### 🔴 A2 — A premissa da chave da NASA está trocada de serviço

`NasaEonetClient` monta a URL da EONET v3 com `limit`, `start`, `end`, `days`,
`status`, `source`, `bbox` e `category`. **Não há parâmetro de chave** — a EONET v3
(`eonet.gsfc.nasa.gov/api/v3`) é aberta e pede só um `User-Agent`.

A chave que o projeto realmente usa é a do **Google Geocoding** (`google.maps.apikey`),
e é exatamente ela que vazou em A1. A preocupação estava certa; o serviço, não.

Consequência para o plano: se a reconstrução usar as APIs de `api.nasa.gov` (APOD,
DONKI, NeoWs, EPIC), aí **sim** haverá `api_key` — e a guarda já cobre esse formato.

### 🔴 A3 — A API legada não sobe: não existe configuração

`src/main/resources` do `gsapi` está **vazio**. Não há `application.properties`. O
`.gitignore` do módulo bloqueia `application*.properties` de propósito — foi o que
salvou o módulo Java de vazar o que o módulo .NET vazou. Efeito colateral: o legado é
**material de leitura, não software executável**. Nenhuma medição de paridade em
runtime é possível contra ele.

Toda a configuração precisa ser reconstruída a partir dos `@Value` do código:
`nasa.eonet.api.url` · `google.maps.geocoding.api.url` · `google.maps.apikey` ·
`app.geocoding.user-agent` · `viacep.api.url` · `spring.mail.*`.

### 🟡 A4 — A idempotência da sincronização mora só no Java

`EonetService.sincronizarEventosDaNasa` faz upsert por `findByEonetIdApi(...).orElse(new Eonet())`.
Correto na intenção. Mas o DDL **não tem nenhuma constraint UNIQUE além das chaves
primárias** — conferido nos três arquivos de DDL. `eonet_id` não é único no banco.

Duas sincronizações simultâneas leem "não existe" e inserem as duas: evento duplicado,
sem erro. É a invariante *"o banco decide, não o Java"* quebrada, e a memória
*"índice sem UNIQUE não previne nada"*.

Mesma classe atinge `documento` do cliente (CPF duplicado) e `email` do contato.

### 🟡 A5 — Efeito externo dentro de transação de leitura

`UserSpecificAlertService.processAndSendAlert` é `@Transactional(readOnly = true)` e
dispara **envio de e-mail** lá dentro. Se o SMTP demorar, a conexão do banco fica
presa esperando rede de terceiro; se o envio falhar depois do commit lógico, não há
registro do que foi ou não enviado. Sem outbox, sem idempotência, sem auditoria:
reenviar o mesmo alerta duas vezes é indistinguível de enviá-lo uma.

### 🟡 A6 — Data de nascimento é `VARCHAR2(10)`

`Cliente.dataNascimento` é `String` mapeada para `VARCHAR2(10)`. Não há ordenação,
comparação, nem validação de data possível no banco. Erro de modelagem herdado do
DDL — **não se reproduz**.

### 🟡 A7 — `complemento` é `NOT NULL`

`tb_endereco3.complemento VARCHAR2(255) NOT NULL`. Endereço sem complemento é a
maioria dos endereços do Brasil. A regra obriga o operador a inventar um valor —
erro de boa-fé induzido pelo próprio modelo. Não se reproduz.

### 🟡 A8 — Fonte de verdade dupla no evento EONET

`tb_eonet3` guarda o **CLOB `json`** com o payload cru da NASA **e** as colunas `data`
e `eonet_id` extraídas dele. Duas cópias da mesma verdade divergem quando uma é
atualizada e a outra não — e o `atualizarEventoManualmente` deixa o cliente escrever
as duas de forma independente.

### 🟡 A9 — Zero testes

`find src -path "*test*" -name "*.java"` devolveu **0**. Não há teste unitário, de
integração, nem de contrato em todo o `gsapi`.

### ⚪ A10 — Sem autenticação, com aparência de ter

`OpenApiConfig` declara um `securityScheme` `bearerAuth` com formato JWT e o texto
*"Insira o token JWT"*. Não existe nenhuma configuração de segurança no projeto: a
documentação anuncia uma proteção que o código não tem. Os 33 endpoints são abertos.

### ⚪ A11 — Domínio duplicado em duas linguagens, já divergente

O mesmo domínio existe em Java (`Java_Advanced`) e em C# (`.NET`), com o `.NET`
carregando a configuração e o Java não. A reconstrução consolida numa implementação
só; o C# fica como acervo.

---

## 4. Invariantes do sistema novo

Declarados antes de escrever código — quem não consegue enunciar o invariante ainda
não entendeu a tarefa.

| ID | nome | dano se quebrado | camada que protege | como se prova |
|---|---|---|---|---|
| **INV-SEG-001** | Nenhuma credencial entra no repositório | chave publicada em repo público; revogar é a única cura | `.gitignore` (caminho) + `guarda-segredos` no `pre-commit` (conteúdo) | controle positivo: commit real bloqueado — **já feito** |
| **INV-EONET-001** | Um evento da NASA existe **uma vez** por `eonet_id` | evento duplicado infla estatística e mapa; a tela mente sobre o mundo | `UNIQUE(eonet_id)` **no SQLite** + upsert na aplicação | teste concorrente: N sincronizações simultâneas → contagem final = N distintos |
| **INV-CLIENTE-001** | `documento` identifica um cliente e só um | alerta enviado ao cliente errado; cadastro duplicado | `UNIQUE(documento)` no banco | teste negativo recusado pelo banco, erro colado |
| **INV-ALERTA-001** | O mesmo evento não alerta o mesmo cliente duas vezes | e-mail duplicado; usuário perde confiança e desliga o alerta | tabela de envio com `UNIQUE(cliente_id, eonet_id)` + outbox | duas execuções seguidas → 1 e-mail, prova no banco |
| **INV-TEMPO-001** | Instante é gravado em UTC | evento aparece em dia errado na virada; janela de "últimos N dias" erra | coluna com default UTC explícito + `Instant`, nunca `LocalDateTime.now()` | teste atravessando meia-noite |
| **INV-EXT-001** | Falha de API externa nunca corrompe estado local | sincronização parcial vira acervo mentiroso | transação fecha antes da chamada externa; retry idempotente | teste com adaptador que falha no meio |

> **Multi-tenant: N/A declarado.** O sistema não tem inquilino — um acervo, um
> operador. Toda exigência de `casa_id`, RLS e FK composta da REGRA é inaplicável
> aqui, e isto está escrito para ser lacuna **conhecida**, não silenciosa.

---

## 5. Arquitetura alvo

### 5.1 O modelo em uma frase

Fatias verticais autônomas consumindo peers compartilhados e um kernel técnico,
falando com o mundo por portas + adaptadores, com as arestas congeladas por guarda
executável que reprova o build.

Princípio raiz: **duplicação consciente > acoplamento.**

### 5.2 Os módulos

```
org.nasa
├── core/                      KERNEL TÉCNICO — todos podem depender; ele não depende de fatia
│   ├── util/                  escrita atômica, formatação, relógio (Clock injetável)
│   ├── exception/             raiz das exceções
│   ├── presentation/web/      chrome do painel, resposta padrão, fragmento HTMX
│   └── execucao/              fila de trabalho pesado / virtual threads
│
├── peer/                      PEERS COMPARTILHADOS — dono único de um conceito
│   ├── geo/                   coordenada, bounding box, distância (o GeoUtils do legado, testado)
│   ├── persistencia/          acesso SQLite, migração, transação
│   └── eventoNatural/         modelo do evento EONET (domínio puro)
│
└── fatia/                     FATIAS VERTICAIS — uma por caso de uso completo
    ├── cliente/               CRUD + pesquisa
    ├── contato/               CRUD
    ├── endereco/              CRUD + CEP + geocodificação
    ├── eventoEonet/           consulta, sincronização com a NASA, proximidade
    ├── estatistica/           contagem por categoria e por tempo
    ├── alerta/                casar evento × endereço e notificar
    └── painel/                as 12 telas em Qute + HTMX
```

Camadas dentro de cada fatia:

```
<fatia>/
  domain/           puro: tipos, invariantes, exceções. SEM framework, SEM I/O.
    ports/          INTERFACES do que a fatia precisa do mundo externo
  application/      casos de uso
  infrastructure/   ADAPTADORES que implementam as ports
  presentation/     entrada HTTP (REST e/ou fragmento Qute)
```

**Regra de ouro:** `application` depende de `domain/ports`, **nunca** de
`infrastructure`. O adaptador é injetado. Consequência: caso de uso testável sem rede
e sem disco.

### 5.3 As portas que isolam o que pode mudar

| porta | por quê |
|---|---|
| `GeocodificacaoPort` | provedor é adaptador, não arquitetura. Hoje **Nominatim/OSM**; Photon e uma instância própria entram sem tocar na fatia. Foi esta porta que fez a saída do Google custar uma linha de configuração |
| `ConsultaCepPort` | **BrasilAPI** como primário e ViaCEP como reserva — dois adaptadores, um contrato. A porta também é onde mora a regra "coordenada ausente é ausente, nunca 0,0" |
| `EventoNaturalPort` | EONET hoje; outras APIs da NASA depois |
| `NotificacaoPort` | e-mail hoje; a fatia de alerta não conhece SMTP |
| `RelogioPort` | teste de virada de dia sem esperar meia-noite |

### 5.4 A guarda de fronteira

ArchUnit com **allowlist por tipo exato**. Dependência nova reprova o build listando
a aresta exata que apareceu. Nasce **vista reprovando** uma violação montada à mão —
guarda que só rodou no código são pode estar passando por não enxergar nada.

⚠️ Gradle produz **falso-verde** em teste de arquitetura por cache: rodar com
`--rerun-tasks`. Cicatriz medida no KRONOS.

---

## 6. Decisões técnicas, com o motivo

### 6.1 Frontend: Qute + HTMX, zero Node

- Página nova nasce em HTMX; o Qute já renderiza no servidor.
- **Nada de regra crítica no HTMX** — o backend valida, o `hx-*` apresenta.
- `htmx.min.js` servido **local**, nunca CDN: dependência externa em página do
  sistema é proibida pela regra.
- `.htmx-request` para esmaecer durante a requisição — sem indicador o operador
  clica duas vezes achando que não funcionou.
- CSS/JS estático **fora do template**, em `/estatico/`, com `?v={assetVersion}`.
- Bloco com `{` no Qute vai em raw `{|...|}`, senão o Qute avalia como expressão.
- **CSS em porcentagem, largura cheia** — `width:100%` + padding em `%`,
  `repeat(auto-fit, minmax(Xrem, 1fr))`, nunca `100vw`, nunca coluna central. `px`
  só em borda fina, sombra e breakpoint.
- Mapa: Leaflet servido local (o legado já usa Leaflet, via `react-leaflet`).
  Gráfico: Chart.js local (o legado já usa).

### 6.2 Banco: SQLite

**Cabe.** O esquema são 7 tabelas sem recurso proprietário; as `SEQUENCE` do Oracle
viram `INTEGER PRIMARY KEY AUTOINCREMENT`; `CLOB` vira `TEXT`; `NUMBER(10,7)` vira
`REAL`; `TIMESTAMP WITH LOCAL TIME ZONE` vira `TEXT` em UTC ISO-8601.

O que **ganha** em relação ao Oracle da FIAP: some a credencial de banco (A1), some a
dependência de rede para desenvolver, e o teste passa a rodar contra o banco real em
vez de um dublê.

O que **precisa de cuidado, declarado**:

- **um escritor por vez.** WAL (`journal_mode=WAL`) dá leitores concorrentes com um
  escritor; escrita concorrente serializa. Para este sistema — leitura pesada de
  eventos, escrita esporádica de cadastro — é adequado. Se a sincronização da NASA
  virar paralela, ela precisa de fila, não de mais conexões de escrita.
- `busy_timeout` explícito, senão escrita concorrente devolve `SQLITE_BUSY` na cara
  do operador.
- `foreign_keys=ON` **por conexão** — no SQLite a integridade referencial vem
  **desligada** por padrão. Sem isso as FKs do modelo são decorativas, e é o tipo de
  defeito que só aparece quando o dado já está inconsistente.
- O arquivo do banco **não entra no git** (já bloqueado no `.gitignore`, seção 3).

> **Risco medido pendente:** a compatibilidade `Quarkus 3.39.1 + Java 25 + SQLite` não
> está provada nesta máquina. O SQLite não é extensão oficial do Quarkus. É o
> **primeiro item executável** do plano, e nada é construído em cima antes de ele ter
> artefato. Se não fechar, o plano B é Postgres em contêiner ou H2 em arquivo — e a
> troca custa pouco porque o acesso a dados mora atrás de um peer.

### 6.3 Concorrência: virtual threads e JIT

- **Virtual threads** (`@RunOnVirtualThread`) nos endpoints que esperam rede — as
  chamadas à NASA, ao ViaCEP e ao geocodificador. É onde o ganho existe.
- **Não usar virtual thread para segurar conexão de banco**: a conexão não espera
  rede de terceiro, e o *pinning* de virtual thread é armadilha já registrada.
- **`@Transactional` na CLASSE** dos resources de página. Sem isso cada consulta
  adquire conexão própria; a página funciona e só fica lenta — falha invisível, e por
  isso vira guarda, não lembrete.
- **JIT:** modo JVM (não `native-image`), que é onde o C2 aquece e otimiza em
  execução. Medição de desempenho, se houver, com rampa de concorrência (8/16/32/64) —
  medir com 1 cliente não representa nada.

### 6.4 CEP e geocodificação: pilha 100% aberta, medida

Ordem do Paulo (2026-09-02): *"ao invés do Google, nesse projeto usaremos alternativas
de código aberto para a mesma função ou melhor"*. O Google sai inteiro — sem adaptador,
sem chave, sem cobrança, e some junto o vetor que produziu o achado A1.

Medido ao vivo em 2026-09-02, com `curl`, não estimado:

| serviço | licença/modelo | HTTP | tempo | devolve |
|---|---|---|---|---|
| **BrasilAPI CEP v2** | aberto, sem chave | 200 | **0,23 s** | endereço + **coordenada** + IBGE + fuso |
| **Nominatim** (OSM) | ODbL, sem chave | 200 | 0,82 s | coordenada + endereço detalhado |
| Nominatim `/reverse` | ODbL | 200 | 0,39 s | coordenada → endereço |
| **Photon** (Komoot) | aberto, sobre OSM | 200 | 1,04 s | GeoJSON, bom para autocompletar |
| ViaCEP *(o do legado)* | aberto, sem chave | 200 | 1,04 s | endereço, **sem coordenada** |

**O "ou melhor" é literal e mensurável.** O legado gastava **duas** chamadas para
preencher um endereço — ViaCEP para os campos e Google para a coordenada, porque o
ViaCEP não devolve lat/lon (confirmado na medição). A BrasilAPI devolve as duas coisas
numa chamada só, em **um quarto do tempo do ViaCEP sozinho**, e ainda traz IBGE e fuso
IANA — que é exatamente o dado de que o INV-TEMPO-001 precisa e que hoje não existe.

**A armadilha, medida antes de virar bug:** a coordenada da BrasilAPI **não é
garantida**. Amostra de 6 CEPs de perfis diferentes:

```
01310200 | São Paulo      | open-cep | -23.5614961
88010400 | Florianópolis  | open-cep | -27.5982477
69900000 | (sem cidade)   | correios | **SEM COORDENADA**
59900000 | Pau dos Ferros | open-cep | -6.10917
78990000 | Seringueiras   | open-cep | -11.77016
01001000 | São Paulo      | open-cep | -23.5503898
```

5 de 6. Quando a BrasilAPI cai no provedor `correios`, vem endereço sem coordenada — e
em um dos casos sem nem a cidade. Consequências que entram no desenho:

1. **Nominatim é degradação declarada**, não enfeite: `ConsultaCepPort` devolve o que
   tem, e a fatia `endereco` chama a `GeocodificacaoPort` só quando a coordenada faltou.
2. **Coordenada ausente é ausente** — nunca `0.0, 0.0`. O par `0,0` é o *null island*,
   no Golfo da Guiné: o endereço do cliente iria parar no oceano, o mapa desenharia o
   pino lá, e **nenhum erro apareceria**. É o tipo de defeito que passa por toda
   varredura de segurança e falha em silêncio. O domínio usa `Optional`/nulo explícito,
   e o banco tem `CHECK` recusando o par exato `(0,0)`.
3. **Sem coordenada, o alerta de proximidade não roda para aquele endereço** — e diz
   isso na tela, em vez de calcular distância a partir de uma coordenada inventada.
   Alerta que não roda é ruim; alerta que roda sobre coordenada falsa é pior.

**Política de uso, que é regra e não recomendação:** o Nominatim público permite **1
requisição por segundo** e exige `User-Agent` identificável — quem ignora leva bloqueio
de IP, e o sintoma chega como "a geocodificação parou de funcionar". Por isso: cache
antes da chamada (CEP não muda), limite de vazão no adaptador, e a saída de emergência
declarada é **subir Nominatim ou Photon próprio** (os dois são open source e
self-hostáveis) se o volume crescer.

**Licença:** dado do OpenStreetMap é **ODbL** e exige atribuição visível —
`© OpenStreetMap contributors` na tela do mapa, não só num comentário do código.

**Mapa na tela:** Leaflet + tiles do OpenStreetMap, servidos localmente. O legado já
usava Leaflet (via `react-leaflet`); some o React, fica a biblioteca.

### 6.5 Documentação de código

Todo método/classe de domínio nasce com os **3 pilares**: propósito de negócio,
invariantes do domínio, comportamento em caso de falha.

---

## 7. Riscos, pelas três lentes

### Adversarial — *como alguém faria o sistema executar o impossível?*

| vetor | resposta do desenho |
|---|---|
| `POST /api/eonet` aberto grava lixo no acervo | escrita exige autorização; hoje o legado tem 33 endpoints abertos (A10) |
| enumerar `/api/clientes/{id}` e varrer a base | paginação com teto e autorização na leitura de dado pessoal |
| forjar `trigger-user-specific-alert` para spammar e-mail | limite por cliente/evento (INV-ALERTA-001) + teto de envio |
| injeção de SQL pelo campo de pesquisa | consulta parametrizada, sempre; guarda que reprova concatenação |
| payload gigante no `json` do evento | teto de tamanho antes de gravar |

### Boa-fé — *como uma pessoa honesta causa dano sem perceber?*

| situação | resposta do desenho |
|---|---|
| clique duplo em "Sincronizar com a NASA" | idempotência por `eonet_id` **no banco** + indicador `.htmx-request` |
| operador clica "Excluir" no cliente errado por semelhança visual | confirmação que **nomeia** o cliente, não um "tem certeza?" genérico |
| tela velha: alterar cliente com dados de 10 minutos atrás | versão otimista; conflito devolve a divergência, não sobrescreve |
| endereço sem complemento e o campo é obrigatório (A7) | complemento passa a ser opcional |
| dois operadores editando o mesmo cadastro | mesma versão otimista |
| recuperação depois de timeout reenvia o alerta | chave de idempotência do alerta gerada **antes** da primeira tentativa |

### Falha operacional — *e se o componente cair no meio?*

| cenário | falha aberto/fechado | estado que persiste | dá para repetir | como detecta | como reconcilia | evidência |
|---|---|---|---|---|---|---|
| NASA fora do ar na sincronização | **fechado** — nada é gravado | acervo anterior intacto | sim, é idempotente | contagem processada = 0 com trabalho esperado é **anomalia**, não sucesso | nova sincronização | log com contagem e motivo |
| SMTP fora do ar no alerta | **fechado** — alerta fica pendente | registro `pendente` na fila | sim, retry com backoff | fila acumulando | worker reprocessa | linha na tabela de envio |
| processo morre no meio da sincronização | **fechado** | eventos já commitados; nenhum pela metade | sim | comparação com a API | nova sincronização | commit por lote |
| arquivo SQLite bloqueado (`SQLITE_BUSY`) | **fechado** com erro explícito | nada gravado | sim | erro visível, não silêncio | `busy_timeout` + retry | log |
| relógio dessincronizado | — | data do evento em UTC | — | evento no futuro é anomalia | data vem da API, não do host | INV-TEMPO-001 |

> **Job silencioso** é o modo de falha que o fail-closed cria: o que não roda é mais
> difícil de perceber que o que vaza. Toda sincronização afirma quantos processou;
> `0` sem motivo conhecido é **alerta**, nunca sucesso.

---

## 8. Fila ordenada do escopo

Cada item fecha com artefato antes do próximo começar.

| # | item | critério de fechamento |
|---|---|---|
| **0** | ~~Bloqueio de segredos: `.gitignore` + guarda executável + hook~~ | ✅ **FEITO** — controle positivo em produção |
| **1** | **Prova de viabilidade SQLite** em Quarkus 3.39.1 / Java 25 | app sobe, cria tabela, grava e lê linha; saída colada |
| 2 | Esqueleto arquitetural: `core`, `peer`, `fatia` + guarda de fronteira ArchUnit | guarda **vista reprovando** violação montada à mão |
| 3 | Peer `persistencia`: migração, WAL, `foreign_keys=ON`, `busy_timeout` | `PRAGMA foreign_keys` = 1 provado em runtime |
| 4 | Esquema + invariantes **no banco** (UNIQUE de A4, datas em UTC, complemento opcional) | teste negativo recusado pelo banco, erro colado |
| 5 | Peer `geo` (bounding box e distância do legado) | teste com coordenada conhecida |
| 6 | Fatia `cliente` completa (domínio → porta → adaptador → REST → tela HTMX) | positivo + negativo + efeito no banco + tela no navegador |
| 7 | Fatias `contato` e `endereco` (+ portas CEP e geocodificação) | idem, com adaptador falho testado |
| 8 | Fatia `eventoEonet` (consulta, sincronização, proximidade) | idempotência provada com N chamadas simultâneas |
| 9 | Fatia `estatistica` | contagens conferidas contra o banco |
| 10 | Fatia `alerta` (outbox + idempotência + notificação) | dois disparos → um e-mail, prova no banco |
| 11 | Painel: as 12 telas em Qute + HTMX, CSS em % | cada tela conferida no navegador, console sem erro |
| 12 | Guardas restantes no endereço único `guardas/guardas.ps1` | placar com as três contagens |

---

## 9. O que é decisão do Paulo (lista curta e fechada)

Tudo fora desta lista eu decido e executo.

1. ~~**Revogar a chave do Google Cloud de A1.**~~ — **FECHADO em 2026-09-02.** Paulo
   informou que a chave **já estava invalidada havia tempo**. Ele é o dono da
   credencial e a fonte autoritativa. *Inferência a partir de informação do dono, não
   evidência direta:* não testei a chave, e de propósito — provar que ela não serve
   exigiria transmiti-la ao Google. **Continua valendo** a credencial Oracle da linha
   13 do mesmo arquivo, e o bloqueio permanece: ele não foi feito para aquela chave,
   foi feito para a próxima.
2. ~~**Provedor de geocodificação.**~~ — **DECIDIDO em 2026-09-02 por Paulo:**
   *"ao invés do Google, nesse projeto usaremos alternativas de código aberto para a
   mesma função ou melhor"*. O Google sai do projeto inteiro — não há adaptador dele.
   A pilha escolhida está medida em §6.4.
3. **Alerta por e-mail:** manter o recurso exige credencial SMTP. Assumo **manter a
   fatia com adaptador de log** (grava o que enviaria) até haver credencial — assim o
   fluxo é testável sem segredo nenhum.
4. **Paridade:** assumo **paridade total** com as 33 rotas e 12 telas, porque o pedido
   foi "refazer o projeto". Reduzir escopo é decisão dele.

---

## 10. Definition of Done de cada item

1. implementação em Java 25 / Quarkus, integrada ao fluxo real;
2. invariante protegido **no banco**, não só no Java;
3. teste positivo;
4. teste **negativo** recusado pelo banco, com o erro colado;
5. teste concorrente onde houver dado disputado;
6. efeito no banco conferido (`SELECT` antes/depois);
7. quando houver tela: Qute renderizando, no navegador, console sem erro;
8. guarda de arquitetura verde com `--rerun-tasks`;
9. artefato colado — a saída que um cético usaria para contestar.

**"Está implementado" não significa "está concluído".**
