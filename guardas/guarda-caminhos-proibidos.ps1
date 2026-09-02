#Requires -Version 7
<#
.SYNOPSIS
    Guarda de caminhos proibidos — impede que pastas declaradas fora do versionamento
    entrem em commit, mesmo forçadas.

.DESCRIPTION
    PROPÓSITO DE NEGÓCIO
        O `.gitignore` declara que `gs/` fica fora do repositório. Mas `.gitignore` é
        uma intenção contornável: `git add -f` passa por cima dele sem reclamar, editar
        uma linha do arquivo o desliga, e uma IDE configurada para "adicionar tudo"
        pode forçar sem ninguém pedir. Esta guarda é o mecanismo que torna a decisão
        efetiva — ela olha o que está NO ÍNDICE, que é a única coisa que vira commit.

        A divisão é a mesma do resto do projeto:
          .gitignore                     -> declara a intenção
          guarda-caminhos-proibidos      -> impõe a intenção
          guarda-segredos                -> olha o CONTEÚDO, não o caminho

    INVARIANTES DO DOMÍNIO
        INV-CAMINHO-001  Nenhum arquivo sob um prefixo proibido entra no índice do git.
                         Dano se quebrado: 308 arquivos de acervo acadêmico publicados
                         num repositório público sem ninguém ter decidido publicá-los.
        INV-CAMINHO-002  A guarda se calibra antes de julgar. Se não distinguir caminho
                         proibido de caminho legítimo, sai 2 e não emite veredito.

    COMPORTAMENTO EM CASO DE FALHA
        0  PASSOU        — nada proibido no índice
        1  REPROVOU      — há caminho proibido staged; o commit para
        2  NÃO VERIFICOU — não pôde varrer (git ausente, índice vazio, calibração
                           falhou). NÃO É APROVAÇÃO.

.PARAMETER Modo
    staged  (padrão) olha o índice do git — é o que o hook usa.
    arvore  olha se algum caminho proibido está RASTREADO (já entrou em commit
            anteriormente), que é um estado diferente e pior.
#>
[CmdletBinding()]
param(
    [ValidateSet('staged', 'arvore')]
    [string]$Modo = 'staged',
    [string]$Caminho = (Split-Path -Parent $PSScriptRoot),
    [switch]$Silencioso
)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'
try { [Console]::OutputEncoding = [System.Text.UTF8Encoding]::new($false) } catch { }

function EscreverTitulo($t) { if (-not $Silencioso) { Write-Host "-- $t" -ForegroundColor DarkYellow } }
function EscreverOk($m)     { if (-not $Silencioso) { Write-Host "   [ok] $m" -ForegroundColor Green } }
function EscreverFalha($m)  { Write-Host "   [PROIBIDO] $m" -ForegroundColor Red }
function EscreverSemVeredito($m) { Write-Host "   [?] $m" -ForegroundColor Yellow }

# ---------------------------------------------------------------------------
# OS PREFIXOS PROIBIDOS
#
# Ancorados no início do caminho (`^`) de propósito. Sem a âncora, `gs/` casaria
# com `src/main/java/org/nasa/fatia/logs/` e a guarda reprovaria código legítimo —
# e guarda que reprova o certo ensina a desligar o alarme, que é como ela morre.
# ---------------------------------------------------------------------------
$PROIBIDOS = @(
    @{ Prefixo = '^gs/'; Motivo = 'acervo academico de 2025: material de referencia, nao codigo deste sistema (decisao de Paulo, 2026-09-02)' }
)

<#
.SYNOPSIS
    Este caminho está proibido? Devolve o motivo, ou $null.
.DESCRIPTION
    INVARIANTE: compara sempre com barra normal. O git relata caminho com `/` mesmo no
    Windows; comparar com `\` daria zero casamentos e a guarda ficaria verde e cega.
#>
function ObterMotivoDeProibicao([string]$arquivo) {
    $normalizado = $arquivo -replace '\\', '/'
    foreach ($p in $PROIBIDOS) {
        if ($normalizado -match $p.Prefixo) { return $p.Motivo }
    }
    return $null
}

<#
.SYNOPSIS
    Calibração: a guarda reprova o proibido e aprova o legítimo?
.DESCRIPTION
    FALHA: devolve $false; o chamador sai 2. Um detector que nunca foi visto reprovando
    pode estar aprovando por cegueira — e aqui o modo de falha é silencioso por
    natureza, porque "nada proibido no índice" e "não sei olhar" imprimem igual.
#>
function TestarCalibracao() {
    $devemSerRecusados = @(
        'gs/README.md'
        'gs/Java_Advanced/gsapi/pom.xml'
        'gs/Advanced_Business_Development_with.NET/gsApi/gsApi/appsettings.json'
    )
    $devemPassar = @(
        'src/main/java/org/nasa/fatia/endereco/domain/ports/GeocodificacaoPort.java'
        'docs/PLANO-MESTRE.md'
        'guardas/guarda-segredos.ps1'
        'build.gradle'
        # O caso que a âncora existe para proteger: contém "gs" mas não começa com "gs/"
        'src/main/resources/logs/leia-me.md'
        'src/main/java/org/nasa/core/logs/Registro.java'
    )

    $falhas = @()
    foreach ($c in $devemSerRecusados) {
        if (-not (ObterMotivoDeProibicao $c)) { $falhas += "NAO recusou caminho proibido: $c" }
    }
    foreach ($c in $devemPassar) {
        if (ObterMotivoDeProibicao $c) { $falhas += "recusou caminho LEGITIMO: $c" }
    }

    if ($falhas.Count -gt 0) {
        EscreverSemVeredito 'CALIBRACAO FALHOU — o detector nao discrimina:'
        $falhas | ForEach-Object { Write-Host "        $_" -ForegroundColor DarkGray }
        return $false
    }
    EscreverOk "calibrada: $($devemSerRecusados.Count) proibidos recusados, $($devemPassar.Count) legitimos aprovados"
    return $true
}

# ===========================================================================
if (-not (Test-Path -LiteralPath (Join-Path $Caminho '.git'))) {
    EscreverSemVeredito "nao ha repositorio git em $Caminho"
    exit 2
}
Push-Location -LiteralPath $Caminho
try {
    EscreverTitulo "guarda de caminhos proibidos — modo '$Modo'"

    if (-not (TestarCalibracao)) { exit 2 }

    if ($Modo -eq 'staged') {
        $alvo = @(& git diff --cached --name-only --diff-filter=ACMR 2>$null)
        $rotulo = 'no indice'
    } else {
        $alvo = @(& git ls-files --cached 2>$null)
        $rotulo = 'JA RASTREADO (entrou em commit anterior)'
    }
    if ($LASTEXITCODE -ne 0) {
        EscreverSemVeredito 'o comando git falhou — NAO VERIFICOU'
        exit 2
    }
    if ($alvo.Count -eq 0) {
        # Alvo vazio sai 2, nunca 0: "nada staged" nao e "nada proibido".
        EscreverSemVeredito "nenhum arquivo para examinar — alvo vazio (isto NAO e aprovacao)"
        exit 2
    }

    $achados = @()
    foreach ($a in $alvo) {
        $motivo = ObterMotivoDeProibicao $a
        if ($motivo) { $achados += [pscustomobject]@{ Arquivo = $a; Motivo = $motivo } }
    }

    EscreverOk "$($alvo.Count) caminho(s) examinado(s)"

    if ($achados.Count -gt 0) {
        Write-Host ''
        # Lista no maximo 10: 308 linhas de erro escondem a mensagem que importa.
        $achados | Select-Object -First 10 | ForEach-Object { EscreverFalha "$($_.Arquivo)" }
        if ($achados.Count -gt 10) {
            Write-Host "   [PROIBIDO] ... e mais $($achados.Count - 10) arquivo(s)" -ForegroundColor Red
        }
        Write-Host ''
        Write-Host "[X] CAMINHO PROIBIDO $rotulo — $($achados.Count) arquivo(s)." -ForegroundColor Red
        Write-Host "    Motivo: $($achados[0].Motivo)" -ForegroundColor DarkGray
        Write-Host '    Para tirar do indice sem apagar nada do disco:' -ForegroundColor DarkGray
        Write-Host '        git restore --staged gs/     (se foi staged agora)' -ForegroundColor DarkGray
        Write-Host '        git rm -r --cached gs/       (se ja estava rastreado)' -ForegroundColor DarkGray
        exit 1
    }

    Write-Host ''
    Write-Host '[OK] GUARDA DE CAMINHOS PASSOU — nada proibido.' -ForegroundColor Green
    exit 0
}
finally {
    Pop-Location
}
