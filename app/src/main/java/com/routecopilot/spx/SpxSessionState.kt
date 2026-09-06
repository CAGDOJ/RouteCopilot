package com.routecopilot.spx

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class SpxState {
    IDLE, STARTING_IMPORT, OPENING_SPX, CHECKING_SESSION,
    LOGIN_REQUIRED, CONSENT_REQUIRED, FACE_CHECK_REQUIRED,
    AUTHENTICATED, FINDING_DOWNLOAD_BUTTON, DOWNLOAD_BUTTON_FOUND,
    DOWNLOADING_ROUTE, WAITING_ROUTE_DOWNLOAD, ROUTE_DOWNLOADED,
    OPENING_DELIVERIES, OPENING_IN_ROUTE, READING_ROUTE,
    VALIDATING_ROUTE, CALCULATING_ROUTE, IMPORT_COMPLETE,
    RETURNING_TO_COPILOT, ROUTE_READY, ERROR
}

data class RotaImportada(
    val at: String? = null,
    val dataCarregamento: String? = null,
    val totalEsperado: Int? = null,
    val pedidosImportados: Int = 0,
    val pedidos: Set<String> = emptySet()
)

object SpxSessionState {
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

    fun beginImport() {
        _atCode.value = null
        _dataCarregamento.value = null
        _totalEsperado.value = null
        _packageCodes.value = emptySet()
        _packageCount.value = 0
        _state.value = SpxState.STARTING_IMPORT
        _statusMessage.value = "Preparando sincronização..."
    }

    fun updateState(state: SpxState, message: String? = null) {
        _state.value = state
        if (!message.isNullOrBlank()) _statusMessage.value = message
    }

    fun updateAtCode(at: String?) {
        if (!at.isNullOrBlank()) _atCode.value = at.trim().uppercase()
    }

    fun updateDataCarregamento(data: String?) {
        if (!data.isNullOrBlank()) _dataCarregamento.value = data.trim()
    }

    fun updateTotalEsperado(total: Int?) {
        if (total == null || total <= 0) return
        val atual = _totalEsperado.value
        if (atual == null || total > atual) _totalEsperado.value = total
    }

    fun addPackageCodes(codes: Collection<String>): Int {
        if (codes.isEmpty()) return 0
        val atual = LinkedHashSet(_packageCodes.value)
        val antes = atual.size
        codes.forEach { raw ->
            val code = raw.trim().uppercase()
            if (code.startsWith("BR") && code.length >= 10) atual.add(code)
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

    fun fail(message: String) {
        _state.value = SpxState.ERROR
        _statusMessage.value = message
    }

    fun reset() {
        _state.value = SpxState.IDLE
        _statusMessage.value = "Pronto para iniciar"
        _atCode.value = null
        _dataCarregamento.value = null
        _totalEsperado.value = null
        _packageCodes.value = emptySet()
        _packageCount.value = 0
    }
}
