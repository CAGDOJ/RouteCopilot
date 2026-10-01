$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$backend = Join-Path $root "backend"
$venv = Join-Path $backend ".venv"
$pythonExe = Join-Path $venv "Scripts\python.exe"

function Get-SystemPython {
    $py = Get-Command py -ErrorAction SilentlyContinue
    if ($py) { return @("py", "-3") }

    $python = Get-Command python -ErrorAction SilentlyContinue
    if ($python) { return @("python") }

    throw "Python 3 não foi encontrado."
}

if (-not (Test-Path $pythonExe)) {
    $systemPython = Get-SystemPython

    if ($systemPython.Count -eq 2) {
        & $systemPython[0] $systemPython[1] -m venv $venv
    } else {
        & $systemPython[0] -m venv $venv
    }

    if ($LASTEXITCODE -ne 0) {
        throw "Falha ao criar ambiente virtual."
    }
}

& $pythonExe -m pip install -r (Join-Path $backend "requirements.txt") --disable-pip-version-check
if ($LASTEXITCODE -ne 0) {
    throw "Falha ao instalar dependências."
}

$env:ROUTECOPILOT_ADMIN_TOKEN = "routecopilot-local-admin"
$env:ROUTECOPILOT_ALLOWED_ORIGINS = "http://127.0.0.1:8080,http://localhost:8080"

Set-Location $backend
Write-Host "Backend RouteCopilot: http://127.0.0.1:8000" -ForegroundColor Green
Write-Host "Swagger: http://127.0.0.1:8000/docs" -ForegroundColor Green
Write-Host "Admin local token: routecopilot-local-admin" -ForegroundColor Yellow
& $pythonExe -m uvicorn main:app --host 127.0.0.1 --port 8000
