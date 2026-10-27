package com.routecopilot.route

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

object RouteRepository {

    private const val PREFS = "routecopilot_routes_v2"
    private const val KEY_ACTIVE = "active_route"
    private const val KEY_HISTORY = "route_history"
    private const val MAX_HISTORY = 30

    private var appContext: Context? = null

    private val _activeRoute =
        MutableStateFlow<ActiveRoute?>(null)

    val activeRoute: StateFlow<ActiveRoute?> =
        _activeRoute.asStateFlow()

    private val _history =
        MutableStateFlow<List<RouteHistoryItem>>(emptyList())

    val history: StateFlow<List<RouteHistoryItem>> =
        _history.asStateFlow()

    private val _driverLocation =
        MutableStateFlow<DriverLocation?>(null)

    val driverLocation: StateFlow<DriverLocation?> =
        _driverLocation.asStateFlow()

    @Synchronized
    fun initialize(context: Context) {
        if (appContext != null) return

        appContext = context.applicationContext
        load()
    }

    @Synchronized
    fun startOrSwitchRoute(
        at: String,
        loadingDate: String?,
        expectedTotal: Int?
    ): Boolean {
        val normalizedAt =
            at.trim().uppercase()

        val current =
            _activeRoute.value

        if (current?.at == normalizedAt) {
            _activeRoute.value =
                current.copy(
                    loadingDate =
                        loadingDate
                            ?: current.loadingDate,
                    expectedTotal =
                        pickExpected(
                            current.expectedTotal,
                            expectedTotal
                        ),
                    updatedAt =
                        System.currentTimeMillis()
                )

            persist()
            return false
        }

        if (
            current != null &&
            current.packages.isNotEmpty()
        ) {
            archive(current)
        }

        _activeRoute.value =
            ActiveRoute(
                at = normalizedAt,
                loadingDate = loadingDate,
                expectedTotal = expectedTotal,
                packages = emptyList(),
                ready = false,
                partial = false
            )

        persist()
        return true
    }

    @Synchronized
    fun updateExpectedTotal(total: Int?) {
        if (total == null || total <= 0) return

        val current =
            _activeRoute.value
                ?: return

        _activeRoute.value =
            current.copy(
                expectedTotal =
                    pickExpected(
                        current.expectedTotal,
                        total
                    ),
                updatedAt =
                    System.currentTimeMillis()
            )

        persist()
    }

    @Synchronized
    fun mergeCandidates(
        candidates: Collection<ImportedPackageCandidate>
    ): Int {
        val route =
            _activeRoute.value
                ?: return 0

        if (candidates.isEmpty()) return 0

        val map =
            LinkedHashMap<String, RoutePackage>()

        route.packages
            .sortedBy { it.sequence }
            .forEach {
                map[it.br.uppercase()] = it
            }

        var nextSequence =
            (map.values.maxOfOrNull { it.sequence } ?: 0) + 1

        var added = 0

        candidates.forEach { candidate ->
            val br =
                candidate.br
                    .trim()
                    .uppercase()

            if (br.isBlank()) {
                return@forEach
            }

            val old =
                map[br]

            if (old == null) {
                map[br] =
                    RoutePackage(
                        br = br,
                        recipient =
                            candidate.recipient
                                ?.takeIf { it.isNotBlank() },
                        phone =
                            candidate.phone
                                ?.takeIf { it.isNotBlank() },
                        address =
                            candidate.address
                                ?.takeIf { it.isNotBlank() },
                        sequence =
                            nextSequence++
                    )

                added++
            } else {
                map[br] =
                    old.copy(
                        recipient =
                            old.recipient
                                ?: candidate.recipient
                                    ?.takeIf { it.isNotBlank() },
                        phone =
                            old.phone
                                ?: candidate.phone
                                    ?.takeIf { it.isNotBlank() },
                        address =
                            old.address
                                ?: candidate.address
                                    ?.takeIf { it.isNotBlank() }
                    )
            }
        }

        _activeRoute.value =
            route.copy(
                packages =
                    map.values
                        .sortedBy { it.sequence },
                updatedAt =
                    System.currentTimeMillis()
            )

        persist()
        return added
    }

    @Synchronized
    fun updatePackageCoordinates(
        br: String,
        latitude: Double,
        longitude: Double
    ) {
        val route =
            _activeRoute.value
                ?: return

        val normalized =
            br.uppercase()

        val updated =
            route.packages.map {
                if (it.br == normalized) {
                    it.copy(
                        latitude = latitude,
                        longitude = longitude
                    )
                } else {
                    it
                }
            }

        _activeRoute.value =
            route.copy(
                packages = updated,
                updatedAt =
                    System.currentTimeMillis()
            )

        persist()
    }

    @Synchronized
    fun updatePackageStatus(
        br: String,
        status: DeliveryStatus
    ) {
        val route =
            _activeRoute.value
                ?: return

        val normalized =
            br.uppercase()

        val updated =
            route.packages.map {
                if (it.br == normalized) {
                    it.copy(status = status)
                } else {
                    it
                }
            }

        _activeRoute.value =
            route.copy(
                packages = updated,
                updatedAt =
                    System.currentTimeMillis()
            )

        persist()
    }

    @Synchronized
    fun markReady(partial: Boolean) {
        val route =
            _activeRoute.value
                ?: return

        _activeRoute.value =
            route.copy(
                ready = true,
                partial = partial,
                updatedAt =
                    System.currentTimeMillis()
            )

        persist()
    }

    @Synchronized
    fun markNotReady() {
        val route =
            _activeRoute.value
                ?: return

        _activeRoute.value =
            route.copy(
                ready = false,
                partial = false,
                updatedAt =
                    System.currentTimeMillis()
            )

        persist()
    }

    fun updateDriverLocation(
        location: DriverLocation
    ) {
        _driverLocation.value =
            location
    }

    @Synchronized
    fun clearActiveRoute() {
        val current =
            _activeRoute.value

        if (
            current != null &&
            current.packages.isNotEmpty()
        ) {
            archive(current)
        }

        _activeRoute.value =
            null

        persist()
    }

    fun buildRouteSummary(): String {
        val route =
            _activeRoute.value
                ?: return "Nenhuma rota ativa"

        return buildString {
            appendLine(
                "AT: ${route.at}"
            )

            appendLine(
                "Data de carregamento: ${
                    route.loadingDate
                        ?: "Não identificada"
                }"
            )

            append(
                "Total de pedidos: ${
                    route.expectedTotal
                        ?: route.packages.size
                }"
            )
        }
    }

    private fun pickExpected(
        current: Int?,
        newValue: Int?
    ): Int? {
        if (
            newValue == null ||
            newValue <= 0
        ) {
            return current
        }

        if (current == null) {
            return newValue
        }

        return maxOf(
            current,
            newValue
        )
    }

    private fun archive(route: ActiveRoute) {
        val item =
            RouteHistoryItem(
                at = route.at,
                loadingDate =
                    route.loadingDate,
                packageCount =
                    route.packages.size,
                finishedAt =
                    System.currentTimeMillis()
            )

        _history.value =
            (
                listOf(item) +
                    _history.value
                        .filterNot {
                            it.at == item.at
                        }
            )
                .take(MAX_HISTORY)
    }

    private fun prefs() =
        appContext
            ?.getSharedPreferences(
                PREFS,
                Context.MODE_PRIVATE
            )

    private fun persist() {
        val prefs =
            prefs()
                ?: return

        val activeJson =
            _activeRoute.value
                ?.let(::routeToJson)
                ?.toString()

        val historyJson =
            JSONArray().apply {
                _history.value.forEach {
                    put(historyToJson(it))
                }
            }.toString()

        prefs.edit()
            .putString(
                KEY_ACTIVE,
                activeJson
            )
            .putString(
                KEY_HISTORY,
                historyJson
            )
            .apply()
    }

    private fun load() {
        val prefs =
            prefs()
                ?: return

        _activeRoute.value =
            runCatching {
                prefs.getString(
                    KEY_ACTIVE,
                    null
                )
                    ?.takeIf {
                        it.isNotBlank() &&
                        it != "null"
                    }
                    ?.let {
                        routeFromJson(
                            JSONObject(it)
                        )
                    }
            }.getOrNull()

        _history.value =
            runCatching {
                val raw =
                    prefs.getString(
                        KEY_HISTORY,
                        null
                    )
                        ?: return@runCatching emptyList()

                val array =
                    JSONArray(raw)

                buildList {
                    for (
                        i in 0 until array.length()
                    ) {
                        add(
                            historyFromJson(
                                array.getJSONObject(i)
                            )
                        )
                    }
                }
            }.getOrElse {
                emptyList()
            }
    }

    private fun routeToJson(
        route: ActiveRoute
    ): JSONObject =
        JSONObject().apply {
            put(
                "at",
                route.at
            )

            put(
                "loadingDate",
                route.loadingDate
                    ?: JSONObject.NULL
            )

            put(
                "expectedTotal",
                route.expectedTotal
                    ?: JSONObject.NULL
            )

            put(
                "createdAt",
                route.createdAt
            )

            put(
                "updatedAt",
                route.updatedAt
            )

            put(
                "ready",
                route.ready
            )

            put(
                "partial",
                route.partial
            )

            put(
                "packages",
                JSONArray().apply {
                    route.packages.forEach {
                        put(packageToJson(it))
                    }
                }
            )
        }

    private fun routeFromJson(
        obj: JSONObject
    ): ActiveRoute {
        val packagesJson =
            obj.optJSONArray("packages")
                ?: JSONArray()

        val packages =
            buildList {
                for (
                    i in 0 until packagesJson.length()
                ) {
                    add(
                        packageFromJson(
                            packagesJson.getJSONObject(i)
                        )
                    )
                }
            }

        return ActiveRoute(
            at =
                obj.getString("at"),
            loadingDate =
                obj.optNullableString(
                    "loadingDate"
                ),
            expectedTotal =
                obj.optNullableInt(
                    "expectedTotal"
                ),
            packages =
                packages
                    .sortedBy { it.sequence },
            createdAt =
                obj.optLong(
                    "createdAt",
                    System.currentTimeMillis()
                ),
            updatedAt =
                obj.optLong(
                    "updatedAt",
                    System.currentTimeMillis()
                ),
            ready =
                obj.optBoolean(
                    "ready",
                    false
                ),
            partial =
                obj.optBoolean(
                    "partial",
                    false
                )
        )
    }

    private fun packageToJson(
        pkg: RoutePackage
    ): JSONObject =
        JSONObject().apply {
            put(
                "br",
                pkg.br
            )

            put(
                "recipient",
                pkg.recipient
                    ?: JSONObject.NULL
            )

            put(
                "phone",
                pkg.phone
                    ?: JSONObject.NULL
            )

            put(
                "address",
                pkg.address
                    ?: JSONObject.NULL
            )

            put(
                "sequence",
                pkg.sequence
            )

            put(
                "status",
                pkg.status.name
            )

            put(
                "latitude",
                pkg.latitude
                    ?: JSONObject.NULL
            )

            put(
                "longitude",
                pkg.longitude
                    ?: JSONObject.NULL
            )
        }

    private fun packageFromJson(
        obj: JSONObject
    ): RoutePackage =
        RoutePackage(
            br =
                obj.getString("br"),
            recipient =
                obj.optNullableString(
                    "recipient"
                ),
            phone =
                obj.optNullableString(
                    "phone"
                ),
            address =
                obj.optNullableString(
                    "address"
                ),
            sequence =
                obj.optInt(
                    "sequence",
                    0
                ),
            status =
                runCatching {
                    DeliveryStatus.valueOf(
                        obj.optString(
                            "status",
                            DeliveryStatus.IMPORTED.name
                        )
                    )
                }.getOrDefault(
                    DeliveryStatus.IMPORTED
                ),
            latitude =
                obj.optNullableDouble(
                    "latitude"
                ),
            longitude =
                obj.optNullableDouble(
                    "longitude"
                )
        )

    private fun historyToJson(
        item: RouteHistoryItem
    ): JSONObject =
        JSONObject().apply {
            put(
                "at",
                item.at
            )

            put(
                "loadingDate",
                item.loadingDate
                    ?: JSONObject.NULL
            )

            put(
                "packageCount",
                item.packageCount
            )

            put(
                "finishedAt",
                item.finishedAt
            )
        }

    private fun historyFromJson(
        obj: JSONObject
    ): RouteHistoryItem =
        RouteHistoryItem(
            at =
                obj.getString("at"),
            loadingDate =
                obj.optNullableString(
                    "loadingDate"
                ),
            packageCount =
                obj.optInt(
                    "packageCount",
                    0
                ),
            finishedAt =
                obj.optLong(
                    "finishedAt",
                    0L
                )
        )

    private fun JSONObject.optNullableString(
        key: String
    ): String? {
        if (
            !has(key) ||
            isNull(key)
        ) {
            return null
        }

        return optString(key)
            .takeIf {
                it.isNotBlank()
            }
    }

    private fun JSONObject.optNullableInt(
        key: String
    ): Int? {
        if (
            !has(key) ||
            isNull(key)
        ) {
            return null
        }

        return optInt(key)
            .takeIf {
                it > 0
            }
    }

    private fun JSONObject.optNullableDouble(
        key: String
    ): Double? {
        if (
            !has(key) ||
            isNull(key)
        ) {
            return null
        }

        return optDouble(key)
            .takeIf {
                !it.isNaN()
            }
    }
}
