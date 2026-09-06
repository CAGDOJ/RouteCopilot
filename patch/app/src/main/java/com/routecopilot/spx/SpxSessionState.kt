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
    AUTHENTICATED,
    CONSENT_REQUIRED,
    FACE_CHECK_REQUIRED,

    FINDING_ROUTE,
    ROUTE_DETECTED,

    FINDING_DOWNLOAD_BUTTON,
    DOWNLOAD_BUTTON_FOUND,
    WAITING_ROUTE_DOWNLOAD,
    DOWNLOADING_ROUTE,

    OPENING_DELIVERIES,
    OPENING_IN_ROUTE,
    READING_ROUTE,

    IMPORTING_PACKAGES,
    PACKAGE_DETECTED,
    WAITING_CONTENT,

    SYNCING_OCCURRENCES,
    SYNCING_CLOSED,

    VALIDATING_ROUTE,
    CALCULATING_ROUTE,
    PREPARING_ROUTE,

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
    val pedidos: Set<String> = emptySet(),
    val ocorrencias: Int = 0,
    val encerrados: Int = 0,
    val pronta: Boolean = false
)

object SpxSessionState {

    private const val PREFS = "routecopilot_spx_state"

    private lateinit var appContext: Context
    private var initialized = false

    private val _state = MutableStateFlow(SpxState.IDLE)
    val state: StateFlow<SpxState> = _state.asStateFlow()

    private val _message = MutableStateFlow("Pronto para iniciar")
    val message: StateFlow<String> = _message.asStateFlow()
    val statusMessage: StateFlow<String> = _message.asStateFlow()

    private val _packageName = MutableStateFlow<String?>(null)
    val packageName: StateFlow<String?> = _packageName.asStateFlow()

    private val _atCode = MutableStateFlow<String?>(null)
    val atCode: StateFlow<String?> = _atCode.asStateFlow()

    private val _date = MutableStateFlow<String?>(null)
    val date: StateFlow<String?> = _date.asStateFlow()
    val dataCarregamento: StateFlow<String?> = _date.asStateFlow()

    private val _expectedTotal = MutableStateFlow<Int?>(null)
    val expectedTotal: StateFlow<Int?> = _expectedTotal.asStateFlow()
    val totalEsperado: StateFlow<Int?> = _expectedTotal.asStateFlow()

    private val _brCode = MutableStateFlow<String?>(null)
    val brCode: StateFlow<String?> = _brCode.asStateFlow()

    private val _packageCodes = MutableStateFlow<Set<String>>(emptySet())
    val packageCodes: StateFlow<Set<String>> = _packageCodes.asStateFlow()

    private val _packageCount = MutableStateFlow(0)
    val packageCount: StateFlow<Int> = _packageCount.asStateFlow()

    /*
     * Estes dois números vêm do SPX:
     * - ocorrências = pedidos que estão em Ocorrência;
     * - encerrados = todos os pedidos finalizados naquele dia.
     */
    private val _occurrenceCount = MutableStateFlow(0)
    val occurrenceCount: StateFlow<Int> = _occurrenceCount.asStateFlow()

    private val _closedCount = MutableStateFlow(0)
    val closedCount: StateFlow<Int> = _closedCount.asStateFlow()

    private val _occurrenceDescriptions =
        MutableStateFlow<List<String>>(emptyList())

    val occurrenceDescriptions: StateFlow<List<String>> =
        _occurrenceDescriptions.asStateFlow()

    private val _syncActive = MutableStateFlow(false)
    val syncActive: StateFlow<Boolean> = _syncActive.asStateFlow()

    private val _routeReady = MutableStateFlow(false)
    val routeReady: StateFlow<Boolean> = _routeReady.asStateFlow()

    private val _sessionId = MutableStateFlow(0L)
    val sessionId: StateFlow<Long> = _sessionId.asStateFlow()

    fun initialize(context: Context) {
        if (initialized) return

        appContext = context.applicationContext
        initialized = true
        restore()
    }

    private fun prefs() =
        appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun restore() {
        val p = prefs()

        _state.value = runCatching {
            SpxState.valueOf(
                p.getString("state", SpxState.IDLE.name)
                    ?: SpxState.IDLE.name
            )
        }.getOrDefault(SpxState.IDLE)

        _message.value =
            p.getString("message", "Pronto para iniciar")
                ?: "Pronto para iniciar"

        _packageName.value = p.getString("packageName", null)
        _atCode.value = p.getString("at", null)
        _date.value = p.getString("date", null)

        val total = p.getInt("expectedTotal", -1)
        _expectedTotal.value = total.takeIf { it > 0 }

        val codes =
            p.getStringSet("packageCodes", emptySet())
                ?.toSet()
                ?: emptySet()

        _packageCodes.value = codes
        _packageCount.value = codes.size
        _brCode.value = p.getString("brCode", null)

        _occurrenceCount.value =
            p.getInt("occurrenceCount", 0)

        _closedCount.value =
            p.getInt("closedCount", 0)

        _occurrenceDescriptions.value =
            p.getStringSet(
                "occurrenceDescriptions",
                emptySet()
            )?.toList()
                ?: emptyList()

        _syncActive.value =
            p.getBoolean("syncActive", false)

        _routeReady.value =
            p.getBoolean("routeReady", false)

        _sessionId.value =
            p.getLong("sessionId", 0L)

        if (_routeReady.value) {
            _state.value = SpxState.ROUTE_READY
            _syncActive.value = false
        }
    }

    fun begin() = beginImport()

    fun beginImport() {
        ensureInitialized()
        clearRouteData()

        _sessionId.value =
            System.currentTimeMillis()

        _syncActive.value =
            true

        _routeReady.value =
            false

        _state.value =
            SpxState.STARTING_IMPORT

        _message.value =
            "Preparando importação..."

        persist()
    }

    fun reset() {
        ensureInitialized()
        clearRouteData()

        _syncActive.value =
            false

        _routeReady.value =
            false

        _state.value =
            SpxState.IDLE

        _message.value =
            "Pronto para iniciar"

        persist()
    }

    fun resetRoute() = reset()

    private fun clearRouteData() {
        _packageName.value = null
        _atCode.value = null
        _date.value = null
        _expectedTotal.value = null
        _brCode.value = null
        _packageCodes.value = emptySet()
        _packageCount.value = 0

        _occurrenceCount.value = 0
        _closedCount.value = 0
        _occurrenceDescriptions.value = emptyList()
    }

    fun update(
        state: SpxState,
        message: String
    ) = updateState(state, message)

    fun updateState(state: SpxState) {
        ensureInitialized()
        _state.value = state
        updateFlags(state)
        persist()
    }

    fun updateState(
        state: SpxState,
        message: String
    ) {
        ensureInitialized()
        _state.value = state
        _message.value = message
        updateFlags(state)
        persist()
    }

    fun updateMessage(message: String) {
        ensureInitialized()
        _message.value = message
        persist()
    }

    private fun updateFlags(state: SpxState) {
        when (state) {
            SpxState.IDLE ->
                _syncActive.value = false

            SpxState.ROUTE_READY -> {
                _syncActive.value = false
                _routeReady.value = true
            }

            SpxState.ERROR ->
                _syncActive.value = false

            else ->
                _syncActive.value = true
        }
    }

    fun updatePackageName(packageName: String?) {
        ensureInitialized()
        if (packageName.isNullOrBlank()) return

        _packageName.value =
            packageName.trim()

        persist()
    }

    fun setAt(at: String?) {
        ensureInitialized()
        if (at.isNullOrBlank()) return

        _atCode.value =
            at.trim().uppercase()

        persist()
    }

    fun updateAtCode(at: String?) = setAt(at)

    fun setDate(date: String?) {
        ensureInitialized()
        if (date.isNullOrBlank()) return

        _date.value =
            date.trim()

        persist()
    }

    fun updateDataCarregamento(date: String?) =
        setDate(date)

    fun setExpectedTotal(total: Int?) {
        ensureInitialized()
        if (total == null || total <= 0) return

        val old = _expectedTotal.value

        if (
            old == null ||
            total >= old
        ) {
            _expectedTotal.value =
                total

            persist()
        }
    }

    fun updateTotalEsperado(total: Int?) =
        setExpectedTotal(total)

    fun setOccurrenceCount(count: Int?) {
        ensureInitialized()
        if (count == null || count < 0) return

        _occurrenceCount.value =
            count

        persist()
    }

    fun setClosedCount(count: Int?) {
        ensureInitialized()
        if (count == null || count < 0) return

        _closedCount.value =
            count

        persist()
    }

    fun addOccurrenceDescriptions(
        descriptions: Collection<String>
    ) {
        ensureInitialized()

        val set =
            LinkedHashSet(
                _occurrenceDescriptions.value
            )

        descriptions
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .forEach(set::add)

        _occurrenceDescriptions.value =
            set.toList()

        persist()
    }

    fun addPackageCode(
        rawCode: String
    ): Boolean {
        ensureInitialized()

        val code =
            rawCode
                .trim()
                .uppercase()

        if (code.isBlank()) return false

        val set =
            LinkedHashSet(
                _packageCodes.value
            )

        if (!set.add(code)) return false

        _packageCodes.value =
            set

        _packageCount.value =
            set.size

        _brCode.value =
            code

        persist()

        return true
    }

    fun addPackageCodes(
        codes: Collection<String>
    ): Int {
        var added = 0

        codes.forEach {
            if (addPackageCode(it)) {
                added++
            }
        }

        return added
    }

    fun getRotaAtual() =
        RotaImportada(
            at =
                _atCode.value,

            dataCarregamento =
                _date.value,

            totalEsperado =
                _expectedTotal.value,

            pedidosImportados =
                _packageCount.value,

            pedidos =
                _packageCodes.value,

            ocorrencias =
                _occurrenceCount.value,

            encerrados =
                _closedCount.value,

            pronta =
                _routeReady.value
        )

    fun markImportComplete() =
        updateState(
            SpxState.IMPORT_COMPLETE,
            "Importação concluída."
        )

    fun markRouteReady() {
        ensureInitialized()

        _state.value =
            SpxState.ROUTE_READY

        _message.value =
            "Rota pronta."

        _syncActive.value =
            false

        _routeReady.value =
            true

        persist()
    }

    fun cancelSync() {
        ensureInitialized()
        _syncActive.value = false

        if (!_routeReady.value) {
            _state.value =
                SpxState.IDLE

            _message.value =
                "Sincronização cancelada."
        }

        persist()
    }

    fun fail(message: String) {
        ensureInitialized()

        _state.value =
            SpxState.ERROR

        _message.value =
            message

        _syncActive.value =
            false

        persist()
    }

    private fun persist() {
        if (!initialized) return

        prefs()
            .edit()
            .putString(
                "state",
                _state.value.name
            )
            .putString(
                "message",
                _message.value
            )
            .putString(
                "packageName",
                _packageName.value
            )
            .putString(
                "at",
                _atCode.value
            )
            .putString(
                "date",
                _date.value
            )
            .putInt(
                "expectedTotal",
                _expectedTotal.value
                    ?: -1
            )
            .putString(
                "brCode",
                _brCode.value
            )
            .putStringSet(
                "packageCodes",
                HashSet(
                    _packageCodes.value
                )
            )
            .putInt(
                "occurrenceCount",
                _occurrenceCount.value
            )
            .putInt(
                "closedCount",
                _closedCount.value
            )
            .putStringSet(
                "occurrenceDescriptions",
                HashSet(
                    _occurrenceDescriptions.value
                )
            )
            .putBoolean(
                "syncActive",
                _syncActive.value
            )
            .putBoolean(
                "routeReady",
                _routeReady.value
            )
            .putLong(
                "sessionId",
                _sessionId.value
            )
            .apply()
    }

    private fun ensureInitialized() {
        check(initialized) {
            "SpxSessionState.initialize(context) precisa ser chamado antes de usar o estado."
        }
    }
}
