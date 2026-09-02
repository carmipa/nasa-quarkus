# Fontes de dados

**Nenhuma exige chave de API.** Isso não é detalhe de implementação: significa que o
sistema continua funcionando sem cadastro, sem cota, e **sem uma credencial que possa
vazar**.

| fonte | para quê | chave | situação |
|---|---|---|---|
| **NASA EONET v3** | os eventos que disparam o alerta | não exige | mantida do original |
| **BrasilAPI CEP v2** | endereço pelo CEP, já com coordenada | não exige | nova — é a mais rápida e a única que traz coordenada junto |
| **ViaCEP** | endereço pelo CEP, quando a primeira não responde | não exige | mantida, agora como reserva |
| **Nominatim / OSM** | coordenada quando o CEP não a traz | não exige | substituiu o Google Geocoding |
| **GDACS (ONU/UE)** | o noticiário da home | não exige | substituiu a ReliefWeb, desativada |
| **OpenStreetMap** | os ladrilhos do mapa | não exige | atribuição ODbL obrigatória |
| **Esri World Imagery** | a camada de satélite | não exige | alternativa ao Mapbox, que cobra |

## Sobre a chave da NASA que não existe

A EONET v3 é aberta. Medido: a chamada responde `200` **sem cabeçalho nenhum**. O
`User-Agent` que enviamos é etiqueta, não autenticação. Não há chave da NASA neste projeto,
e não havia no original.

## As duas que saíram, e por quê

**Google Geocoding** exigia chave e cobrança por uso. Foi trocada pelo **Nominatim**, que é
aberto. A política de uso dele é regra, não recomendação: **uma requisição por segundo** e
`User-Agent` identificável. Quem ignora leva bloqueio de IP — e o sintoma chega como "a
geocodificação parou de funcionar", dias depois, sem relação aparente com a causa. Por isso
o limite está **no código**, e não na esperança de que ninguém faça um laço.

**ReliefWeb** foi desativada:

```
api.reliefweb.int/v1   HTTP 410  "v1 has been decommissioned"
api.reliefweb.int/v2   HTTP 403  "not using an approved appname"
```

A v2 exige um identificador de aplicação previamente aprovado. O carrossel de notícias do
projeto original **não funciona hoje**.

## Por que o GDACS é melhor que a fonte anterior

Não é só substituição. Medido no feed real — 1 MB, 348 itens:

- é especificamente sobre **desastres**, não sobre relatórios humanitários em geral;
- traz **nível de alerta** (verde/laranja/vermelho): a tela destaca o grave em vez de
  listar 348 itens com o mesmo peso;
- traz **coordenadas**, o que permite cruzar a notícia com a mesma geodésia do resto do
  sistema.

## Como cada uma degrada

| fonte fora | o que acontece |
|---|---|
| NASA | a base local continua válida; o alerta segue funcionando sobre ela |
| BrasilAPI | cai para o ViaCEP |
| todos os de CEP | o endereço entra **sem coordenada**, marcado; o cadastro não falha |
| Nominatim | idem |
| GDACS | a home mostra "noticiário indisponível" e continua inteira; se houver cache válido, ele é servido |

Nenhuma delas derruba uma tela.
