package com.routecopilot.spx

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class SpxState { UNKNOWN, OPENING_SPX, LOGIN_REQUIRED, FINDING_ROUTE, ROUTE_DETECTED, SCANNING_PACKAGES, IMPORT_COMPLETE, RETURNING_TO_COPILOT, ROUTE_READY }
enum class SpxAutomationMode { NONE, IMPORT_ROUTE, OUT_OF_ROUTE }
enum class OccurrencePhase { IDLE, SEARCHING_ORDER, OPENING_ORDER, OPENING_OCCURRENCE, SELECTING_REASON, REASON_SELECTED, OPENING_EVIDENCE, WAITING_PHOTO, INVALID_PHOTO, CONFIRMING, DONE, ERROR }

object SpxSessionState {
    private val _state = MutableStateFlow(SpxState.UNKNOWN); val state = _state.asStateFlow()
    private val _mode = MutableStateFlow(SpxAutomationMode.NONE); val mode = _mode.asStateFlow()
    private val _message = MutableStateFlow("Aguardando SPX"); val message = _message.asStateFlow()
    private val _at = MutableStateFlow<String?>(null); val at = _at.asStateFlow()
    private val _date = MutableStateFlow<String?>(null); val date = _date.asStateFlow()
    private val _expected = MutableStateFlow<Int?>(null); val expected = _expected.asStateFlow()
    private val _brs = MutableStateFlow<Set<String>>(emptySet()); val brs = _brs.asStateFlow()
    private val _targetBr = MutableStateFlow<String?>(null); val targetBr = _targetBr.asStateFlow()
    private val _occurrence = MutableStateFlow(OccurrencePhase.IDLE); val occurrence = _occurrence.asStateFlow()

    fun startImport() { _mode.value = SpxAutomationMode.IMPORT_ROUTE; _state.value = SpxState.OPENING_SPX; _message.value = "Abrindo SPX..."; _at.value = null; _date.value = null; _expected.value = null; _brs.value = emptySet() }
    fun setState(s: SpxState, m: String) { _state.value = s; _message.value = m }
    fun setAt(v: String) { _at.value = v.uppercase() }
    fun setDate(v: String?) { _date.value = v }
    fun setExpected(v: Int?) { if (v != null && v > 0) _expected.value = v }
    fun addBrs(values: Collection<String>): Int { val before = _brs.value.size; _brs.value = LinkedHashSet(_brs.value).apply { addAll(values.map { it.uppercase() }) }; return _brs.value.size - before }
    fun finishImport() { _mode.value = SpxAutomationMode.NONE; _state.value = SpxState.ROUTE_READY; _message.value = "Rota pronta." }
    fun startOutOfRoute(br: String? = null) { _mode.value = SpxAutomationMode.OUT_OF_ROUTE; _targetBr.value = br?.uppercase(); _occurrence.value = if (br == null) OccurrencePhase.OPENING_ORDER else OccurrencePhase.SEARCHING_ORDER }
    fun setOccurrence(p: OccurrencePhase) { _occurrence.value = p }
    fun finishOutOfRoute() { _occurrence.value = OccurrencePhase.DONE; _mode.value = SpxAutomationMode.NONE; _targetBr.value = null }
}
