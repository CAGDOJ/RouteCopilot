$ErrorActionPreference = "Stop"

Write-Host "" 
Write-Host "=== RouteCopilot - Build e Instalação ===" -ForegroundColor Cyan
Write-Host ""

if (-not (Test-Path ".\local.properties")) {
    $sdk = Join-Path $env:LOCALAPPDATA "Android\Sdk"
    if (Test-Path $sdk) {
        $sdkEscaped = $sdk.Replace("\\", "\\\\")
        "sdk.dir=$sdkEscaped" | Set-Content -Encoding ASCII ".\local.properties"
        Write-Host "local.properties criado automaticamente." -ForegroundColor Green
    }
}

& .\gradlew.bat clean
if ($LASTEXITCODE -ne 0) { throw "Gradle clean falhou." }

& .\gradlew.bat assembleDebug
if ($LASTEXITCODE -ne 0) { throw "O build falhou." }

$adb = Get-Command adb -ErrorAction SilentlyContinue | Select-Object -ExpandProperty Source -First 1
if (-not $adb) {
    $sdkAdb = Join-Path $env:LOCALAPPDATA "Android\Sdk\platform-tools\adb.exe"
    if (Test-Path $sdkAdb) { $adb = $sdkAdb }
}
if (-not $adb) { throw "ADB não encontrado." }

$apk = ".\app\build\outputs\apk\debug\app-debug.apk"
if (-not (Test-Path $apk)) { throw "APK não encontrado: $apk" }

Write-Host ""
Write-Host "=== Dispositivos ===" -ForegroundColor Cyan
& $adb devices

Write-Host ""
Write-Host "=== Instalando ===" -ForegroundColor Cyan
& $adb install -r $apk
if ($LASTEXITCODE -ne 0) {
    Write-Host "A atualização falhou. Se aparecer assinatura incompatível, desinstale a versão anterior manualmente e execute novamente." -ForegroundColor Yellow
    exit $LASTEXITCODE
}

Write-Host ""
Write-Host "Instalação concluída." -ForegroundColor Green
