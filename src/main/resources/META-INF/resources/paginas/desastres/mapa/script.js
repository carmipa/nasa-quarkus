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

    L.circleMarker([lat, lon], {
      radius: ativo ? 8 : 5,
      color: cor,
      fillColor: cor,
      fillOpacity: ativo ? 0.7 : 0.25,
      // Encerrado ganha traco tracejado ALEM da opacidade menor: opacidade
      // sozinha se confunde com sobreposicao de pinos numa regiao cheia.
      dashArray: ativo ? null : '3 3',
      weight: 2
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

  caixa.setAttribute('data-mapa-ativo', '');
})();
