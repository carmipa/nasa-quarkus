#Requires -Version 7
<#
.SYNOPSIS
    Guarda de segredos — impede que credencial entre no repositório.

.DESCRIPTION
    PROPÓSITO DE NEGÓCIO
        O `origin` deste repositório é PÚBLICO (medido 2026-09-02:
        api.github.com/repos/carmipa/nasa-quarkus -> "private": false) e o
        sistema consome APIs com chave (NASA EONET, Google Geocoding). O
        `.gitignore` protege por CAMINHO e é cego para a chave colada dentro de
        um `.java`, de um `README.md` ou de um teste. Esta guarda protege por
        CONTEÚDO, e reprova o commit.

        "Documento e hash provam a ENTRADA (a regra foi lida); só guarda
        executável prova a SAÍDA (a regra foi aplicada)."

    INVARIANTES DO DOMÍNIO
        INV-SEG-001  Nenhum conteúdo com padrão de credencial viva entra no
                     índice do git. Dano se quebrado: chave publicada na
                     internet aberta, de onde não se recolhe — revogar é a
                     única cura, e ela custa o serviço no ar.
        INV-SEG-002  A guarda NUNCA imprime o segredo que encontrou. Ela diz
                     arquivo, linha e tipo. Precedente pago: o relatório que
                     denunciava a chave passou a carregá-la, e o GitHub Push
                     Protection barrou o push do próprio relatório.
        INV-SEG-003  A guarda se calibra ANTES de julgar. Se não distinguir um
                     caso doente de um caso são, ela sai 2 e não emite veredito.
                     Detector que nunca foi visto reprovando pode estar
                     aprovando por cegueira.

    COMPORTAMENTO EM CASO DE FALHA
        Três estados, nunca dois:
          0  PASSOU        — varreu e nada encontrou
          1  REPROVOU      — achou credencial; o commit para
          2  NÃO VERIFICOU — não pôde varrer (git ausente, alvo vazio,
                             calibração falhou). NÃO É APROVAÇÃO.
        Alvo vazio sai 2, jamais 0: "não achei nada" e "não tinha como achar"
        não podem ter a mesma cara.

.PARAMETER Modo
    staged  (padrão) varre só as linhas ADICIONADAS no índice — é o que o
            hook pre-commit usa; é o conteúdo que está prestes a virar commit.
    arvore  varre todo arquivo que o git versionaria hoje
            (`git ls-files -co --exclude-standard`) — auditoria completa.

.PARAMETER Caminho
    Raiz do repositório. Padrão: a pasta acima desta.

.EXAMPLE
    pwsh -NoProfile -File guardas/guarda-segredos.ps1
    pwsh -NoProfile -File guardas/guarda-segredos.ps1 -Modo arvore
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

# Sem isto o console redirecionado escreve em codepage OEM e o relatório sai com
# acento corrompido ("conex�o"). Cicatriz já paga nesta casa, com caminho de
# arquivo virando `NoSuchFileException` por causa do mesmo detalhe.
try { [Console]::OutputEncoding = [System.Text.UTF8Encoding]::new($false) } catch { }

# ATENÇÃO a nome de função em PowerShell: `nv` é alias nativo de New-Variable e
# alias vence função — uma guarda desta casa saiu 0 onde devia sair 2 por causa
# disso. Os nomes abaixo são longos de propósito.
function EscreverTitulo($t) { if (-not $Silencioso) { Write-Host "-- $t" -ForegroundColor DarkYellow } }
function EscreverOk($m)     { if (-not $Silencioso) { Write-Host "   [ok] $m" -ForegroundColor Green } }
function EscreverFalha($m)  { Write-Host "   [ACHADO] $m" -ForegroundColor Red }
function EscreverSemVeredito($m) { Write-Host "   [?] $m" -ForegroundColor Yellow }

# ---------------------------------------------------------------------------
# OS PADRÕES
#
# Precisão acima de recall onde o falso positivo é caro. Guarda que reprova
# código correto ensina a desligar o alarme — no PRIDE uma reprovou
# `inset: 0 auto 0 0`, que estava certo, e quase morreu na estreia.
# Por isso: formato exato de chave conhecida > heurística genérica.
# ---------------------------------------------------------------------------
$PADROES = @(
    @{ Nome = 'Google API key (AIza…)';        Regex = 'AIza[0-9A-Za-z_\-]{35}' }
    @{ Nome = 'AWS Access Key ID';             Regex = '\b(AKIA|ASIA|ABIA|ACCA)[0-9A-Z]{16}\b' }
    @{ Nome = 'AWS Secret Access Key';         Regex = '(?i)aws(.{0,20})?(secret|private)(.{0,20})?[=:]\s*["'']?[A-Za-z0-9/+]{40}["'']?' }
    @{ Nome = 'GitHub token (ghp_/gho_/ghs_)'; Regex = '\bgh[pousr]_[A-Za-z0-9]{36,255}\b' }
    @{ Nome = 'GitHub fine-grained PAT';       Regex = '\bgithub_pat_[A-Za-z0-9_]{22,255}\b' }
    @{ Nome = 'Slack token';                   Regex = '\bxox[baprs]-[A-Za-z0-9-]{10,}\b' }
    @{ Nome = 'Stripe secret key';             Regex = '\bsk_(live|test)_[A-Za-z0-9]{16,}\b' }
    @{ Nome = 'OpenAI/Anthropic key (sk-…)';   Regex = '\bsk-(ant-|proj-)?[A-Za-z0-9_\-]{24,}\b' }
    @{ Nome = 'Google OAuth client secret';    Regex = '\bGOCSPX-[A-Za-z0-9_\-]{20,}\b' }
    @{ Nome = 'Discord bot token';             Regex = '\b[MNO][A-Za-z\d_\-]{23,25}\.[\w\-]{6}\.[\w\-]{27,39}\b' }
    @{ Nome = 'Chave privada (PEM/OpenSSH)';   Regex = '-----BEGIN (RSA |DSA |EC |OPENSSH |PGP |ENCRYPTED )?PRIVATE KEY( BLOCK)?-----' }
    @{ Nome = 'JWT com payload';               Regex = '\beyJ[A-Za-z0-9_\-]{10,}\.eyJ[A-Za-z0-9_\-]{10,}\.[A-Za-z0-9_\-]{10,}\b' }
    @{ Nome = 'Senha em string de conexão';    Regex = '(?i)\b(password|pwd|senha)\s*=\s*[^;"''\s,<>{}\)]{4,}' }
    @{ Nome = 'Credencial em URL';             Regex = '(?i)\b(https?|ftp|mongodb(\+srv)?|postgres(ql)?|mysql|redis|amqp)://[^/\s:@]{1,64}:[^/\s:@]{3,}@' }
    @{ Nome = 'Atribuição de chave/segredo';   Regex = '(?i)\b(api[_\-]?key|apikey|access[_\-]?token|auth[_\-]?token|client[_\-]?secret|secret[_\-]?key|private[_\-]?key|bearer)\b\s*[=:]\s*["'']?[A-Za-z0-9_\-\.\/\+]{16,}["'']?' }
)

# ---------------------------------------------------------------------------
# OS PLACEHOLDERS — o que NÃO é segredo, e por que a lista existe em DOIS grupos
#
# Este repositório é acadêmico e didático: documentação, README e arquivo de
# exemplo falam de senha o tempo todo. Sem esta lista a guarda vira ruído e o
# alarme é desligado — que é como guarda morre.
#
# CICATRIZ (2026-09-02, pega pela própria calibração antes de a guarda ser
# confiada): a primeira versão tinha `abcdef` e `1234567` como substring livre.
# Efeito medido: QUALQUER chave real que contivesse essas sequências em
# qualquer posição era descartada em silêncio — um furo de falso NEGATIVO
# dentro da guarda escrita para não ter furo. A palavra curta e genérica só
# vale com fronteira de palavra (`\b`), porque dentro de uma chave alfanumérica
# aleatória não existe fronteira, e aí ela não casa — que é exatamente o que se
# quer.
# ---------------------------------------------------------------------------

# Grupo 1 — distintivos ou estruturais: seguros como substring livre. Nenhum
# aparece por acaso no meio de uma credencial gerada por máquina.
$PLACEHOLDERS_LIVRES = @(
    'DEMO_KEY'
    'your[_\-]?(api[_\-]?)?key', 'your[_\-]?secret', 'your[_\-]?password', 'your[_\-]?token'
    'sua[_\-]?senha', 'seu[_\-]?token', 'sua[_\-]?chave', 'seu[_\-]?email', 'sua[_\-]?chave'
    'change[_\-]?me', 'changeit', 'placeholder', 'example', 'exemplo', 'sample'
    'insira', 'coloque', 'preencha', 'substitua', 'informe', 'seuprovedor'
    'senha_de_aplicativo', 'senha_ou_email', 'redacted', 'omitid', 'oculta'
    '\$\{[^}]*\}'          # ${VARIAVEL}
    '%[A-Za-z_][A-Za-z0-9_]*%'  # %VARIAVEL%
    '<[^>]{1,40}>'         # <coloque-aqui>
    '\{\{[^}]*\}\}'        # {{variavel}}
    'x{4,}', '\*{3,}'      # xxxxxxxx / ********
)

# Grupo 2 — palavras curtas e comuns: SÓ com fronteira de palavra.
$PLACEHOLDERS_COM_FRONTEIRA = @(
    'null', 'none', 'undefined', 'todo', 'fixme', 'dummy', 'test', 'fake'
)

$REGEX_PLACEHOLDER = '(?i)(' +
    (($PLACEHOLDERS_LIVRES -join '|') + '|' +
     (($PLACEHOLDERS_COM_FRONTEIRA | ForEach-Object { "\b$_\b" }) -join '|')) + ')'

# Selo de exceção, no espírito do `-- DESTRUTIVO-AUTORIZADO:` das migrations.
# Serve para o caso legítimo raro; exige motivo escrito na mesma linha.
$SELO_EXCECAO = 'SEGREDO-FALSO-POSITIVO-AUTORIZADO:'

# Arquivo que é ruído por natureza: lockfile e minificado têm hash longo que
# casa com "atribuição de chave" sem ser segredo nenhum.
$EXTENSOES_IGNORADAS = @(
    '.png','.jpg','.jpeg','.gif','.ico','.webp','.svg','.pdf','.docx','.xlsx',
    '.zip','.gz','.tar','.jar','.class','.exe','.dll','.so','.dylib','.woff','.woff2','.ttf'
)
$NOMES_IGNORADOS = @(
    'package-lock.json','yarn.lock','pnpm-lock.yaml','gradle.lockfile','poetry.lock','Cargo.lock'
)

<#
.SYNOPSIS
    Julga UMA linha de texto: ela carrega credencial viva?

.DESCRIPTION
    PROPÓSITO: separar chave real de menção didática a chave.
    INVARIANTE: linha com selo de exceção ou valor de placeholder NUNCA é
                acusada — falso positivo mata a guarda.
    FALHA: devolve $null quando não há achado; devolve o NOME do padrão quando
           há. Nunca devolve o trecho casado, para não propagar o segredo.
#>
function ObterTipoDeSegredoNaLinha([string]$linha) {
    if ([string]::IsNullOrWhiteSpace($linha)) { return $null }
    if ($linha.Contains($SELO_EXCECAO))       { return $null }
    if ($linha.Length -gt 2000)               { return $null }   # minificado

    foreach ($p in $PADROES) {
        $m = [regex]::Match($linha, $p.Regex)
        if (-not $m.Success) { continue }

        # O teste de placeholder vale sobre o VALOR, não sobre o casamento
        # inteiro. `Password=%DB_PASSWORD%` é fictício; `Password=fiap25` não —
        # e os dois começam pela mesma palavra. Sem separar chave de valor, a
        # palavra "password" do prefixo diluiria a medida e o veredito passaria
        # a depender do tamanho do nome do campo, não do conteúdo.
        $valor = $m.Value
        $corte = [Math]::Max($valor.LastIndexOf('='), $valor.LastIndexOf(':'))
        if ($corte -ge 0 -and $corte -lt ($valor.Length - 1)) {
            $valor = $valor.Substring($corte + 1)
        }
        $valor = $valor.Trim().Trim('"', "'", ' ')

        if ([regex]::IsMatch($valor, $REGEX_PLACEHOLDER)) { continue }

        return $p.Nome
    }
    return $null
}

<#
.SYNOPSIS
    Calibração: a guarda enxerga? Ela sabe dizer NÃO?

.DESCRIPTION
    PROPÓSITO: provar, no mesmo experimento, que o detector reprova o doente e
               aprova o são. Sem isso, um zero não é prova — é hipótese.
    INVARIANTE: falhou um caso-controle, a saída sobre os arquivos reais NÃO
                vale e o script sai 2.
    FALHA: devolve $false; o chamador sai com 2 (NÃO VERIFICOU).
#>
function TestarCalibracao() {
    # Devem dar POSITIVO. Valores sintéticos e de alta entropia aparente: têm
    # de PARECER credencial, senão a calibração aprova por o caso doente não
    # ser doente o bastante. (A primeira versão usava `0123456789…` e
    # `abcdef…`, e a própria lista de placeholders os anulava — a calibração
    # reprovou e o furo apareceu antes de a guarda entrar em serviço.)
    # O selo em cada linha não é enfeite: sem ele a guarda REPROVA O PRÓPRIO
    # ARQUIVO quando varre a árvore — medido em 2026-09-02, 8 achados, todos
    # aqui. É a armadilha já registrada no vault ("o comentário que documenta o
    # defeito reprova o build"). O selo está no COMENTÁRIO, fora das aspas: a
    # string que a calibração testa continua sendo só a credencial sintética.
    $doentes = @(
        'ApiKey = "AIzaSyD9tQmRvKpLbXcHnWzGjUyEoAfTiSdNhKrQw"'   # SEGREDO-FALSO-POSITIVO-AUTORIZADO: caso-controle sintetico
        'aws_key: AKIAQWERTYUIOPASDFGH'                          # SEGREDO-FALSO-POSITIVO-AUTORIZADO: caso-controle sintetico
        'ghp_QmRvKpLbXcHnWzGjUyEoAfTiSdNhKrQwZxCv'               # SEGREDO-FALSO-POSITIVO-AUTORIZADO: caso-controle sintetico
        '"OracleDb": "User ID=rm999999;Password=Tr0vaoAzul7;"'   # SEGREDO-FALSO-POSITIVO-AUTORIZADO: caso-controle sintetico
        'https://usuario:Tr0vaoAzul7@servidor.interno/base'      # SEGREDO-FALSO-POSITIVO-AUTORIZADO: caso-controle sintetico
        '-----BEGIN RSA PRIVATE KEY-----'                        # SEGREDO-FALSO-POSITIVO-AUTORIZADO: caso-controle sintetico
        'client_secret: "GOCSPX-QmRvKpLbXcHnWzGjUyEo"'           # SEGREDO-FALSO-POSITIVO-AUTORIZADO: caso-controle sintetico
        'sk-ant-QmRvKpLbXcHnWzGjUyEoAfTiSdNhKrQw'                # SEGREDO-FALSO-POSITIVO-AUTORIZADO: caso-controle sintetico
    )
    # Devem dar NEGATIVO. São as frases que o repositório legitimamente tem —
    # documentação, exemplo e referência a variável de ambiente.
    $saos = @(
        'nasa.api.key=DEMO_KEY'
        '"Password": "sua_senha_de_aplicativo_ou_email"'
        'google.maps.apikey=${GOOGLE_MAPS_APIKEY}'
        '# Configure a variavel API_KEY antes de subir'
        'private String googleApiKey;'
        'ApiKey = "COLOQUE_SUA_CHAVE_AQUI"'
        'password=<informe-a-senha>'
        'senha = "xxxxxxxx"'
        'quarkus.datasource.jdbc.url=jdbc:sqlite:data/nasa.db'
        'Password=%DB_PASSWORD%'
        'nasa.api.key=${NASA_API_KEY:DEMO_KEY}'
        '| `NASA_API_KEY` | chave da API da NASA | obrigatoria em producao |'
    )

    $falhas = @()
    foreach ($d in $doentes) {
        if (-not (ObterTipoDeSegredoNaLinha $d)) { $falhas += "NAO reprovou caso doente: $($d.Substring(0, [Math]::Min(40, $d.Length)))..." }
    }
    foreach ($s in $saos) {
        $t = ObterTipoDeSegredoNaLinha $s
        if ($t) { $falhas += "reprovou caso SAO como '$t': $s" }
    }

    if ($falhas.Count -gt 0) {
        EscreverSemVeredito 'CALIBRACAO FALHOU — o detector nao discrimina. Veredito sobre os arquivos reais NAO vale:'
        $falhas | ForEach-Object { Write-Host "        $_" -ForegroundColor DarkGray }
        return $false
    }
    EscreverOk "calibrada: $($doentes.Count) casos doentes reprovados, $($saos.Count) casos saos aprovados"
    return $true
}

function DeveIgnorarArquivo([string]$arquivo) {
    $nome = Split-Path -Leaf $arquivo
    if ($NOMES_IGNORADOS -contains $nome) { return $true }
    $ext = [System.IO.Path]::GetExtension($arquivo).ToLowerInvariant()
    if ($EXTENSOES_IGNORADAS -contains $ext) { return $true }
    return $false
}

# ===========================================================================
# EXECUÇÃO
# ===========================================================================
if (-not (Test-Path -LiteralPath (Join-Path $Caminho '.git'))) {
    EscreverSemVeredito "nao ha repositorio git em $Caminho — sem como varrer o que seria commitado"
    exit 2
}
Push-Location -LiteralPath $Caminho
try {
    EscreverTitulo "guarda de segredos — modo '$Modo'"

    if (-not (TestarCalibracao)) { exit 2 }

    $achados = @()
    $linhasVarridas = 0
    $arquivosVarridos = 0

    if ($Modo -eq 'staged') {
        # Só as linhas ADICIONADAS. É exatamente o que vira commit agora.
        $diff = @(& git diff --cached --unified=0 --no-color --diff-filter=ACM 2>$null)
        if ($LASTEXITCODE -ne 0) {
            EscreverSemVeredito 'git diff --cached falhou — NAO VERIFICOU'
            exit 2
        }
        if ($diff.Count -eq 0) {
            # Alvo vazio sai 2, nunca 0. "Nada staged" nao e "nada perigoso".
            EscreverSemVeredito 'nada no indice do git — nao ha o que varrer (isto NAO e aprovacao)'
            exit 2
        }

        $arquivoAtual = '(desconhecido)'
        $numeroLinha  = 0
        foreach ($l in $diff) {
            if ($l -match '^\+\+\+ b/(.+)$') {
                $arquivoAtual = $Matches[1]
                $arquivosVarridos += 1
                continue
            }
            if ($l -match '^@@ -\d+(?:,\d+)? \+(\d+)') { $numeroLinha = [int]$Matches[1] - 1; continue }
            if ($l -notmatch '^\+') { continue }
            if ($l -match '^\+\+\+') { continue }

            $numeroLinha += 1
            if (DeveIgnorarArquivo $arquivoAtual) { continue }

            $conteudo = $l.Substring(1)
            $linhasVarridas += 1
            $tipo = ObterTipoDeSegredoNaLinha $conteudo
            if ($tipo) { $achados += [pscustomobject]@{ Arquivo = $arquivoAtual; Linha = $numeroLinha; Tipo = $tipo } }
        }
    }
    else {
        # Universo = tudo que o git versionaria hoje (rastreado + novo não
        # ignorado). É a mesma pergunta do commit, feita sobre a árvore toda.
        $arquivos = @(& git ls-files --cached --others --exclude-standard 2>$null)
        if ($LASTEXITCODE -ne 0) {
            EscreverSemVeredito 'git ls-files falhou — NAO VERIFICOU'
            exit 2
        }
        if ($arquivos.Count -eq 0) {
            EscreverSemVeredito 'nenhum arquivo versionavel encontrado — alvo vazio (isto NAO e aprovacao)'
            exit 2
        }

        foreach ($a in $arquivos) {
            if (DeveIgnorarArquivo $a) { continue }
            if (-not (Test-Path -LiteralPath $a -PathType Leaf)) { continue }
            $info = Get-Item -LiteralPath $a
            if ($info.Length -gt 4MB) { continue }

            $arquivosVarridos += 1
            $n = 0
            foreach ($linha in [System.IO.File]::ReadLines($info.FullName)) {
                $n += 1
                $linhasVarridas += 1
                $tipo = ObterTipoDeSegredoNaLinha $linha
                if ($tipo) { $achados += [pscustomobject]@{ Arquivo = $a; Linha = $n; Tipo = $tipo } }
            }
        }
    }

    # Controle positivo do ALVO: varri mesmo alguma coisa?
    if ($linhasVarridas -eq 0) {
        EscreverSemVeredito "varri $arquivosVarridos arquivo(s) e ZERO linhas — instrumento possivelmente cego"
        exit 2
    }
    EscreverOk "$arquivosVarridos arquivo(s), $linhasVarridas linha(s) varrida(s)"

    if ($achados.Count -gt 0) {
        Write-Host ''
        # INV-SEG-002: arquivo, linha e tipo. NUNCA o conteudo.
        foreach ($x in $achados) { EscreverFalha "$($x.Arquivo):$($x.Linha) — $($x.Tipo)" }
        Write-Host ''
        Write-Host "[X] GUARDA DE SEGREDOS REPROVOU — $($achados.Count) achado(s)." -ForegroundColor Red
        Write-Host '    O conteudo NAO e impresso de proposito: imprimi-lo faria o log carregar a chave.' -ForegroundColor DarkGray
        Write-Host '    Saidas: (a) tirar o valor do arquivo e ler de variavel de ambiente;' -ForegroundColor DarkGray
        Write-Host "            (b) `git restore --staged <arquivo>` e ignorar o caminho no .gitignore;" -ForegroundColor DarkGray
        Write-Host "            (c) se for MESMO falso positivo, selo `"$SELO_EXCECAO <motivo>`" na linha." -ForegroundColor DarkGray
        exit 1
    }

    Write-Host ''
    Write-Host '[OK] GUARDA DE SEGREDOS PASSOU — nenhum padrao de credencial encontrado.' -ForegroundColor Green
    exit 0
}
finally {
    Pop-Location
}
