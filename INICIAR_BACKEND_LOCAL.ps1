$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$backend = Join-Path $root "backend"
$venv = Join-Path $backend ".venv"
$pythonExe = Join-Path $venv "Scripts\python.exe"
$requirements = Join-Path $backend "requirements.txt"

function Find-WorkingPython {
    $candidates = @()

    $py = Get-Command py.exe -ErrorAction SilentlyContinue
    if ($py) {
        $candidates += @{
            Command = $py.Source
            Prefix = @("-3")
        }
    }

    $python = Get-Command python.exe -ErrorAction SilentlyContinue
    if ($python) {
        $candidates += @{
            Command = $python.Source
            Prefix = @()
        }
    }

    foreach ($candidate in $candidates) {
        try {
            & $candidate.Command @($candidate.Prefix) --version *> $null
            if ($LASTEXITCODE -eq 0) {
                return $candidate
            }
        } catch {
        }
    }

    return $null
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
    $systemPython = Find-WorkingPython

    if ($null -eq $systemPython) {
        Write-Host "Python 3 real nao esta instalado." -ForegroundColor Red
        Write-Host ""
        Write-Host "O Windows encontrou apenas o atalho da Microsoft Store, que nao executa Python." -ForegroundColor Yellow
        Write-Host ""
        Write-Host "Instale com:" -ForegroundColor Cyan
        Write-Host "winget install -e --id Python.Python.3.13" -ForegroundColor White
        Write-Host ""
        Write-Host "Depois feche e abra o PowerShell e execute novamente:" -ForegroundColor Cyan
        Write-Host ".\ABRIR_SITES_ROUTE_COPILOT.ps1" -ForegroundColor White
        exit 2
    }

    Write-Host "[1/3] Criando ambiente Python..." -ForegroundColor Yellow

    & $systemPython.Command @($systemPython.Prefix) -m venv $venv

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
