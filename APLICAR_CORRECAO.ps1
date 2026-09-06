param(
    [string]$ProjectRoot = (Get-Location).Path
)

$ErrorActionPreference = "Stop"

$Here = Split-Path -Parent $MyInvocation.MyCommand.Path
$Patch = Join-Path $Here "patch"
$Backup = Join-Path $ProjectRoot ("_backup_routecopilot_real_" + (Get-Date -Format "yyyyMMdd_HHmmss"))

Write-Host ""
Write-Host "RouteCopilot - aplicando correcao consolidada" -ForegroundColor Cyan
Write-Host "Projeto: $ProjectRoot"
Write-Host "Backup:  $Backup"
Write-Host ""

New-Item -ItemType Directory -Force $Backup | Out-Null

$files = Get-ChildItem $Patch -Recurse -File

foreach ($file in $files) {
    $relative = $file.FullName.Substring($Patch.Length).TrimStart('\','/')
    $target = Join-Path $ProjectRoot $relative
    $targetDir = Split-Path -Parent $target

    if (Test-Path $target) {
        $backupTarget = Join-Path $Backup $relative
        New-Item -ItemType Directory -Force (Split-Path -Parent $backupTarget) | Out-Null
        Copy-Item $target $backupTarget -Force
    }

    New-Item -ItemType Directory -Force $targetDir | Out-Null
    Copy-Item $file.FullName $target -Force

    Write-Host "OK  $relative" -ForegroundColor Green
}

Write-Host ""
Write-Host "Correcao aplicada." -ForegroundColor Green
Write-Host "Agora rode:" -ForegroundColor Yellow
Write-Host ".\gradlew.bat assembleDebug"
