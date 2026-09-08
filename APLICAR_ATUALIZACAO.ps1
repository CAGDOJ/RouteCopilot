param(
    [Parameter(Mandatory=$false)]
    [string]$ProjectPath = (Get-Location).Path
)

$ErrorActionPreference = "Stop"

# Mantem a exibicao UTF-8 no PowerShell quando possivel.
try {
    [Console]::OutputEncoding = [System.Text.Encoding]::UTF8
    $OutputEncoding = [System.Text.Encoding]::UTF8
} catch {}

$PackageRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$PayloadRoot = Join-Path $PackageRoot "payload"

if (-not (Test-Path $PayloadRoot)) {
    throw "Pasta 'payload' nao encontrada. Extraia o ZIP completo antes de executar este script."
}

if (-not (Test-Path $ProjectPath)) {
    throw "Projeto nao encontrado: $ProjectPath"
}

$ProjectPath = (Resolve-Path $ProjectPath).Path
$PayloadRoot = (Resolve-Path $PayloadRoot).Path

Write-Host ""
Write-Host "RouteCopilot V4.2 - aplicando atualizacao" -ForegroundColor Cyan
Write-Host "Origem : $PayloadRoot"
Write-Host "Destino: $ProjectPath"
Write-Host ""

if (-not (Test-Path (Join-Path $ProjectPath "settings.gradle.kts"))) {
    throw "A pasta informada nao parece ser a raiz do projeto RouteCopilot."
}

# Esta verificacao impede exatamente o erro da V4 anterior.
if ($PayloadRoot.TrimEnd('\') -eq $ProjectPath.TrimEnd('\')) {
    throw "A origem da atualizacao nao pode ser a mesma pasta do projeto."
}

# Copia o app V4.
$AppSource = Join-Path $PayloadRoot "app"
if (Test-Path $AppSource) {
    Copy-Item -Path $AppSource -Destination $ProjectPath -Recurse -Force
} else {
    throw "Payload do app nao encontrado."
}

# Portal do cliente.
$PortalSource = Join-Path $PayloadRoot "client_portal"
if (Test-Path $PortalSource) {
    Copy-Item -Path $PortalSource -Destination $ProjectPath -Recurse -Force
}

# Arquivos Gradle da raiz que acompanham a V4.
$RootFiles = @(
    "build.gradle.kts",
    "settings.gradle.kts",
    "gradle.properties",
    ".gitattributes",
    ".gitignore"
)

foreach ($File in $RootFiles) {
    $Source = Join-Path $PayloadRoot $File
    if (Test-Path $Source) {
        Copy-Item -Path $Source -Destination (Join-Path $ProjectPath $File) -Force
    }
}

# Atualiza o catalogo de versoes/dependencias.
$GradleSource = Join-Path $PayloadRoot "gradle"
if (Test-Path $GradleSource) {
    Copy-Item -Path $GradleSource -Destination $ProjectPath -Recurse -Force
}

# Remove arquivo antigo de compatibilidade que causou erro 'initialize'.
$OldCompat = Join-Path $ProjectPath "app\src\main\java\com\routecopilot\RouteCopilotApp.kt"
if (Test-Path $OldCompat) {
    Remove-Item $OldCompat -Force
    Write-Host "Removido RouteCopilotApp.kt antigo." -ForegroundColor DarkGray
}

Write-Host ""
Write-Host "Atualizacao aplicada com sucesso." -ForegroundColor Green
Write-Host ""
Write-Host "Agora execute no projeto:" -ForegroundColor Yellow
Write-Host "  .\gradlew.bat clean"
Write-Host "  .\gradlew.bat assembleDebug"
Write-Host ""
