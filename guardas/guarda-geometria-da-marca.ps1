# =============================================================================
# GUARDA: o icone da marca fica AO LADO do texto — medido no navegador.
#
# O QUE ORIGINOU ISTO, medido em 03/09/2026. Paulo pediu duas vezes o icone a
# esquerda do texto. A regra existia e estava certa:
#
#     .cabecalho-marca { display:flex; align-items:center; gap:0.7rem; }   /* 734 */
#
# e nao valia, porque 670 linhas acima, no MESMO arquivo, sobrevivia:
#
#     .cabecalho-marca { display:flex; flex-direction:column; }            /* 65 */
#
# No CSS a regra posterior sobrepoe SO as propriedades que repete. O bloco de
# baixo redeclarava `display` e nao `flex-direction` — a coluna venceu, calada,
# e a tela ficou empilhada com um CSS que dizia "icone a esquerda".
#
# POR QUE ESTA GUARDA MEDE A TELA, E NAO O TEXTO DO CSS. A primeira tentativa
# procurava seletor duplicado com geometria orfa. Ela achou o defeito de verdade
# e mais DOIS falsos: `.menu` e `.rodape` tambem sao declarados duas vezes, de
# proposito, e ali o bloco posterior redeclara exatamente a propriedade que quer
# mudar. Instrumento que grita em codigo correto e desligado na terceira semana.
# `getComputedStyle` nao tem essa ambiguidade: ele devolve o que o navegador
# realmente aplicou, depois da cascata inteira.
#
# TRES ESTADOS: 0 passou · 1 reprovou · 2 NAO VERIFICOU (que nao e aprovacao).
# =============================================================================
Set-StrictMode -Version Latest

$base = if ($env:NASA_BASE) { $env:NASA_BASE } else { 'http://localhost:8080' }
$edge = @(
  "${env:ProgramFiles(x86)}\Microsoft\Edge\Application\msedge.exe",
  "${env:ProgramFiles}\Microsoft\Edge\Application\msedge.exe"
) | Where-Object { Test-Path $_ } | Select-Object -First 1

if (-not $edge) { Write-Host 'NAO VERIFICOU: Edge nao encontrado' ; exit 2 }
try { Invoke-WebRequest -Uri $base -TimeoutSec 20 -UseBasicParsing | Out-Null }
catch { Write-Host "NAO VERIFICOU: $base nao responde" ; exit 2 }

$publico = 'src/main/resources/META-INF/resources'
if (-not (Test-Path $publico)) { Write-Host 'NAO VERIFICOU: pasta publica ausente'; exit 2 }

# A sonda e servida pelo PROPRIO app: `file://` nao consegue ler o CSS de
# localhost (CORS), e a medicao voltava vazia — que e um `0` mentiroso.
function Medir([string]$estiloExtra) {
  $nome = "_guarda-marca-$([guid]::NewGuid().ToString('N').Substring(0,8)).html"
  $alvo = Join-Path $publico $nome
  @"
<link rel="stylesheet" href="/estatico/css/base.css">
<style>$estiloExtra</style>
<a class="cabecalho-marca" href="/"><img class="marca-icone"
   src="/estatico/img/icone/logo-96.png" width="96" height="96" alt="">
   <span class="marca-texto"><span class="marca-titulo">Alerta de Desastres
   Naturais</span><span class="marca-fonte">NASA EONET</span></span></a>
<h1 id="r">medindo</h1>
<script>
addEventListener('load',function(){
  var a=document.querySelector('.cabecalho-marca');
  var i=a.querySelector('.marca-icone').getBoundingClientRect();
  var t=a.querySelector('.marca-texto').getBoundingClientRect();
  document.getElementById('r').textContent='VEREDITO='+
    ((t.left>=i.right-1 && i.left<t.left) ? 'LADO_A_LADO' : 'EMPILHADO')+
    ' dir='+getComputedStyle(a).flexDirection;
});
</script>
"@ | Set-Content -Path $alvo -Encoding UTF8
  try {
    # ESPERAR A SONDA APARECER, e nao dormir um numero fixo de segundos. O
    # Quarkus copia `META-INF/resources` para o classpath antes de servir, e o
    # atraso varia. Com `Start-Sleep 3` a sonda vinha 404, o `--dump-dom`
    # devolvia a pagina de erro, a expressao nao casava e a guarda saia 2 numa
    # tela que estava CERTA — instrumento que reprova o que funciona morre de
    # desuso. Espera ATE 30s pela condicao, e desiste como NAO VERIFICOU.
    $noAr = $false
    foreach ($tentativa in 1..30) {
      try {
        $r = Invoke-WebRequest -Uri "$base/$nome" -TimeoutSec 5 -UseBasicParsing
        if ($r.StatusCode -eq 200 -and $r.Content -match 'cabecalho-marca') {
          $noAr = $true; break
        }
      } catch { }
      Start-Sleep -Seconds 1
    }
    if (-not $noAr) { return $null }

    # `msedge.exe` E BINARIO DE SUBSISTEMA GRAFICO: lancado com `& $edge` pelo
    # PowerShell, o stdout NAO chega ao pipeline — `$dom` vinha nulo e a guarda
    # saia 2 com a tela correta. No bash a mesma linha funciona, o que fez o
    # engano parecer defeito da pagina. `Start-Process -RedirectStandardOutput`
    # e o caminho que funciona nos dois: o DOM vai para arquivo, e arquivo se le.
    $saida = Join-Path ([System.IO.Path]::GetTempPath()) "marca-$([guid]::NewGuid().ToString('N')).html"
    try {
      # O stderr vai para arquivo TAMBEM, e nao para a tela: o Edge headless
      # despeja linhas de `task_manager` e de `sync` que nao dizem nada sobre a
      # medicao, e guarda que polui a saida ensina a ignorar a saida.
      $ruido = "$saida.err"
      Start-Process -FilePath $edge -Wait -NoNewWindow `
        -RedirectStandardOutput $saida -RedirectStandardError $ruido `
        -ArgumentList '--headless=new','--disable-gpu','--no-sandbox',
                      '--virtual-time-budget=8000','--dump-dom',"$base/$nome"
      if (-not (Test-Path $saida)) { return $null }
      $texto = Get-Content -Path $saida -Raw
    } finally {
      Remove-Item $saida -ErrorAction SilentlyContinue
      Remove-Item "$saida.err" -ErrorAction SilentlyContinue
    }

    if ([string]::IsNullOrEmpty($texto)) { return $null }
    if ($texto -match 'VEREDITO=(\w+) dir=([\w-]+)') {
      return @{ veredito = $Matches[1]; direcao = $Matches[2] }
    }
    return $null
  } finally { Remove-Item $alvo -ErrorAction SilentlyContinue }
}

# ---- CONTROLE POSITIVO, primeiro. Reintroduz o defeito exato que existia; se a
# sonda aprovar ISTO, ela nao mede nada e o `0` do caso real nao vale nada.
$doente = Medir '.cabecalho-marca { flex-direction: column !important; }'
if ($null -eq $doente) { Write-Host 'NAO VERIFICOU: a sonda nao devolveu medida'; exit 2 }
if ($doente.veredito -ne 'EMPILHADO') {
  Write-Host "NAO VERIFICOU: com `flex-direction:column` forcado a sonda disse"
  Write-Host "  '$($doente.veredito)' — ela nao distingue empilhado de lado a lado."
  exit 2
}

# ---- e agora a tela de verdade.
$real = Medir ''
if ($null -eq $real) { Write-Host 'NAO VERIFICOU: a sonda nao devolveu medida'; exit 2 }

if ($real.veredito -ne 'LADO_A_LADO') {
  Write-Host "REPROVOU: o icone da marca esta $($real.veredito) (flex-direction=$($real.direcao))."
  Write-Host '  Procure um SEGUNDO bloco `.cabecalho-marca` no base.css: o defeito'
  Write-Host '  original era um `flex-direction: column` esquecido 670 linhas acima.'
  exit 1
}

Write-Host "PASSOU: icone a esquerda do texto (flex-direction=$($real.direcao));"
Write-Host '  e o controle com `column` forcado foi corretamente reprovado.'
exit 0
