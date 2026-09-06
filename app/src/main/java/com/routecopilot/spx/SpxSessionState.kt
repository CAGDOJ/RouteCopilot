package com.routecopilot.spx

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class SpxState {

    // =========================================================
    // ESTADOS GERAIS
    // =========================================================

    IDLE,

    STARTING_IMPORT,
    OPENING_SPX,
    CHECKING_SESSION,

    // =========================================================
    // AUTENTICAÇÃO
    // =========================================================

    LOGIN_REQUIRED,
    AUTHENTICATED,

    CONSENT_REQUIRED,
    FACE_CHECK_REQUIRED,

    // =========================================================
    // LOCALIZAÇÃO DA ROTA
    // =========================================================

    FINDING_ROUTE,
    ROUTE_DETECTED,

    // =========================================================
    // DOWNLOAD
    // =========================================================

    FINDING_DOWNLOAD_BUTTON,
    DOWNLOAD_BUTTON_FOUND,

    WAITING_ROUTE_DOWNLOAD,
    DOWNLOADING_ROUTE,

    // =========================================================
    // ABERTURA DA ROTA
    // =========================================================

    OPENING_DELIVERIES,
    OPENING_IN_ROUTE,

    READING_ROUTE,

    // =========================================================
    // IMPORTAÇÃO
    // =========================================================

    IMPORTING_PACKAGES,

    PACKAGE_DETECTED,

    WAITING_CONTENT,

    // =========================================================
    // VALIDAÇÃO / CÁLCULO
    // =========================================================

    VALIDATING_ROUTE,
    CALCULATING_ROUTE,

    PREPARING_ROUTE,

    // =========================================================
    // FINALIZAÇÃO
    // =========================================================

    IMPORT_COMPLETE,
    RETURNING_TO_COPILOT,
    ROUTE_READY,

    // =========================================================
    // ERRO
    // =========================================================

    ERROR
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

    // =========================================================
    // CONTEXTO
    // =========================================================

    private var applicationContext: Context? =
        null

    fun initialize(
        context: Context
    ) {

        applicationContext =
            context.applicationContext
    }

    // =========================================================
    // ESTADO PRINCIPAL
    // =========================================================

    private val _state =
        MutableStateFlow(
            SpxState.IDLE
        )

    val state: StateFlow<SpxState> =
        _state.asStateFlow()

    // =========================================================
    // MENSAGEM
    // =========================================================

    private val _message =
        MutableStateFlow(
            "Pronto para iniciar"
        )

    val message: StateFlow<String> =
        _message.asStateFlow()

    /*
     * Nome utilizado pela interface anterior.
     */
    val statusMessage: StateFlow<String> =
        _message.asStateFlow()

    // =========================================================
    // PACKAGE SPX
    // =========================================================

    private val _packageName =
        MutableStateFlow<String?>(
            null
        )

    val packageName: StateFlow<String?> =
        _packageName.asStateFlow()

    // =========================================================
    // AT
    // =========================================================

    private val _atCode =
        MutableStateFlow<String?>(
            null
        )

    val atCode: StateFlow<String?> =
        _atCode.asStateFlow()

    // =========================================================
    // DATA
    // =========================================================

    private val _date =
        MutableStateFlow<String?>(
            null
        )

    val date: StateFlow<String?> =
        _date.asStateFlow()

    val dataCarregamento: StateFlow<String?> =
        _date.asStateFlow()

    // =========================================================
    // TOTAL ESPERADO
    // =========================================================

    private val _expectedTotal =
        MutableStateFlow<Int?>(
            null
        )

    val expectedTotal: StateFlow<Int?> =
        _expectedTotal.asStateFlow()

    val totalEsperado: StateFlow<Int?> =
        _expectedTotal.asStateFlow()

    // =========================================================
    // BR ATUAL
    // =========================================================

    private val _brCode =
        MutableStateFlow<String?>(
            null
        )

    val brCode: StateFlow<String?> =
        _brCode.asStateFlow()

    // =========================================================
    // LISTA DE BRs
    // =========================================================

    private val _packageCodes =
        MutableStateFlow<Set<String>>(
            emptySet()
        )

    val packageCodes: StateFlow<Set<String>> =
        _packageCodes.asStateFlow()

    // =========================================================
    // CONTADOR
    // =========================================================

    private val _packageCount =
        MutableStateFlow(
            0
        )

    val packageCount: StateFlow<Int> =
        _packageCount.asStateFlow()

    // =========================================================
    // SINCRONIZAÇÃO
    // =========================================================

    private val _syncActive =
        MutableStateFlow(
            false
        )

    val syncActive: StateFlow<Boolean> =
        _syncActive.asStateFlow()

    // =========================================================
    // ROTA PRONTA
    // =========================================================

    private val _routeReady =
        MutableStateFlow(
            false
        )

    val routeReady: StateFlow<Boolean> =
        _routeReady.asStateFlow()

    // =========================================================
    // INICIAR IMPORTAÇÃO
    // =========================================================

    fun begin() {

        beginImport()
    }

    /*
     * Compatibilidade com o MainActivity antigo.
     */
    fun beginImport() {

        limparDadosDaRota()

        _syncActive.value =
            true

        _routeReady.value =
            false

        _state.value =
            SpxState.STARTING_IMPORT

        _message.value =
            "Preparando importação..."
    }

    // =========================================================
    // RESET
    // =========================================================

    fun reset() {

        limparDadosDaRota()

        _syncActive.value =
            false

        _routeReady.value =
            false

        _state.value =
            SpxState.IDLE

        _message.value =
            "Pronto para iniciar"
    }

    /*
     * Compatibilidade com versões que utilizavam resetRoute().
     */
    fun resetRoute() {

        reset()
    }

    private fun limparDadosDaRota() {

        _packageName.value =
            null

        _atCode.value =
            null

        _date.value =
            null

        _expectedTotal.value =
            null

        _brCode.value =
            null

        _packageCodes.value =
            emptySet()

        _packageCount.value =
            0
    }

    // =========================================================
    // UPDATE PRINCIPAL
    // =========================================================

    fun update(
        state: SpxState,
        message: String
    ) {

        updateState(
            state,
            message
        )
    }

    // =========================================================
    // UPDATE STATE - 1 ARGUMENTO
    // =========================================================

    fun updateState(
        state: SpxState
    ) {

        _state.value =
            state

        atualizarFlags(
            state
        )
    }

    // =========================================================
    // UPDATE STATE - 2 ARGUMENTOS
    //
    // É ESTE OVERLOAD QUE ESTAVA FALTANDO NO MAINACTIVITY
    // =========================================================

    fun updateState(
        state: SpxState,
        message: String
    ) {

        _state.value =
            state

        _message.value =
            message

        atualizarFlags(
            state
        )
    }

    fun updateMessage(
        message: String
    ) {

        _message.value =
            message
    }

    private fun atualizarFlags(
        state: SpxState
    ) {

        when (state) {

            SpxState.IDLE -> {

                _syncActive.value =
                    false
            }

            SpxState.ROUTE_READY -> {

                _syncActive.value =
                    false

                _routeReady.value =
                    true
            }

            SpxState.ERROR -> {

                _syncActive.value =
                    false
            }

            else -> {

                _syncActive.value =
                    true
            }
        }
    }

    // =========================================================
    // PACKAGE NAME
    // =========================================================

    fun updatePackageName(
        packageName: String?
    ) {

        if (
            packageName.isNullOrBlank()
        ) {
            return
        }

        _packageName.value =
            packageName.trim()
    }

    // =========================================================
    // AT
    // =========================================================

    fun setAt(
        at: String?
    ) {

        if (
            at.isNullOrBlank()
        ) {
            return
        }

        _atCode.value =
            at
                .trim()
                .uppercase()
    }

    fun updateAtCode(
        at: String?
    ) {

        setAt(
            at
        )
    }

    // =========================================================
    // DATA
    // =========================================================

    fun setDate(
        date: String?
    ) {

        if (
            date.isNullOrBlank()
        ) {
            return
        }

        _date.value =
            date.trim()
    }

    fun updateDataCarregamento(
        date: String?
    ) {

        setDate(
            date
        )
    }

    // =========================================================
    // TOTAL
    // =========================================================

    fun setExpectedTotal(
        total: Int?
    ) {

        if (
            total == null ||
            total <= 0
        ) {
            return
        }

        val atual =
            _expectedTotal.value

        /*
         * Se já encontramos um total maior,
         * não diminuímos por causa de algum
         * contador secundário da interface.
         */
        if (
            atual == null ||
            total >= atual
        ) {

            _expectedTotal.value =
                total
        }
    }

    fun updateTotalEsperado(
        total: Int?
    ) {

        setExpectedTotal(
            total
        )
    }

    // =========================================================
    // ADICIONAR 1 BR
    // =========================================================

    fun addPackageCode(
        rawCode: String
    ): Boolean {

        val code =
            rawCode
                .trim()
                .uppercase()

        if (
            code.isBlank()
        ) {
            return false
        }

        val atual =
            LinkedHashSet(
                _packageCodes.value
            )

        if (
            !atual.add(
                code
            )
        ) {

            return false
        }

        _packageCodes.value =
            atual

        _packageCount.value =
            atual.size

        _brCode.value =
            code

        return true
    }

    // =========================================================
    // ADICIONAR VÁRIOS BRs
    // =========================================================

    fun addPackageCodes(
        codes: Collection<String>
    ): Int {

        var novos =
            0

        codes.forEach { code ->

            if (
                addPackageCode(
                    code
                )
            ) {

                novos++
            }
        }

        return novos
    }

    // =========================================================
    // ROTA ATUAL
    // =========================================================

    fun getRotaAtual():
        RotaImportada {

        return RotaImportada(

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

            pronta =
                _routeReady.value
        )
    }

    // =========================================================
    // IMPORTAÇÃO CONCLUÍDA
    // =========================================================

    fun markImportComplete() {

        _state.value =
            SpxState.IMPORT_COMPLETE

        _message.value =
            "Importação concluída."

        _syncActive.value =
            true
    }

    // =========================================================
    // ROTA PRONTA
    // =========================================================

    fun markRouteReady() {

        _state.value =
            SpxState.ROUTE_READY

        _message.value =
            "Rota pronta."

        _syncActive.value =
            false

        _routeReady.value =
            true
    }

    // =========================================================
    // CANCELAR SINCRONIZAÇÃO
    // =========================================================

    fun cancelSync() {

        _syncActive.value =
            false

        if (
            !_routeReady.value
        ) {

            _state.value =
                SpxState.IDLE

            _message.value =
                "Sincronização cancelada."
        }
    }

    // =========================================================
    // ERRO
    // =========================================================

    fun fail(
        message: String
    ) {

        _state.value =
            SpxState.ERROR

        _message.value =
            message

        _syncActive.value =
            false
    }
}