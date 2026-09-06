$main = ".\app\src\main\java\com\routecopilot\MainActivity.kt"

Write-Host "Verificando MainActivity.kt..." -ForegroundColor Cyan

if (-not (Test-Path $main)) {
    throw "MainActivity.kt não encontrado."
}

$hits = Select-String -Path $main -Pattern "\.weight\("

if ($hits) {
    Write-Host ""
    Write-Host "ERRO: ainda existem chamadas .weight():" -ForegroundColor Red
    $hits
    exit 1
}

Write-Host "OK: nenhuma chamada .weight() encontrada." -ForegroundColor Green
Write-Host ""
Write-Host "Limpando e compilando..." -ForegroundColor Cyan

& .\gradlew.bat clean
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }

& .\gradlew.bat assembleDebug
exit $LASTEXITCODE
