$ErrorActionPreference = "Stop"

$main = ".\app\src\main\java\com\routecopilot\MainActivity.kt"

Write-Host "=== RouteCopilot V5 ===" -ForegroundColor Cyan

if (-not (Test-Path $main)) {
    throw "MainActivity.kt nao encontrado."
}

$weights = Select-String -Path $main -Pattern "\.weight\("

if ($weights) {
    Write-Host "ERRO: ainda existem .weight():" -ForegroundColor Red
    $weights
    exit 1
}

Write-Host "OK: MainActivity sem .weight()." -ForegroundColor Green

& .\gradlew.bat clean
if ($LASTEXITCODE -ne 0) {
    exit $LASTEXITCODE
}

& .\gradlew.bat assembleDebug
exit $LASTEXITCODE
