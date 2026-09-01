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
    PACKAGE_DETECTED,
    IMPORT_COMPLETE,
    RETURNING_TO_COPILOT,
    ROUTE_READY
}

data class RotaImportada(
    val at: String? = null,
    val dataCarregamento: String? = null,
    val totalEsperado: Int? = null,
    val pedidosImportados: Int = 0,
    val pedidos: Set<String> = emptySet()
)

object SpxSessionState {

    private val _state =
        MutableStateFlow(SpxState.UNKNOWN)

    val state: StateFlow<SpxState> =
        _state.asStateFlow()

    private val _packageName =
        MutableStateFlow<String?>(null)

    val packageName: StateFlow<String?> =
        _packageName.asStateFlow()

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

    private val _statusMessage =
        MutableStateFlow("Aguardando SPX")

    val statusMessage: StateFlow<String> =
        _statusMessage.asStateFlow()

    /*
     * Este estado é usado para mandar o MainActivity
     * diretamente para a tela de gestão.
     *
     * Isso resolve o caso em que o MainActivity já estava
     * aberto e o Intent do serviço não recriava a tela.
     */
    private val _openManagement =
        MutableStateFlow(false)

    val openManagement: StateFlow<Boolean> =
        _openManagement.asStateFlow()

    fun updateState(
        newState: SpxState
    ) {
        _state.value = newState
    }

    fun updateMessage(
        message: String
    ) {
        _statusMessage.value = message
    }

    fun updatePackageName(
        packageName: String
    ) {
        _packageName.value =
            packageName
    }

    fun updateAtCode(
        at: String?
    ) {
        if (
            !at.isNullOrBlank()
        ) {

            _atCode.value =
                at
                    .trim()
                    .uppercase()
        }
    }

    fun updateDataCarregamento(
        data: String?
    ) {
        if (
            !data.isNullOrBlank()
        ) {
            _dataCarregamento.value =
                data
        }
    }

    fun updateTotalEsperado(
        total: Int?
    ) {
        if (
            total == null ||
            total <= 0
        ) {
            return
        }

        val atual =
            _totalEsperado.value

        /*
         * Não deixa um contador pequeno lido posteriormente
         * substituir um total maior já identificado.
         */
        if (
            atual == null ||
            total > atual
        ) {
            _totalEsperado.value =
                total
        }
    }

    fun addPackageCode(
        br: String
    ): Boolean {

        val codigo =
            br
                .trim()
                .uppercase()

        if (
            codigo.isBlank()
        ) {
            return false
        }

        val atual =
            _packageCodes.value

        if (
            codigo in atual
        ) {
            return false
        }

        val novo =
            LinkedHashSet<String>()

        novo.addAll(
            atual
        )

        novo.add(
            codigo
        )

        _packageCodes.value =
            novo

        _packageCount.value =
            novo.size

        _brCode.value =
            codigo

        return true
    }

    fun addPackageCodes(
        codes: Collection<String>
    ): Int {

        var novos =
            0

        codes.forEach { code ->

            if (
                addPackageCode(code)
            ) {
                novos++
            }
        }

        return novos
    }

    fun requestOpenManagement() {

        _openManagement.value =
            true
    }

    fun clearOpenManagement() {

        _openManagement.value =
            false
    }

    fun getRotaAtual(): RotaImportada {

        return RotaImportada(
            at = _atCode.value,
            dataCarregamento = _dataCarregamento.value,
            totalEsperado = _totalEsperado.value,
            pedidosImportados = _packageCount.value,
            pedidos = _packageCodes.value
        )
    }

    fun resetRoute() {

        _state.value =
            SpxState.UNKNOWN

        _atCode.value =
            null

        _dataCarregamento.value =
            null

        _totalEsperado.value =
            null

        _brCode.value =
            null

        _packageCodes.value =
            emptySet()

        _packageCount.value =
            0

        _statusMessage.value =
            "Preparando importação..."

        _openManagement.value =
            false
    }
}