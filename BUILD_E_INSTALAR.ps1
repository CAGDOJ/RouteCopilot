$ErrorActionPreference = "Stop"

Write-Host ""
Write-Host "=== RouteCopilot - Build ===" -ForegroundColor Cyan
Write-Host ""

& .\gradlew.bat assembleDebug
if ($LASTEXITCODE -ne 0) {
    throw "O build falhou. Corrija o erro antes de instalar."
}

$adb = Get-Command adb -ErrorAction SilentlyContinue | Select-Object -ExpandProperty Source -First 1

if (-not $adb) {
    $sdkAdb = Join-Path $env:LOCALAPPDATA "Android\Sdk\platform-tools\adb.exe"
    if (Test-Path $sdkAdb) {
        $adb = $sdkAdb
    }
}

if (-not $adb) {
    throw "ADB não encontrado. Instale Android SDK Platform-Tools ou adicione platform-tools ao PATH."
}

$apk = ".\app\build\outputs\apk\debug\app-debug.apk"

Write-Host ""
Write-Host "=== Dispositivos ===" -ForegroundColor Cyan
& $adb devices

Write-Host ""
Write-Host "=== Instalando APK ===" -ForegroundColor Cyan
& $adb install -r $apk

if ($LASTEXITCODE -ne 0) {
    throw "Falha ao instalar o APK."
}

Write-Host ""
Write-Host "Instalação concluída." -ForegroundColor Green
