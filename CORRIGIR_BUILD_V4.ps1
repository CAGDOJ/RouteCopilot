param(
    [string]$ProjectRoot = "C:\Users\stel-adm\Documents\GitHub\RouteCopilot"
)

$ErrorActionPreference = "Stop"

$File = Join-Path $ProjectRoot "app\src\main\java\com\routecopilot\MainActivity.kt"

if (-not (Test-Path $File)) {
    throw "MainActivity.kt nao encontrado em: $File"
}

$Content = Get-Content $File

$NewContent = $Content | Where-Object {
    $_.Trim() -ne "import androidx.compose.foundation.layout.weight"
}

$NewContent | Set-Content -Path $File -Encoding UTF8

Write-Host "Hotfix aplicado em MainActivity.kt" -ForegroundColor Green
Write-Host "Agora rode: .\gradlew.bat clean assembleDebug" -ForegroundColor Yellow
