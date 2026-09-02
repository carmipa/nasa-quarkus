/*
 * RELÓGIO DA PÁGINA — a hora do servidor (UTC) e a do visitante, lado a lado.
 *
 * PROPÓSITO DE NEGÓCIO: este é um sistema de alerta de desastre, em que "quando" decide
 *   se um evento entra na janela de risco. A confusão mais cara deste domínio é alguém
 *   ler um horário local e compará-lo com um dado gravado em UTC. Mostrar as duas horas,
 *   sempre rotuladas, custa uma linha e elimina a dúvida.
 *
 * INVARIANTES:
 *   1. O relógio ANDA a partir do instante do SERVIDOR, não do relógio do navegador. O
 *      servidor manda o instante ISO no carregamento; o navegador só conta o tempo que
 *      passou desde então. Assim um aparelho com a hora errada não faz a página mentir
 *      sobre a hora do sistema — ele erra apenas a própria linha "Local", que é dele.
 *   2. A hora UTC é formatada com `timeZone: 'UTC'` explícito. Deixar o padrão usaria o
 *      fuso do aparelho e a linha rotulada "UTC" mostraria hora local — o defeito seria
 *      invisível para quem está no fuso zero e enganoso para todos os outros.
 *   3. O bloco do relógio leva `translate="no"`: número de hora traduzido vira lixo.
 *
 * FALHA: sem o atributo `data-instante-servidor`, o relógio não inventa hora — ele para
 *   e escreve "—". Relógio que mostra a hora errada é pior que relógio parado, porque o
 *   parado se denuncia.
 */
(function () {
  'use strict';

  var bloco = document.querySelector('.relogio[data-instante-servidor]');
  if (!bloco) {
    return;   // a página não tem relógio: nada a fazer, e nada a inventar
  }

  var instanteServidor = Date.parse(bloco.getAttribute('data-instante-servidor'));
  var alvoUtc = document.getElementById('relogio-utc');
  var alvoLocal = document.getElementById('relogio-local');
  var alvoFuso = document.getElementById('relogio-fuso');

  if (isNaN(instanteServidor)) {
    if (alvoUtc) { alvoUtc.textContent = '—'; }
    if (alvoLocal) { alvoLocal.textContent = '—'; }
    return;   // instante ilegível: parar, nunca chutar
  }

  // Quanto o relógio DESTE aparelho difere do do servidor. Guardado uma vez, no
  // carregamento, para que a contagem seguinte seja só aritmética.
  var defasagem = Date.now() - instanteServidor;

  if (alvoFuso) {
    try {
      alvoFuso.textContent = Intl.DateTimeFormat().resolvedOptions().timeZone || '';
    } catch (e) {
      alvoFuso.textContent = '';
    }
  }

  function doisDigitos(n) {
    return (n < 10 ? '0' : '') + n;
  }

  function formatar(data, emUtc) {
    var a = emUtc ? data.getUTCFullYear() : data.getFullYear();
    var m = (emUtc ? data.getUTCMonth() : data.getMonth()) + 1;
    var d = emUtc ? data.getUTCDate() : data.getDate();
    var h = emUtc ? data.getUTCHours() : data.getHours();
    var mi = emUtc ? data.getUTCMinutes() : data.getMinutes();
    var s = emUtc ? data.getUTCSeconds() : data.getSeconds();
    return a + '-' + doisDigitos(m) + '-' + doisDigitos(d) + ' '
      + doisDigitos(h) + ':' + doisDigitos(mi) + ':' + doisDigitos(s);
  }

  function tique() {
    var agora = new Date(Date.now() - defasagem);   // a hora do SERVIDOR, andando
    if (alvoUtc) { alvoUtc.textContent = formatar(agora, true); }
    if (alvoLocal) { alvoLocal.textContent = formatar(new Date(), false); }
  }

  tique();
  setInterval(tique, 1000);
})();
