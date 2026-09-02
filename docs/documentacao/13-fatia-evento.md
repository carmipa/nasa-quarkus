# Fatia `evento`

O que a NASA publica, e o que dispara o alerta. É a razão de o sistema existir.

## A fonte

**EONET v3** — `eonet.gsfc.nasa.gov/api/v3/events`. É **aberta**: não usa chave de API.
Medido: a chamada responde `200` até **sem cabeçalho nenhum**. O `User-Agent` que enviamos
é etiqueta, não autenticação.

## O defeito de 456 km

A EONET devolve **vários pontos de geometria** por evento — a trajetória, com uma data por
ponto. O projeto original usava `getGeometry().get(0)`: o **primeiro**, que é onde o evento
*começou*.

Medido na resposta real, em 02/09/2026, evento `EONET_23800` (Tropical Storm Marie, seis
pontos):

```
primeiro ponto   2026-09-01T06:00Z   lat  14.10  lon -108.10   ← o que o legado usava
último ponto     2026-09-02T12:00Z   lat  16.80  lon -111.30   ← onde ela está agora
distância ................................................  456 km
```

Num alerta de raio 100 km, isso **avisa quem está longe e cala para quem está perto** — e
não aparece erro nenhum. Aqui a posição é sempre a do ponto de data **mais recente**.

## A segunda armadilha: a ordem das coordenadas

GeoJSON é `[longitude, latitude]`, ao contrário do que quase todo mundo escreve ao falar.

Ler na ordem intuitiva põe o evento do outro lado do planeta. E quando os dois números
estão na faixa válida — como `[-40, -20]` — **não dá exceção nenhuma**: só um pino no lugar
errado do mapa.

## Sincronizar é um upsert, e isso não é detalhe

Três desenhos possíveis, e dois quebram:

| desenho | consequência |
|---|---|
| inserir sempre | uma cópia por sincronização; mapa e estatística incham sem erro |
| `ON CONFLICT DO NOTHING` | **congela a posição no primeiro dia**; o alerta decide sobre onde a tempestade *estava* |
| `ON CONFLICT DO UPDATE` | o certo |

O primeiro era o defeito do original: a unicidade morava só no Java
(`findByEonetIdApi().orElse(new)`), e duas sincronizações simultâneas liam "não existe" e
inseriam as duas.

`xmax = 0` distingue INSERIU de ATUALIZOU na mesma ida ao banco — sem isso, *"trouxe 50"*
e *"os mesmos 50 de sempre"* produziriam a mesma linha de log.

## Proximidade: duas etapas, e a segunda é a que decide

1. **Caixa delimitadora** — retângulo em graus, que o banco resolve por índice.
2. **Geodésia** — a distância real sobre a esfera.

Parar na primeira é o erro tentador, porque "já filtrou". Mas caixa é **retângulo** e raio
é **círculo**: o canto fica a `raio × √2` do centro — **41% mais longe**.

Medido no teste, com raio de 100 km: **o canto da caixa está a 140 km**. Sem a geodésia, o
alerta avisaria essa gente — e quem é avisado à toa é quem desliga a notificação antes do
evento que importava.

## Zero eventos é alerta, não sucesso

A EONET praticamente nunca devolve vazio para uma janela de dias. Zero quase sempre
significa filtro errado ou contrato mudado, e por isso sai em `WARN`. Sucesso silencioso
aqui é o que faz ninguém perceber que o sistema parou de receber dados.

## Polígono não vira ponto

Alguns eventos têm geometria de área, não de ponto. Reduzir área a ponto exigiria escolher
um centro que a NASA **não declarou** — e esse centro entraria no alerta como se tivesse
sido medido. O evento entra sem coordenada, e a tela diz por quê.
