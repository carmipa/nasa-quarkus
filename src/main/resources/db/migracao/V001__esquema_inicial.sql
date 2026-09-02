-- =============================================================================
-- V001 — esquema inicial
--
-- PROPÓSITO DE NEGÓCIO: guardar quem deve ser avisado, onde essa pessoa está, e
--   quais eventos naturais aconteceram — o mínimo para decidir se um desastre
--   está perto de alguém.
--
-- Este arquivo é IMUTÁVEL depois de aplicado. Corrigir qualquer coisa aqui muda
-- o checksum e ABORTA o boot de quem já rodou a versão antiga. Ajuste vira V002.
--
-- O que este esquema conserta do legado (achados da auditoria de 2026-09-02):
--   A4  o DDL Oracle não tinha NENHUMA constraint UNIQUE além das PKs. A
--       idempotência da sincronização com a NASA morava só no Java, e duas
--       execuções simultâneas inseriam o mesmo evento duas vezes, sem erro.
--       Aqui `eonet_id` e `documento` são UNIQUE no BANCO.
--   A6  `data_nascimento` era VARCHAR2(10) sem forma: não ordenava, não
--       comparava, não validava. Agora é TEXT em ISO-8601 com CHECK de formato.
--   A7  `complemento` era NOT NULL. A maioria dos endereços do Brasil não tem
--       complemento, e a regra obrigava o operador a inventar um valor.
--   —   `latitude`/`longitude` eram NOT NULL. Coordenada que a origem não tem
--       é AUSENTE, e o CHECK abaixo impede o par (0,0) — o "null island", no
--       Golfo da Guiné, que poria o endereço do cliente no oceano com o mapa
--       desenhando o pino lá e nenhum erro aparecendo.
--
-- Todo instante é TEXT em ISO-8601 UTC: o SQLite não tem tipo de data, e texto
-- ISO ordena corretamente como string.
-- =============================================================================

CREATE TABLE cliente (
    id               INTEGER PRIMARY KEY AUTOINCREMENT,
    nome             TEXT NOT NULL,
    sobrenome        TEXT NOT NULL,
    data_nascimento  TEXT NOT NULL,
    documento        TEXT NOT NULL,
    criado_em        TEXT NOT NULL,

    -- INV-CLIENTE-001: o documento identifica UM cliente e só um. Sem isto,
    -- o mesmo CPF entra duas vezes e o alerta vai para o cadastro errado.
    CONSTRAINT cliente_documento_unico UNIQUE (documento),

    -- Data em ISO-8601 (AAAA-MM-DD). O CHECK é grosseiro de propósito: ele pega
    -- o erro de FORMA, que é o que quebra ordenação e comparação.
    CONSTRAINT cliente_nascimento_iso CHECK (
        length(data_nascimento) = 10
        AND substr(data_nascimento, 5, 1) = '-'
        AND substr(data_nascimento, 8, 1) = '-'
    ),
    CONSTRAINT cliente_nome_nao_vazio CHECK (length(trim(nome)) > 0),
    CONSTRAINT cliente_documento_nao_vazio CHECK (length(trim(documento)) > 0)
);

CREATE TABLE contato (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    ddd           TEXT,
    telefone      TEXT,
    celular       TEXT,
    whatsapp      TEXT,
    email         TEXT NOT NULL,
    tipo_contato  TEXT NOT NULL,
    criado_em     TEXT NOT NULL,

    -- O legado expunha `GET /api/contatos/email/{email}` devolvendo UM contato.
    -- Sem unicidade, esse endpoint é ambíguo por construção: com dois contatos
    -- no mesmo e-mail, qual dos dois ele devolve? A resposta tem de ser "não
    -- existem dois".
    CONSTRAINT contato_email_unico UNIQUE (email),
    CONSTRAINT contato_email_tem_arroba CHECK (email LIKE '%_@_%')
);

CREATE TABLE endereco (
    id           INTEGER PRIMARY KEY AUTOINCREMENT,
    cep          TEXT NOT NULL,
    numero       INTEGER,
    logradouro   TEXT NOT NULL,
    bairro       TEXT,
    localidade   TEXT NOT NULL,
    uf           TEXT NOT NULL,
    complemento  TEXT,                  -- A7: opcional, como o mundo real
    latitude     REAL,                  -- NULO = a origem não tinha coordenada
    longitude    REAL,
    criado_em    TEXT NOT NULL,

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
    -- `geo` aceita (0,0) de propósito.)
    CONSTRAINT endereco_sem_null_island CHECK (
        latitude IS NULL OR NOT (latitude = 0 AND longitude = 0)
    )
);

CREATE TABLE evento_natural (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    eonet_id      TEXT NOT NULL,
    titulo        TEXT NOT NULL,
    categoria     TEXT,
    ocorrido_em   TEXT NOT NULL,        -- ISO-8601 UTC
    latitude      REAL,
    longitude     REAL,
    json_original TEXT,
    sincronizado_em TEXT NOT NULL,

    -- INV-EONET-001: um evento da NASA existe UMA VEZ. No legado esta garantia
    -- morava só no Java (`findByEonetIdApi().orElse(new)`), e duas sincronizações
    -- simultâneas liam "não existe" e inseriam as duas — evento duplicado
    -- inflando estatística e mapa, sem nenhum erro.
    CONSTRAINT evento_eonet_id_unico UNIQUE (eonet_id),
    CONSTRAINT evento_coordenada_na_terra CHECK (
        latitude IS NULL
        OR (latitude BETWEEN -90 AND 90 AND longitude BETWEEN -180 AND 180)
    )
);

CREATE TABLE cliente_contato (
    cliente_id  INTEGER NOT NULL REFERENCES cliente(id) ON DELETE CASCADE,
    contato_id  INTEGER NOT NULL REFERENCES contato(id) ON DELETE CASCADE,
    PRIMARY KEY (cliente_id, contato_id)
);

CREATE TABLE cliente_endereco (
    cliente_id   INTEGER NOT NULL REFERENCES cliente(id) ON DELETE CASCADE,
    endereco_id  INTEGER NOT NULL REFERENCES endereco(id) ON DELETE CASCADE,
    PRIMARY KEY (cliente_id, endereco_id)
);

-- INV-ALERTA-001: o mesmo evento não avisa o mesmo cliente duas vezes.
-- A unicidade é a chave de idempotência, e ela mora no BANCO — não na memória
-- de um worker que pode reiniciar no meio.
CREATE TABLE alerta_enviado (
    id           INTEGER PRIMARY KEY AUTOINCREMENT,
    cliente_id   INTEGER NOT NULL REFERENCES cliente(id) ON DELETE CASCADE,
    evento_id    INTEGER NOT NULL REFERENCES evento_natural(id) ON DELETE CASCADE,
    destino      TEXT NOT NULL,
    situacao     TEXT NOT NULL,          -- PENDENTE | ENVIADO | FALHOU
    causa_raiz   TEXT,                   -- preenchida quando situacao = FALHOU
    tentativas   INTEGER NOT NULL DEFAULT 0,
    criado_em    TEXT NOT NULL,
    concluido_em TEXT,

    CONSTRAINT alerta_uma_vez_por_cliente_e_evento UNIQUE (cliente_id, evento_id),
    CONSTRAINT alerta_situacao_conhecida CHECK (situacao IN ('PENDENTE', 'ENVIADO', 'FALHOU')),

    -- Situação terminal exige instante de conclusão: sem isto, um alerta fica
    -- "ENVIADO" sem que ninguém saiba quando, e a auditoria não fecha.
    CONSTRAINT alerta_terminal_tem_instante CHECK (
        situacao = 'PENDENTE' OR concluido_em IS NOT NULL
    )
);

-- Índices das consultas que o sistema realmente faz.
CREATE INDEX idx_evento_ocorrido_em ON evento_natural(ocorrido_em);
CREATE INDEX idx_evento_categoria ON evento_natural(categoria);
CREATE INDEX idx_endereco_cep ON endereco(cep);
CREATE INDEX idx_alerta_situacao ON alerta_enviado(situacao);
