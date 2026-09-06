package com.routecopilot.spx

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class SpxState {
    IDLE,
    STARTING_IMPORT,
    CHECKING_SESSION,
    LOGIN_REQUIRED,
    CONSENT_REQUIRED,
    FACE_CHECK_REQUIRED,
    FINDING_ROUTE,
    ROUTE_DETECTED,
    READING_ROUTE,
    IMPORTING_PACKAGES,
    IMPORT_COMPLETE,
    RETURNING_TO_COPILOT,
    ROUTE_READY,
    ERROR
}

object SpxSessionState {

    private val _state = MutableStateFlow(SpxState.IDLE)
    val state: StateFlow<SpxState> = _state.asStateFlow()

    private val _message = MutableStateFlow("Pronto para iniciar")
    val message: StateFlow<String> = _message.asStateFlow()

    private val _atCode = MutableStateFlow<String?>(null)
    val atCode: StateFlow<String?> = _atCode.asStateFlow()

    private val _date = MutableStateFlow<String?>(null)
    val date: StateFlow<String?> = _date.asStateFlow()

    private val _expectedTotal = MutableStateFlow<Int?>(null)
    val expectedTotal: StateFlow<Int?> = _expectedTotal.asStateFlow()

    private val _packageCodes = MutableStateFlow<Set<String>>(emptySet())
    val packageCodes: StateFlow<Set<String>> = _packageCodes.asStateFlow()

    private val _packageCount = MutableStateFlow(0)
    val packageCount: StateFlow<Int> = _packageCount.asStateFlow()

    fun begin() {
        _state.value = SpxState.STARTING_IMPORT
        _message.value = "Abrindo SPX..."
        _atCode.value = null
        _date.value = null
        _expectedTotal.value = null
        _packageCodes.value = emptySet()
        _packageCount.value = 0
    }

    fun update(state: SpxState, message: String) {
        _state.value = state
        _message.value = message
    }

    fun setAt(at: String?) {
        if (!at.isNullOrBlank()) _atCode.value = at.trim().uppercase()
    }

    fun setDate(date: String?) {
        if (!date.isNullOrBlank()) _date.value = date
    }

    fun setExpectedTotal(total: Int?) {
        if (total == null || total <= 0) return
        val old = _expectedTotal.value
        if (old == null || total >= old) _expectedTotal.value = total
    }

    fun addPackageCodes(codes: Collection<String>): Int {
        val set = LinkedHashSet(_packageCodes.value)
        var added = 0
        codes.forEach { raw ->
            val code = raw.trim().uppercase()
            if (code.isNotBlank() && set.add(code)) added++
        }
        if (added > 0) {
            _packageCodes.value = set
            _packageCount.value = set.size
        }
        return added
    }

    fun fail(message: String) {
        _state.value = SpxState.ERROR
        _message.value = message
    }
}
