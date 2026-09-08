package com.routecopilot.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object RomaneioSession {
    private var appContext: Context? = null

    private val _route = MutableStateFlow<RomaneioRoute?>(null)
    val route: StateFlow<RomaneioRoute?> = _route.asStateFlow()

    private val _availableRoutes = MutableStateFlow<List<RomaneioRoute>>(emptyList())
    val availableRoutes: StateFlow<List<RomaneioRoute>> = _availableRoutes.asStateFlow()

    private val _statuses = MutableStateFlow<Map<String, PackageStatus>>(emptyMap())
    val statuses: StateFlow<Map<String, PackageStatus>> = _statuses.asStateFlow()

    private val _preferences = MutableStateFlow<Map<String, ClientPreference>>(emptyMap())
    val preferences: StateFlow<Map<String, ClientPreference>> = _preferences.asStateFlow()

    private val _runState = MutableStateFlow(RouteRunState.IDLE)
    val runState: StateFlow<RouteRunState> = _runState.asStateFlow()

    private val _pauseReason = MutableStateFlow("")
    val pauseReason: StateFlow<String> = _pauseReason.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _status = MutableStateFlow("Pronto")
    val status: StateFlow<String> = _status.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _lastSync = MutableStateFlow(0L)
    val lastSync: StateFlow<Long> = _lastSync.asStateFlow()

    private val _activities = MutableStateFlow<List<ActivityEntry>>(emptyList())
    val activities: StateFlow<List<ActivityEntry>> = _activities.asStateFlow()

    fun initialize(context: Context) {
        if (appContext != null) return
        appContext = context.applicationContext

        RoutePersistence.load(context)?.let { snapshot ->
            _route.value = snapshot.route
            _statuses.value = snapshot.statuses
            _preferences.value = snapshot.preferences
            _runState.value = snapshot.runState
            _pauseReason.value = snapshot.pauseReason
            _lastSync.value = snapshot.lastSync

            if (snapshot.route != null) {
                _availableRoutes.value = listOf(snapshot.route)
                addActivity("Rota anterior restaurada")
            }
        }
    }

    fun startLoading(message: String) {
        _busy.value = true
        _status.value = message
        _error.value = null
    }

    fun applySync(result: RouteSyncResult) {
        _availableRoutes.value = result.routes
        _lastSync.value = System.currentTimeMillis()
        _busy.value = false
        _status.value = "Sincronizado"
        _error.value = null

        val current = _route.value
        val selected = when {
            current != null -> result.routes.firstOrNull { it.routeKey == current.routeKey }
                ?: result.routes.firstOrNull()
            else -> result.routes.firstOrNull()
        }

        if (selected != null) selectRoute(selected)
        addActivity("${result.routes.size} rota(s) sincronizada(s)")
        persist()
    }

    fun selectRoute(newRoute: RomaneioRoute) {
        val oldRoute = _route.value
        val sameRoute = oldRoute?.routeKey == newRoute.routeKey

        val previousByBr = oldRoute?.packages?.associateBy { it.spxTn }.orEmpty()
        val newIds = newRoute.packages.map { it.spxTn }.toSet()

        val retainedOccurrences = if (sameRoute) {
            previousByBr.values.filter { oldPkg ->
                oldPkg.spxTn !in newIds &&
                    (_statuses.value[oldPkg.spxTn] == PackageStatus.OCCURRENCE ||
                        _statuses.value[oldPkg.spxTn] == PackageStatus.POSSIBLE_OCCURRENCE)
            }
        } else {
            emptyList()
        }

        val mergedPackages = (newRoute.packages + retainedOccurrences)
            .distinctBy { it.spxTn }

        _route.value = newRoute.copy(packages = mergedPackages)

        if (!sameRoute) {
            _statuses.value = mergedPackages.associate { it.spxTn to PackageStatus.PENDING }
            _preferences.value = emptyMap()
            _runState.value = RouteRunState.IDLE
            _pauseReason.value = ""
        } else {
            val nextStatuses = _statuses.value.toMutableMap()
            mergedPackages.forEach { pkg -> nextStatuses.putIfAbsent(pkg.spxTn, PackageStatus.PENDING) }
            _statuses.value = nextStatuses
        }

        _busy.value = false
        _status.value = "Rota carregada"
        _error.value = null
        addActivity("Rota ${newRoute.atId} carregada")
        persist()
    }

    fun fail(message: String) {
        _busy.value = false
        _status.value = "Falha na sincronização"
        _error.value = message
        addActivity(message, isWarning = true)
    }

    fun startDeliveries() {
        _runState.value = RouteRunState.IN_PROGRESS
        _pauseReason.value = ""
        addActivity("Entregas iniciadas")
        persist()
    }

    fun pause(reason: String) {
        _runState.value = RouteRunState.PAUSED
        _pauseReason.value = reason
        addActivity("Rota pausada${if (reason.isNotBlank()) ": $reason" else ""}")
        persist()
    }

    fun resume() {
        _runState.value = RouteRunState.IN_PROGRESS
        _pauseReason.value = ""
        addActivity("Entregas retomadas")
        persist()
    }

    fun markDelivered(br: String) {
        updateStatus(br, PackageStatus.DELIVERED)
        addActivity("Pedido $br marcado como entregue")
    }

    fun markOccurrence(br: String) {
        updateStatus(br, PackageStatus.OCCURRENCE)
        addActivity("Ocorrência registrada para $br", isWarning = true)
    }

    fun markPossibleOccurrence(br: String, reason: String = "Cliente informou que não há ninguém para receber") {
        updateStatus(br, PackageStatus.POSSIBLE_OCCURRENCE)
        addActivity("$br: $reason", isWarning = true)
    }

    fun retryDelivery(br: String) {
        updateStatus(br, PackageStatus.PENDING)
        addActivity("$br voltou para a sequência de entrega")
    }

    fun setPreference(br: String, preference: ClientPreference) {
        _preferences.value = _preferences.value.toMutableMap().apply { put(br, preference) }
        if (preference.type == DeliveryPreferenceType.NOBODY_AVAILABLE) {
            markPossibleOccurrence(br)
        } else {
            addActivity("Cliente confirmou preferência para $br")
            persist()
        }
    }

    fun clearRoute() {
        _route.value = null
        _availableRoutes.value = emptyList()
        _statuses.value = emptyMap()
        _preferences.value = emptyMap()
        _runState.value = RouteRunState.IDLE
        _pauseReason.value = ""
        _error.value = null
        _status.value = "Pronto"
        _activities.value = emptyList()
        persist()
    }

    private fun updateStatus(br: String, status: PackageStatus) {
        _statuses.value = _statuses.value.toMutableMap().apply { put(br, status) }
        persist()
    }

    private fun addActivity(text: String, isWarning: Boolean = false) {
        val next = listOf(ActivityEntry(System.currentTimeMillis(), text, isWarning)) + _activities.value
        _activities.value = next.take(40)
    }

    private fun persist() {
        val context = appContext ?: return
        RoutePersistence.save(
            context = context,
            route = _route.value,
            statuses = _statuses.value,
            preferences = _preferences.value,
            runState = _runState.value,
            pauseReason = _pauseReason.value,
            lastSync = _lastSync.value
        )
    }
}
