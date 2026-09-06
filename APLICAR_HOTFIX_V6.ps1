$ErrorActionPreference = "Stop"

Write-Host ""
Write-Host "==========================================" -ForegroundColor Cyan
Write-Host " RouteCopilot - HOTFIX REABERTURA V6" -ForegroundColor Cyan
Write-Host "==========================================" -ForegroundColor Cyan
Write-Host ""

$root = (Get-Location).Path

$sessionPath = Join-Path $root "app\src\main\java\com\routecopilot\spx\SpxSessionState.kt"
$servicePath = Join-Path $root "app\src\main\java\com\routecopilot\spx\SpxAccessibilityService.kt"
$appPath = Join-Path $root "app\src\main\java\com\routecopilot\RouteCopilotApp.kt"
$manifestPath = Join-Path $root "app\src\main\AndroidManifest.xml"

foreach ($p in @($sessionPath, $servicePath, $manifestPath)) {
    if (!(Test-Path $p)) {
        throw "Arquivo obrigatório não encontrado: $p"
    }
}

$stamp = Get-Date -Format "yyyyMMdd_HHmmss"
$backup = Join-Path $root "_backup_hotfix_$stamp"
New-Item -ItemType Directory -Force $backup | Out-Null

Copy-Item $sessionPath (Join-Path $backup "SpxSessionState.kt")
Copy-Item $servicePath (Join-Path $backup "SpxAccessibilityService.kt")
Copy-Item $manifestPath (Join-Path $backup "AndroidManifest.xml")

Write-Host "Backup criado em: $backup" -ForegroundColor DarkGray

# ---------------------------------------------------------------------
# 1) RouteCopilotApp.kt
# ---------------------------------------------------------------------

$routeCopilotApp = @'
package com.routecopilot

import android.app.Application
import com.routecopilot.spx.SpxSessionState

class RouteCopilotApp : Application() {

    override fun onCreate() {
        super.onCreate()
        SpxSessionState.initialize(this)
    }
}
'@

[System.IO.File]::WriteAllText(
    $appPath,
    $routeCopilotApp,
    [System.Text.UTF8Encoding]::new($false)
)

Write-Host "[OK] RouteCopilotApp.kt" -ForegroundColor Green

# ---------------------------------------------------------------------
# 2) SpxSessionState.kt persistente
# ---------------------------------------------------------------------

$session = @'
package com.routecopilot.spx

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class SpxState {
    IDLE,
    STARTING_IMPORT,
    OPENING_SPX,
    CHECKING_SESSION,
    LOGIN_REQUIRED,
    CONSENT_REQUIRED,
    FACE_CHECK_REQUIRED,
    AUTHENTICATED,
    FINDING_DOWNLOAD_BUTTON,
    DOWNLOAD_BUTTON_FOUND,
    DOWNLOADING_ROUTE,
    WAITING_ROUTE_DOWNLOAD,
    ROUTE_DOWNLOADED,
    OPENING_DELIVERIES,
    OPENING_IN_ROUTE,
    READING_ROUTE,
    VALIDATING_ROUTE,
    CALCULATING_ROUTE,
    IMPORT_COMPLETE,
    RETURNING_TO_COPILOT,
    ROUTE_READY,
    ERROR
}

data class RotaImportada(
    val at: String? = null,
    val dataCarregamento: String? = null,
    val totalEsperado: Int? = null,
    val pedidosImportados: Int = 0,
    val pedidos: Set<String> = emptySet()
)

object SpxSessionState {

    private const val PREFS = "routecopilot_session"
    private const val KEY_ROUTE_AT = "route_at"
    private const val KEY_ROUTE_DATE = "route_date"
    private const val KEY_ROUTE_TOTAL = "route_total"
    private const val KEY_ROUTE_PACKAGES = "route_packages"
    private const val KEY_IMPORT_ACTIVE = "import_active"
    private const val KEY_IMPORT_STARTED_AT = "import_started_at"

    private var appContext: Context? = null
    private var initialized = false

    private val _state = MutableStateFlow(SpxState.IDLE)
    val state: StateFlow<SpxState> = _state.asStateFlow()

    private val _statusMessage = MutableStateFlow("Pronto para iniciar")
    val statusMessage: StateFlow<String> = _statusMessage.asStateFlow()

    private val _atCode = MutableStateFlow<String?>(null)
    val atCode: StateFlow<String?> = _atCode.asStateFlow()

    private val _dataCarregamento = MutableStateFlow<String?>(null)
    val dataCarregamento: StateFlow<String?> = _dataCarregamento.asStateFlow()

    private val _totalEsperado = MutableStateFlow<Int?>(null)
    val totalEsperado: StateFlow<Int?> = _totalEsperado.asStateFlow()

    private val _packageCodes = MutableStateFlow<Set<String>>(emptySet())
    val packageCodes: StateFlow<Set<String>> = _packageCodes.asStateFlow()

    private val _packageCount = MutableStateFlow(0)
    val packageCount: StateFlow<Int> = _packageCount.asStateFlow()

    fun initialize(context: Context) {
        appContext = context.applicationContext

        if (initialized) return
        initialized = true

        prefs()?.edit()
            ?.putBoolean(KEY_IMPORT_ACTIVE, false)
            ?.remove(KEY_IMPORT_STARTED_AT)
            ?.apply()

        restoreCompletedRoute()
    }

    fun beginImport() {
        _atCode.value = null
        _dataCarregamento.value = null
        _totalEsperado.value = null
        _packageCodes.value = emptySet()
        _packageCount.value = 0

        prefs()?.edit()
            ?.putBoolean(KEY_IMPORT_ACTIVE, true)
            ?.putLong(KEY_IMPORT_STARTED_AT, System.currentTimeMillis())
            ?.apply()

        _state.value = SpxState.STARTING_IMPORT
        _statusMessage.value = "Preparando sincronização..."
    }

    fun isImportActive(): Boolean {
        return prefs()?.getBoolean(KEY_IMPORT_ACTIVE, false)
            ?: (_state.value == SpxState.STARTING_IMPORT)
    }

    fun updateState(state: SpxState, message: String? = null) {
        _state.value = state

        if (!message.isNullOrBlank()) {
            _statusMessage.value = message
        }

        when (state) {
            SpxState.IMPORT_COMPLETE,
            SpxState.ROUTE_READY -> {
                if (_packageCount.value > 0) {
                    saveCompletedRoute()
                }

                prefs()?.edit()
                    ?.putBoolean(KEY_IMPORT_ACTIVE, false)
                    ?.remove(KEY_IMPORT_STARTED_AT)
                    ?.apply()
            }

            SpxState.ERROR -> {
                prefs()?.edit()
                    ?.putBoolean(KEY_IMPORT_ACTIVE, false)
                    ?.remove(KEY_IMPORT_STARTED_AT)
                    ?.apply()
            }

            else -> Unit
        }
    }

    fun updateAtCode(at: String?) {
        if (!at.isNullOrBlank()) {
            _atCode.value = at.trim().uppercase()
        }
    }

    fun updateDataCarregamento(data: String?) {
        if (!data.isNullOrBlank()) {
            _dataCarregamento.value = data.trim()
        }
    }

    fun updateTotalEsperado(total: Int?) {
        if (total == null || total <= 0) return

        val atual = _totalEsperado.value

        if (atual == null || total > atual) {
            _totalEsperado.value = total
        }
    }

    fun addPackageCodes(codes: Collection<String>): Int {
        if (codes.isEmpty()) return 0

        val atual = LinkedHashSet(_packageCodes.value)
        val antes = atual.size

        codes.forEach { raw ->
            val code = raw.trim().uppercase()

            if (code.startsWith("BR") && code.length >= 10) {
                atual.add(code)
            }
        }

        _packageCodes.value = atual
        _packageCount.value = atual.size

        return atual.size - antes
    }

    fun currentRoute() = RotaImportada(
        at = _atCode.value,
        dataCarregamento = _dataCarregamento.value,
        totalEsperado = _totalEsperado.value,
        pedidosImportados = _packageCount.value,
        pedidos = _packageCodes.value
    )

    fun hasSavedRoute(): Boolean {
        val p = prefs() ?: return false
        val at = p.getString(KEY_ROUTE_AT, null)
        val packages = p.getStringSet(KEY_ROUTE_PACKAGES, emptySet()).orEmpty()
        return !at.isNullOrBlank() || packages.isNotEmpty()
    }

    fun restoreCompletedRoute() {
        val p = prefs() ?: return

        val at = p.getString(KEY_ROUTE_AT, null)
        val date = p.getString(KEY_ROUTE_DATE, null)

        val total = if (p.contains(KEY_ROUTE_TOTAL)) {
            p.getInt(KEY_ROUTE_TOTAL, 0).takeIf { it > 0 }
        } else {
            null
        }

        val packages =
            LinkedHashSet(
                p.getStringSet(
                    KEY_ROUTE_PACKAGES,
                    emptySet()
                ).orEmpty()
            )

        if (at.isNullOrBlank() && packages.isEmpty()) {
            _state.value = SpxState.IDLE
            _statusMessage.value = "Pronto para iniciar"
            return
        }

        _atCode.value = at
        _dataCarregamento.value = date
        _totalEsperado.value = total
        _packageCodes.value = packages
        _packageCount.value = packages.size

        _state.value = SpxState.ROUTE_READY
        _statusMessage.value = "Rota restaurada."
    }

    fun fail(message: String) {
        _state.value = SpxState.ERROR
        _statusMessage.value = message

        prefs()?.edit()
            ?.putBoolean(KEY_IMPORT_ACTIVE, false)
            ?.remove(KEY_IMPORT_STARTED_AT)
            ?.apply()
    }

    fun reset() {
        _state.value = SpxState.IDLE
        _statusMessage.value = "Pronto para iniciar"

        _atCode.value = null
        _dataCarregamento.value = null
        _totalEsperado.value = null
        _packageCodes.value = emptySet()
        _packageCount.value = 0

        prefs()?.edit()
            ?.clear()
            ?.apply()
    }

    private fun saveCompletedRoute() {
        val p = prefs() ?: return
        val editor = p.edit()

        val at = _atCode.value
        val date = _dataCarregamento.value
        val total = _totalEsperado.value
        val packages = HashSet(_packageCodes.value)

        if (at.isNullOrBlank()) editor.remove(KEY_ROUTE_AT)
        else editor.putString(KEY_ROUTE_AT, at)

        if (date.isNullOrBlank()) editor.remove(KEY_ROUTE_DATE)
        else editor.putString(KEY_ROUTE_DATE, date)

        if (total == null || total <= 0) editor.remove(KEY_ROUTE_TOTAL)
        else editor.putInt(KEY_ROUTE_TOTAL, total)

        editor.putStringSet(KEY_ROUTE_PACKAGES, packages)
        editor.putBoolean(KEY_IMPORT_ACTIVE, false)
        editor.remove(KEY_IMPORT_STARTED_AT)
        editor.apply()
    }

    private fun prefs() =
        appContext?.getSharedPreferences(
            PREFS,
            Context.MODE_PRIVATE
        )
}
'@

[System.IO.File]::WriteAllText(
    $sessionPath,
    $session,
    [System.Text.UTF8Encoding]::new($false)
)

Write-Host "[OK] SpxSessionState.kt" -ForegroundColor Green

# ---------------------------------------------------------------------
# 3) AndroidManifest: Application inicializa a sessão antes da UI
# ---------------------------------------------------------------------

$manifest = Get-Content $manifestPath -Raw

if ($manifest -notmatch 'android:name\s*=\s*"\.RouteCopilotApp"') {
    $manifest = [regex]::Replace(
        $manifest,
        '<application(\s*)',
        '<application$1        android:name=".RouteCopilotApp"' + "`r`n",
        1
    )
}

[System.IO.File]::WriteAllText(
    $manifestPath,
    $manifest,
    [System.Text.UTF8Encoding]::new($false)
)

Write-Host "[OK] AndroidManifest.xml" -ForegroundColor Green

# ---------------------------------------------------------------------
# 4) SpxAccessibilityService:
#    só pode controlar o SPX quando beginImport() autorizou a importação.
# ---------------------------------------------------------------------

$service = Get-Content $servicePath -Raw

if ($service -notmatch 'HOTFIX_V6_IMPORT_GATE_EVENT') {
    $patternEvent = '(?s)(if\s*\(\s*packageName\s*!=\s*SPX_PACKAGE\s*\)\s*\{\s*return\s*\})'

    $replacementEvent = @'
$1

        // HOTFIX_V6_IMPORT_GATE_EVENT
        // Se o usuário apenas abriu/reabriu o app ou abriu o SPX manualmente,
        // o serviço NÃO inicia uma sincronização sozinho.
        if (!SpxSessionState.isImportActive()) {
            handler.removeCallbacks(scanRunnable)
            hideBanner()
            return
        }
'@

    $newService = [regex]::Replace(
        $service,
        $patternEvent,
        $replacementEvent,
        1
    )

    if ($newService -eq $service) {
        throw "Não consegui localizar o bloco packageName/SPX_PACKAGE no SpxAccessibilityService.kt."
    }

    $service = $newService
}

if ($service -notmatch 'HOTFIX_V6_IMPORT_GATE_SCAN') {
    $patternScan = '(?s)(private\s+fun\s+scanCurrentScreen\s*\(\s*\)\s*\{\s*if\s*\(\s*importFinished\s*\)\s*\{\s*return\s*\})'

    $replacementScan = @'
$1

        // HOTFIX_V6_IMPORT_GATE_SCAN
        if (!SpxSessionState.isImportActive()) {
            handler.removeCallbacks(scanRunnable)
            hideBanner()
            return
        }
'@

    $newService = [regex]::Replace(
        $service,
        $patternScan,
        $replacementScan,
        1
    )

    if ($newService -eq $service) {
        throw "Não consegui localizar scanCurrentScreen() no SpxAccessibilityService.kt."
    }

    $service = $newService
}

# Inicialização extra de segurança no serviço.
if ($service -notmatch 'HOTFIX_V6_INIT_SESSION') {
    $patternConnected = '(?s)(override\s+fun\s+onServiceConnected\s*\(\s*\)\s*\{\s*super\.onServiceConnected\s*\(\s*\))'

    $replacementConnected = @'
$1

        // HOTFIX_V6_INIT_SESSION
        SpxSessionState.initialize(applicationContext)
'@

    $service = [regex]::Replace(
        $service,
        $patternConnected,
        $replacementConnected,
        1
    )
}

[System.IO.File]::WriteAllText(
    $servicePath,
    $service,
    [System.Text.UTF8Encoding]::new($false)
)

Write-Host "[OK] SpxAccessibilityService.kt protegido contra reabertura indevida" -ForegroundColor Green

Write-Host ""
Write-Host "HOTFIX aplicado." -ForegroundColor Green
Write-Host ""
Write-Host "Agora rode:" -ForegroundColor Yellow
Write-Host "  .\gradlew.bat clean"
Write-Host "  .\gradlew.bat assembleDebug"
Write-Host ""
