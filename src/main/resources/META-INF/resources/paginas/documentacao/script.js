/*
 * O SUMÁRIO "Nesta página" — montado dos títulos que o Markdown gerou.
 *
 * POR QUE NO NAVEGADOR, E NÃO NO SERVIDOR. O renderizador de Markdown deste
 * projeto (`MarkdownSeguro`) não põe `id` nos títulos, e pôr exigiria mexer
 * nele — que é a peça que impede injeção de marcação no texto. Não se encosta
 * na trava de segurança para ganhar uma migalha de navegação. Aqui o custo de
 * ser no cliente é conhecido e pequeno: sem JavaScript, o sumário simplesmente
 * não aparece, e o documento continua inteiro e legível.
 *
 * O QUE ELE NÃO FAZ: não altera o texto, não reordena nada e não mexe em
 * `location` — clicar num item usa o `href` de âncora, que é navegação nativa
 * do navegador. Assim o botão voltar funciona e o endereço fica compartilhável.
 */
(function () {
  "use strict";

  /*
   * OS DIAGRAMAS.
   *
   * O CommonMark, com `escapeHtml(true)`, transforma um bloco cercado
   * ```mermaid num `<pre><code class="language-mermaid">` com o conteudo
   * ESCAPADO. O mermaid, por sua vez, espera `<pre class="mermaid">` com o
   * texto cru. A conversao acontece aqui, no navegador, pelo mesmo motivo do
   * sumario: fazer isso no servidor exigiria mexer no `MarkdownSeguro`, que e a
   * peca que impede injecao de marcacao — e nao se encosta na trava de
   * seguranca para desenhar uma caixinha.
   *
   * `textContent` devolve o texto DESESCAPADO pelo proprio DOM, que e
   * exatamente o que o mermaid precisa receber. Nao ha `innerHTML` em lugar
   * nenhum deste caminho.
   */
  function prepararDiagramas() {
    var brutos = document.querySelectorAll("#doc-texto code.language-mermaid");
    var achados = [];
    brutos.forEach(function (codigo) {
      var caixa = document.createElement("pre");
      caixa.className = "mermaid";
      caixa.textContent = codigo.textContent;
      // O `<pre>` que embrulha o `<code>` e o que sai; trocar so o `<code>`
      // deixaria o `<pre>` de fora com o estilo de bloco de codigo por cima.
      var moldura = codigo.closest("pre") || codigo;
      moldura.replaceWith(caixa);
      achados.push(caixa);
    });
    return achados;
  }

  var diagramas = prepararDiagramas();

  if (diagramas.length > 0 && window.mermaid) {
    try {
      window.mermaid.initialize({
        startOnLoad: false,
        // `strict` e nao `loose`. O binmapper usa `loose` para aceitar `<br/>`
        // nos rotulos; aqui o texto do diagrama vem de arquivo do repositorio,
        // mas afrouxar a sanitizacao do mermaid para ganhar uma quebra de linha
        // e trocar seguranca por estetica. Quebra de linha se faz com rotulo
        // entre aspas com escape de nova linha.
        securityLevel: "strict",
        htmlLabels: false,
        theme: "base",
        // As cores saem da PALETA DO PROJETO, lidas do CSS em tempo de
        // execucao: assim o diagrama acompanha o tema da aplicacao em vez de
        // ter uma segunda paleta que envelhece separado.
        themeVariables: temaDoProjeto(),
        flowchart: { curve: "basis", padding: 14, useMaxWidth: true },
        fontFamily: "system-ui, -apple-system, Segoe UI, sans-serif"
      });
      window.mermaid.run({ nodes: diagramas });
    } catch (naoDesenhou) {
      // DIAGRAMA QUEBRADO NAO DERRUBA O DOCUMENTO. O texto e o conteudo; o
      // desenho e apoio. Sem isto, um erro de sintaxe numa seta apagaria a
      // pagina inteira — e a pessoa perderia o que veio ler.
      diagramas.forEach(function (caixa) {
        caixa.classList.add("mermaid-falhou");
      });
    }
  }

  /**
   * A paleta do diagrama, lida do CSS da aplicacao.
   *
   * Repetir os hexadecimais aqui criaria uma segunda fonte de verdade: trocar a
   * cor do projeto deixaria os diagramas na cor antiga, e ninguem ligaria as
   * duas coisas.
   */
  function temaDoProjeto() {
    var raiz = getComputedStyle(document.documentElement);
    function cor(nome, reserva) {
      var v = raiz.getPropertyValue(nome).trim();
      return v || reserva;
    }
    var fundo = cor("--cor-fundo-painel", "#141a23");
    var linha = cor("--cor-borda", "#2a3341");
    var frente = cor("--cor-texto", "#e8eaef");
    var destaque = cor("--cor-destaque", "#4da3ff");
    return {
      background: "transparent",
      primaryColor: fundo,
      primaryBorderColor: destaque,
      primaryTextColor: frente,
      secondaryColor: fundo,
      secondaryBorderColor: linha,
      secondaryTextColor: frente,
      tertiaryColor: fundo,
      tertiaryBorderColor: linha,
      tertiaryTextColor: frente,
      lineColor: cor("--cor-texto-fraco", "#8b95a6"),
      textColor: frente,
      mainBkg: fundo,
      nodeBorder: linha,
      clusterBkg: "transparent",
      clusterBorder: linha,
      titleColor: frente,
      edgeLabelBackground: cor("--cor-fundo", "#0d1117"),
      fontSize: "13px"
    };
  }

  var texto = document.getElementById("doc-texto");
  var moldura = document.getElementById("doc-sumario");
  var lista = document.getElementById("doc-sumario-lista");
  if (!texto || !moldura || !lista) {
    return;
  }

  var titulos = texto.querySelectorAll("h2, h3");

  // DOCUMENTO SEM SUBTÍTULO mantém o sumário escondido. Ao contrário, a moldura
  // "Nesta página" apareceria com uma lista em branco ao lado do texto — o que
  // parece defeito, e é indistinguível de um script que quebrou no meio.
  if (titulos.length === 0) {
    return;
  }

  /**
   * O `id` de um título, a partir do texto dele.
   *
   * Acento é removido por decomposição (`NFD`) antes de descartar o que não é
   * letra: sem isso, "Migração" e "Migracao" gerariam âncoras diferentes, e um
   * endereço compartilhado deixaria de funcionar ao trocar uma palavra de lugar.
   */
  function apelido(bruto) {
    return bruto
      .toLowerCase()
      .normalize("NFD")
      .replace(/[̀-ͯ]/g, "")
      .replace(/[^a-z0-9\s-]/g, "")
      .trim()
      .replace(/\s+/g, "-")
      .slice(0, 60);
  }

  var links = {};
  var usados = {};

  titulos.forEach(function (titulo) {
    if (!titulo.id) {
      // DOIS TÍTULOS COM O MESMO TEXTO existem de verdade: "O que mudou"
      // aparece em mais de um documento e pode repetir dentro de um. Sem o
      // sufixo, os dois teriam o mesmo `id`, e o navegador rolaria sempre para
      // o primeiro — o segundo item do sumário pareceria não funcionar.
      var base = apelido(titulo.textContent) || "secao";
      var id = base;
      var n = 2;
      while (usados[id] || document.getElementById(id)) {
        id = base + "-" + n;
        n += 1;
      }
      usados[id] = true;
      titulo.id = id;
    }

    var item = document.createElement("li");
    if (titulo.tagName === "H3") {
      item.className = "doc-sumario-sub";
    }
    var link = document.createElement("a");
    link.href = "#" + titulo.id;
    link.textContent = titulo.textContent;
    item.appendChild(link);
    lista.appendChild(item);
    links[titulo.id] = link;
  });

  // Só agora o sumário aparece: ele existe porque há o que listar.
  moldura.hidden = false;

  /*
   * O REALCE ACOMPANHA A ROLAGEM.
   *
   * `IntersectionObserver` e não um ouvinte de `scroll`: o ouvinte dispara
   * dezenas de vezes por segundo e obriga a medir posição a cada disparo, o que
   * trava a rolagem em documento longo. O observador avisa só quando um título
   * entra ou sai, e o navegador faz a medição fora da linha principal.
   *
   * `rootMargin` de -75% na base encolhe a área de interesse ao TERÇO SUPERIOR
   * da tela. Sem isso, o último título da página ficaria permanentemente ativo
   * assim que aparecesse no rodapé, mesmo com o leitor no meio do texto.
   */
  if (!window.IntersectionObserver) {
    return; // sem o observador, o sumário fica sem realce — e continua servindo
  }

  var ativo = null;
  var observador = new IntersectionObserver(
    function (entradas) {
      entradas.forEach(function (entrada) {
        if (!entrada.isIntersecting) {
          return;
        }
        if (ativo) {
          ativo.classList.remove("doc-sumario-ativo");
        }
        ativo = links[entrada.target.id];
        if (ativo) {
          ativo.classList.add("doc-sumario-ativo");
        }
      });
    },
    { rootMargin: "0px 0px -75% 0px", threshold: 0 }
  );

  titulos.forEach(function (titulo) {
    observador.observe(titulo);
  });
})();
