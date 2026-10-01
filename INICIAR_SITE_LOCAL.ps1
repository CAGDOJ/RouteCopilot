$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $MyInvocation.MyCommand.Path
$docs = Join-Path $root "docs"
$venv = Join-Path $root "backend\.venv"
$pythonExe = Join-Path $venv "Scripts\python.exe"

if (-not (Test-Path $pythonExe)) {
    throw "Execute primeiro .\INICIAR_BACKEND_LOCAL.ps1 para preparar o Python."
}

Write-Host "Site RouteCopilot: http://127.0.0.1:8080/" -ForegroundColor Green
Write-Host "Admin RouteCopilot: http://127.0.0.1:8080/admin.html" -ForegroundColor Green
& $pythonExe -m http.server 8080 --bind 127.0.0.1 --directory $docs
