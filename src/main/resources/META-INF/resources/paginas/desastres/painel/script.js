/*
 * SCRIPT DO PAINEL DE DESASTRES — e só dele.
 *
 * PROPÓSITO: duas coisas que só o navegador faz — trocar de aba e ler a
 *   posição do aparelho.
 *
 * O QUE ELE NÃO FAZ: não busca dados (quem busca é o HTMX, declarado no HTML),
 *   não monta lista, não valida coordenada. Latitude fora da faixa é recusada
 *   pelo peer `geo`, no servidor, com a mensagem certa.
 *
 * INVARIANTES:
 *   1. As abas usam o atributo `hidden`, não estilo em linha. `hidden` é
 *      semântico: leitor de tela pula o painel escondido.
 *   2. A geolocalização só é PEDIDA quando alguém clica. Pedir ao abrir a
 *      página dispara a permissão sem contexto — e a maioria nega, o que queima
 *      o pedido para sempre naquele site.
 *
 * FALHA: sem este arquivo, todas as abas aparecem empilhadas e os formulários
 *   continuam funcionando, porque quem os envia é o HTMX. Perde-se a
 *   organização, não a função.
 */
(function () {
  'use strict';

  var abas = document.querySelectorAll('[data-aba]');
  var paineis = document.querySelectorAll('[data-painel]');

  function mostrar(nome) {
    abas.forEach(function (b) {
      b.classList.toggle('aba-ativa', b.dataset.aba === nome);
    });
    paineis.forEach(function (p) {
      p.hidden = p.dataset.painel !== nome;
    });
  }

  abas.forEach(function (b) {
    b.addEventListener('click', function () {
      mostrar(b.dataset.aba);
    });
  });

  var botao = document.querySelector('[data-usar-minha-posicao]');
  var lat = document.getElementById('latitude');
  var lon = document.getElementById('longitude');

  if (!botao || !lat || !lon) {
    return;
  }
  if (!navigator.geolocation) {
    botao.hidden = true;   // botao que nao faz nada ensina que a tela quebrou
    return;
  }

  botao.addEventListener('click', function () {
    var textoOriginal = botao.textContent;
    botao.disabled = true;
    botao.textContent = 'Localizando…';

    navigator.geolocation.getCurrentPosition(
      function (pos) {
        // Seis casas decimais equivalem a ~11 cm. Mais que isso e ruido do
        // proprio aparelho, e polui o campo.
        lat.value = pos.coords.latitude.toFixed(6);
        lon.value = pos.coords.longitude.toFixed(6);
        botao.disabled = false;
        botao.textContent = textoOriginal;
      },
      function (erro) {
        // Negar e resposta legitima, nao defeito: o formulario continua
        // utilizavel digitando a coordenada a mao.
        botao.disabled = false;
        botao.textContent = erro.code === erro.PERMISSION_DENIED
          ? 'Permissão negada — digite a coordenada'
          : 'Não consegui localizar';
      },
      { enableHighAccuracy: false, timeout: 10000, maximumAge: 60000 }
    );
  });
})();
