package com.routecopilot.spx

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class SpxState {
    UNKNOWN,
    OPENING_SPX,
    CHECKING_SESSION,
    LOGIN_REQUIRED,
    AUTHENTICATED,
    FINDING_ROUTE,
    ROUTE_DETECTED,
    SCANNING_PACKAGES,
    WAITING_PHOTO,
    IMPORT_COMPLETE,
    RETURNING_TO_COPILOT,
    ROUTE_READY
}

enum class SpxAutomationMode {
    NONE,
    IMPORT_ROUTE,
    OUT_OF_ROUTE
}

enum class OccurrencePhase {
    IDLE,
    SEARCHING_ORDER,
    OPENING_ORDER,
    OPENING_OCCURRENCE,
    SELECTING_REASON,
    REASON_SELECTED,
    OPENING_EVIDENCE,
    WAITING_PHOTO,
    INVALID_PHOTO,
    CONFIRMING,
    DONE,
    ERROR
}

data class SpxPackage(
    val br: String,
    val address: String? = null,
    val recipient: String? = null,
    val phone: String? = null
)

object SpxSessionState {
    private val _state =
        MutableStateFlow(
            SpxState.UNKNOWN
        )

    val state =
        _state.asStateFlow()

    private val _automationMode =
        MutableStateFlow(
            SpxAutomationMode.NONE
        )

    val automationMode =
        _automationMode.asStateFlow()

    val mode = automationMode

    private val _statusMessage =
        MutableStateFlow(
            "Aguardando SPX"
        )

    val statusMessage =
        _statusMessage.asStateFlow()

    val message = statusMessage

    private val _atCode =
        MutableStateFlow<String?>(
            null
        )

    val atCode =
        _atCode.asStateFlow()

    val at = atCode

    private val _dataCarregamento =
        MutableStateFlow<String?>(
            null
        )

    val dataCarregamento =
        _dataCarregamento.asStateFlow()

    val loadDate =
        dataCarregamento

    private val _totalEsperado =
        MutableStateFlow<Int?>(
            null
        )

    val totalEsperado =
        _totalEsperado.asStateFlow()

    val expectedTotal =
        totalEsperado

    private val _packages =
        MutableStateFlow<
            Map<String, SpxPackage>
        >(
            emptyMap()
        )

    val packages =
        _packages.asStateFlow()

    private val _packageCount =
        MutableStateFlow(0)

    val packageCount =
        _packageCount.asStateFlow()

    private val _occurrencePhase =
        MutableStateFlow(
            OccurrencePhase.IDLE
        )

    val occurrencePhase =
        _occurrencePhase.asStateFlow()

    private val _targetBr =
        MutableStateFlow<String?>(
            null
        )

    val targetBr =
        _targetBr.asStateFlow()

    val brs =
        kotlinx.coroutines.flow.MutableStateFlow(
            emptySet<String>()
        )

    fun startImport() {
        _state.value =
            SpxState.OPENING_SPX

        _automationMode.value =
            SpxAutomationMode.IMPORT_ROUTE

        _statusMessage.value =
            "Abrindo SPX..."

        _atCode.value =
            null

        _dataCarregamento.value =
            null

        _totalEsperado.value =
            null

        _packages.value =
            emptyMap()

        _packageCount.value =
            0

        brs.value =
            emptySet()

        _occurrencePhase.value =
            OccurrencePhase.IDLE

        _targetBr.value =
            null
    }

    fun resetRoute() {
        startImport()
    }

    fun updateState(
        value: SpxState
    ) {
        _state.value =
            value
    }

    fun updateMessage(
        value: String
    ) {
        _statusMessage.value =
            value
    }

    fun setState(
        value: SpxState,
        message: String
    ) {
        _state.value =
            value

        _statusMessage.value =
            message
    }

    fun setAt(
        value: String
    ) {
        _atCode.value =
            value.uppercase()
    }

    fun updateAtCode(
        value: String?
    ) {
        if (
            !value.isNullOrBlank()
        ) {
            setAt(value)
        }
    }

    fun setLoadDate(
        value: String?
    ) {
        _dataCarregamento.value =
            value
    }

    fun updateDataCarregamento(
        value: String?
    ) {
        setLoadDate(value)
    }

    fun setExpectedTotal(
        value: Int?
    ) {
        if (
            value != null &&
            value > 0
        ) {
            _totalEsperado.value =
                value
        }
    }

    fun updateTotalEsperado(
        value: Int?
    ) {
        setExpectedTotal(value)
    }

    fun addPackages(
        values: Collection<SpxPackage>
    ): Int {
        val before =
            _packages.value.size

        val map =
            LinkedHashMap(
                _packages.value
            )

        values.forEach { item ->
            map[item.br.uppercase()] =
                item.copy(
                    br =
                        item.br.uppercase()
                )
        }

        _packages.value =
            map

        _packageCount.value =
            map.size

        brs.value =
            map.keys

        return map.size - before
    }

    fun addBrs(
        values: Collection<String>
    ): Int {
        return addPackages(
            values.map { br ->
                SpxPackage(
                    br = br
                )
            }
        )
    }

    fun startOutOfRoute(
        br: String? = null
    ) {
        _automationMode.value =
            SpxAutomationMode.OUT_OF_ROUTE

        _targetBr.value =
            br?.uppercase()

        _occurrencePhase.value =
            if (
                br == null
            ) {
                OccurrencePhase.OPENING_ORDER
            } else {
                OccurrencePhase.SEARCHING_ORDER
            }
    }

    fun requestOutOfRoute(
        br: String? = null
    ) {
        startOutOfRoute(br)
    }

    fun setOccurrencePhase(
        phase: OccurrencePhase
    ) {
        _occurrencePhase.value =
            phase
    }

    fun finishOutOfRoute() {
        _occurrencePhase.value =
            OccurrencePhase.DONE

        _automationMode.value =
            SpxAutomationMode.NONE

        _targetBr.value =
            null
    }

    fun finishImport() {
        _state.value =
            SpxState.ROUTE_READY

        _automationMode.value =
            SpxAutomationMode.NONE

        _statusMessage.value =
            "Rota pronta."
    }
}
