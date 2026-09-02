-- =============================================================================
-- V001 — esquema inicial (PostgreSQL)
--
-- As invariantes são as MESMAS da versão anterior, em SQLite. O que mudou foi
-- a forma de dizê-las, e três ficaram mais fortes porque o PostgreSQL tem tipos
-- que o SQLite não tinha:
--
--   - instante era TEXT ISO-8601, agora é TIMESTAMPTZ. O banco passa a guardar
--     instante absoluto e a recusar texto que não seja data. Isto sustenta, no
--     armazenamento, o mesmo invariante de UTC que em 02/09 se descobriu
--     quebrado no LOG: agora nem o banco aceita hora ambígua.
--   - data_nascimento era TEXT com CHECK de formato — declarado "grosseiro de
--     propósito" porque o SQLite não tinha DATE. Agora é DATE, e o tipo recusa
--     2026-02-31 e 2026-13-01, que o CHECK de posição de hífen deixava passar.
--   - id era INTEGER AUTOINCREMENT, agora é BIGINT GENERATED ALWAYS AS IDENTITY:
--     padrão SQL, e o ALWAYS impede gravar id à mão e colidir com a sequência
--     depois — defeito que só aparece muitos registros adiante.
--
-- MANTIDO DE PROPÓSITO, e a razão importa:
--   - json_original continua TEXT, e NÃO virou JSONB. É cópia forense da
--     resposta da NASA: se um dia ela mandar algo malformado, é exatamente esse
--     payload que se vai querer ler. JSONB recusaria a inserção e descartaria a
--     única prova do problema.
--   - unicidade de e-mail continua sensível a maiúsculas, como já era. Mudar
--     regra no meio de uma portabilidade faz com que, diante do próximo defeito,
--     ninguém saiba se veio da troca de banco ou da regra nova.
--
-- O QUE O LEGADO ERRAVA, e continua corrigido aqui:
--   - documento sem UNIQUE: "111.222.333-44" e "11122233344" eram duas pessoas;
--   - latitude/longitude NOT NULL: coordenada ausente virava (0,0), o "null
--     island" no Golfo da Guiné, com o mapa desenhando o pino lá e nenhum erro;
--   - unicidade do evento EONET só no Java: duas sincronizações simultâneas
--     liam "não existe" e inseriam as duas.
-- =============================================================================

CREATE TABLE cliente (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    nome             TEXT NOT NULL,
    sobrenome        TEXT NOT NULL,
    data_nascimento  DATE NOT NULL,
    documento        TEXT NOT NULL,
    criado_em        TIMESTAMPTZ NOT NULL,

    -- INV-CLIENTE-001: o documento identifica UM cliente e só um. Sem isto, o
    -- mesmo CPF entra duas vezes e o alerta vai para o cadastro errado.
    CONSTRAINT cliente_documento_unico UNIQUE (documento),

    CONSTRAINT cliente_nome_nao_vazio CHECK (length(trim(nome)) > 0),
    CONSTRAINT cliente_sobrenome_nao_vazio CHECK (length(trim(sobrenome)) > 0),
    CONSTRAINT cliente_documento_nao_vazio CHECK (length(trim(documento)) > 0)
);

CREATE TABLE contato (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    ddd           TEXT,
    telefone      TEXT,
    celular       TEXT,
    whatsapp      TEXT,
    email         TEXT NOT NULL,
    tipo_contato  TEXT NOT NULL,
    criado_em     TIMESTAMPTZ NOT NULL,

    -- O legado expunha GET /api/contatos/email/{email} devolvendo UM contato.
    -- Sem unicidade, esse endpoint é ambíguo por construção: com dois contatos
    -- no mesmo e-mail, qual dos dois ele devolve? A resposta tem de ser "não
    -- existem dois".
    CONSTRAINT contato_email_unico UNIQUE (email),
    CONSTRAINT contato_email_tem_arroba CHECK (email LIKE '%_@_%')
);

CREATE TABLE endereco (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    cep          TEXT NOT NULL,
    numero       INTEGER,
    logradouro   TEXT NOT NULL,
    bairro       TEXT,
    localidade   TEXT NOT NULL,
    uf           TEXT NOT NULL,
    complemento  TEXT,                     -- A7: opcional, como o mundo real
    latitude     DOUBLE PRECISION,         -- NULO = a origem não tinha coordenada
    longitude    DOUBLE PRECISION,
    criado_em    TIMESTAMPTZ NOT NULL,

    CONSTRAINT endereco_uf_com_duas_letras CHECK (length(uf) = 2),

    -- Coordenada é um PAR: ter só uma metade é pior que não ter nenhuma, porque
    -- parece preenchida.
    CONSTRAINT endereco_coordenada_completa CHECK (
        (latitude IS NULL AND longitude IS NULL)
        OR (latitude IS NOT NULL AND longitude IS NOT NULL)
    ),
    CONSTRAINT endereco_coordenada_na_terra CHECK (
        latitude IS NULL
        OR (latitude BETWEEN -90 AND 90 AND longitude BETWEEN -180 AND 180)
    ),

    -- NULL ISLAND: o par exato (0,0) fica no Golfo da Guiné e é o destino
    -- clássico de coordenada que faltou e alguém preencheu com o padrão do tipo.
    -- Endereço de cliente nunca fica lá. (Esta regra é DO ENDEREÇO: um evento
    -- natural PODE ocorrer em alto-mar sobre aquele ponto, e por isso o peer
    -- geo aceita (0,0) de propósito.)
    CONSTRAINT endereco_sem_null_island CHECK (
        latitude IS NULL OR NOT (latitude = 0 AND longitude = 0)
    )
);

CREATE TABLE evento_natural (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    eonet_id        TEXT NOT NULL,
    titulo          TEXT NOT NULL,
    categoria       TEXT,
    ocorrido_em     TIMESTAMPTZ NOT NULL,
    latitude        DOUBLE PRECISION,
    longitude       DOUBLE PRECISION,
    json_original   TEXT,                  -- cópia forense: TEXT de propósito
    sincronizado_em TIMESTAMPTZ NOT NULL,

    -- INV-EONET-001: um evento da NASA existe UMA VEZ. No legado esta garantia
    -- morava só no Java (findByEonetIdApi().orElse(new)), e duas sincronizações
    -- simultâneas liam "não existe" e inseriam as duas — evento duplicado
    -- inflando estatística e mapa, sem nenhum erro.
    CONSTRAINT evento_eonet_id_unico UNIQUE (eonet_id),
    CONSTRAINT evento_coordenada_na_terra CHECK (
        latitude IS NULL
        OR (latitude BETWEEN -90 AND 90 AND longitude BETWEEN -180 AND 180)
    )
);

CREATE TABLE cliente_contato (
    cliente_id  BIGINT NOT NULL REFERENCES cliente(id) ON DELETE CASCADE,
    contato_id  BIGINT NOT NULL REFERENCES contato(id) ON DELETE CASCADE,
    PRIMARY KEY (cliente_id, contato_id)
);

CREATE TABLE cliente_endereco (
    cliente_id   BIGINT NOT NULL REFERENCES cliente(id) ON DELETE CASCADE,
    endereco_id  BIGINT NOT NULL REFERENCES endereco(id) ON DELETE CASCADE,
    PRIMARY KEY (cliente_id, endereco_id)
);

-- INV-ALERTA-001: o mesmo evento não avisa o mesmo cliente duas vezes.
-- A unicidade é a chave de idempotência, e ela mora no BANCO — não na memória
-- de um worker que pode reiniciar no meio.
CREATE TABLE alerta_enviado (
    id           BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    cliente_id   BIGINT NOT NULL REFERENCES cliente(id) ON DELETE CASCADE,
    evento_id    BIGINT NOT NULL REFERENCES evento_natural(id) ON DELETE CASCADE,
    destino      TEXT NOT NULL,
    situacao     TEXT NOT NULL,          -- PENDENTE | ENVIADO | FALHOU
    causa_raiz   TEXT,                   -- preenchida quando situacao = FALHOU
    tentativas   INTEGER NOT NULL DEFAULT 0,
    criado_em    TIMESTAMPTZ NOT NULL,
    concluido_em TIMESTAMPTZ,

    CONSTRAINT alerta_uma_vez_por_cliente_e_evento UNIQUE (cliente_id, evento_id),
    CONSTRAINT alerta_situacao_conhecida CHECK (situacao IN ('PENDENTE', 'ENVIADO', 'FALHOU')),

    -- Situação terminal exige instante de conclusão: sem isto, um alerta fica
    -- "ENVIADO" sem que ninguém saiba quando, e a auditoria não fecha.
    CONSTRAINT alerta_terminal_tem_instante CHECK (
        situacao = 'PENDENTE' OR concluido_em IS NOT NULL
    ),
    CONSTRAINT alerta_tentativas_nao_negativas CHECK (tentativas >= 0)
);

-- Índices das consultas que o sistema realmente faz.
CREATE INDEX idx_evento_ocorrido_em ON evento_natural(ocorrido_em);
CREATE INDEX idx_evento_categoria ON evento_natural(categoria);
CREATE INDEX idx_endereco_cep ON endereco(cep);
CREATE INDEX idx_alerta_situacao ON alerta_enviado(situacao);
