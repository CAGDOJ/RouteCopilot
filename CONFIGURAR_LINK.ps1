param(
    [Parameter(Mandatory=$true)]
    [string]$BaseUrl,

    [string]$ProjectRoot = "C:\Users\stel-adm\Documents\GitHub\RouteCopilot"
)

$ErrorActionPreference = "Stop"

$BaseUrl = $BaseUrl.Trim().TrimEnd('/')

if (-not ($BaseUrl.StartsWith("https://"))) {
    throw "Use uma URL HTTPS. Exemplo: https://rastreio.seudominio.com"
}

$File = Join-Path $ProjectRoot "app\src\main\java\com\routecopilot\tracking\TrackingConfig.kt"

if (-not (Test-Path $File)) {
    throw "TrackingConfig.kt nao encontrado: $File"
}

$Content = Get-Content $File -Raw
$Content = [regex]::Replace(
    $Content,
    'const val BASE_URL\s*=\s*"[^"]*"',
    ('const val BASE_URL = "' + $BaseUrl + '"')
)

Set-Content -Path $File -Value $Content -Encoding UTF8

Write-Host "Link configurado:" -ForegroundColor Green
Write-Host $BaseUrl
Write-Host ""
Write-Host "Agora compile novamente:" -ForegroundColor Yellow
Write-Host ".\gradlew.bat assembleDebug"
