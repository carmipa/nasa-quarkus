#Requires -Version 7
<#
.SYNOPSIS
    Endereço único das guardas do nasa-quarkus.

.DESCRIPTION
    PROPÓSITO DE NEGÓCIO
        Guarda que depende de alguém LEMBRAR de rodar é documentação com sorte.
        Este é o comando único: guarda nova entra aqui no mesmo commit em que
        nasce, senão ela existe e ninguém a invoca.

    INVARIANTES DO DOMÍNIO
        INV-GUARDA-001  O placar sempre mostra as três contagens — passou,
                        reprovou e SEM VEREDITO. A terceira nunca some, senão
                        o total parece completo quando não é.
        INV-GUARDA-002  Uma guarda sem veredito (2) não conta como aprovação.
                        O script inteiro sai 2 nesse caso, salvo se já houver
                        reprovação real (1), que é mais grave.

    COMPORTAMENTO EM CASO DE FALHA
        0  todas passaram
        1  pelo menos uma reprovou — o trabalho para
        2  nenhuma reprovou, mas pelo menos uma não pôde verificar

.PARAMETER Modo
    Repassado às guardas que aceitam modo. 'staged' (padrão) ou 'arvore'.
#>
[CmdletBinding()]
param(
    [ValidateSet('staged', 'arvore')]
    [string]$Modo = 'arvore'
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$raiz = Split-Path -Parent $PSScriptRoot
$passou = 0; $reprovou = 0; $semVeredito = 0
$detalhe = @()

function ExecutarGuarda([string]$nome, [string]$script, [string[]]$argumentos) {
    Write-Host ''
    Write-Host "=== $nome ===" -ForegroundColor Cyan
    $caminho = Join-Path $PSScriptRoot $script
    if (-not (Test-Path -LiteralPath $caminho -PathType Leaf)) {
        Write-Host "   [?] guarda ausente: $caminho" -ForegroundColor Yellow
        $script:semVeredito += 1
        $script:detalhe += "$nome : AUSENTE"
        return
    }
    & pwsh -NoProfile -File $caminho @argumentos
    switch ($LASTEXITCODE) {
        0       { $script:passou      += 1; $script:detalhe += "$nome : PASSOU" }
        1       { $script:reprovou    += 1; $script:detalhe += "$nome : REPROVOU" }
        default { $script:semVeredito += 1; $script:detalhe += "$nome : NAO VERIFICOU (rc=$LASTEXITCODE)" }
    }
}

ExecutarGuarda 'guarda de caminhos proibidos' 'guarda-caminhos-proibidos.ps1' @('-Modo', $Modo, '-Caminho', $raiz)
ExecutarGuarda 'guarda de segredos'            'guarda-segredos.ps1'            @('-Modo', $Modo, '-Caminho', $raiz)
ExecutarGuarda 'guarda de CSS em porcentagem'  'guarda-css-porcentagem.ps1'     @('-Caminho', $raiz)
# Esta guarda mede a TELA, com o navegador, e por isso depende do app no ar.
# Sem ele ela sai 2 (NAO VERIFICOU) — que nao e aprovacao e aparece como tal.
ExecutarGuarda 'guarda de geometria da marca'  'guarda-geometria-da-marca.ps1'  @()
# Tambem mede a TELA: sem o app no ar ela sai 2 (NAO VERIFICOU), nunca 0.
ExecutarGuarda 'guarda de hidden que esconde' 'guarda-hidden-esconde.ps1'     @()

Write-Host ''
Write-Host '================ PLACAR ================' -ForegroundColor White
$detalhe | ForEach-Object { Write-Host "  $_" }
Write-Host ''
Write-Host "  passaram ......... $passou"
Write-Host "  reprovaram ....... $reprovou"
Write-Host "  SEM VEREDITO ..... $semVeredito   (nao e aprovacao)"
Write-Host '========================================' -ForegroundColor White

if ($reprovou -gt 0)    { exit 1 }
if ($semVeredito -gt 0) { exit 2 }
exit 0
