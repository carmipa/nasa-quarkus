/*
 * SCRIPT DA HOME — e só dela: o carrossel de notícias.
 *
 * PROPÓSITO: fazer o carrossel andar sozinho e acrescentar as setas. O trilho
 *   em si é CSS (`overflow-x` + `scroll-snap`), então rola com o dedo, com a
 *   roda do mouse e com o teclado mesmo sem este arquivo.
 *
 * POR QUE ISSO IMPORTA: o legado usava `react-slick` — um dos 484 pacotes do
 *   `node_modules` — que monta o carrossel inteiro em JavaScript. Quando a
 *   biblioteca falha, o CONTEÚDO vai junto e o visitante vê um bloco vazio.
 *   Aqui, se este arquivo não carregar, perde-se o movimento automático e duas
 *   setas; as notícias continuam lá, roláveis.
 *
 * CONTEÚDO QUE SE MOVE SOZINHO PRECISA PODER PARAR, e isso não é preferência —
 *   é acessibilidade. Movimento automático atrapalha quem lê devagar, quem usa
 *   ampliador de tela e quem tem sensibilidade a movimento. Por isso há QUATRO
 *   formas de parar, e todas automáticas:
 *     1. `prefers-reduced-motion` — quem pediu menos movimento no sistema
 *        operacional nunca vê o carrossel andar sozinho;
 *     2. mouse em cima — para enquanto a pessoa lê;
 *     3. foco pelo teclado — para para quem navega sem mouse;
 *     4. aba escondida — para de gastar bateria e requisição quando ninguém vê.
 *
 * INVARIANTES:
 *   1. `replaceState` não é usado aqui, e nenhuma navegação acontece: o
 *      carrossel só rola. Andar sozinho nunca pode mexer no histórico.
 *   2. Rola por uma LARGURA DE CARTÃO medida do DOM, nunca por número fixo — a
 *      largura é `min(22rem, 82%)` e muda com a janela.
 *   3. Ao chegar no fim, volta ao começo. Um carrossel que trava na última
 *      notícia parece quebrado.
 *
 * FALHA: qualquer erro deixa o trilho como estava — uma lista rolável. É por
 *   isso que este script não monta nada.
 */
(function () {
  'use strict';

  /*
   * O CARROSSEL CHEGA DEPOIS DA PÁGINA, e este bloco precisa esperar por ele.
   *
   * REGRESSÃO MEDIDA em 03/09/2026. O noticiário passou a ser carregado por
   * HTMX depois da página — porque, dentro da requisição, ele fazia a home
   * levar 1340 ms em média e 4015 ms no pior caso. A troca consertou a home e
   * QUEBROU o carrossel: este script rodava no carregamento, não encontrava
   * `[data-carrossel]` (que ainda não existia) e desistia em silêncio.
   *
   * O sintoma era exatamente o que dá para ver: as notícias apareciam, e o
   * carrossel simplesmente não andava. Nenhum erro no console — o script tinha
   * feito o que estava escrito.
   *
   * A correção é ouvir o HTMX: `htmx:afterSwap` dispara quando o pedaço entra
   * no documento. E o `iniciar()` continua sendo chamado no carregamento
   * também, porque a mesma função serve para o carrossel que já vem no HTML —
   * o dia em que alguém voltar a renderizá-lo direto, isto continua valendo.
   *
   * `data-carrossel-ativo` é a trava contra iniciar duas vezes: dois
   * temporizadores no mesmo trilho o fariam pular duas notícias por vez.
   */
  function iniciar() {

  var carrossel = document.querySelector('[data-carrossel]:not([data-carrossel-ativo])');
  if (!carrossel) {
    return;
  }

  var trilho = carrossel.querySelector('[data-carrossel-trilho]');
  var anterior = carrossel.querySelector('[data-carrossel-anterior]');
  var proximo = carrossel.querySelector('[data-carrossel-proximo]');
  if (!trilho || !anterior || !proximo) {
    return;
  }

  var INTERVALO = parseInt(carrossel.dataset.intervalo, 10) || 6000;
  var temporizador = null;
  var pausadoPeloUsuario = false;

  /** Quem pediu menos movimento no sistema NUNCA vê o carrossel andar sozinho. */
  var querMenosMovimento = window.matchMedia
    && window.matchMedia('(prefers-reduced-motion: reduce)').matches;

  /** Um cartão + o vão entre eles, medido do DOM — nunca um número fixo. */
  function passo() {
    var cartao = trilho.querySelector('li');
    if (!cartao) {
      return trilho.clientWidth;
    }
    var estilo = window.getComputedStyle(trilho);
    var vao = parseFloat(estilo.columnGap || estilo.gap || '0') || 0;
    return cartao.getBoundingClientRect().width + vao;
  }

  function noFim() {
    // 2px de tolerância: navegadores devolvem fração de pixel em `scrollLeft`.
    return trilho.scrollLeft >= trilho.scrollWidth - trilho.clientWidth - 2;
  }

  function atualizarSetas() {
    anterior.disabled = trilho.scrollLeft <= 2;
    proximo.disabled = noFim();
  }

  function avancar() {
    if (noFim()) {
      // Volta ao começo: um carrossel que trava na última notícia parece quebrado.
      trilho.scrollTo({ left: 0, behavior: 'smooth' });
    } else {
      trilho.scrollBy({ left: passo(), behavior: 'smooth' });
    }
  }

  // ---------------------------------------------------------- andar sozinho

  function comecar() {
    if (querMenosMovimento || pausadoPeloUsuario || temporizador) {
      return;
    }
    temporizador = window.setInterval(avancar, INTERVALO);
    carrossel.removeAttribute('data-pausado');
  }

  function parar(porQuemUsa) {
    if (porQuemUsa) {
      pausadoPeloUsuario = true;
      carrossel.setAttribute('data-pausado', '');
    }
    if (temporizador) {
      window.clearInterval(temporizador);
      temporizador = null;
    }
  }

  function retomar() {
    pausadoPeloUsuario = false;
    comecar();
  }

  // Mouse em cima: para enquanto a pessoa lê.
  carrossel.addEventListener('mouseenter', function () { parar(true); });
  carrossel.addEventListener('mouseleave', retomar);

  // Foco pelo teclado: quem navega sem mouse também precisa que pare.
  carrossel.addEventListener('focusin', function () { parar(true); });
  carrossel.addEventListener('focusout', function (evento) {
    if (!carrossel.contains(evento.relatedTarget)) {
      retomar();
    }
  });

  // Aba escondida: nao gasta bateria nem requisicao com o que ninguem ve.
  document.addEventListener('visibilitychange', function () {
    if (document.hidden) {
      parar(false);
    } else {
      comecar();
    }
  });

  // ------------------------------------------------------------------ setas

  anterior.addEventListener('click', function () {
    parar(true);   // clicou: assumiu o controle, e o automatico sai da frente
    trilho.scrollBy({ left: -passo(), behavior: 'smooth' });
  });

  proximo.addEventListener('click', function () {
    parar(true);
    avancar();
  });

  trilho.addEventListener('scroll', atualizarSetas, { passive: true });
  window.addEventListener('resize', atualizarSetas);

  // Só agora as setas aparecem: até aqui, elas não faziam nada.
  carrossel.setAttribute('data-carrossel-ativo', '');
  atualizarSetas();
  comecar();
  }

  // O carrossel que já vem no HTML — hoje não vem, e o dia em que voltar a vir
  // isto continua valendo.
  iniciar();

  // E o que chega por HTMX. `afterSwap` dispara quando o pedaço entra no
  // documento; `afterSettle` seria tarde demais para a primeira medição de
  // largura, que o cálculo do passo precisa.
  document.body.addEventListener('htmx:afterSwap', function (evento) {
    if (evento.target && evento.target.querySelector
        && (evento.target.matches('[data-carrossel]')
            || evento.target.querySelector('[data-carrossel]'))) {
      iniciar();
    }
  });
})();
