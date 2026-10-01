$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $MyInvocation.MyCommand.Path

Start-Process powershell.exe -ArgumentList @(
    "-NoExit",
    "-ExecutionPolicy", "Bypass",
    "-File", (Join-Path $root "INICIAR_BACKEND_LOCAL.ps1")
)

Start-Sleep -Seconds 4

Start-Process powershell.exe -ArgumentList @(
    "-NoExit",
    "-ExecutionPolicy", "Bypass",
    "-File", (Join-Path $root "INICIAR_SITE_LOCAL.ps1")
)

Start-Sleep -Seconds 3

Start-Process "http://127.0.0.1:8080/"
Start-Process "http://127.0.0.1:8080/admin.html"
Start-Process "http://127.0.0.1:8000/docs"
Start-Process "http://127.0.0.1:8000/health"

Write-Host ""
Write-Host "SITE COMERCIAL : http://127.0.0.1:8080/" -ForegroundColor Green
Write-Host "PAINEL ADMIN   : http://127.0.0.1:8080/admin.html" -ForegroundColor Green
Write-Host "API / SWAGGER  : http://127.0.0.1:8000/docs" -ForegroundColor Green
Write-Host "STATUS BACKEND : http://127.0.0.1:8000/health" -ForegroundColor Green
Write-Host ""
Write-Host "No painel admin use:" -ForegroundColor Cyan
Write-Host "Backend: http://127.0.0.1:8000"
Write-Host "Token:   routecopilot-local-admin"
Write-Host ""
Write-Host "Esse token é somente para teste local." -ForegroundColor Yellow
