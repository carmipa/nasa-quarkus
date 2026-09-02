/*
 * SCRIPT DA TELA "LISTA DE CLIENTES" — e só dela.
 *
 * PROPÓSITO DE NEGÓCIO: fazer a busca sobreviver a um F5 e ao botão "voltar".
 *   Sem isto, quem filtra por "silva", abre um cliente e volta, cai numa lista
 *   sem filtro nenhum e precisa digitar de novo — que é o momento em que se
 *   desiste da tela e se pede a informação a outra pessoa.
 *
 * O QUE ESTE ARQUIVO NÃO FAZ, DE PROPÓSITO:
 *   - não monta HTML: a lista vem pronta do servidor;
 *   - não valida nada: regra validada aqui é regra que diverge da do servidor,
 *     e a que vale é sempre a do servidor;
 *   - não busca dados: quem busca é o HTMX, declarado no próprio HTML.
 *   Restou o que só o navegador sabe fazer — mexer na barra de endereço.
 *
 * INVARIANTES:
 *   1. `replaceState`, nunca `pushState`. Com `pushState`, cada letra digitada
 *      viraria uma entrada no histórico, e sair da página exigiria apertar
 *      "voltar" uma vez por caractere.
 *   2. A página funciona INTEIRA sem este arquivo. Se ele falhar ao carregar,
 *      perde-se a memória do filtro na barra de endereço — nada além disso.
 *
 * FALHA: envolvido em try/catch porque `history` é bloqueado em alguns modos de
 *   privacidade, e uma exceção aqui interromperia os ouvintes do HTMX
 *   registrados depois — quebrando a busca inteira para salvar a barra de
 *   endereço, que é a troca errada.
 */
(function () {
  'use strict';

  var campo = document.getElementById('busca');
  if (!campo) {
    return;
  }

  /** Põe o termo na barra de endereço, sem recarregar e sem sujar o histórico. */
  function lembrarNaUrl(termo) {
    try {
      var url = new URL(window.location.href);
      if (termo) {
        url.searchParams.set('termo', termo);
      } else {
        url.searchParams.delete('termo');
      }
      // Também some com a página: mudar o filtro invalida o número da página, e
      // manter `pagina=7` num filtro novo abriria numa página vazia.
      url.searchParams.delete('pagina');
      window.history.replaceState(null, '', url.toString());
    } catch (e) {
      /* Modo privado pode bloquear o history. A busca continua funcionando. */
    }
  }

  // Ouve o MESMO evento que dispara o HTMX, para os dois andarem juntos. Um
  // temporizador próprio aqui acabaria fora de passo com o `delay:300ms` do
  // HTMX, e a URL mostraria um termo diferente do da lista.
  document.body.addEventListener('htmx:afterRequest', function (evento) {
    if (evento.detail && evento.detail.elt === campo) {
      lembrarNaUrl(campo.value.trim());
    }
  });

  // Ao abrir com `?termo=` na URL, o campo já vem preenchido pelo servidor. O
  // cursor vai para o FIM do texto: por padrão ele iria para o começo, e a
  // primeira tecla digitada apareceria antes do que já estava escrito.
  if (campo.value) {
    campo.focus();
    campo.setSelectionRange(campo.value.length, campo.value.length);
  }
})();
