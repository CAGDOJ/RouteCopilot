$ErrorActionPreference = "Stop"

$sdk = Join-Path $env:LOCALAPPDATA "Android\Sdk"
$adb = Join-Path $sdk "platform-tools\adb.exe"

if (-not (Test-Path $sdk)) {
    throw "Android SDK não encontrado em: $sdk"
}

$sdkEscaped = $sdk.Replace("\\", "\\\\")
"sdk.dir=$sdkEscaped" | Set-Content -Encoding ASCII ".\local.properties"

Write-Host "local.properties criado:" -ForegroundColor Green
Write-Host "sdk.dir=$sdkEscaped"

if (Test-Path $adb) {
    Write-Host "ADB encontrado: $adb" -ForegroundColor Green
} else {
    Write-Host "Aviso: platform-tools/adb.exe não foi encontrado." -ForegroundColor Yellow
}
