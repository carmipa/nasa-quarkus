/*
 * SCRIPT DO DETALHE DO EVENTO — e só dele: um mapa com um pino.
 *
 * A POSIÇÃO É A DO PONTO MAIS RECENTE da trajetória, decidida no servidor. O
 *   legado usava o primeiro ponto da lista, e num evento medido isso dava
 *   456 km de diferença — este mapa mostraria a tempestade onde ela esteve
 *   ontem, e o alerta decidiria sobre esse lugar.
 *
 * FALHA: sem Leaflet ou sem este arquivo, a página continua com a coordenada
 *   escrita em texto, na lista de campos acima. Perde-se o desenho, não o dado.
 */
(function () {
  'use strict';

  var caixa = document.getElementById('mapa');
  if (!caixa || typeof window.L === 'undefined') {
    return;
  }

  var lat = parseFloat(caixa.dataset.latitude);
  var lon = parseFloat(caixa.dataset.longitude);
  if (isNaN(lat) || isNaN(lon)) {
    return;
  }

  var ativo = caixa.dataset.ativo === 'true';

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

  // Roda do mouse LIGADA, e satelite disponivel: num evento unico, ampliar ate
  // ver o terreno e exatamente o que se quer fazer.
  var mapa = L.map(caixa, { scrollWheelZoom: true, layers: [satelite] })
    .setView([lat, lon], 6);
  L.control.layers({ 'Satélite': satelite, 'Ruas': ruas }, null,
                   { position: 'topright' }).addTo(mapa);

  L.circleMarker([lat, lon], {
    radius: 10,
    color: ativo ? '#ff9f43' : '#9198a1',
    fillColor: ativo ? '#ff9f43' : '#9198a1',
    fillOpacity: 0.6,
    weight: 3
  }).addTo(mapa);
})();
