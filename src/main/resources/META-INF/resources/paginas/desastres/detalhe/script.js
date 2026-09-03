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
  // ABERTO, pela mesma razao do mapa geral: as imagens do Leaflet
  // (`images/layers.png`) NAO estao vendorizadas e respondem 404, entao o
  // controle fechado desenhava um quadrado branco vazio — a troca existia e
  // era invisivel. Medido em 02/09/2026.
  L.control.layers({ 'Satélite': satelite, 'Ruas': ruas }, null,
                   { position: 'topright', collapsed: false }).addTo(mapa);

  // A cor do pino vem do TIPO, como no mapa geral — vinda do servidor pelo
  // `data-cor`. Duas telas com codificacao de cor diferente para a mesma coisa
  // obrigariam reaprender a leitura ao mudar de tela.
  var corDoTipo = /^#[0-9a-fA-F]{6}$/.test(caixa.dataset.cor || '')
    ? caixa.dataset.cor : '#8b949e';

  // Contorno escuro, miolo na cor do tipo — a mesma leitura do mapa geral.
  // Sem o anel, cor clara sobre ladrilho claro some.
  L.circleMarker([lat, lon], {
    radius: 10,
    color: '#11161d',
    fillColor: corDoTipo,
    fillOpacity: 0.92,
    fillOpacity: 0.6,
    weight: 3
  }).addTo(mapa);
})();
