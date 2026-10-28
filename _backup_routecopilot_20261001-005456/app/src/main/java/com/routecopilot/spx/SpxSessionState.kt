package com.routecopilot.spx

import android.content.Context
import com.routecopilot.route.ImportedPackageCandidate
import com.routecopilot.route.RouteRepository
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
    OPENING_DELIVERIES,
    OPENING_IN_ROUTE,
    READING_ROUTE,
    IMPORTING_PACKAGES,
    VERIFYING_END,
    IMPORT_COMPLETE,
    IMPORT_PARTIAL,
    RETURNING_TO_COPILOT,
    ROUTE_READY,
    ERROR
}

object SpxSessionState {

    private var initialized = false

    private val _state =
        MutableStateFlow(
            SpxState.IDLE
        )

    val state: StateFlow<SpxState> =
        _state.asStateFlow()

    private val _message =
        MutableStateFlow(
            "Pronto para iniciar"
        )

    val statusMessage: StateFlow<String> =
        _message.asStateFlow()

    private val _syncActive =
        MutableStateFlow(
            false
        )

    val syncActive: StateFlow<Boolean> =
        _syncActive.asStateFlow()

    private val _importId =
        MutableStateFlow(
            0
        )

    val importId: StateFlow<Int> =
        _importId.asStateFlow()

    private val _packageCodes =
        MutableStateFlow<Set<String>>(
            emptySet()
        )

    val packageCodes: StateFlow<Set<String>> =
        _packageCodes.asStateFlow()

    private val _packageCount =
        MutableStateFlow(
            0
        )

    val packageCount: StateFlow<Int> =
        _packageCount.asStateFlow()

    private val _scannerRequested =
        MutableStateFlow(
            false
        )

    val scannerRequested: StateFlow<Boolean> =
        _scannerRequested.asStateFlow()

    fun initialize(
        context: Context
    ) {
        if (initialized) return

        RouteRepository.initialize(
            context.applicationContext
        )

        syncFromRepository()

        initialized = true
    }

    fun beginImport() {
        _importId.value += 1

        _syncActive.value =
            true

        _state.value =
            SpxState.STARTING_IMPORT

        _message.value =
            "Preparando sincronização..."

        RouteRepository.markNotReady()
    }

    fun resetUi() {
        _syncActive.value =
            false

        _state.value =
            SpxState.IDLE

        _message.value =
            "Pronto para iniciar"

        syncFromRepository()
    }

    fun update(
        state: SpxState,
        message: String
    ) {
        _state.value =
            state

        _message.value =
            message

        _syncActive.value =
            when (state) {
                SpxState.IDLE,
                SpxState.ROUTE_READY,
                SpxState.ERROR ->
                    false

                else ->
                    true
            }
    }

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

    fun acceptDetectedRoute(
        at: String,
        loadingDate: String?,
        expectedTotal: Int?
    ): Boolean {
        val switched =
            RouteRepository.startOrSwitchRoute(
                at = at,
                loadingDate = loadingDate,
                expectedTotal = expectedTotal
            )

        syncFromRepository()

        return switched
    }

    fun updateExpectedTotal(
        total: Int?
    ) {
        RouteRepository.updateExpectedTotal(
            total
        )

        syncFromRepository()
    }

    fun addCandidates(
        candidates: Collection<ImportedPackageCandidate>
    ): Int {
        val added =
            RouteRepository.mergeCandidates(
                candidates
            )

        syncFromRepository()

        return added
    }

    fun markReady(
        partial: Boolean
    ) {
        RouteRepository.markReady(
            partial
        )

        _syncActive.value =
            false

        _state.value =
            if (partial) {
                SpxState.IMPORT_PARTIAL
            } else {
                SpxState.ROUTE_READY
            }

        _message.value =
            if (partial) {
                "Rota importada parcialmente. Revise antes de iniciar."
            } else {
                "Rota pronta."
            }

        syncFromRepository()
    }

    fun requestScanner() {
        _scannerRequested.value =
            true
    }

    fun clearScannerRequest() {
        _scannerRequested.value =
            false
    }

    fun syncFromRepository() {
        val route =
            RouteRepository.activeRoute.value

        val codes =
            route?.packages
                ?.sortedBy {
                    it.sequence
                }
                ?.map {
                    it.br
                }
                ?.toCollection(
                    LinkedHashSet()
                )
                ?: linkedSetOf()

        _packageCodes.value =
            codes

        _packageCount.value =
            codes.size
    }
}
