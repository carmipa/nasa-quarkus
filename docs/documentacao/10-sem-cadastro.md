# Sem cadastro

Este sistema **não guarda ninguém**. Não há tela de inscrição, não há lista de e-mails, não
há tabela de gente. Quem quer saber se tem desastre por perto informa um CEP, vê a resposta
na tela, e nada fica.

Chegar a isso levou duas remoções, e as duas estão medidas abaixo. É a decisão de arquitetura
mais consequente do projeto, e a que mais código apagou.

## A primeira remoção: três fatias viram uma

O projeto original tinha `cliente`, `contato` e `endereco` — três fatias verticais, cinco
tabelas mais duas de ligação, três telas de CRUD completas.

Aquilo modelava **gestão de clientes**. Este sistema não gerencia clientes: ele avisa gente
sobre desastre. Uma inscrição bastava — quem é, onde está, por onde avisar.

```
commit 22f5035  "de PostgreSQL para SQLite; cliente/contato/endereco viram `inscrito`"
  151 arquivos    +2.718    -8.944
  a fatia `inscrito` que entrou no lugar:  25 arquivos, 1.952 linhas
```

**O que funcionava foi preservado**, movido com `git mv` para o histórico sobreviver: a busca
de CEP (BrasilAPI e ViaCEP em cadeia), a geocodificação pelo Nominatim e a validação de
e-mail. Todos três seguem em uso hoje, na consulta de alerta. Só o CRUD morreu.

## A segunda remoção: a inscrição também sai

```
commit a79308e
  26 arquivos    -2.130
  migração V002  DROP TABLE alerta_enviado;  DROP TABLE inscrito;
```

Três motivos, e nenhum deles é técnico:

1. **Uma lista de e-mails é um alvo.** O repositório é público e a aplicação fica numa VPS
   que divide máquina com outros nove serviços. O valor de invadir isto era exatamente o
   tamanho da lista — e agora é zero, porque não há lista.
2. **Dado pessoal traz obrigação.** Guardar nome, e-mail e CEP de terceiros cria dever de
   guarda, de exclusão a pedido e de aviso em caso de vazamento. Uma vitrine de portfólio não
   tem por que assumir isso.
3. **Formulário público que escreve no banco é abusado.** Sem cadastro, o caminho de abuso
   não existe: não há o que inserir. É a diferença entre *limitar* a escrita e *não ter*
   escrita.

O terceiro é o que dispensou um limitador de taxa, uma fila de moderação e uma rotina de
expurgo — três peças que precisariam existir, funcionar e ser testadas.

## O que ficou no lugar

A consulta de alerta, sem estado. Está descrita em [Alerta](/documentacao/fatia-alerta), e a
forma é:

- **`POST`, não `GET`**, mesmo sem escrever nada — o e-mail vai no corpo. Num `GET` ele iria
  na URL, que fica no log de acesso, no histórico do navegador e no `Referer`. *Idempotência
  não é o critério; onde o dado sensível trafega é.*
- **O e-mail é opcional na API.** Na tela ele serve para a pessoa ver a mensagem endereçada a
  ela; numa integração não tem uso, e exigi-lo forçaria quem integra a inventar um endereço —
  que é pior que não ter.
- **`guardado: false` está no contrato da resposta.** Não é enfeite: quem integra precisa
  poder afirmar, pelo próprio contrato, que nada foi persistido.

## Como a decisão fica travada

Uma decisão de não guardar dado pessoal é fácil de reverter por acidente: basta alguém
acrescentar uma tabela numa migração futura, e o sistema volta a guardar **em silêncio**,
porque nada mais reclamaria.

O que impede:

| trava | onde | o que ela pega |
|---|---|---|
| `naoGuardaGente` | `FluxoDeAlertaTest` | reprova se `inscrito` ou `alerta_enviado` voltarem a existir |
| `naoGuardaGente` | `EsquemaDoBancoTest` | o mesmo, mais `cliente`, `contato` e `endereco` |
| guarda de fronteira | `FronteiraArquiteturaTest` | a lista de fatias é declarada; fatia nova sem registro reprova |

As duas primeiras consultam `sqlite_master` diretamente. É de propósito: uma checagem
baseada nas classes Java passaria com a tabela existindo no banco e nenhuma entidade mapeada
— que é exatamente o estado intermediário de quem está reintroduzindo cadastro.

## Por que a V002 é uma migração nova, e não uma correção da V001

Migração aplicada é **imutável**. Reescrever a V001 para nunca ter criado as tabelas faria o
banco de quem já rodou a V001 divergir do banco de quem rodou só a versão corrigida — e as
duas se chamariam "V001". O aplicador confere o que já rodou; um `checksum` diferente para o
mesmo nome é falha de arranque, não um detalhe.

A V002 também **não é reversível**, e isso está escrito nela: ela apaga dado. Um `DOWN` que
recriasse as tabelas devolveria a estrutura vazia, o que é pior que não ter `DOWN` nenhum —
pareceria uma reversão bem-sucedida.

A ordem dos `DROP` importa: `alerta_enviado` primeiro, `inscrito` depois. A chave estrangeira
aponta naquele sentido, e a ordem inversa falha com o banco cheio e passa com o banco vazio —
o pior tipo de migração, a que só quebra em produção.
