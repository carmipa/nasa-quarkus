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
  L.control.layers({ 'Ruas': ruas, 'Satélite': satelite }, null,
                   { position: 'topright' }).addTo(mapa);

  var pontos = [];
  itens.forEach(function (item) {
    var lat = parseFloat(item.dataset.latitude);
    var lon = parseFloat(item.dataset.longitude);
    if (isNaN(lat) || isNaN(lon)) {
      return;
    }
    var ativo = item.dataset.ativo === 'true';

    L.circleMarker([lat, lon], {
      radius: ativo ? 8 : 5,
      color: ativo ? '#ff9f43' : '#9198a1',
      fillColor: ativo ? '#ff9f43' : '#9198a1',
      fillOpacity: ativo ? 0.65 : 0.35,
      weight: 2
    })
      .bindPopup(
        '<strong>' + escapar(item.dataset.titulo) + '</strong><br>' +
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

  caixa.setAttribute('data-mapa-ativo', '');
})();
