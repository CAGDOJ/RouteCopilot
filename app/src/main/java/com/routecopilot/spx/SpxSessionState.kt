package com.routecopilot.spx

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class SpxState {
    UNKNOWN,
    OPENING_SPX,
    CHECKING_SESSION,
    LOGIN_REQUIRED,
    AUTHENTICATED,
    FINDING_ROUTE,
    ROUTE_DETECTED,
    READING_ROUTE,
    SCANNING_PACKAGES,
    WAITING_CONTENT,
    IMPORT_COMPLETE,
    RETURNING_TO_COPILOT,
    ROUTE_READY
}

data class SpxPackage(
    val code: String,
    val address: String? = null
)

object SpxSessionState {

    private val _state =
        MutableStateFlow(SpxState.UNKNOWN)

    val state: StateFlow<SpxState> =
        _state.asStateFlow()

    private val _statusMessage =
        MutableStateFlow("Aguardando SPX")

    val statusMessage: StateFlow<String> =
        _statusMessage.asStateFlow()

    private val _atCode =
        MutableStateFlow<String?>(null)

    val atCode: StateFlow<String?> =
        _atCode.asStateFlow()

    private val _dataCarregamento =
        MutableStateFlow<String?>(null)

    val dataCarregamento: StateFlow<String?> =
        _dataCarregamento.asStateFlow()

    private val _totalEsperado =
        MutableStateFlow<Int?>(null)

    val totalEsperado: StateFlow<Int?> =
        _totalEsperado.asStateFlow()

    private val _packages =
        MutableStateFlow<Map<String, SpxPackage>>(
            linkedMapOf()
        )

    val packages: StateFlow<Map<String, SpxPackage>> =
        _packages.asStateFlow()

    private val _packageCount =
        MutableStateFlow(0)

    val packageCount: StateFlow<Int> =
        _packageCount.asStateFlow()

    fun updateState(
        state: SpxState
    ) {
        _state.value = state
    }

    fun updateMessage(
        message: String
    ) {
        _statusMessage.value = message
    }

    fun updateAtCode(
        at: String?
    ) {
        if (!at.isNullOrBlank()) {
            _atCode.value =
                at.trim().uppercase()
        }
    }

    fun updateDataCarregamento(
        data: String?
    ) {
        if (!data.isNullOrBlank()) {
            _dataCarregamento.value = data
        }
    }

    fun updateTotalEsperado(
        total: Int?
    ) {
        if (
            total == null ||
            total <= 0 ||
            total > 1000
        ) {
            return
        }

        val atual =
            _totalEsperado.value

        if (
            atual == null ||
            total > atual
        ) {
            _totalEsperado.value =
                total
        }
    }

    fun addOrUpdatePackages(
        encontrados: Map<String, String?>
    ): Int {

        val atual =
            LinkedHashMap(
                _packages.value
            )

        var novos = 0

        encontrados.forEach {
                (codigoOriginal, endereco) ->

            val codigo =
                codigoOriginal
                    .trim()
                    .uppercase()

            if (codigo.isBlank()) {
                return@forEach
            }

            val existente =
                atual[codigo]

            if (existente == null) {

                atual[codigo] =
                    SpxPackage(
                        code = codigo,
                        address = endereco
                    )

                novos++

            } else if (
                existente.address.isNullOrBlank() &&
                !endereco.isNullOrBlank()
            ) {

                atual[codigo] =
                    existente.copy(
                        address = endereco
                    )
            }
        }

        _packages.value =
            atual

        _packageCount.value =
            atual.size

        return novos
    }

    fun resetRoute() {

        _state.value =
            SpxState.UNKNOWN

        _statusMessage.value =
            "Preparando importação..."

        _atCode.value =
            null

        _dataCarregamento.value =
            null

        _totalEsperado.value =
            null

        _packages.value =
            linkedMapOf()

        _packageCount.value =
            0
    }
}