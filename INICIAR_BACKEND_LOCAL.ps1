$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$backend = Join-Path $root "backend"
$venv = Join-Path $backend ".venv"
$pythonExe = Join-Path $venv "Scripts\python.exe"
$requirements = Join-Path $backend "requirements.txt"

function Invoke-SystemPython {
    param(
        [Parameter(ValueFromRemainingArguments = $true)]
        [string[]]$Arguments
    )

    $py = Get-Command py.exe -ErrorAction SilentlyContinue
    if (-not $py) {
        $py = Get-Command py -ErrorAction SilentlyContinue
    }

    if ($py) {
        & $py.Source -3 @Arguments
        return
    }

    $python = Get-Command python.exe -ErrorAction SilentlyContinue
    if (-not $python) {
        $python = Get-Command python -ErrorAction SilentlyContinue
    }

    if ($python) {
        & $python.Source @Arguments
        return
    }

    throw "Python 3 nao foi encontrado no Windows."
}

Write-Host ""
Write-Host "============================================" -ForegroundColor Cyan
Write-Host " ROUTECOPILOT - BACKEND LOCAL" -ForegroundColor Cyan
Write-Host "============================================" -ForegroundColor Cyan
Write-Host ""

if (-not (Test-Path $backend)) {
    throw "Pasta backend nao encontrada: $backend"
}

if (-not (Test-Path $requirements)) {
    throw "Arquivo requirements.txt nao encontrado: $requirements"
}

if (-not (Test-Path $pythonExe)) {
    Write-Host "[1/3] Criando ambiente Python..." -ForegroundColor Yellow
    Invoke-SystemPython -Arguments @("-m", "venv", $venv)

    if ($LASTEXITCODE -ne 0 -or -not (Test-Path $pythonExe)) {
        throw "Falha ao criar o ambiente virtual Python."
    }
} else {
    Write-Host "[1/3] Ambiente Python ja existe." -ForegroundColor Green
}

Write-Host "[2/3] Instalando/verificando dependencias..." -ForegroundColor Yellow
& $pythonExe -m pip install -r $requirements --disable-pip-version-check

if ($LASTEXITCODE -ne 0) {
    throw "Falha ao instalar as dependencias do backend."
}

$env:ROUTECOPILOT_ADMIN_TOKEN = "routecopilot-local-admin"
$env:ROUTECOPILOT_ALLOWED_ORIGINS = "http://127.0.0.1:8080,http://localhost:8080"

Write-Host "[3/3] Iniciando backend..." -ForegroundColor Yellow
Write-Host ""
Write-Host "Backend : http://127.0.0.1:8000" -ForegroundColor Green
Write-Host "Swagger : http://127.0.0.1:8000/docs" -ForegroundColor Green
Write-Host "Health  : http://127.0.0.1:8000/health" -ForegroundColor Green
Write-Host ""
Write-Host "Token admin LOCAL: routecopilot-local-admin" -ForegroundColor Yellow
Write-Host "Nao feche esta janela enquanto estiver testando." -ForegroundColor Yellow
Write-Host ""

Set-Location $backend
& $pythonExe -m uvicorn main:app --host 127.0.0.1 --port 8000
