-- =============================================================================
-- V002 — o tipo de contato passa a ser um conjunto FECHADO
--
-- O QUE O LEGADO FAZIA: `tipoContato` era um <input type="text"> livre, com o
-- valor padrão "Principal" preenchido no código. Texto livre num campo de
-- classificação produz, em pouco tempo, "Principal", "principal", "PRINCIPAL"
-- e "Pincipal" convivendo na mesma coluna — todos parecendo certos numa tela
-- que mostra um contato de cada vez.
--
-- POR QUE ISSO IMPORTA, e não é arrumação: a fatia de alerta precisa responder
-- "quais são os contatos de EMERGÊNCIA deste cliente?" para decidir quem
-- avisar quando um evento natural acontece perto do endereço dele. Com texto
-- livre essa pergunta não tem resposta confiável — um contato gravado como
-- "emergencia" sem acento simplesmente não apareceria, e ninguém seria
-- avisado. O silêncio seria idêntico ao de "este cliente não tem contato de
-- emergência".
--
-- POR QUE É MIGRAÇÃO NOVA, E NÃO EDIÇÃO DA V001: migração aplicada é IMUTÁVEL.
-- A V001 já rodou e o checksum dela está gravado; editá-la faria o aplicador
-- abortar o arranque — que é exatamente o comportamento desejado, e que não se
-- contorna só porque agora seria conveniente. Correção vira migração nova.
--
-- BACKFILL ANTES DA RESTRIÇÃO: os UPDATE precedem o CHECK de propósito.
-- Aplicar a restrição primeiro faria esta migração falhar em qualquer banco que
-- já tenha um "Principal" gravado — inclusive o de quem já usou a tela. A ordem
-- é o que torna esta migração segura de rodar num banco com dado dentro.
--
-- SEM `unaccent()`: aquela função exige a extensão homônima, que pode não estar
-- instalada no servidor de destino — e migração que depende de extensão falha
-- no deploy, não aqui. As variantes com e sem acento são listadas à mão; são
-- poucas e conhecidas.
--
-- NORMALIZAÇÃO CONSERVADORA: o que não for reconhecido vira ALTERNATIVO, e
-- NUNCA EMERGENCIA. Errar para o lado do alerta a mais criaria um contato de
-- emergência que ninguém escolheu ser, e alguém receberia aviso de desastre
-- por causa de um backfill.
-- =============================================================================

UPDATE contato SET tipo_contato = 'PRINCIPAL'
 WHERE upper(btrim(tipo_contato)) IN ('PRINCIPAL', 'PRINCIPAIS', 'MAIN', 'PADRAO', 'PADRÃO');

UPDATE contato SET tipo_contato = 'EMERGENCIA'
 WHERE upper(btrim(tipo_contato)) IN ('EMERGENCIA', 'EMERGÊNCIA', 'EMERGENCIAL', 'URGENCIA', 'URGÊNCIA');

UPDATE contato SET tipo_contato = 'COMERCIAL'
 WHERE upper(btrim(tipo_contato)) IN ('COMERCIAL', 'TRABALHO', 'EMPRESA', 'PROFISSIONAL');

-- Tudo o que sobrou. Ver "normalização conservadora" acima.
UPDATE contato SET tipo_contato = 'ALTERNATIVO'
 WHERE tipo_contato NOT IN ('PRINCIPAL', 'EMERGENCIA', 'COMERCIAL');

ALTER TABLE contato
  ADD CONSTRAINT contato_tipo_conhecido
  CHECK (tipo_contato IN ('PRINCIPAL', 'ALTERNATIVO', 'EMERGENCIA', 'COMERCIAL'));

-- O alerta pergunta por tipo e por cliente. Sem este índice, achar os contatos
-- de emergência varre a tabela inteira a cada evento processado — e são muitos
-- eventos por sincronização.
CREATE INDEX idx_contato_tipo ON contato(tipo_contato);
