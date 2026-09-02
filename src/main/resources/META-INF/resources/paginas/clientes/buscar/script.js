/*
 * SCRIPT DA TELA "BUSCAR POR DOCUMENTO" — e só dela.
 *
 * PROPÓSITO: pontuar o documento e evitar consulta com documento incompleto.
 *
 * POR QUE SEGURAR A CONSULTA ATÉ 11 DÍGITOS: esta busca é EXATA. Com três
 *   dígitos digitados ela responde "nenhum cliente com este documento" — que é
 *   verdade e é péssimo, porque quem lê conclui que a pessoa não está
 *   cadastrada quando só faltou terminar de digitar. Um "nenhum resultado"
 *   prematuro é a forma mais fácil de cadastrar em duplicidade alguém que já
 *   existe.
 *
 * O QUE ISTO NÃO É: não é validação. Não recusa nada, não bloqueia tecla, não
 *   decide se o documento é válido. Só adia a pergunta até ela fazer sentido —
 *   quem decide se o documento vale é o servidor, e a tela mostra a resposta.
 *
 * INVARIANTE: o cancelamento usa o evento `htmx:configRequest`, que é o ponto
 *   em que o HTMX ainda aceita desistir. Tentar impedir no `keyup` disputaria
 *   com o próprio HTMX, e o resultado dependeria da ordem de registro dos
 *   ouvintes — que ninguém controla.
 *
 * FALHA: sem este arquivo, a busca dispara desde o primeiro dígito. Funciona,
 *   só volta a mostrar "não encontrado" cedo demais.
 */
(function () {
  'use strict';

  var MINIMO_DE_DIGITOS = 11;   // CPF. CNPJ tem 14 e passa por aqui naturalmente.

  var campo = document.getElementById('documento');
  if (!campo) {
    return;
  }

  if (window.aplicarMascaraDeDocumento) {
    window.aplicarMascaraDeDocumento(campo);
  }

  document.body.addEventListener('htmx:configRequest', function (evento) {
    if (evento.detail.elt !== campo) {
      return;
    }
    var digitos = campo.value.replace(/\D/g, '');
    if (digitos.length > 0 && digitos.length < MINIMO_DE_DIGITOS) {
      evento.preventDefault();
    }
  });
})();
