/*
 * SCRIPT DO FORMULÁRIO DE CONTATO — e só dele.
 *
 * PROPÓSITO: destacar, EM TEMPO REAL, quando o tipo escolhido faz este contato
 *   receber alerta de desastre. É a única consequência desta tela que não se vê
 *   olhando os campos — e descobri-la depois do desastre é tarde.
 *
 * O QUE ELE NÃO FAZ: não valida nada. Telefone com tamanho errado, e-mail
 *   malformado e tipo inventado são recusados pelo servidor, com a mensagem
 *   certa. Validar aqui criaria uma segunda regra, que diverge da primeira no
 *   dia em que uma das duas mudar.
 *
 * FALHA: sem este arquivo, o formulário funciona igual e a explicação do tipo
 *   continua visível — ela é escrita pelo servidor, não por aqui. O que se
 *   perde é o destaque acompanhar a escolha.
 */
(function () {
  'use strict';

  var tipo = document.getElementById('tipoContato');
  var explicacao = document.querySelector('.tipo-explicacao');

  if (tipo && explicacao) {
    var destacar = function () {
      explicacao.setAttribute('data-emergencia',
        tipo.value === 'EMERGENCIA' ? 'sim' : 'nao');
    };
    tipo.addEventListener('change', destacar);
    destacar();
  }

  // Clique duplo no envio: o banco protege pelo UNIQUE do e-mail, mas ver a
  // mensagem "ja existe" causada pelo PROPRIO clique faz concluir que existe
  // uma duplicata que nao existe.
  var formulario = document.querySelector('form.formulario');
  if (!formulario) {
    return;
  }
  formulario.addEventListener('submit', function () {
    var botao = formulario.querySelector('button[type="submit"]');
    if (!botao) {
      return;
    }
    // Depois que o envio ja partiu: desabilitar antes faria o navegador nao
    // enviar o proprio botao.
    window.setTimeout(function () {
      botao.disabled = true;
      botao.textContent = 'Salvando…';
    }, 0);
  });
})();
