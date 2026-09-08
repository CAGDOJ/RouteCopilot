package com.routecopilot.spx

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class SpxState {
    IDLE,
    UNKNOWN,
    SERVICE_READY,
    STARTING_IMPORT,
    OPENING_SPX,
    CHECKING_SESSION,
    WAITING_CONTENT,

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
    SCANNING_PACKAGES,
    PACKAGE_DETECTED,

    VALIDATING_ROUTE,
    CALCULATING_ROUTE,
    PREPARING_ROUTE,

    IMPORT_COMPLETE,
    SYNCED,
    MONITORING,
    RETURNING_TO_COPILOT,
    ROUTE_READY,

    ERROR
}

/*
 * Compatibilidade com o MainActivity.kt e o SpxBridge.kt já existentes
 * no projeto atual.
 *
 * Inclui estados antigos e novos:
 * - UNAVAILABLE
 * - CHECKING
 * - CONNECTED
 * - SYNCED
 * - MONITORING
 * etc.
 */
enum class SpxStatus {
    IDLE,
    UNKNOWN,

    UNAVAILABLE,
    DISCONNECTED,
    CONNECTED,
    CONNECTING,
    CHECKING,
    SERVICE_READY,

    OPENING_SPX,
    CHECKING_SESSION,

    LOGIN_REQUIRED,
    AUTH_REQUIRED,
    AUTHENTICATED,

    CONSENT_REQUIRED,
    FACE_CHECK_REQUIRED,

    FINDING_ROUTE,
    ROUTE_DETECTED,
    READING_ROUTE,

    IMPORTING,
    IMPORTING_PACKAGES,
    SCANNING_PACKAGES,
    PACKAGE_DETECTED,

    SYNCING,
    SYNCED,
    MONITORING,

    IMPORT_COMPLETE,
    RETURNING_TO_COPILOT,
    ROUTE_READY,

    ERROR
}

enum class SpxSyncMode {
    IDLE,
    IMPORTING,
    MONITORING
}

data class RotaImportada(
    val at: String? = null,
    val dataCarregamento: String? = null,
    val totalEsperado: Int? = null,
    val pedidosImportados: Int = 0,
    val pedidos: Set<String> = emptySet(),
    val pronta: Boolean = false
)

object SpxSessionState {

    private var applicationContext: Context? = null

    fun initialize(context: Context) {
        applicationContext = context.applicationContext
    }

    private val _state =
        MutableStateFlow(SpxState.IDLE)

    val state: StateFlow<SpxState> =
        _state.asStateFlow()

    private val _status =
        MutableStateFlow(SpxStatus.IDLE)

    val status: StateFlow<SpxStatus> =
        _status.asStateFlow()

    private val _syncMode =
        MutableStateFlow(SpxSyncMode.IDLE)

    val syncMode: StateFlow<SpxSyncMode> =
        _syncMode.asStateFlow()

    private val _message =
        MutableStateFlow("Pronto para iniciar")

    val message: StateFlow<String> =
        _message.asStateFlow()

    val statusMessage: StateFlow<String> =
        _message.asStateFlow()

    private val _packageName =
        MutableStateFlow<String?>(null)

    val packageName: StateFlow<String?> =
        _packageName.asStateFlow()

    private val _atCode =
        MutableStateFlow<String?>(null)

    val atCode: StateFlow<String?> =
        _atCode.asStateFlow()

    private val _date =
        MutableStateFlow<String?>(null)

    val date: StateFlow<String?> =
        _date.asStateFlow()

    val dataCarregamento: StateFlow<String?> =
        _date.asStateFlow()

    private val _expectedTotal =
        MutableStateFlow<Int?>(null)

    val expectedTotal: StateFlow<Int?> =
        _expectedTotal.asStateFlow()

    val totalEsperado: StateFlow<Int?> =
        _expectedTotal.asStateFlow()

    private val _brCode =
        MutableStateFlow<String?>(null)

    val brCode: StateFlow<String?> =
        _brCode.asStateFlow()

    private val _packageCodes =
        MutableStateFlow<Set<String>>(emptySet())

    val packageCodes: StateFlow<Set<String>> =
        _packageCodes.asStateFlow()

    private val _packageCount =
        MutableStateFlow(0)

    val packageCount: StateFlow<Int> =
        _packageCount.asStateFlow()

    private val _syncActive =
        MutableStateFlow(false)

    val syncActive: StateFlow<Boolean> =
        _syncActive.asStateFlow()

    private val _routeReady =
        MutableStateFlow(false)

    val routeReady: StateFlow<Boolean> =
        _routeReady.asStateFlow()

    private val _lastSyncAt =
        MutableStateFlow<Long?>(null)

    val lastSyncAt: StateFlow<Long?> =
        _lastSyncAt.asStateFlow()

    fun update(status: SpxStatus) {
        _status.value = status
        sincronizarStateAPartirDoStatus(status)
    }

    fun update(
        status: SpxStatus,
        message: String
    ) {
        _status.value = status
        _message.value = message
        sincronizarStateAPartirDoStatus(status)
    }

    fun updateState(state: SpxState) {
        _state.value = state
        sincronizarStatusAPartirDoState(state)
    }

    fun updateState(
        state: SpxState,
        message: String
    ) {
        _state.value = state
        _message.value = message
        sincronizarStatusAPartirDoState(state)
    }

    fun updateSyncMode(mode: SpxSyncMode) {
        _syncMode.value = mode

        when (mode) {
            SpxSyncMode.IDLE ->
                if (
                    _status.value == SpxStatus.MONITORING ||
                    _status.value == SpxStatus.SYNCING
                ) {
                    _status.value = SpxStatus.IDLE
                }

            SpxSyncMode.IMPORTING ->
                _status.value = SpxStatus.SYNCING

            SpxSyncMode.MONITORING ->
                _status.value = SpxStatus.MONITORING
        }
    }

    fun updateMessage(message: String) {
        _message.value = message
    }

    fun updatePackageName(value: String) {
        _packageName.value = value
    }

    fun updateAtCode(value: String?) {
        if (!value.isNullOrBlank()) {
            _atCode.value =
                value.trim().uppercase()
        }
    }

    fun updateDataCarregamento(value: String?) {
        if (!value.isNullOrBlank()) {
            _date.value = value
        }
    }

    fun updateDate(value: String?) {
        updateDataCarregamento(value)
    }

    fun updateTotalEsperado(value: Int?) {
        if (
            value == null ||
            value <= 0
        ) {
            return
        }

        val atual =
            _expectedTotal.value

        if (
            atual == null ||
            value > atual
        ) {
            _expectedTotal.value = value
        }
    }

    fun updateExpectedTotal(value: Int?) {
        updateTotalEsperado(value)
    }

    fun updateBrCode(value: String?) {
        if (!value.isNullOrBlank()) {
            _brCode.value =
                value.trim().uppercase()
        }
    }

    fun addPackageCode(value: String): Boolean {

        val code =
            value.trim().uppercase()

        if (code.isBlank()) {
            return false
        }

        val atual =
            _packageCodes.value

        if (code in atual) {
            _brCode.value = code
            return false
        }

        val novo =
            LinkedHashSet<String>().apply {
                addAll(atual)
                add(code)
            }

        _packageCodes.value = novo
        _packageCount.value = novo.size
        _brCode.value = code

        return true
    }

    fun addPackageCodes(
        values: Collection<String>
    ): Int {

        var novos = 0

        values.forEach {
            if (addPackageCode(it)) {
                novos++
            }
        }

        return novos
    }

    fun begin() {
        beginImport()
    }

    fun beginImport() {

        limparDadosDaRota()

        _syncActive.value = true
        _routeReady.value = false

        _syncMode.value =
            SpxSyncMode.IMPORTING

        _state.value =
            SpxState.STARTING_IMPORT

        _status.value =
            SpxStatus.SYNCING

        _message.value =
            "Preparando sincronização..."
    }

    fun startImport() {
        beginImport()

        updateState(
            SpxState.OPENING_SPX,
            "Abrindo SPX..."
        )
    }

    fun markSynced() {

        _state.value =
            SpxState.SYNCED

        _status.value =
            SpxStatus.SYNCED

        _syncMode.value =
            SpxSyncMode.MONITORING

        _syncActive.value =
            true

        _lastSyncAt.value =
            System.currentTimeMillis()

        _message.value =
            "Rota sincronizada. Monitorando SPX."
    }

    fun markMonitoring() {

        _state.value =
            SpxState.MONITORING

        _status.value =
            SpxStatus.MONITORING

        _syncMode.value =
            SpxSyncMode.MONITORING

        _syncActive.value =
            true

        _lastSyncAt.value =
            System.currentTimeMillis()

        _message.value =
            "SPX monitorado."
    }

    fun markImportComplete() {

        _state.value =
            SpxState.IMPORT_COMPLETE

        _status.value =
            SpxStatus.IMPORT_COMPLETE

        _routeReady.value =
            true

        _lastSyncAt.value =
            System.currentTimeMillis()
    }

    fun markRouteReady() {

        _state.value =
            SpxState.ROUTE_READY

        _status.value =
            SpxStatus.ROUTE_READY

        _routeReady.value =
            true

        _syncMode.value =
            SpxSyncMode.MONITORING
    }

    fun touchSync() {
        _lastSyncAt.value =
            System.currentTimeMillis()
    }

    fun fail(message: String) {

        _state.value =
            SpxState.ERROR

        _status.value =
            SpxStatus.ERROR

        _message.value =
            message

        _syncActive.value =
            false
    }

    fun getRotaAtual(): RotaImportada {

        return RotaImportada(
            at = _atCode.value,
            dataCarregamento = _date.value,
            totalEsperado = _expectedTotal.value,
            pedidosImportados = _packageCount.value,
            pedidos = _packageCodes.value,
            pronta = _routeReady.value
        )
    }

    fun reset() {

        limparDadosDaRota()

        _syncActive.value = false
        _routeReady.value = false
        _syncMode.value = SpxSyncMode.IDLE
        _state.value = SpxState.IDLE
        _status.value = SpxStatus.IDLE
        _message.value = "Pronto para iniciar"
        _lastSyncAt.value = null
    }

    fun resetRoute() {
        reset()
    }

    private fun limparDadosDaRota() {

        _packageName.value = null
        _atCode.value = null
        _date.value = null
        _expectedTotal.value = null
        _brCode.value = null
        _packageCodes.value = emptySet()
        _packageCount.value = 0
    }

    private fun sincronizarStatusAPartirDoState(
        state: SpxState
    ) {

        _status.value =
            when (state) {

                SpxState.IDLE,
                SpxState.UNKNOWN ->
                    SpxStatus.IDLE

                SpxState.SERVICE_READY ->
                    SpxStatus.SERVICE_READY

                SpxState.STARTING_IMPORT ->
                    SpxStatus.SYNCING

                SpxState.OPENING_SPX ->
                    SpxStatus.OPENING_SPX

                SpxState.CHECKING_SESSION,
                SpxState.WAITING_CONTENT ->
                    SpxStatus.CHECKING

                SpxState.LOGIN_REQUIRED ->
                    SpxStatus.LOGIN_REQUIRED

                SpxState.AUTHENTICATED ->
                    SpxStatus.AUTHENTICATED

                SpxState.CONSENT_REQUIRED ->
                    SpxStatus.CONSENT_REQUIRED

                SpxState.FACE_CHECK_REQUIRED ->
                    SpxStatus.FACE_CHECK_REQUIRED

                SpxState.FINDING_ROUTE ->
                    SpxStatus.FINDING_ROUTE

                SpxState.ROUTE_DETECTED ->
                    SpxStatus.ROUTE_DETECTED

                SpxState.READING_ROUTE,
                SpxState.OPENING_DELIVERIES,
                SpxState.OPENING_IN_ROUTE,
                SpxState.FINDING_DOWNLOAD_BUTTON,
                SpxState.DOWNLOAD_BUTTON_FOUND,
                SpxState.WAITING_ROUTE_DOWNLOAD,
                SpxState.DOWNLOADING_ROUTE ->
                    SpxStatus.READING_ROUTE

                SpxState.IMPORTING_PACKAGES,
                SpxState.SCANNING_PACKAGES ->
                    SpxStatus.SCANNING_PACKAGES

                SpxState.PACKAGE_DETECTED ->
                    SpxStatus.PACKAGE_DETECTED

                SpxState.VALIDATING_ROUTE,
                SpxState.CALCULATING_ROUTE,
                SpxState.PREPARING_ROUTE ->
                    SpxStatus.SYNCING

                SpxState.IMPORT_COMPLETE ->
                    SpxStatus.IMPORT_COMPLETE

                SpxState.SYNCED ->
                    SpxStatus.SYNCED

                SpxState.MONITORING ->
                    SpxStatus.MONITORING

                SpxState.RETURNING_TO_COPILOT ->
                    SpxStatus.RETURNING_TO_COPILOT

                SpxState.ROUTE_READY ->
                    SpxStatus.ROUTE_READY

                SpxState.ERROR ->
                    SpxStatus.ERROR
            }
    }

    private fun sincronizarStateAPartirDoStatus(
        status: SpxStatus
    ) {

        _state.value =
            when (status) {

                SpxStatus.IDLE,
                SpxStatus.UNKNOWN,
                SpxStatus.UNAVAILABLE,
                SpxStatus.DISCONNECTED ->
                    SpxState.IDLE

                SpxStatus.CONNECTED,
                SpxStatus.CONNECTING,
                SpxStatus.SERVICE_READY ->
                    SpxState.SERVICE_READY

                SpxStatus.CHECKING ->
                    SpxState.CHECKING_SESSION

                SpxStatus.OPENING_SPX ->
                    SpxState.OPENING_SPX

                SpxStatus.CHECKING_SESSION ->
                    SpxState.CHECKING_SESSION

                SpxStatus.LOGIN_REQUIRED,
                SpxStatus.AUTH_REQUIRED ->
                    SpxState.LOGIN_REQUIRED

                SpxStatus.AUTHENTICATED ->
                    SpxState.AUTHENTICATED

                SpxStatus.CONSENT_REQUIRED ->
                    SpxState.CONSENT_REQUIRED

                SpxStatus.FACE_CHECK_REQUIRED ->
                    SpxState.FACE_CHECK_REQUIRED

                SpxStatus.FINDING_ROUTE ->
                    SpxState.FINDING_ROUTE

                SpxStatus.ROUTE_DETECTED ->
                    SpxState.ROUTE_DETECTED

                SpxStatus.READING_ROUTE ->
                    SpxState.READING_ROUTE

                SpxStatus.IMPORTING,
                SpxStatus.IMPORTING_PACKAGES,
                SpxStatus.SCANNING_PACKAGES,
                SpxStatus.SYNCING ->
                    SpxState.SCANNING_PACKAGES

                SpxStatus.PACKAGE_DETECTED ->
                    SpxState.PACKAGE_DETECTED

                SpxStatus.SYNCED ->
                    SpxState.SYNCED

                SpxStatus.MONITORING ->
                    SpxState.MONITORING

                SpxStatus.IMPORT_COMPLETE ->
                    SpxState.IMPORT_COMPLETE

                SpxStatus.RETURNING_TO_COPILOT ->
                    SpxState.RETURNING_TO_COPILOT

                SpxStatus.ROUTE_READY ->
                    SpxState.ROUTE_READY

                SpxStatus.ERROR ->
                    SpxState.ERROR
            }
    }
}
