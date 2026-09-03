-- =============================================================================
-- V001 — o esquema, em SQLite
--
-- Quatro tabelas: quem quer ser avisado, o que a NASA publica, o que foi
-- avisado, e o que o sistema mediu de si mesmo.
--
-- ─────────────────────────────────────────────────────────────────────────
-- COMO O UTC CONTINUA SENDO MECANISMO SEM `TIMESTAMPTZ`
--
-- O SQLite não tem tipo de data. Datas são TEXT, REAL ou INTEGER, e ele aceita
-- qualquer coisa em qualquer coluna. A disciplina de UTC deste projeto morava
-- no TIPO da coluna — e tipo é mecanismo: não depende de ninguém lembrar.
--
-- No lugar dele, três coisas, e as três são do banco:
--
--   1. INSTANTE É TEXTO EM ISO-8601 TERMINADO EM `Z`.
--      `2026-09-03T01:23:45Z` — sempre 20 caracteres, sempre UTC.
--
--   2. `CHECK` EXIGE O `Z`. Uma gravação em hora local é RECUSADA pelo banco,
--      não aceita em silêncio. É o que impede o defeito que este projeto já
--      pagou: o log saindo em `-03:00` enquanto a API falava `Z`.
--
--   3. LARGURA FIXA FAZ A ORDEM ALFABÉTICA COINCIDIR COM A CRONOLÓGICA.
--      `ORDER BY`, `MIN` e `MAX` continuam corretos sem função de data alguma —
--      e `substr(x, 1, 4)` é o ano, `substr(x, 1, 10)` é o dia. Foi assim que
--      `EXTRACT(YEAR FROM ... AT TIME ZONE 'UTC')` sumiu sem perder nada.
--
-- O QUE NÃO SE RECUPEROU, declarado: `ILIKE`. O SQLite tem `LIKE` insensível a
-- caixa por padrão, mas só para ASCII — "JOSÉ" e "josé" não casam. As buscas
-- que dependiam disso viviam em `cliente` e `contato`, que saíram do projeto.
-- ─────────────────────────────────────────────────────────────────────────
--
-- `INTEGER PRIMARY KEY` é o `rowid` do SQLite: gera sozinho e é o equivalente
-- do `GENERATED ALWAYS AS IDENTITY`. Escrever `BIGINT PRIMARY KEY` NÃO seria a
-- mesma coisa — só `INTEGER` exatamente casa com o rowid e auto-incrementa.
-- =============================================================================

-- =============================================================================
-- INSCRITO — quem pediu para ser avisado
--
-- Substituiu `cliente`, `contato` e `endereco` (5.645 linhas, 5 tabelas). Aquilo
-- modelava gestão de clientes; este sistema não gerencia clientes, ele AVISA
-- GENTE SOBRE DESASTRE. Uma inscrição é o que basta: quem é, onde está, por
-- onde avisar.
-- =============================================================================
CREATE TABLE inscrito (
    id           INTEGER PRIMARY KEY,

    nome         TEXT NOT NULL,
    -- O e-mail é o único canal que o sistema sabe usar. Aceitar inscrição sem
    -- ele criaria um registro que nunca pode ser avisado — um alerta que existe
    -- no banco e não chega a ninguém.
    email        TEXT NOT NULL,
    -- Telefone é REGISTRO, não canal: o sistema ainda não manda mensagem.
    -- Guardá-lo prometendo envio que não existe seria mentir no cadastro.
    telefone     TEXT,

    -- O CEP é o que a pessoa sabe informar; a coordenada é o que o sistema
    -- deriva dele. Só dígitos: o mesmo CEP com e sem hífen viraria dois.
    cep          TEXT NOT NULL,
    -- NULA É ESTADO LEGÍTIMO, e significa "ainda não dá para calcular
    -- proximidade para esta pessoa". Provedores externos falham; perder a
    -- inscrição porque o Nominatim estava fora seria punir a pessoa por uma
    -- falha nossa.
    latitude     REAL,
    longitude    REAL,

    raio_km      REAL NOT NULL DEFAULT 100.0,

    criado_em    TEXT NOT NULL,
    -- NULO significa ATIVA. Cancelar não apaga: a inscrição some dos alertas e
    -- o histórico do que já foi enviado continua fazendo sentido.
    cancelado_em TEXT,

    -- INV-INSCRITO-001: um e-mail se inscreve UMA VEZ. Sem isto, clicar duas
    -- vezes no botão criaria duas inscrições e a pessoa receberia cada alerta
    -- em dobro — o erro de boa-fé mais comum que existe num formulário.
    CONSTRAINT inscrito_email_unico UNIQUE (email),

    -- As duas coordenadas andam juntas: metade de uma posição não é posição.
    CONSTRAINT inscrito_coordenada_completa CHECK (
        (latitude IS NULL AND longitude IS NULL)
        OR (latitude IS NOT NULL AND longitude IS NOT NULL)
    ),
    CONSTRAINT inscrito_coordenada_na_terra CHECK (
        latitude IS NULL
        OR (latitude BETWEEN -90 AND 90 AND longitude BETWEEN -180 AND 180)
    ),
    -- 20.000 km é metade da circunferência da Terra: acima disso o raio cobre o
    -- planeta e "proximidade" deixa de significar coisa alguma.
    CONSTRAINT inscrito_raio_util CHECK (raio_km BETWEEN 1 AND 20000),
    CONSTRAINT inscrito_cep_oito_digitos CHECK (length(cep) = 8),

    -- O `Z` no lugar do `TIMESTAMPTZ`. Gravação em hora local é RECUSADA.
    CONSTRAINT inscrito_criado_em_utc CHECK (criado_em LIKE '%Z'),
    CONSTRAINT inscrito_cancelado_em_utc CHECK (
        cancelado_em IS NULL OR cancelado_em LIKE '%Z'
    )
);

-- A varredura de alertas pergunta "quem está ativo e tem posição?". O índice
-- parcial atende exatamente isso e não gasta espaço com quem cancelou.
CREATE INDEX idx_inscrito_ativo ON inscrito (latitude, longitude)
    WHERE cancelado_em IS NULL AND latitude IS NOT NULL;

-- =============================================================================
-- EVENTO_NATURAL — o que a NASA publica
-- =============================================================================
CREATE TABLE evento_natural (
    id              INTEGER PRIMARY KEY,
    eonet_id        TEXT NOT NULL,
    titulo          TEXT NOT NULL,
    categoria       TEXT,
    ocorrido_em     TEXT NOT NULL,
    latitude        REAL,
    longitude       REAL,
    -- Cópia forense do que a NASA mandou. TEXT de propósito: um formato
    -- estruturado recusaria justamente o payload torto que se quer poder ler.
    json_original   TEXT,
    -- Quando este evento entrou na base. O `DO UPDATE` não toca nesta coluna.
    criado_em       TEXT NOT NULL,

    -- QUANTAS VEZES ESTE EVENTO FOI RESSINCRONIZADO. Zero na linha recém-inserida;
    -- o `DO UPDATE` soma 1. É assim que se sabe, na mesma ida ao banco, se o upsert
    -- INSERIU ou ATUALIZOU.
    --
    -- No PostgreSQL isso era `(xmax = 0)`, uma coluna de sistema — a única das 15
    -- construções que exigiu repensar em vez de traduzir.
    --
    -- A PRIMEIRA TENTATIVA COMPARAVA `criado_em = sincronizado_em`, E ESTAVA ERRADA.
    -- O instante é truncado em segundos (a largura fixa é o que faz a ordem
    -- alfabética valer), então duas sincronizações DENTRO DO MESMO SEGUNDO gravam o
    -- mesmo texto — e a comparação dizia "inseriu" numa linha que foi atualizada.
    -- O teste de idempotência pegou na primeira execução.
    --
    -- Um contador não depende de resolução de relógio nenhuma. E, de quebra, `versao`
    -- responde algo que ninguém sabia: quantas vezes a NASA republicou este evento.
    versao          INTEGER NOT NULL DEFAULT 0,
    sincronizado_em TEXT NOT NULL,
    -- NULO significa ATIVO, e é o estado de todo evento quando aparece. Um
    -- valor sentinela faria "ativo" e "encerrado numa data estranha" ficarem
    -- indistinguíveis em consulta.
    encerrado_em    TEXT,

    -- INV-EONET-001: um evento da NASA existe UMA VEZ. No legado esta garantia
    -- morava só no Java (`findByEonetId().orElse(new)`), e duas sincronizações
    -- simultâneas liam "não existe" e inseriam as duas — evento duplicado
    -- inflando estatística e mapa, sem nenhum erro.
    CONSTRAINT evento_eonet_id_unico UNIQUE (eonet_id),
    CONSTRAINT evento_coordenada_completa CHECK (
        (latitude IS NULL AND longitude IS NULL)
        OR (latitude IS NOT NULL AND longitude IS NOT NULL)
    ),
    CONSTRAINT evento_coordenada_na_terra CHECK (
        latitude IS NULL
        OR (latitude BETWEEN -90 AND 90 AND longitude BETWEEN -180 AND 180)
    ),
    CONSTRAINT evento_ocorrido_em_utc CHECK (ocorrido_em LIKE '%Z'),
    CONSTRAINT evento_criado_em_utc CHECK (criado_em LIKE '%Z'),
    CONSTRAINT evento_sincronizado_em_utc CHECK (sincronizado_em LIKE '%Z'),
    CONSTRAINT evento_encerrado_em_utc CHECK (
        encerrado_em IS NULL OR encerrado_em LIKE '%Z'
    )
);

CREATE INDEX idx_evento_ocorrido_em ON evento_natural (ocorrido_em);
CREATE INDEX idx_evento_categoria ON evento_natural (categoria);
-- O mapa e o alerta perguntam por eventos COM posição. Índice parcial: quem não
-- tem coordenada nunca é resposta dessas consultas.
CREATE INDEX idx_evento_desenhavel ON evento_natural (ocorrido_em DESC)
    WHERE latitude IS NOT NULL;
-- A varredura de alertas quer só os ATIVOS.
CREATE INDEX idx_evento_ativo ON evento_natural (ocorrido_em)
    WHERE encerrado_em IS NULL;

-- =============================================================================
-- ALERTA_ENVIADO — o padrão outbox
--
-- A linha é gravada PENDENTE antes de qualquer tentativa de envio. Sem isso, um
-- processo que cai entre "decidiu avisar" e "avisou" perde o aviso sem deixar
-- rastro — e ninguém descobre, porque não há o que consultar.
-- =============================================================================
CREATE TABLE alerta_enviado (
    id           INTEGER PRIMARY KEY,
    inscrito_id  INTEGER NOT NULL REFERENCES inscrito(id) ON DELETE CASCADE,
    evento_id    INTEGER NOT NULL REFERENCES evento_natural(id) ON DELETE CASCADE,
    destino      TEXT NOT NULL,
    situacao     TEXT NOT NULL,
    causa_raiz   TEXT,
    tentativas   INTEGER NOT NULL DEFAULT 0,
    criado_em    TEXT NOT NULL,
    concluido_em TEXT,

    -- INV-ALERTA-001: uma pessoa é avisada UMA VEZ sobre o MESMO evento. A
    -- varredura roda repetidamente sobre os mesmos dados; sem esta restrição,
    -- cada execução mandaria o mesmo aviso de novo — e quem recebe o mesmo
    -- alerta cinco vezes desliga a notificação.
    CONSTRAINT alerta_uma_vez_por_inscrito_e_evento UNIQUE (inscrito_id, evento_id),
    CONSTRAINT alerta_situacao_conhecida CHECK (
        situacao IN ('PENDENTE', 'ENVIADO', 'FALHOU')
    ),
    -- Situação terminal exige instante: sem isto um alerta fica "ENVIADO" sem
    -- que ninguém saiba quando, e a auditoria não fecha.
    CONSTRAINT alerta_terminal_tem_instante CHECK (
        situacao = 'PENDENTE' OR concluido_em IS NOT NULL
    ),
    CONSTRAINT alerta_tentativas_nao_negativas CHECK (tentativas >= 0),
    CONSTRAINT alerta_criado_em_utc CHECK (criado_em LIKE '%Z'),
    CONSTRAINT alerta_concluido_em_utc CHECK (
        concluido_em IS NULL OR concluido_em LIKE '%Z'
    )
);

CREATE INDEX idx_alerta_situacao ON alerta_enviado (situacao, criado_em DESC);

-- =============================================================================
-- TELEMETRIA_OPERACAO — o que o sistema mediu de si mesmo
--
-- Agregado por (operação, hora). Uma linha por chamada faria a tabela crescer
-- sem limite e um gráfico varrer milhões de linhas.
--
-- Guarda SOMA, MÍNIMO e MÁXIMO — nunca média. Média de médias está errada:
-- agregar uma hora com 1 chamada e outra com 1000 dando peso igual mente. Com
-- soma e contagem a média de qualquer janela sai certa, e o máximo revela o
-- caso ruim que a média esconde — que é o que derruba o sistema.
-- =============================================================================
CREATE TABLE telemetria_operacao (
    id              INTEGER PRIMARY KEY,
    operacao        TEXT NOT NULL,
    hora            TEXT NOT NULL,

    -- RECUSA e FALHA separadas: recusa é o sistema decidindo não fazer e
    -- sabendo por quê; falha é algo que quebrou. Somá-las faria um rastreador
    -- varrendo URLs inexistentes parecer uma pane.
    chamadas        INTEGER NOT NULL DEFAULT 0,
    recusas         INTEGER NOT NULL DEFAULT 0,
    falhas          INTEGER NOT NULL DEFAULT 0,

    duracao_soma_ms INTEGER NOT NULL DEFAULT 0,
    duracao_min_ms  INTEGER,
    duracao_max_ms  INTEGER,

    atualizado_em   TEXT NOT NULL,

    -- A chave de negócio, e é ela que torna a descarga idempotente: o processo
    -- acumula em memória e descarrega periodicamente, e duas descargas na mesma
    -- hora precisam SOMAR na linha existente. Sem isto, um reinício no meio da
    -- hora produziria duas linhas e todo gráfico contaria em dobro.
    CONSTRAINT telemetria_operacao_unica UNIQUE (operacao, hora),
    CONSTRAINT telemetria_hora_utc CHECK (hora LIKE '%Z'),
    CONSTRAINT telemetria_atualizado_em_utc CHECK (atualizado_em LIKE '%Z')
);

CREATE INDEX idx_telemetria_hora ON telemetria_operacao (hora DESC, operacao);
