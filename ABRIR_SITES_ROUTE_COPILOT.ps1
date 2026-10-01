$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$backendScript = Join-Path $root "INICIAR_BACKEND_LOCAL.ps1"
$siteScript = Join-Path $root "INICIAR_SITE_LOCAL.ps1"

function Wait-Url {
    param(
        [string]$Url,
        [int]$TimeoutSeconds = 120
    )

    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)

    while ((Get-Date) -lt $deadline) {
        try {
            $response = Invoke-WebRequest -Uri $Url -UseBasicParsing -TimeoutSec 3
            if ($response.StatusCode -ge 200 -and $response.StatusCode -lt 500) {
                return $true
            }
        } catch {
        }

        Start-Sleep -Seconds 2
    }

    return $false
}

Write-Host ""
Write-Host "============================================" -ForegroundColor Cyan
Write-Host " ROUTECOPILOT - ABRIR AMBIENTE LOCAL" -ForegroundColor Cyan
Write-Host "============================================" -ForegroundColor Cyan
Write-Host ""

Write-Host "[1/4] Iniciando backend..." -ForegroundColor Yellow
Start-Process powershell.exe -ArgumentList @(
    "-NoExit",
    "-ExecutionPolicy", "Bypass",
    "-File", $backendScript
)

Write-Host "[2/4] Aguardando backend ficar pronto..." -ForegroundColor Yellow
$backendReady = Wait-Url -Url "http://127.0.0.1:8000/health" -TimeoutSeconds 120

if (-not $backendReady) {
    Write-Host ""
    Write-Host "O backend nao iniciou." -ForegroundColor Red
    Write-Host "Veja o erro na janela ROUTECOPILOT - BACKEND LOCAL." -ForegroundColor Red
    Write-Host "As paginas nao serao abertas para evitar tela em branco." -ForegroundColor Yellow
    exit 1
}

Write-Host "[3/4] Iniciando site comercial e painel admin..." -ForegroundColor Yellow
Start-Process powershell.exe -ArgumentList @(
    "-NoExit",
    "-ExecutionPolicy", "Bypass",
    "-File", $siteScript
)

$siteReady = Wait-Url -Url "http://127.0.0.1:8080/" -TimeoutSeconds 30

if (-not $siteReady) {
    Write-Host ""
    Write-Host "O servidor do site nao iniciou." -ForegroundColor Red
    Write-Host "Veja o erro na segunda janela do PowerShell." -ForegroundColor Red
    exit 1
}

Write-Host "[4/4] Abrindo paginas..." -ForegroundColor Green

Start-Process "http://127.0.0.1:8080/"
Start-Process "http://127.0.0.1:8080/admin.html"
Start-Process "http://127.0.0.1:8000/docs"
Start-Process "http://127.0.0.1:8000/health"

Write-Host ""
Write-Host "Tudo pronto." -ForegroundColor Green
Write-Host ""
Write-Host "SITE COMERCIAL : http://127.0.0.1:8080/" -ForegroundColor Green
Write-Host "PAINEL ADMIN   : http://127.0.0.1:8080/admin.html" -ForegroundColor Green
Write-Host "API / SWAGGER  : http://127.0.0.1:8000/docs" -ForegroundColor Green
Write-Host "STATUS BACKEND : http://127.0.0.1:8000/health" -ForegroundColor Green
Write-Host ""
Write-Host "No painel admin local:" -ForegroundColor Cyan
Write-Host "Backend: http://127.0.0.1:8000"
Write-Host "Token:   routecopilot-local-admin"
Write-Host ""
Write-Host "Esse token e somente para teste local." -ForegroundColor Yellow
