/*
 * INTERNACIONALIZAÇÃO AUTOMÁTICA — página inteira, sem dicionário.
 *
 * PROPÓSITO DE NEGÓCIO: a tela nova já nasce traduzida. O caminho do dicionário foi
 *   medido e abandonado no binmapper: 1185 textos em 24 arquivos, ~2370 traduções para
 *   dois idiomas — e o pior nem é o volume, é que cada frase editada sem atualizar a
 *   chave deixa o texto em português EM SILÊNCIO, sem erro e sem sintoma.
 *
 * INVARIANTES:
 *   1. Este arquivo é carregado ANTES do `element.js` do Google. O `element.js` chama
 *      `googleTranslateElementInit` como callback; se a função ainda não existir naquele
 *      instante, o widget não inicializa e nada traduz, sem erro visível.
 *   2. Português = AUSÊNCIA do cookie `googtrans`. Não existe "traduzir para pt": existe
 *      apagar o cookie e recarregar.
 *   3. O `lang` do <html> NUNCA é alterado. O Google o usa como idioma de ORIGEM;
 *      marcá-lo como "en" faz ele concluir que a página já está traduzida.
 *   4. O que o JavaScript pinta DEPOIS do load nasce desprotegido — por isso o
 *      MutationObserver carimba `translate="no"` no que aparecer.
 *
 * FALHA: falha ABERTA de propósito. Sem rede ou com o Google bloqueado, a página fica no
 *   idioma de origem e continua inteira. Tradução é conforto, não função crítica, e
 *   derrubar a tela por causa dela seria pior.
 */

function googleTranslateElementInit() {
  new google.translate.TranslateElement(
    { pageLanguage: 'pt', autoDisplay: false }, 'google_translate_element');
}

function traduzir(lang) {
  var d = window.location.hostname;
  if (lang === 'pt') {
    // Português é a ausência do cookie — apagar no host e no domínio.
    document.cookie = 'googtrans=; expires=Thu, 01 Jan 1970 00:00:00 UTC; path=/;';
    document.cookie = 'googtrans=; expires=Thu, 01 Jan 1970 00:00:00 UTC; path=/; domain=' + d + ';';
  } else {
    document.cookie = 'googtrans=/pt/' + lang + '; path=/;';
    document.cookie = 'googtrans=/pt/' + lang + '; path=/; domain=' + d + ';';
  }
  window.location.reload();   // o widget lê o cookie no carregamento
}

/* Seletores das ilhas técnicas: aqui o texto do elemento NÃO é linguagem humana. */
var ILHAS_TECNICAS = 'code, pre, kbd, samp, .relogio, .idiomas, [data-tecnico]';

function protegerIlhasTecnicas(raiz) {
  var alvos = raiz.querySelectorAll ? raiz.querySelectorAll(ILHAS_TECNICAS) : [];
  for (var i = 0; i < alvos.length; i++) {
    if (!alvos[i].hasAttribute('translate')) {
      alvos[i].setAttribute('translate', 'no');
      alvos[i].classList.add('notranslate');
    }
  }
}

document.addEventListener('DOMContentLoaded', function () {
  protegerIlhasTecnicas(document);

  // O que o HTMX trouxer depois nasce desprotegido: carimbar o que aparecer.
  var observador = new MutationObserver(function (mudancas) {
    for (var i = 0; i < mudancas.length; i++) {
      var novos = mudancas[i].addedNodes;
      for (var j = 0; j < novos.length; j++) {
        if (novos[j].nodeType === 1) {
          protegerIlhasTecnicas(novos[j]);
        }
      }
    }
  });
  observador.observe(document.body, { childList: true, subtree: true });

  // O seletor de bandeiras. `data-idioma` em vez de onclick inline: o canon proíbe
  // JavaScript estático dentro do template.
  var botoes = document.querySelectorAll('.bandeira[data-idioma]');
  for (var k = 0; k < botoes.length; k++) {
    botoes[k].addEventListener('click', function () {
      traduzir(this.getAttribute('data-idioma'));
    });
  }
});
