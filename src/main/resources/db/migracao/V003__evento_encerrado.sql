-- =============================================================================
-- V003 — o evento passa a saber quando ACABOU
--
-- O QUE FALTAVA: o esquema guardava quando o evento OCORREU, nunca quando ele
-- foi encerrado. A EONET marca isso no campo `closed`, e a diferenca importa:
-- um incendio encerrado ha tres semanas continuaria, para nos, tao ativo quanto
-- o de ontem — e continuaria disparando alerta de proximidade para quem mora
-- perto do lugar onde ele ja apagou.
--
-- POR QUE NAO BASTA FILTRAR `status=open` NA CHAMADA: esse filtro decide o que
-- ENTRA. Nada faz o evento que ja esta gravado virar encerrado quando a NASA o
-- encerra depois — e e justamente esse o caso comum, porque evento natural
-- costuma durar mais que o intervalo entre duas sincronizacoes.
--
-- NULO SIGNIFICA ATIVO, e e deliberado: e o estado de todo evento no momento em
-- que aparece. Usar um valor sentinela (uma data no futuro, o epoch) faria
-- "ativo" e "encerrado numa data estranha" ficarem indistinguiveis em consulta.
-- =============================================================================

ALTER TABLE evento_natural ADD COLUMN encerrado_em TIMESTAMPTZ;

-- A consulta de alerta pergunta "quais eventos estao ATIVOS perto daqui?".
-- Indice PARCIAL: so as linhas ativas entram nele, que sao justamente as que a
-- consulta olha. Um indice sobre a coluna inteira cresceria para sempre,
-- carregando eventos encerrados que nenhuma consulta de alerta vai ler.
CREATE INDEX idx_evento_ativo ON evento_natural(ocorrido_em)
    WHERE encerrado_em IS NULL;
