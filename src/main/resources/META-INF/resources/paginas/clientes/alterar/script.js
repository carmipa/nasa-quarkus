/*
 * SCRIPT DA TELA "ALTERAR CLIENTE" — e só dela.
 *
 * PROPÓSITO: pontuar o documento enquanto se digita e impedir o clique duplo em
 *   "Salvar". Nada aqui valida: validação é do servidor, sempre.
 *
 * POR QUE O CLIQUE DUPLO IMPORTA MENOS AQUI, E MESMO ASSIM É TRATADO: alterar é
 *   idempotente — mandar a mesma alteração duas vezes deixa o cadastro no mesmo
 *   estado, ao contrário de cadastrar, onde o segundo envio esbarraria no
 *   UNIQUE do documento. O ganho aqui é outro e é sobre confiança: sem retorno
 *   visível, quem clica duas vezes numa rede lenta não sabe se salvou, e a
 *   próxima coisa que faz é recarregar a página no meio do envio.
 *
 * FALHA: sem este arquivo o formulário continua inteiro. Perde-se a pontuação
 *   do documento e o aviso de "salvando" — nada além disso.
 */
(function () {
  'use strict';

  if (window.aplicarMascaraDeDocumento) {
    window.aplicarMascaraDeDocumento(document.getElementById('documento'));
  }

  var formulario = document.querySelector('form.formulario');
  if (!formulario) {
    return;
  }

  formulario.addEventListener('submit', function () {
    var botao = formulario.querySelector('button[type="submit"]');
    if (!botao) {
      return;
    }
    // Depois que o envio já partiu: desabilitar antes faria o navegador não
    // enviar o próprio botão.
    window.setTimeout(function () {
      botao.disabled = true;
      botao.textContent = 'Salvando…';
    }, 0);
  });
})();
