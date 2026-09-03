# Segurança

Nada aqui é precaução teórica. Cada trava tem um vetor concreto e um teste que a vê
recusando.

## SSRF — o proxy de imagens

O carrossel da home mostra imagens do GDACS. Elas passam **pelo nosso servidor**, por duas
razões medidas: o gdacs.org limita vazão (a mesma URL responde `200` isolada e falha em
sequência), e um `<img>` apontando para fora faria o navegador de **cada visitante**
entregar o IP dele a um terceiro.

Mas **todo proxy é um convite a SSRF**: um endpoint que busca a URL que lhe mandarem vira
ferramenta de varredura da rede interna e de leitura de metadados de nuvem.

Cinco travas, e a mais importante é a comparação de host por **igualdade exata**:

> `gdacs.org.atacante.com` **termina** em `gdacs.org` e passaria por qualquer filtro
> escrito com `endsWith` — que é como quase todo mundo escreve na primeira tentativa.

As outras: só `https`; sem `usuario:senha@`; só porta 443; **não segue redirecionamento**
(senão as travas valeriam só para o primeiro salto); e a resposta precisa ser `image/*`
dentro do teto de tamanho.

Sete vetores testados ao vivo, **todos 404**, e oito testes de unidade — com **controle do
controle**: a URL legítima **passa**, senão uma trava que recusasse tudo passaria em todos
os casos negativos e pareceria perfeita.

## XXE — o leitor de RSS

O noticiário lê **XML de origem externa**. Um `DocumentBuilderFactory` com as opções de
fábrica é a porta aberta mais clássica que existe em Java: entidades que leem arquivos
locais, alcançam a rede interna, ou se expandem até consumir a memória.

Quatro travas, sendo `disallow-doctype-decl` a mais forte — recusa o documento antes de
qualquer entidade existir, e um feed RSS legítimo nunca precisa de DOCTYPE.

**Dois controles positivos** no teste: DOCTYPE com entidade de arquivo (`file:///etc/passwd`)
e com entidade **remota** (`169.254.169.254`).

## Travessia de caminho — a documentação

Esta página lê arquivos do disco. Todo caminho é resolvido contra a pasta base e conferido
com `startsWith` **depois** de `normalize()` — é o `normalize` que resolve os `..`, e
conferir antes dele não protege nada.

O nome do arquivo vem do catálogo, não da URL. Mas isso é garantia de *hoje*; a trava
existe para o dia em que alguém passar a aceitar o nome de fora.

## Injeção de SQL

Toda consulta é parametrizada — inclusive a pesquisa por texto, que é onde a tentação
aparece. E a limpeza do termo remove `%` e `_`, que são curingas **dentro** do padrão: sem
isso, quem digitasse `%` listaria a base inteira.

## Dados pessoais

- **Destino de alerta sai mascarado** (`pa***@exemplo.com`) no log e na tela de auditoria.
- **Mensagem de erro nunca carrega o valor digitado** — carrega o *nome do campo*.
  Mensagem de erro vai para arquivo de log, para tela, e para o print que alguém cola num
  chat.
- **A URL do banco é higienizada** antes de ir para o log, nos dois formatos que carregam
  senha. Medido: a senha errada não aparece em nenhuma das 98 linhas do log de arranque.

## Segredos no repositório

O `origin` é **público**. Duas camadas independentes:

1. `.gitignore` com regras por nome, incluindo a pasta `gs/` inteira;
2. guarda de conteúdo, que varre antes de cada commit e **bloqueia** — já bloqueou um
   commit meu.

## O que a página pública informa

O `robots.txt` diz em voz alta que **é um pedido, não uma tranca**: todo rastreador honesto
obedece, nenhum mal-intencionado obedece. Por isso não há caminho sigiloso listado nele —
`Disallow` é a forma mais eficiente de anunciar um caminho a quem procura exatamente isso.
Quem protege rota é a autenticação.

## O que a auditoria de 03/09/2026 encontrou

Feita depois da troca de banco e da remoção de três fatias — o momento em que mais coisa
quebra em silêncio. Sete achados, cada um medido antes de virar item.

### Um achado refutado, e o motivo importa

A suspeita era que o formulário público chamasse o Nominatim sem limite, e o Nominatim
**bane IP** que não respeita a política dele. Medido: o adaptador **já tem** limite de 1
requisição por segundo, com `User-Agent` identificável e relógio injetado para o teste poder
provar o intervalo. A suspeita estava errada, e fica registrada — senão a próxima auditoria
a reencontra e ninguém lembra que já foi julgada.

### O defeito silencioso

A telemetria contava linhas de `cliente`, `endereco` e `contato` — tabelas removidas. A
consulta estourava, um `catch` devolvia lista vazia, e a seção **"Tamanho da base" sumia da
página**. Sem erro, sem log. Uma página com uma seção a menos é indistinguível de uma
correta.

A lista foi corrigida, e a tabela ausente passou a ser **nomeada** na exceção: a tela
continua degradando, mas o log agora diz *qual* tabela sumiu — a única informação capaz de
levar alguém ao conserto.

### A superfície de abuso

Medido: **dez inscrições criadas em segundos**, sem nada barrando. Cada uma dispara chamadas
à BrasilAPI e ao ViaCEP. O risco não é a base encher — é o projeto ser bloqueado pelos
provedores dos quais ele depende.

Entrou um limite por origem, e três decisões dele valem ser ditas:

- **falha aberto**: se o contador quebrar, a inscrição passa. Um limitador com defeito que
  bloqueia todo mundo transforma proteção contra abuso na negação de serviço que ele existe
  para impedir;
- **não usa `X-Forwarded-For`**: aquele cabeçalho é escrito pelo cliente, e confiar nele daria
  a qualquer um um limite novo por requisição. Usa o endereço da conexão, que o cliente não
  escolhe. O custo declarado: quem está atrás do mesmo NAT divide o limite;
- **não é captcha**: captcha exige serviço de terceiro, script externo e cookie — três coisas
  que este projeto recusou em toda decisão — e resolve o problema errado, incomodando gente
  para barrar robô.

### O que a API não devolve

`/api/inscritos` existe, e **não devolve e-mail nem telefone**. Uma API pública que lista os
contatos dos inscritos é uma lista para spam servida de bandeja. O que fica é o que permite
operar: quem é, se recebe alerta, e o CEP — que identifica região, não pessoa.

### O que continuou de pé

As cinco guardas anteriores sobreviveram à reescrita, e foi conferido uma a uma: SSRF no
proxy de imagens, XXE na leitura de XML, travessia de caminho na documentação, lista de
permissão da cor no atributo `style`, e o escape do Markdown. Nenhuma tela vaza rastro de
pilha.

### Dois defeitos que as próprias guardas do projeto pegaram

Durante a correção: a checagem de saúde importava um caso de uso de **outra fatia** — a
regra 3 da fronteira reprovaria o build, e reprovou; e ela lançava `IllegalStateException`,
que a catraca de exceções recusa. As duas foram encontradas por mecanismo, não por revisão.
