/*
 * PAGINAÇÃO DA TABELA "Por operação" — no máximo 20 linhas por tela.
 *
 * PROPÓSITO: a tabela tem uma linha por operação medida, e o número cresce com
 * o sistema — cada rota nova, cada caso de uso novo, aparece aqui. Passou de
 * uma tela, e uma tabela que não cabe na tela deixa de servir para comparar,
 * que é a única coisa que ela existe para fazer.
 *
 * ────────────────────────────────────────────────────────────────────────────
 * POR QUE NO NAVEGADOR, E NÃO NO SERVIDOR.
 *
 * Aqui a razão é DIFERENTE da lista do mapa. Lá, paginar no servidor encolheria
 * os pinos do mapa junto — havia um segundo consumidor do mesmo HTML. Aqui não
 * há: a tabela é o único consumidor.
 *
 * O motivo é outro, e é a ORDENAÇÃO. As linhas vêm ordenadas pelas mais
 * chamadas, e essa ordem é a informação principal da tela: a primeira linha é a
 * operação que mais roda no sistema. Paginar no servidor com `LIMIT/OFFSET`
 * sobre uma agregação que muda a cada 60 segundos faria a segunda página
 * repetir ou pular linhas que se moveram entre as duas requisições — e o
 * resultado seria uma tabela que soma errado sem dar nenhum sinal.
 *
 * Recortando no cliente, as 20 linhas visíveis vêm sempre do MESMO retrato,
 * e a soma fecha.
 * ────────────────────────────────────────────────────────────────────────────
 *
 * SEM JAVASCRIPT a tabela aparece inteira, sem paginação — e isso é coerente:
 * a tabela completa é exatamente o que se quer quando ela é tudo o que sobrou.
 *
 * FALHA: qualquer erro aqui deixa a tabela inteira visível, como veio do
 * servidor. Este bloco não apaga nem reordena linha nenhuma — só esconde e
 * mostra.
 */
(function () {
  'use strict';

  var POR_PAGINA = 20;

  var corpo = document.querySelector('.tel-tabela tbody');
  if (!corpo) {
    return;
  }

  var todas = Array.prototype.slice.call(corpo.rows);
  if (todas.length <= POR_PAGINA) {
    // NADA A PAGINAR. Sem esta saída, a tela ganharia um controle dizendo
    // "1–7 de 7 (página 1 de 1)" — que é ruído com aparência de função.
    return;
  }

  var paginaAtual = 0;
  var caixa = document.querySelector('.tel-tabela-caixa') || corpo;

  // ----------------------------------------------------------- os controles

  var controle = document.createElement('div');
  controle.className = 'lista-paginacao';

  var anterior = document.createElement('button');
  anterior.type = 'button';
  anterior.className = 'botao botao-discreto';
  anterior.textContent = '‹ Anteriores';

  var proximo = document.createElement('button');
  proximo.type = 'button';
  proximo.className = 'botao botao-discreto';
  proximo.textContent = 'Próximas ›';

  var situacao = document.createElement('span');
  situacao.className = 'lista-paginacao-situacao';
  // `polite` avisa quem usa leitor de tela que a página mudou, sem interromper
  // o que estiver sendo lido. Sem isto, trocar de página seria silencioso.
  situacao.setAttribute('aria-live', 'polite');

  controle.appendChild(anterior);
  controle.appendChild(situacao);
  controle.appendChild(proximo);
  caixa.parentNode.insertBefore(controle, caixa.nextSibling);

  anterior.addEventListener('click', function () { irPara(paginaAtual - 1); });
  proximo.addEventListener('click', function () { irPara(paginaAtual + 1); });

  function irPara(pagina) {
    paginaAtual = pagina;
    desenhar();
    // Volta ao topo da tabela: sem isto, quem clica no fim continua no fim,
    // olhando o rodapé de uma tabela que acabou de trocar por completo.
    caixa.scrollIntoView({ block: 'start', behavior: 'smooth' });
  }

  // -------------------------------------------------------------- o desenho

  function desenhar() {
    var totalDePaginas = Math.max(1, Math.ceil(todas.length / POR_PAGINA));
    paginaAtual = Math.max(0, Math.min(paginaAtual, totalDePaginas - 1));

    var primeiro = paginaAtual * POR_PAGINA;
    var ultimo = Math.min(primeiro + POR_PAGINA, todas.length);

    // Esconde TUDO e mostra a fatia. Percorrer só o que mudou seria mais rápido
    // e mais fácil de errar: bastaria um caminho esquecido para sobrar uma
    // linha de outra página no meio do recorte.
    todas.forEach(function (linha) { linha.hidden = true; });
    for (var i = primeiro; i < ultimo; i++) {
      // `hidden` e não `display:none`: o atributo é o mecanismo padrão, e a
      // linha escondida assim sai da ordem de foco e da leitura de tela — que
      // é o que se quer de uma linha fora da página atual.
      todas[i].hidden = false;
    }

    situacao.textContent = (primeiro + 1) + '–' + ultimo + ' de ' + todas.length
      + ' operações  (página ' + (paginaAtual + 1) + ' de ' + totalDePaginas + ')';

    anterior.disabled = paginaAtual === 0;
    proximo.disabled = paginaAtual >= totalDePaginas - 1;
  }

  desenhar();
})();
