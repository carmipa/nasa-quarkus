# A pilha, e o porquê de cada peça

| camada | escolha | o que substituiu |
|---|---|---|
| linguagem | Java 25 | Java 17/21 |
| plataforma | Quarkus 3.39 | Spring Boot |
| tela | Qute + HTMX | Next.js + React |
| banco | PostgreSQL 17 | Oracle |
| mapa | Leaflet (servido daqui) | react-leaflet |
| gráfico | CSS puro | Chart.js + react-chartjs-2 |
| carrossel | CSS + 90 linhas de JS | react-slick |

## O número que resume a diferença

```
front do projeto original:  484 pacotes npm no lock (23 diretos)
este projeto:               3 arquivos JS próprios + Leaflet, 55 KB + 147 KB
```

Cada um daqueles 484 é uma dependência que se atualiza sozinha e que ninguém audita.

## Por que o servidor devolve HTML

A API JSON continua existindo e é a mesma. As rotas de tela são outra coisa: devolvem a
página pronta. Três consequências práticas:

1. **A regra de negócio nunca é reescrita em JavaScript** para "validar antes de enviar".
   Validação duplicada é validação que diverge — e a que vale é sempre a do servidor.
2. **Os caminhos de leitura funcionam com o JavaScript desligado.** A lista de eventos, o
   gráfico e a documentação são HTML de verdade.
3. **Quando uma biblioteca falha, o conteúdo fica.** O mapa é uma lista com atributos
   `data-`, e o script desenha os pinos a partir dela. Se o Leaflet não carregar, a lista
   continua legível. Com react-leaflet, o conteúdo ia junto e sobrava um retângulo cinza.

## O que o JavaScript faz aqui

Só o que **apenas o navegador** consegue fazer:

- pontuar CPF e CNPJ enquanto se digita (formatar é ajudar a ler; validar é do servidor);
- lembrar o filtro na barra de endereço;
- evitar clique duplo — sem substituir a proteção do banco, que é a que vale para duas
  abas e dois aparelhos;
- ler a posição do aparelho, quando alguém clica pedindo;
- desenhar mapa e mover o carrossel.

Nenhum deles monta conteúdo, e nenhum valida regra.

## CSS: um arquivo por tela

Não há um `globals.css`. Cada tela tem a própria pasta, com o próprio CSS e o próprio JS:

```
templates/paginas/<tela>/pagina.html
META-INF/resources/paginas/<tela>/estilo.css
META-INF/resources/paginas/<tela>/script.js
```

O que **aparece em várias telas** — botão, campo, aviso, menu, grade de formulário — vive
no `base.css`, como componente. A linha é essa: é *da página* o que só existe nela; é *do
sistema* o que aparece em várias.

O erro do projeto original não foi ter um arquivo comum. Foi ter **apenas um**, com 801
linhas de todas as treze telas dentro — mexer no espaçamento de uma arriscava as outras
doze, e não havia como saber qual regra pertencia a quem.
