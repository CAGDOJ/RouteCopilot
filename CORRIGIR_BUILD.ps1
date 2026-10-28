$ErrorActionPreference = "Stop"

$ProjectRoot = Get-Location
$JavaRoot = Join-Path $ProjectRoot "app\src\main\java\com\routecopilot"

if (-not (Test-Path (Join-Path $ProjectRoot "app\build.gradle.kts"))) {
    throw "Execute este script na raiz do projeto RouteCopilot."
}

$Timestamp = Get-Date -Format "yyyyMMdd-HHmmss"
$BackupRoot = Join-Path $ProjectRoot "_backup_stale_v21_$Timestamp"
New-Item -ItemType Directory -Force $BackupRoot | Out-Null

Write-Host "" 
Write-Host "RouteCopilot V2.1 - limpeza de arquivos antigos" -ForegroundColor Cyan
Write-Host "Backup: $BackupRoot" -ForegroundColor DarkGray
Write-Host ""

# Diretórios antigos que pertenciam à arquitetura anterior.
# A V2 atual não usa estes pacotes; mantê-los faz o Gradle compilar
# código antigo contra os modelos novos, gerando DeliveryStop/SpxStatus etc.
$OldDirs = @(
    "map",
    "messaging",
    "scanner",
    "tracking"
)

foreach ($DirName in $OldDirs) {
    $SourceDir = Join-Path $JavaRoot $DirName
    if (Test-Path $SourceDir) {
        $BackupDir = Join-Path $BackupRoot $DirName
        Copy-Item $SourceDir $BackupDir -Recurse -Force
        Remove-Item $SourceDir -Recurse -Force
        Write-Host "Removido legado: $DirName" -ForegroundColor Yellow
    }
}

# Arquivos antigos dentro de pacotes que continuam existindo na V2.
$OldFiles = @(
    "route\EtaEngine.kt",
    "route\GeocodingManager.kt",
    "route\RouteOptimizer.kt",
    "spx\SpxBridge.kt"
)

foreach ($Relative in $OldFiles) {
    $Source = Join-Path $JavaRoot $Relative
    if (Test-Path $Source) {
        $BackupFile = Join-Path $BackupRoot $Relative
        $BackupDir = Split-Path -Parent $BackupFile
        New-Item -ItemType Directory -Force $BackupDir | Out-Null
        Copy-Item $Source $BackupFile -Force
        Remove-Item $Source -Force
        Write-Host "Removido legado: $Relative" -ForegroundColor Yellow
    }
}

Write-Host ""
Write-Host "Arquivos Kotlin que permanecem ativos:" -ForegroundColor Green
Get-ChildItem $JavaRoot -Recurse -Filter "*.kt" |
    ForEach-Object { $_.FullName.Replace($ProjectRoot + "\", "") } |
    Sort-Object

Write-Host ""
Write-Host "Limpando build antigo..." -ForegroundColor Cyan
& .\gradlew.bat clean
if ($LASTEXITCODE -ne 0) {
    throw "Falha no gradlew clean."
}

Write-Host ""
Write-Host "Compilando V2.1..." -ForegroundColor Cyan
& .\gradlew.bat assembleDebug
if ($LASTEXITCODE -ne 0) {
    Write-Host ""
    Write-Host "A compilacao ainda apresentou erro. Copie o log completo e envie ao Chat." -ForegroundColor Red
    exit $LASTEXITCODE
}

Write-Host ""
Write-Host "BUILD SUCCESSFUL - V2.1 pronta para instalar." -ForegroundColor Green
Write-Host "APK: app\build\outputs\apk\debug\app-debug.apk" -ForegroundColor Green
Write-Host ""
Write-Host "Instalacao:" -ForegroundColor Cyan
Write-Host '& "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe" install -r ".\app\build\outputs\apk\debug\app-debug.apk"'
