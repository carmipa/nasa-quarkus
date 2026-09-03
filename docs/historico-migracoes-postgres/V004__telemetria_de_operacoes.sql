-- =============================================================================
-- V004 — telemetria de operações
--
-- O QUE FALTAVA: o sistema registra tudo em log, no formato canônico com
-- operação, alvo e duração. Log responde "o que aconteceu naquele momento" —
-- e não responde "com que frequência isso acontece", "está mais lento que na
-- semana passada" nem "esta fonte externa caiu quantas vezes hoje". Essas são
-- perguntas de AGREGADO, e ler agregado de log é grep com aritmética à mão.
--
-- POR QUE NO BANCO, E NÃO EM MEMÓRIA. Telemetria em memória zera a cada
-- reinício — e reinício é exatamente o que acontece depois de um incidente,
-- que é exatamente quando se quer olhar o histórico. Medido em 02/09/2026: o
-- `quarkusDev` reiniciando apagou 21.542 eventos do contêiner; a telemetria
-- não pode ter o mesmo destino.
--
-- POR QUE AGREGADO, E NÃO UMA LINHA POR CHAMADA. Uma linha por operação
-- executada faria a tabela crescer sem limite, e uma consulta de página teria
-- de varrer milhões de linhas para desenhar um gráfico. O agregado por
-- operação e por HORA responde as mesmas perguntas com quatro ordens de
-- grandeza menos linhas — e a hora é a menor granularidade que ainda deixa ver
-- "ficou lento depois do almoço".
--
-- POR QUE A DURAÇÃO GUARDA SOMA, MÍNIMO E MÁXIMO, E NÃO A MÉDIA. Média de
-- médias está errada: agregar duas horas com 1 e 1000 chamadas dando peso
-- igual mente. Guardando SOMA e CONTAGEM, a média de qualquer janela sai
-- certa. O MÁXIMO é o que revela o caso ruim que a média esconde — e é o caso
-- ruim que derruba o sistema, não a média.
-- =============================================================================

CREATE TABLE telemetria_operacao (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,

    -- A operação, no mesmo vocabulário do log: `sincronizar-eonet`,
    -- `consultar-cep`, `despachar-alertas`. Um vocabulário só para as duas
    -- coisas — telemetria que nomeia diferente do log obriga a traduzir
    -- mentalmente no pior momento.
    operacao          TEXT        NOT NULL,

    -- A hora, truncada, SEMPRE em UTC. Agrupar no fuso da sessão faria a mesma
    -- linha cair em horas diferentes conforme quem consultasse — a mesma
    -- família de defeito do log em -03:00 e do agrupamento por dia.
    hora              TIMESTAMPTZ NOT NULL,

    -- CHAMADAS conta tudo; as outras duas separam o que deu errado, e a
    -- distinção entre elas é a que mais informa:
    --   recusas  = o sistema decidiu NÃO fazer, e sabe por quê (CEP inválido,
    --              coordenada fora da Terra, evento sem posição). É trabalho
    --              correto, e um pico aqui é sinal de entrada ruim.
    --   falhas   = alguma coisa quebrou (a NASA caiu, o banco recusou). É
    --              trabalho que deveria ter dado certo.
    -- Somar as duas num só contador apagaria essa diferença, e "1000 erros"
    -- levaria a investigar infraestrutura quando o problema é o dado.
    chamadas          BIGINT      NOT NULL DEFAULT 0,
    recusas           BIGINT      NOT NULL DEFAULT 0,
    falhas            BIGINT      NOT NULL DEFAULT 0,

    -- Duração em MILISSEGUNDOS. Soma para calcular média de qualquer janela;
    -- mínimo e máximo para ver a dispersão que a média esconde.
    duracao_soma_ms   BIGINT      NOT NULL DEFAULT 0,
    duracao_min_ms    BIGINT,
    duracao_max_ms    BIGINT,

    atualizado_em     TIMESTAMPTZ NOT NULL DEFAULT now(),

    -- A CHAVE DE NEGÓCIO É (operação, hora), e ela é ÚNICA.
    --
    -- É o que torna a gravação idempotente: o processo acumula em memória e
    -- descarrega periodicamente, e duas descargas na mesma hora precisam SOMAR
    -- na linha existente, não criar uma segunda. Sem esta restrição, um
    -- reinício no meio da hora produziria duas linhas para a mesma hora e todo
    -- gráfico contaria em dobro — silenciosamente.
    CONSTRAINT telemetria_operacao_unica UNIQUE (operacao, hora)
);

-- A página desenha "as últimas N horas", sempre. O índice atende exatamente
-- essa consulta: recorta por hora e já entrega ordenado.
CREATE INDEX idx_telemetria_hora ON telemetria_operacao (hora DESC, operacao);

COMMENT ON TABLE telemetria_operacao IS
    'Agregado por operacao e por hora (UTC). Uma linha por (operacao, hora); '
    'a descarga periodica SOMA na linha existente.';
