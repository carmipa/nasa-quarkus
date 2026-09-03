# Fatia `endereco`

Onde a pessoa está. É esta fatia que transforma um cliente em alguém **localizável** — e
sem coordenada, o alerta de proximidade não tem o que comparar.

## A cadeia de provedores, e a ordem dela

| ordem | provedor | tempo medido | traz coordenada? |
|---|---|---|---|
| 1 | BrasilAPI CEP v2 | 0,23 s | **sim**, na mesma resposta |
| 2 | ViaCEP | 1,04 s | não |
| 3 | Nominatim (OpenStreetMap) | — | só quando a coordenada não veio |

O projeto original fazia **sempre duas chamadas**: ViaCEP para o endereço e Google
Geocoding para a coordenada. Aqui a segunda só acontece quando é necessária — **1 de cada
6 CEPs medidos** volta sem coordenada.

A ordem é **declarada**, não deduzida da resolução de beans. Injetar
`Instance<ConsultaCepPort>` e iterar pareceria elegante e seria um defeito com data
marcada: a ordem do CDI não é garantida, e o provedor primário viraria o reserva sem
ninguém tocar em nada — trocando 0,23 s por 1,04 s em toda consulta, silenciosamente.

## As duas respostas que enganam

Ambas medidas contra o corpo real, e ambas com teste:

**BrasilAPI sem `location`.** Devolve `200` com o objeto de coordenadas **vazio**.
Preencher com `0,0` ali poria o endereço no Golfo da Guiné, com o pino desenhado no mapa e
nenhum erro aparecendo.

**ViaCEP responde HTTP 200 com `{"erro":"true"}`.** Quem confere só o status lê o corpo de
erro como se fosse endereço, e grava um registro com **todos os campos vazios**.

## Quatro estados, nunca dois

| estado | o que a pessoa deve fazer |
|---|---|
| o CEP não existe | conferir o que digitou |
| o provedor caiu | tentar de novo — o CEP pode estar certo |
| o CEP existe, sem coordenada | seguir; o endereço não entra no alerta |
| a geocodificação falhou | seguir; idem |

Dizer "CEP não encontrado" quando o provedor caiu faz a pessoa **apagar um CEP que estava
certo**.

## O CEP preenche, nunca sobrescreve

O preenchimento automático completa o que está **vazio** e para aí. Quem corrigiu o nome
da rua sabe algo que a base do CEP ainda não sabe — normalmente que a rua mudou de nome e
a base não atualizou. Um preenchimento que apaga a correção é pior que nenhum: a pessoa
digita de novo, o campo apaga de novo, e ela conclui que a tela está brigando com ela.

## Falha do provedor NÃO impede o cadastro

O endereço entra com o que foi digitado, sem coordenada, **marcado**. Transferir a
indisponibilidade de um serviço de terceiro para o nosso cadastro seria o pior tipo de
acoplamento.

## Um engano meu, registrado

Achei que `99999999` fosse um CEP falso e que a BrasilAPI estivesse errada ao responder
`200`. Fui medir: **99999999 é real** — Sarandi/PR, Avenida das Torres. O CEP realmente
inexistente, `00000000`, dá `404`, e a tela diz "nenhum provedor conhece este CEP".

Quem estava errado era eu, e quase "corrigi" um comportamento correto.
