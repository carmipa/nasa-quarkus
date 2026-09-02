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
  var mapa = L.map(caixa, { scrollWheelZoom: false }).setView([lat, lon], 6);

  L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
    maxZoom: 12,
    attribution: '&copy; OpenStreetMap contributors'
  }).addTo(mapa);

  L.circleMarker([lat, lon], {
    radius: 10,
    color: ativo ? '#ff9f43' : '#9198a1',
    fillColor: ativo ? '#ff9f43' : '#9198a1',
    fillOpacity: 0.6,
    weight: 3
  }).addTo(mapa);
})();
