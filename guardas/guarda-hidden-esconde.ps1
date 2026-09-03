# =============================================================================
# GUARDA: o atributo `hidden` REALMENTE esconde — medido no navegador.
#
# O QUE ORIGINOU ISTO, medido em 03/09/2026 em `/desastres/mapa`. O script
# escondia 480 dos 500 cartoes com `item.hidden = true`, que e o mecanismo
# padrao e a escolha certa. A pagina continuava com 52.204px de altura —
# CINQUENTA E OITO TELAS de rolagem, vinte cartoes a mostra e quatrocentos e
# oitenta invisiveis ocupando espaco. Foi o Paulo que viu, olhando a tela.
#
# A CAUSA: a folha do NAVEGADOR traz `[hidden] { display: none }`, e qualquer
# declaracao de `display` escrita por nos VENCE essa regra — estilo de autor
# ganha da folha do agente, independentemente de especificidade. Bastou um
# `.mapa-item { display: flex }` para o atributo virar decoracao.
#
# POR QUE ESTA GUARDA E DIFERENTE DE LER O CSS. O defeito e MUDO nos dois
# lados: o JavaScript esta correto e o CSS esta correto; so a combinacao falha.
# Pior, o instrumento obvio MENTE — `querySelectorAll('[hidden]')` conta 480 e
# confirma "esta escondido" enquanto a tela mostra os 480. Foi assim que eu
# medi errado na primeira tentativa e quase disse ao Paulo que estava tudo bem.
# So `offsetParent` e a altura renderizada dizem a verdade.
#
# TRES ESTADOS: 0 passou · 1 reprovou · 2 NAO VERIFICOU (que nao e aprovacao).
# =============================================================================
Set-StrictMode -Version Latest

$base = if ($env:NASA_BASE) { $env:NASA_BASE } else { 'http://localhost:8080' }
$edge = @(
  "${env:ProgramFiles(x86)}\Microsoft\Edge\Application\msedge.exe",
  "${env:ProgramFiles}\Microsoft\Edge\Application\msedge.exe"
) | Where-Object { Test-Path $_ } | Select-Object -First 1

if (-not $edge) { Write-Host 'NAO VERIFICOU: Edge nao encontrado'; exit 2 }
try { Invoke-WebRequest -Uri $base -TimeoutSec 20 -UseBasicParsing | Out-Null }
catch { Write-Host "NAO VERIFICOU: $base nao responde"; exit 2 }

$publico = 'src/main/resources/META-INF/resources'
if (-not (Test-Path $publico)) { Write-Host 'NAO VERIFICOU: pasta publica ausente'; exit 2 }

$nome = "_guarda-hidden-$([guid]::NewGuid().ToString('N').Substring(0,8)).html"
$alvo = Join-Path $publico $nome

# A sonda carrega o base.css REAL e monta dois elementos com `display: flex`
# declarado — que e a combinacao que quebra. Um com `hidden`, outro sem.
#
# O SEGUNDO E O CONTROLE POSITIVO, e sem ele a guarda nao vale nada: uma folha
# que escondesse TUDO (um `.sonda-alvo { display: none }` perdido) faria o
# primeiro caso passar, e a guarda aprovaria uma tela em branco.
@"
<link rel="stylesheet" href="/estatico/css/base.css">
<style>.sonda-alvo { display: flex; height: 3rem; }</style>
<div class="sonda-alvo" id="escondido" hidden>deveria sumir</div>
<div class="sonda-alvo" id="visivel">deveria aparecer</div>
<h1 id="r">medindo</h1>
<script>
addEventListener('load', function () {
  var e = document.getElementById('escondido');
  var v = document.getElementById('visivel');
  document.getElementById('r').textContent =
    'VEREDITO escondeu=' + (e.offsetParent === null && e.getBoundingClientRect().height === 0) +
    ' controle_visivel=' + (v.offsetParent !== null && v.getBoundingClientRect().height > 0);
});
</script>
"@ | Set-Content -Path $alvo -Encoding UTF8

try {
  # Esperar a CONDICAO, e nao dormir um numero fixo: o Quarkus copia
  # `META-INF/resources` para o classpath antes de servir, e o atraso varia.
  $noAr = $false
  foreach ($t in 1..30) {
    try {
      $r = Invoke-WebRequest -Uri "$base/$nome" -TimeoutSec 5 -UseBasicParsing
      if ($r.StatusCode -eq 200 -and $r.Content -match 'sonda-alvo') { $noAr = $true; break }
    } catch { }
    Start-Sleep -Seconds 1
  }
  if (-not $noAr) { Write-Host 'NAO VERIFICOU: a sonda nao ficou disponivel'; exit 2 }

  # `msedge.exe` e binario grafico: com `& $edge` o stdout NAO chega ao
  # pipeline do PowerShell (no bash chega). Saida para arquivo e o caminho que
  # funciona nos dois.
  $saida = Join-Path ([System.IO.Path]::GetTempPath()) "hidden-$([guid]::NewGuid().ToString('N')).html"
  $ruido = "$saida.err"
  try {
    Start-Process -FilePath $edge -Wait -NoNewWindow `
      -RedirectStandardOutput $saida -RedirectStandardError $ruido `
      -ArgumentList '--headless=new','--disable-gpu','--no-sandbox',
                    '--virtual-time-budget=8000','--dump-dom',"$base/$nome"
    if (-not (Test-Path $saida)) { Write-Host 'NAO VERIFICOU: sem saida do navegador'; exit 2 }
    $texto = Get-Content -Path $saida -Raw
  } finally {
    Remove-Item $saida -ErrorAction SilentlyContinue
    Remove-Item $ruido -ErrorAction SilentlyContinue
  }

  if ([string]::IsNullOrEmpty($texto) -or
      $texto -notmatch 'VEREDITO escondeu=(\w+) controle_visivel=(\w+)') {
    Write-Host 'NAO VERIFICOU: a sonda nao devolveu medida'
    exit 2
  }
  $escondeu = $Matches[1] -eq 'true'
  $controle = $Matches[2] -eq 'true'

  # O CONTROLE PRIMEIRO. Se o elemento que DEVE aparecer nao aparece, a sonda
  # esta medindo uma pagina quebrada e o `escondeu=true` nao significa nada.
  if (-not $controle) {
    Write-Host 'NAO VERIFICOU: o elemento de controle (sem `hidden`) tambem esta'
    Write-Host '  invisivel — a sonda nao distingue esconder de nao renderizar.'
    exit 2
  }

  if (-not $escondeu) {
    Write-Host 'REPROVOU: um elemento com `hidden` E `display:flex` CONTINUA OCUPANDO ESPACO.'
    Write-Host '  Falta `[hidden] { display: none !important; }` no base.css.'
    Write-Host '  Sem essa linha, toda tela que pagina ou troca de aba com `hidden`'
    Write-Host '  mostra tudo de uma vez: em 03/09 isso deu 58 telas de rolagem em'
    Write-Host '  /desastres/mapa, com 480 cartoes invisiveis empilhados.'
    exit 1
  }

  Write-Host 'PASSOU: `hidden` esconde de verdade, mesmo com `display` declarado;'
  Write-Host '  e o controle sem `hidden` continua visivel.'
  exit 0

} finally {
  Remove-Item $alvo -ErrorAction SilentlyContinue
}
