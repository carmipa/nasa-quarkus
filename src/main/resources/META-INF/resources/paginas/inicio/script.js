/*
 * SCRIPT DA HOME — e só dela: as setas do carrossel.
 *
 * PROPÓSITO: acrescentar navegação por clique a um carrossel que JÁ FUNCIONA
 *   sem JavaScript. O trilho é `overflow-x:auto` com `scroll-snap`, então rola
 *   com o dedo, com a roda do mouse, com o teclado e com a barra — tudo isso
 *   vindo do CSS. Este arquivo só acrescenta os botões.
 *
 * POR QUE ISSO IMPORTA: o legado usava `react-slick`, que monta o carrossel
 *   inteiro em JavaScript. Quando a biblioteca falha — e ela é um dos 484
 *   pacotes do `node_modules` —, o conteúdo vai junto: o visitante vê um bloco
 *   vazio onde deveriam estar as notícias. Aqui, se este arquivo não carregar,
 *   perde-se DUAS SETAS. As notícias continuam lá, roláveis.
 *
 * INVARIANTES:
 *   1. As setas nascem ESCONDIDAS no CSS e só aparecem quando este script marca
 *      `data-carrossel-ativo`. Botão que não faz nada é pior que botão nenhum:
 *      ensina que a interface está quebrada.
 *   2. As setas desabilitam nas pontas. Botão que rola para lugar nenhum faz a
 *      pessoa clicar de novo achando que não funcionou.
 *   3. Rola por uma LARGURA DE CARTÃO medida do DOM, nunca por um número fixo:
 *      a largura do cartão é `min(22rem, 82%)` e muda com a janela.
 *
 * FALHA: se qualquer coisa aqui quebrar, o carrossel volta a ser o que já era
 *   sem o script — uma lista rolável. É por isso que ele não monta nada.
 */
(function () {
  'use strict';

  var carrossel = document.querySelector('[data-carrossel]');
  if (!carrossel) {
    return;
  }

  var trilho = carrossel.querySelector('[data-carrossel-trilho]');
  var anterior = carrossel.querySelector('[data-carrossel-anterior]');
  var proximo = carrossel.querySelector('[data-carrossel-proximo]');
  if (!trilho || !anterior || !proximo) {
    return;
  }

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

  function atualizarSetas() {
    // 2px de tolerância: navegadores devolvem fração de pixel em `scrollLeft`, e
    // sem a folga a seta da direita nunca desabilitaria no fim.
    var fim = trilho.scrollWidth - trilho.clientWidth - 2;
    anterior.disabled = trilho.scrollLeft <= 2;
    proximo.disabled = trilho.scrollLeft >= fim;
  }

  anterior.addEventListener('click', function () {
    trilho.scrollBy({ left: -passo(), behavior: 'smooth' });
  });

  proximo.addEventListener('click', function () {
    trilho.scrollBy({ left: passo(), behavior: 'smooth' });
  });

  trilho.addEventListener('scroll', atualizarSetas, { passive: true });
  window.addEventListener('resize', atualizarSetas);

  // Só agora as setas aparecem: até aqui, elas não faziam nada.
  carrossel.setAttribute('data-carrossel-ativo', '');
  atualizarSetas();
})();
