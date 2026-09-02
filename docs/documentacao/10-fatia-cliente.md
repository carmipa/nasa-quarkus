# Fatia `cliente`

Quem recebe o alerta. Sem cadastro, o aviso não tem destinatário.

## A invariante central

**INV-CLIENTE-001 — o documento identifica UM cliente e só um.** Protegida por
`UNIQUE (documento)` no banco, não apenas no Java.

E o documento é **normalizado** antes de comparar. No projeto original,
`"111.222.333-44"` e `"11122233344"` eram **duas pessoas diferentes**, e a unicidade não
pegava — o alerta ia para o cadastro errado, ou para um cadastro fantasma enquanto o real
ficava sem aviso.

### A prova

```
POST /api/clientes  {"documento":"12345678909"}      → 201
POST /api/clientes  {"documento":"123.456.789-09"}   → 409 CONFLITO_DE_ESTADO
```

## Concorrência

Oito cadastros **simultâneos** do mesmo documento: **1 entra, 7 são recusados**.

A checagem prévia da aplicação (`já existe?`) **não** protege — entre a pergunta e a
inserção cabe outra requisição, e clique duplo é o caso comum, não o raro. Quem protege é
a restrição do banco.

## O que a fatia NÃO valida

**Dígito verificador de CPF.** É decisão declarada: a validação aqui é de *forma* (11 ou 14
dígitos), não de *autenticidade*. Um CPF com dígito errado entra. A alternativa exigiria
decidir o que fazer com CNPJs, documentos estrangeiros e cadastros de teste — e nenhuma
dessas decisões foi tomada.

## Telas

`listar` · `cadastrar` · `buscar por documento` · `detalhe` · `alterar` · `excluir`

Duas decisões de tela que vieram da revisão de erro de boa-fé:

- **Excluir tem tela própria**, com os dados à vista. Um `confirm()` na lista não diz *de
  quem* se trata, e quem clicou na linha errada responde "sim" com a mesma confiança.
- **Alterar mantendo o MESMO documento tem de passar.** É a alteração mais comum de todas,
  e uma checagem ingênua de "já existe este documento?" a recusaria — porque quem existe é
  o próprio cliente sendo alterado.
