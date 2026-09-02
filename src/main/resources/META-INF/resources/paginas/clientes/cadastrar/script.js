/*
 * SCRIPT DA TELA "CADASTRAR CLIENTE" — e só dela.
 *
 * PROPÓSITO: duas coisas que só o navegador pode fazer, e nenhuma delas é
 *   validar (validação é do servidor, sempre).
 *
 *   1. Pontuar o documento enquanto se digita (componente compartilhado).
 *   2. Impedir o CLIQUE DUPLO no botão de cadastrar.
 *
 * SOBRE O CLIQUE DUPLO, que é a razão séria deste arquivo existir: numa rede
 *   lenta o botão não dá retorno imediato, e clicar de novo é o reflexo humano
 *   normal — não é descuido. Sem isto, saem dois POSTs iguais.
 *
 *   O banco JÁ protege: `cliente_documento_unico` recusa o segundo, e há teste
 *   provando com oito cadastros simultâneos do mesmo documento (um entra, sete
 *   são recusados). Este bloqueio NÃO substitui aquele — a proteção de verdade
 *   é a do banco, porque é a única que vale para duas abas, dois aparelhos e
 *   duas pessoas ao mesmo tempo. O que se evita aqui é a pessoa receber um erro
 *   de "documento já cadastrado" causado pelo próprio clique, e concluir que
 *   existe uma duplicata que não existe.
 *
 * FALHA: se este arquivo não carregar, o formulário continua funcionando e o
 *   banco continua protegendo. Volta o risco de ver a mensagem confusa.
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
    // `disabled` DEPOIS que o envio já partiu: desabilitar antes faria o
    // navegador não enviar o próprio botão, e alguns servidores dependem disso
    // para saber qual botão foi usado. O tempo zero garante a ordem.
    window.setTimeout(function () {
      botao.disabled = true;
      botao.textContent = 'Cadastrando…';
    }, 0);
  });
})();
