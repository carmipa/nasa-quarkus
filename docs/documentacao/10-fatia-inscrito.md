# Inscrito — quem pediu para ser avisado

É a razão de o sistema existir. Alguém informa nome, e-mail e o CEP de onde está; quando a
NASA publica um desastre dentro do raio dela, o alerta é disparado.

## O que isto substituiu, e por quê

Antes eram **três fatias**: `cliente`, `contato` e `endereco` — 8.192 linhas em 111
arquivos, cinco tabelas e duas de ligação, três telas de CRUD completas.

Aquilo modelava **gestão de clientes**. Este sistema não gerencia clientes: ele **avisa
gente sobre desastre**. Uma inscrição é o que basta — quem é, onde está, por onde avisar.

A medida da simplificação aparece melhor no teste do fluxo de alerta: montar o cenário
exigia três inserções e duas tabelas de ligação; virou uma linha.

**O que funcionava foi preservado**, movido com `git mv` para o histórico sobreviver: a
busca de CEP (BrasilAPI + ViaCEP em cadeia), a geocodificação (Nominatim) e a validação de
e-mail. Só o CRUD morreu.

## As invariantes

| # | invariante | dano se quebrada |
|---|---|---|
| 1 | **nome e e-mail obrigatórios** | o e-mail é o único canal que o sistema sabe usar; sem ele o registro nunca pode ser avisado — um alerta que existe no banco e não chega a ninguém |
| 2 | **um e-mail se inscreve uma vez** | o clique duplo no formulário criaria duas inscrições, e a pessoa receberia cada alerta em dobro, sem nada acusando |
| 3 | **CEP obrigatório, coordenada não** | provedores externos falham; recusar a inscrição porque o Nominatim estava fora seria punir a pessoa por uma falha nossa — e ela não voltaria |
| 4 | **sem coordenada não recebe alerta, e a tela DIZ isso** | esconder faria alguém esperar um aviso que nunca vem — a pior falha possível num sistema de alerta, porque é silenciosa dos dois lados |
| 5 | **cancelar não apaga** | apagar deixaria alertas já enviados apontando para ninguém, e a auditoria de "quem foi avisado" ficaria com buracos |

A 2 é garantida pelo **banco** (`UNIQUE (email)`), não por um `SELECT` antes: entre o
`SELECT` e o `INSERT` cabe o segundo clique, que é justamente o caso a cobrir.

## Onde a coordenada entra

O CEP vira coordenada por uma **cadeia de provedores**, na ordem declarada: BrasilAPI
primeiro, ViaCEP depois. Quando nenhum dos dois traz posição, o Nominatim geocodifica o
endereço textual.

A inscrição é gravada **em qualquer caso**. Sem coordenada ela fica marcada, aparece na tela
com o selo *"sem posição — não recebe alerta"*, e é contada num badge próprio no topo. Três
lugares dizendo a mesma coisa, porque este é o estado que não pode passar despercebido.

## O raio

Padrão de **100 km** — a distância em que um desastre natural ainda é assunto de quem mora
ali: fumaça de incêndio florestal viaja mais que isso, e uma tempestade severa a 100 km
chega em horas.

Aceita de 1 a 20.000 km. O teto é metade da circunferência da Terra: acima disso o raio
cobre o planeta e "proximidade" deixa de significar coisa alguma. **O banco recusa** fora
dessa faixa.

## O que a fatia NÃO faz

**Não envia nada.** Inscrever cria o direito de ser avisado; quem avisa é a
[varredura de alertas](/documentacao/fatia-alerta). Misturar as duas coisas faria uma falha
de envio derrubar um cadastro que estava correto.

**Não guarda telefone como canal.** O campo existe e é opcional, mas hoje é só registro: o
sistema não manda mensagem. Guardá-lo prometendo envio que não existe seria mentir no
cadastro — e a tela diz isso na dica do campo.
