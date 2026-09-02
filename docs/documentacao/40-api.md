# API

Todos os endpoints devolvem JSON. Nenhum exige chave de API.

## Clientes

| método | rota | o que garante |
|---|---|---|
| `POST` | `/api/clientes` | 201 com `Location`; 409 no documento repetido, **em qualquer forma** |
| `GET` | `/api/clientes` | paginado, com teto de 100 |
| `GET` | `/api/clientes/pesquisar?termo=` | busca por nome, sobrenome ou documento |
| `GET` | `/api/clientes/{id}` | |
| `GET` | `/api/clientes/documento/{documento}` | aceita com ou sem pontuação |
| `PUT` | `/api/clientes/{id}` | manter o mesmo documento **passa** |
| `DELETE` | `/api/clientes/{id}` | 204; contatos e endereços caem em cascata |

## Endereços

| método | rota | o que garante |
|---|---|---|
| `GET` | `/api/enderecos/consultar-cep/{cep}` | 404 se não existe, **503 se o provedor caiu** |
| `POST` | `/api/enderecos` | o CEP preenche o que faltou; `clienteId` opcional liga na hora |
| `GET` | `/api/enderecos/cliente/{clienteId}` | |
| `POST` | `/api/enderecos/{id}/vincular/{clienteId}` | idempotente |
| `DELETE` | `/api/enderecos/{id}` | |

Toda resposta traz `participaDoAlertaDeProximidade` e o motivo quando é `false`.

## Contatos

| método | rota | o que garante |
|---|---|---|
| `POST` | `/api/contatos` | 409 no e-mail repetido, **inclusive em caixa diferente** |
| `GET` | `/api/contatos/email/{email}` | resposta única — garantida pelo `UNIQUE` |
| `GET` | `/api/contatos/tipo/{tipo}` | |
| `GET` | `/api/contatos/emergencia/cliente/{clienteId}` | **não paginado**: quem vai ser avisado tem de ser avisado inteiro |
| `POST` | `/api/contatos/{id}/vincular/{clienteId}` | é este vínculo que faz o alerta existir |
| `PUT` `DELETE` | `/api/contatos/{id}` | |

Toda resposta traz `recebeAlerta` e `motivoNaoRecebeAlerta`.

## Eventos

| método | rota | o que garante |
|---|---|---|
| `POST` | `/api/eventos/sincronizar?limite=&dias=&apenasAtivos=` | **POST porque escreve**; idempotente por `eonetId` |
| `GET` | `/api/eventos` | mais recentes primeiro |
| `GET` | `/api/eventos/categoria/{categoria}` | |
| `GET` | `/api/eventos/eonet/{eonetId}` | |
| `GET` | `/api/eventos/proximos?latitude=&longitude=&raioKm=&dias=` | duas etapas; a distância vem junto |
| `GET` | `/api/eventos/estatisticas/categorias?dias=` | |
| `GET` | `/api/eventos/resumo` | total e ativos |

## Alertas

| método | rota | o que garante |
|---|---|---|
| `POST` | `/api/alertas/varrer?raioKm=&dias=` | descobre e **registra**, sem enviar; seguro de repetir |
| `POST` | `/api/alertas/despachar?limite=` | envia o que está na fila |
| `GET` | `/api/alertas` `/situacao/{s}` `/cliente/{id}` | destino **mascarado** |
| `GET` | `/api/alertas/resumo` | contagem por situação |
| `GET` | `/api/alertas/meio-de-entrega` | **`entregaDeVerdade`** — a tela não pode mentir |

## Erros

Toda falha devolve o mesmo formato, com a **causa-raiz** nomeada:

```json
{"erro":"...","causa":"CONFLITO_DE_ESTADO","alvo":"documento","operacao":"cadastrar-cliente"}
```

| causa-raiz | HTTP |
|---|---|
| `DADO_INVALIDO` | 400 |
| `DADO_AUSENTE` | 404 |
| `CONFLITO_DE_ESTADO` | 409 |
| `PROVEDOR_INDISPONIVEL` · `TEMPO_ESGOTADO` · `CONCORRENCIA` | 503 |
| `PROVEDOR_RECUSOU` | 502 |
| `PERSISTENCIA_FALHOU` · `CONFIGURACAO_AUSENTE` | 500 |

O campo `alvo` é o **nome do campo**, e é por ele que a tela sabe qual caixa destacar.
