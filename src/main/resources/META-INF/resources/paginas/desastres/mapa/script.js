/*
 * SCRIPT DO MAPA DE DESASTRES — e só dele.
 *
 * PROPÓSITO: transformar a LISTA que já está no HTML em pinos no mapa.
 *
 * A LISTA É A FONTE, e o mapa é a segunda visão dela. Duas consequências boas:
 *   não há JSON embutido que possa divergir do que está escrito na tela, e se o
 *   Leaflet não carregar — são 147 KB — a lista continua ali, legível. O legado
 *   montava tudo em react-leaflet: quando a biblioteca falhava, o conteúdo ia
 *   junto e sobrava um retângulo cinza.
 *
 * MARCADOR VETORIAL (`circleMarker`), nunca o ícone padrão: o ícone do Leaflet
 *   baixa PNGs por um caminho relativo que quebra sempre que os arquivos mudam
 *   de lugar — é o defeito mais comum de quem usa a biblioteca. Círculo é
 *   desenhado, não baixado.
 *
 * INVARIANTES:
 *   1. Só entra no mapa quem TEM coordenada. Item sem posição fica só na lista,
 *      com o aviso — nunca vira um pino em (0,0), no Golfo da Guiné.
 *   2. O mapa se ajusta aos pinos existentes. Uma posição inicial fixa poria o
 *      Brasil na tela enquanto todos os eventos estão no Pacífico.
 *   3. Atribuição do OpenStreetMap sempre visível — exigência da licença ODbL.
 *   4. O título vem do banco e entra em HTML: escapar não é opcional.
 *
 * FALHA: qualquer erro aqui deixa a lista intacta. É por isso que o script não
 *   apaga nem move nada do HTML.
 */
(function () {
  'use strict';

  var caixa = document.querySelector('[data-mapa]');
  var lista = document.querySelector('[data-mapa-eventos]');
  if (!caixa || !lista || typeof window.L === 'undefined') {
    return;
  }

  var itens = Array.prototype.slice.call(lista.querySelectorAll('[data-latitude]'));
  if (itens.length === 0) {
    return;
  }

  function escapar(texto) {
    var d = document.createElement('div');
    d.textContent = texto || '';
    return d.innerHTML;
  }

  /*
   * SO PASSA `#rrggbb`. Qualquer outra coisa vira o cinza padrao.
   *
   * A cor vem do catalogo do SERVIDOR e hoje e sempre uma constante — nem
   * mesmo categoria desconhecida produz cor de fora, porque ela cai no cinza
   * fixo. Entao por que validar?
   *
   * Porque este valor e concatenado dentro de um atributo `style=` num HTML
   * montado por string, e essa e a construcao em que um valor inesperado sai
   * do atributo e vira marcacao. A garantia de que ele e constante e uma
   * garantia de HOJE, que vive em OUTRO arquivo, e some no dia em que alguem
   * fizer a cor vir da API ou de uma preferencia do usuario.
   *
   * Escapar nao bastaria aqui: `escapar` protege TEXTO, e contexto de CSS tem
   * regras proprias. Lista de permissao por formato e a defesa certa para
   * este contexto.
   */
  function corSegura(valor) {
    return /^#[0-9a-fA-F]{6}$/.test(valor || '') ? valor : '#8b949e';
  }

  // ---------------------------------------------------------------- camadas
  //
  // DUAS camadas, e o seletor no canto. Satelite vem do Esri World Imagery, que
  // responde 200 SEM CHAVE (medido em 02/09/2026) — a alternativa comum, o
  // Mapbox, exige token e cobra por uso.
  //
  // A ATRIBUICAO DE CADA UMA E OBRIGATORIA, e nao e enfeite: o OpenStreetMap
  // exige por ODbL, e o Esri exige por termo de uso. Sao licencas, e o Leaflet
  // troca o texto automaticamente quando a camada muda.
  var ruas = L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
    maxZoom: 18,
    attribution: '&copy; OpenStreetMap contributors'
  });

  var satelite = L.tileLayer(
    'https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/{z}/{y}/{x}',
    {
      maxZoom: 18,
      attribution: 'Imagens: Esri, Maxar, Earthstar Geographics'
    });

  // `scrollWheelZoom` LIGADO, a pedido do Paulo. O padrao do Leaflet o deixa
  // ligado; eu o tinha desligado pelo motivo classico — rolar a pagina com o
  // ponteiro sobre o mapa faz o mapa "sequestrar" a rolagem. Com o mapa alto e
  // com conteudo abaixo dele, isso incomoda; mas quem esta usando um mapa quer
  // ampliar com a roda, e essa e a expectativa que vale.
  var mapa = L.map(caixa, { scrollWheelZoom: true, layers: [ruas] });
  /*
   * O SELETOR FICA SEMPRE ABERTO (`collapsed: false`), e nao e preferencia.
   *
   * MEDIDO em 02/09/2026: o CSS do Leaflet pede `images/layers.png`,
   * `images/layers-2x.png` e `images/marker-icon.png` — e as tres respondem
   * 404, porque so o `.css` e o `.js` foram vendorizados. Fechado, o controle
   * dependia desse icone e aparecia como um QUADRADO BRANCO VAZIO no canto: a
   * troca entre ruas e satelite existia e era invisivel.
   *
   * Aberto, ele mostra os dois rotulos escritos. Duas palavras visiveis valem
   * mais que um icone que exige descobrir que da para clicar — mesmo se o
   * icone estivesse la.
   */
  L.control.layers({ 'Ruas': ruas, 'Satélite': satelite }, null,
                   { position: 'topright', collapsed: false }).addTo(mapa);

  var pontos = [];
  itens.forEach(function (item) {
    var lat = parseFloat(item.dataset.latitude);
    var lon = parseFloat(item.dataset.longitude);
    if (isNaN(lat) || isNaN(lon)) {
      return;
    }
    var ativo = item.dataset.ativo === 'true';

    // A COR VEM DO TIPO DE DESASTRE, calculada no SERVIDOR pelo catalogo de
    // categorias — nao ha mapa de cores duplicado aqui. Duas listas de cores em
    // dois arquivos divergem no primeiro dia em que alguem muda uma delas.
    //
    // ANTES a cor dizia ATIVO ou ENCERRADO, e todos os pinos ativos eram
    // laranja: um mapa com trezentos pontos identicos nao responde a pergunta
    // que se faz olhando um mapa, que e "que tipo de coisa esta acontecendo
    // ali". O estado agora e dito pelo TAMANHO e pela OPACIDADE, que sao
    // dimensoes livres — e a cor passou a dizer o tipo.
    var cor = item.dataset.cor || '#8b949e';

    /*
     * CONTORNO ESCURO, PREENCHIMENTO NA COR DO TIPO.
     *
     * VISTO NA TELA em 02/09/2026: com o contorno na propria cor da categoria,
     * os pinos de TERREMOTO (#bcaaa4, bege) ficavam quase invisiveis — o mapa
     * de ruas e claro, e bege sobre bege claro some. Neve (#e0e6ed) some ainda
     * mais.
     *
     * A CAUSA FOI UMA ESCOLHA MINHA MAL CALIBRADA: escolhi as 13 cores para
     * serem legiveis sobre o FUNDO ESCURO do sistema (#0d1117), que e onde
     * elas aparecem nos chips e nos graficos. Mas no mapa elas caem sobre
     * ladrilho CLARO — e sobre satelite caem sobre qualquer coisa. Nao ha cor
     * unica que resolva os tres fundos.
     *
     * O anel escuro resolve por outro caminho: ele separa o pino do fundo seja
     * qual for o fundo, e o miolo continua dizendo o tipo. E a solucao
     * cartografica de sempre, e vale igual em ruas, satelite e tema escuro.
     */
    L.circleMarker([lat, lon], {
      radius: ativo ? 8 : 5,
      color: '#11161d',
      fillColor: cor,
      // Opacidade alta: o miolo E a informacao, e miolo lavado desfaz o
      // ganho do contorno.
      fillOpacity: ativo ? 0.92 : 0.45,
      // Encerrado ganha traco tracejado ALEM da opacidade menor: opacidade
      // sozinha se confunde com sobreposicao de pinos numa regiao cheia.
      dashArray: ativo ? null : '3 3',
      weight: ativo ? 1.5 : 1
    })
      .bindPopup(
        '<strong>' + escapar(item.dataset.titulo) + '</strong><br>' +
        '<span style="color:' + corSegura(item.dataset.cor) + '">' +
        escapar(item.dataset.tipo || 'Sem categoria') + '</span> · ' +
        (ativo ? 'Em curso' : 'Encerrado') +
        '<br><a href="/desastres/' + encodeURIComponent(item.dataset.id) + '">ver detalhes</a>'
      )
      .addTo(mapa);

    pontos.push([lat, lon]);
  });

  if (pontos.length === 1) {
    mapa.setView(pontos[0], 5);
  } else if (pontos.length > 1) {
    mapa.fitBounds(L.latLngBounds(pontos), { padding: [30, 30] });
  }

  /*
   * SEM VAO ACIMA E ABAIXO DO MUNDO.
   *
   * VISTO NA TELA: com pinos espalhados pelo globo, o `fitBounds` escolhe um
   * zoom baixo — e nesse zoom o mapa-mundi e MAIS BAIXO que o container. O que
   * sobra nao e mar: e o fundo da caixa, uma faixa preta acima e abaixo do
   * planeta, que parece o mapa nao ter carregado inteiro.
   *
   * Horizontalmente o Leaflet repete o mundo e o problema nao existe.
   * Verticalmente ele nao pode repetir — o planeta acaba nos polos.
   *
   * A conta: no zoom `z` o mundo tem `256 * 2^z` pixels de altura. Para cobrir
   * a caixa e preciso `256 * 2^z >= altura`, ou seja `z >= log2(altura / 256)`.
   * Arredondando para cima e tomando o maior entre esse e o zoom escolhido, o
   * mundo sempre preenche — e nunca se afasta MAIS do que o `fitBounds` queria.
   */
  var alturaDaCaixa = caixa.clientHeight;
  if (alturaDaCaixa > 0) {
    var zoomQuePreenche = Math.ceil(Math.log2(alturaDaCaixa / 256));
    if (mapa.getZoom() < zoomQuePreenche) {
      mapa.setZoom(zoomQuePreenche);
    }
  }

  caixa.setAttribute('data-mapa-ativo', '');
})();

/*
 * FILTRO DE TIPOS EM UM CLIQUE.
 *
 * PROPÓSITO: marcar "Vulcões" já mostra só vulcões. Sem isto são dois passos —
 * marcar e depois procurar o botão —, e o segundo passo é onde se esquece que
 * o filtro não foi aplicado ainda: os chips dizem uma coisa e o mapa mostra
 * outra, sem nada avisando da diferença.
 *
 * BLOCO SEPARADO, e de propósito. O bloco do mapa desiste cedo quando não há
 * mapa para desenhar (`return` se a caixa não existe), e o filtro precisa
 * funcionar exatamente aí: é quando o recorte não achou nada e a pessoa quer
 * escolher outro. Pendurar isto lá dentro faria o filtro morrer no único
 * momento em que ele é indispensável.
 *
 * NÃO DEPENDE DO LEAFLET. Se a biblioteca falhar, o mapa não desenha mas os
 * chips continuam filtrando a lista de baixo — que é o mesmo dado.
 *
 * O BOTÃO "APLICAR" É ESCONDIDO AQUI, POR SCRIPT, e não no CSS. Se este script
 * não rodar, o botão continua na tela e o formulário continua funcionando.
 * Escondê-lo no CSS deixaria quem está sem JavaScript com chips que marcam e
 * nada que aplique — um filtro quebrado que parece inteiro.
 *
 * FALHA: qualquer erro aqui deixa o formulário exatamente como veio do
 * servidor, com o botão visível e funcionando.
 */
(function () {
  'use strict';

  var formulario = document.querySelector('[data-filtro-mapa]');
  if (!formulario) {
    return;
  }

  var aplicar = formulario.querySelector('[data-aplicar]');
  if (aplicar) {
    aplicar.hidden = true;
  }

  formulario.addEventListener('change', function (evento) {
    if (!evento.target || evento.target.type !== 'checkbox') {
      return;
    }
    // O chip recém-clicado ganha o aviso de "carregando" ANTES do envio: entre
    // o clique e a página nova há uma consulta ao banco, e sem sinal nenhum o
    // clique parece não ter funcionado — e a pessoa clica de novo.
    var chip = evento.target.closest('.mapa-chip');
    if (chip) {
      chip.classList.add('mapa-chip-carregando');
    }
    formulario.submit();
  });
})();


/*
 * A LISTA ABAIXO DO MAPA — filtro por tipo e paginação de 20.
 *
 * PROPÓSITO: com 500 eventos desenhados, a lista tinha 500 cartões e metros de
 * rolagem. Ninguém percorre 500 cartões procurando um. O filtro escolhe o tipo
 * e a paginação corta o resto em pedaços legíveis.
 *
 * ─────────────────────────────────────────────────────────────────────────
 * POR QUE NO NAVEGADOR, E NÃO NO SERVIDOR — a decisão que define este bloco.
 *
 * Esta lista tem DOIS papéis ao mesmo tempo:
 *
 *   1. é a fonte de dados dos PINOS: o desenhador acima lê `data-latitude` de
 *      cada item para plantar os marcadores;
 *   2. é a versão legível do mapa para quem está sem JavaScript.
 *
 * Paginar no servidor mandaria 20 itens — e o mapa passaria a desenhar 20
 * pinos em vez de 500, EM SILÊNCIO. O mapa é o produto da tela; encolhê-lo
 * para arrumar a lista seria consertar o menor problema quebrando o maior.
 * Há teste que reprova essa troca, calibrado com o defeito reintroduzido.
 *
 * A alternativa seria mandar os 500 duas vezes: uma escondida para o mapa,
 * outra paginada para ler. Duas cópias do mesmo dado no mesmo HTML divergem
 * no primeiro dia em que alguém mexer numa delas.
 * ─────────────────────────────────────────────────────────────────────────
 *
 * ESTE FILTRO É LOCAL, E A TELA DIZ ISSO. Ele recorta os CARTÕES; os pinos
 * continuam todos no mapa. É diferente do filtro de cima, que recarrega a
 * página e muda o que o servidor manda. Os dois existem porque respondem a
 * momentos diferentes: o de cima escolhe o recorte; este é para quem já está
 * lendo a lista e não quer voltar ao topo e esperar uma requisição.
 *
 * SEM JAVASCRIPT a lista aparece inteira, sem filtro e sem paginação — e isso
 * é coerente, não uma falha: sem JavaScript também não há mapa nenhum, e a
 * lista completa é exatamente o que se quer quando ela é a única coisa que
 * sobrou.
 *
 * FALHA: qualquer erro aqui deixa a lista inteira visível, como veio do
 * servidor. O bloco não apaga nem move item nenhum — só esconde e mostra.
 */
(function () {
  'use strict';

  // 20 e nao 50. Cinquenta cartoes ainda eram tres telas de rolagem — a
  // paginacao existia e a pessoa continuava rolando para achar o fim da pagina.
  // Vinte cabe de uma vez na altura util de um monitor comum, que e a medida
  // que importa: pagina que nao cabe na tela nao paginou, so numerou a rolagem.
  var POR_PAGINA = 20;

  var lista = document.querySelector('[data-mapa-eventos]');
  if (!lista) {
    return;
  }

  var todos = Array.prototype.slice.call(lista.children);
  if (todos.length === 0) {
    return;
  }

  var tipoEscolhido = null;   // null = todos
  var visiveis = todos;
  var paginaAtual = 0;

  // ------------------------------------------------------------- filtro

  /*
   * Os tipos vêm do que ESTÁ na lista, não das 13 do catálogo. Aqui é chave
   * de leitura do que já chegou: oferecer "Vulcões" num recorte que não tem
   * nenhum daria um clique que leva a lista vazia.
   *
   * É o oposto do filtro de cima, e de propósito: aquele é um CONTROLE
   * ("posso pedir isto ao servidor") e por isso mostra as 13, inclusive as que
   * não têm o que desenhar. Este é um RECORTE do que está na tela.
   */
  var porTipo = {};
  todos.forEach(function (item) {
    var tipo = item.dataset.tipo || 'Sem categoria';
    if (!porTipo[tipo]) {
      porTipo[tipo] = { cor: item.dataset.cor || '#8b949e', itens: [] };
    }
    porTipo[tipo].itens.push(item);
  });
  var tipos = Object.keys(porTipo).sort();

  var barra = document.createElement('div');
  barra.className = 'lista-filtro';

  var rotulo = document.createElement('span');
  rotulo.className = 'lista-filtro-rotulo';
  rotulo.textContent = 'Filtrar cartões:';
  barra.appendChild(rotulo);

  var nota = document.createElement('span');
  nota.className = 'lista-filtro-nota';
  // A tela DIZ que este filtro é local. Sem esta linha, alguém marcaria um
  // tipo, veria a lista encolher e concluiria que os pinos sumiram também.
  nota.textContent = 'só os cartões — o mapa continua com todos os pinos';

  var botoes = [];

  function criarBotao(texto, tipo, cor) {
    var b = document.createElement('button');
    b.type = 'button';
    b.className = 'lista-filtro-tipo';
    b.textContent = texto;
    if (cor) {
      b.style.setProperty('--cor-tipo', cor);
    }
    b.addEventListener('click', function () {
      tipoEscolhido = tipo;
      aplicar();
    });
    barra.appendChild(b);
    botoes.push({ elemento: b, tipo: tipo });
    return b;
  }

  criarBotao('Todos (' + todos.length + ')', null, null);
  tipos.forEach(function (t) {
    criarBotao(t + ' (' + porTipo[t].itens.length + ')', t, porTipo[t].cor);
  });
  barra.appendChild(nota);

  // Um tipo só: o filtro não decide nada, e dois botões que não mudam a lista
  // são ruído. A paginação continua, porque ela ainda serve.
  if (tipos.length > 1) {
    lista.parentNode.insertBefore(barra, lista);
  }

  // ---------------------------------------------------------- paginação

  var controle = document.createElement('nav');
  controle.className = 'lista-paginacao';
  controle.setAttribute('aria-label', 'Páginas da lista de eventos');

  var anterior = document.createElement('button');
  anterior.type = 'button';
  anterior.className = 'botao botao-discreto';
  anterior.textContent = '‹ Anteriores';

  var proximo = document.createElement('button');
  proximo.type = 'button';
  proximo.className = 'botao botao-discreto';
  proximo.textContent = 'Próximos ›';

  var situacao = document.createElement('span');
  situacao.className = 'lista-paginacao-situacao';
  // `polite` avisa quem usa leitor de tela que a página mudou, sem interromper
  // o que estiver sendo lido. Sem isto, trocar de página seria silencioso.
  situacao.setAttribute('aria-live', 'polite');

  controle.appendChild(anterior);
  controle.appendChild(situacao);
  controle.appendChild(proximo);
  lista.parentNode.insertBefore(controle, lista.nextSibling);

  anterior.addEventListener('click', function () { irPara(paginaAtual - 1); });
  proximo.addEventListener('click', function () { irPara(paginaAtual + 1); });

  function irPara(pagina) {
    paginaAtual = pagina;
    desenhar();
    // Volta ao topo da lista: sem isto, quem clica no fim continua no fim,
    // olhando o rodapé de uma lista que acabou de trocar por completo.
    lista.scrollIntoView({ block: 'start', behavior: 'smooth' });
  }

  // ------------------------------------------------------------ desenho

  function aplicar() {
    visiveis = tipoEscolhido === null ? todos : porTipo[tipoEscolhido].itens;
    paginaAtual = 0;

    botoes.forEach(function (b) {
      var ativo = b.tipo === tipoEscolhido;
      b.elemento.classList.toggle('lista-filtro-ativo', ativo);
      // `aria-pressed` diz o estado a quem usa leitor de tela. Sem ele, o
      // botão marcado é indistinguível dos outros fora da tela.
      b.elemento.setAttribute('aria-pressed', ativo ? 'true' : 'false');
    });
    desenhar();
  }

  function desenhar() {
    var totalDePaginas = Math.max(1, Math.ceil(visiveis.length / POR_PAGINA));
    paginaAtual = Math.max(0, Math.min(paginaAtual, totalDePaginas - 1));

    var primeiro = paginaAtual * POR_PAGINA;
    var ultimo = Math.min(primeiro + POR_PAGINA, visiveis.length);

    // Esconde TUDO e mostra a fatia. Percorrer só o que mudou seria mais
    // rápido e mais fácil de errar: bastaria um caminho esquecido para sobrar
    // cartão de outro tipo no meio do recorte.
    todos.forEach(function (item) { item.hidden = true; });
    for (var i = primeiro; i < ultimo; i++) {
      // `hidden` e não `display:none`: o atributo é o mecanismo padrão, e o
      // item escondido assim sai da ordem de foco e da leitura de tela — que
      // é o que se quer de um item fora da página atual.
      visiveis[i].hidden = false;
    }

    var quantos = visiveis.length;
    var deQue = tipoEscolhido === null ? '' : ' de ' + tipoEscolhido;
    situacao.textContent = quantos === 0
      ? 'nenhum cartão' + deQue
      : (primeiro + 1) + '–' + ultimo + ' de ' + quantos + ' cartão(ões)'
        + deQue + '  (página ' + (paginaAtual + 1) + ' de ' + totalDePaginas + ')';

    anterior.disabled = paginaAtual === 0;
    proximo.disabled = paginaAtual >= totalDePaginas - 1;
  }

  aplicar();
})();
