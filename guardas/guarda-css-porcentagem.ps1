#Requires -Version 7
<#
.SYNOPSIS
    Guarda de largura em porcentagem — impede que `px` de layout, `auto-fill` e coluna
    centralizada voltem ao CSS.

.DESCRIPTION
    PROPÓSITO DE NEGÓCIO
        A regra do Paulo (2026-08-28, nascida da página Equipe do binmapper "pingando no
        meio da tela") diz: em web, largura em `%`, conteúdo aproveitando a tela toda,
        lado a lado. `px` fixo trava o layout e impede preencher a tela.

        O comentário no topo do `base.css` já enuncia a regra. Comentário, porém, é
        intenção: ninguém o executa, e a próxima folha de estilo nasce sem ele. Esta
        guarda é o que torna a regra efetiva — a mesma divisão do resto do projeto:

          comentário no base.css      -> declara a intenção
          guarda-css-porcentagem      -> impõe a intenção

    INVARIANTES DO DOMÍNIO
        INV-CSS-001  Nenhuma propriedade de LARGURA, ALTURA, ESPAÇAMENTO ou POSIÇÃO usa
                     `px`. Dano se quebrado: o layout para de preencher a tela e o
                     conteúdo volta a "pingar no meio" numa coluna estreita — o defeito
                     visual exato que originou a regra.
        INV-CSS-002  `auto-fill` nunca aparece em grid. Dano: colunas vazias, cartões
                     colados à esquerda, metade da tela em branco.
        INV-CSS-003  `max-width` + `margin: 0 auto` nunca centralizam um container.
                     Dano: é literalmente o bug do binmapper.
        INV-CSS-004  A guarda SE CALIBRA antes de julgar. Ela precisa provar, em CSS
                     sintético doente, que reprova os três defeitos — e provar, em CSS
                     são, que não acusa borda fina nem breakpoint. Sem as duas metades,
                     um `0` aqui é indistinguível de uma expressão regular quebrada.

        AS EXCEÇÕES SÃO DECLARADAS PELA PRÓPRIA REGRA, não inventadas aqui:
        borda fina, sombra e breakpoint de `@media`. Fora disso, nada.

    COMPORTAMENTO EM CASO DE FALHA
        0  PASSOU        — nenhum px de layout, nenhum auto-fill, nada centralizado
        1  REPROVOU      — há violação; o trabalho para, com arquivo e linha
        2  NÃO VERIFICOU — não achou CSS, ou a calibração falhou. NÃO é aprovação.

    POR QUE COMENTÁRIO É REMOVIDO ANTES DE MEDIR
        Medido em 02/09/2026: a primeira versão desta varredura acusou três violações
        que eram o TEXTO DA REGRA dentro de comentários — `NUNCA max-width + margin:0
        auto` e `auto-fit, nunca auto-fill`. Instrumento que lê comentário não está
        medindo código, e teria mandado "corrigir" a documentação da própria regra.

.PARAMETER Caminho
    Raiz do projeto. Padrão: a pasta acima desta.
#>
[CmdletBinding()]
param(
    [string]$Caminho = (Split-Path -Parent $PSScriptRoot)
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

# `vendor/` fica de fora: é código de terceiro (Leaflet), não escrito aqui, e reescrever
# o px dele seria alterar biblioteca — que é pior do que a violação.
$PASTA_CSS = Join-Path $Caminho 'src/main/resources/META-INF/resources'

$PROPRIEDADES = 'max-width|min-width|width|max-height|min-height|height|margin|padding|gap|top|left|right|bottom|flex-basis|inset'
$RE_PX_LAYOUT = "(?<![a-z-])($PROPRIEDADES)\s*:\s*[^;{}]*?[0-9.]+px"
$RE_CENTRADO  = 'max-width\s*:\s*(?!100%)[^;]+;[^}]*margin\s*:\s*0\s+auto'

function Limpar([string]$texto) {
    # 1. comentário não é código  2. breakpoint de @media é exceção declarada
    # 3. borda e sombra são exceção declarada
    $t = [regex]::Replace($texto, '/\*.*?\*/', '', 'Singleline')
    $t = [regex]::Replace($t, '@media[^{]*\{', '{')
    $t = [regex]::Replace($t, '\b(border|outline|box-shadow|text-shadow)[a-z-]*\s*:[^;}]*[;}]', '')
    return $t
}

function Violacoes([string]$texto) {
    $limpo = Limpar $texto
    $achados = @()
    foreach ($m in [regex]::Matches($limpo, $RE_PX_LAYOUT, 'IgnoreCase')) {
        $achados += "px de layout: $($m.Value.Trim())"
    }
    if ($limpo -match 'auto-fill') { $achados += 'auto-fill em grid (use auto-fit)' }
    if ($limpo -match $RE_CENTRADO) { $achados += 'max-width + margin:0 auto centralizando' }
    # A virgula forca ARRAY mesmo com zero ou um item. Sem ela, o PowerShell devolve
    # $null para lista vazia e `.Count` estoura sob StrictMode — o instrumento morreria
    # exatamente no caso que ele existe para reconhecer: o caso limpo.
    return ,[string[]]$achados
}

# ====================================================================== CALIBRAÇÃO
# Sem as DUAS metades, um `0` lá embaixo não prova nada: uma regex quebrada também
# devolve zero, e um instrumento que acusa tudo também "acha" os doentes.

$doente = @'
.a { width: 800px; }
.b { padding: 20px 10px; }
.c { display: grid; grid-template-columns: repeat(auto-fill, minmax(20rem, 1fr)); }
.d { max-width: 60rem; margin: 0 auto; }
'@
$sao = @'
/* NUNCA max-width + margin:0 auto; use auto-fit, nunca auto-fill. */
.a { width: 100%; padding: 0 2%; }
.b { border: 1px solid red; border-left: 4px solid blue; box-shadow: 0 2px 6px #0003; }
.c { display: grid; grid-template-columns: repeat(auto-fit, minmax(20rem, 1fr)); }
@media (max-width: 720px) { .d { gap: 1rem; } }
'@

$achouDoente = Violacoes $doente
$achouSao    = Violacoes $sao

# CONTROLE POSITIVO: precisa achar os TRÊS defeitos distintos no doente.
$pegouPx       = @($achouDoente | Where-Object { $_ -like 'px de layout*' }).Count -ge 2
$pegouFill     = @($achouDoente | Where-Object { $_ -like 'auto-fill*' }).Count -eq 1
$pegouCentrado = @($achouDoente | Where-Object { $_ -like 'max-width*' }).Count -eq 1

# CONTROLE NEGATIVO: não pode acusar NADA no são — nem a borda, nem o breakpoint,
# nem o texto da regra escrito no comentário.
$limpoNoSao = $achouSao.Count -eq 0

if (-not ($pegouPx -and $pegouFill -and $pegouCentrado -and $limpoNoSao)) {
    Write-Host '   [?] CALIBRACAO FALHOU — a guarda nao sabe distinguir doente de sao' -ForegroundColor Yellow
    Write-Host "       controle positivo: px=$pegouPx auto-fill=$pegouFill centrado=$pegouCentrado"
    Write-Host "       controle negativo (CSS sao limpo): $limpoNoSao"
    if (-not $limpoNoSao) { $achouSao | ForEach-Object { Write-Host "         falso positivo: $_" } }
    Write-Host '       NAO VERIFICOU — isto nao e aprovacao.' -ForegroundColor Yellow
    exit 2
}

Write-Host '   [ok] calibrada: reprova os 3 defeitos no doente, e nao acusa nada no sao'

# ======================================================================== VEREDITO

if (-not (Test-Path -LiteralPath $PASTA_CSS)) {
    Write-Host "   [?] pasta de CSS ausente: $PASTA_CSS" -ForegroundColor Yellow
    exit 2
}

$arquivos = @(Get-ChildItem -LiteralPath $PASTA_CSS -Filter '*.css' -Recurse -File |
    Where-Object { $_.FullName -notmatch '[\\/]vendor[\\/]' })

if ($arquivos.Count -eq 0) {
    # Zero arquivo é hipótese, não aprovação: o caminho pode ter mudado.
    Write-Host '   [?] nenhum CSS encontrado — o caminho mudou?' -ForegroundColor Yellow
    exit 2
}

$problemas = @()
foreach ($arquivo in $arquivos) {
    $texto = Get-Content -LiteralPath $arquivo.FullName -Raw -Encoding UTF8
    foreach ($v in (Violacoes $texto)) {
        $relativo = $arquivo.FullName.Substring($Caminho.Length).TrimStart('\', '/')
        $problemas += "$relativo -> $v"
    }
}

Write-Host "   [ok] $($arquivos.Count) folhas de estilo varridas (vendor de fora)"

if ($problemas.Count -gt 0) {
    Write-Host "   [X] $($problemas.Count) violacao(oes) da regra de porcentagem:" -ForegroundColor Red
    $problemas | ForEach-Object { Write-Host "       $_" -ForegroundColor Red }
    Write-Host '       A regra: largura/altura/espacamento/posicao em % ou rem.' -ForegroundColor Red
    Write-Host '       px so em borda fina, sombra e breakpoint de @media.' -ForegroundColor Red
    exit 1
}

Write-Host '   [ok] nenhum px de layout, nenhum auto-fill, nada centralizado' -ForegroundColor Green
exit 0
