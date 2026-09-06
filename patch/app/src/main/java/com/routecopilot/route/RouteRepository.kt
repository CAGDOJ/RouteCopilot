package com.routecopilot.route

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

object RouteRepository {

    private const val PREFS = "routecopilot_route_store"
    private const val KEY_STOPS = "stops"

    private lateinit var appContext: Context
    private var initialized = false

    private val _stops = MutableStateFlow<List<DeliveryStop>>(emptyList())
    val stops: StateFlow<List<DeliveryStop>> = _stops.asStateFlow()

    fun initialize(context: Context) {
        if (initialized) return
        appContext = context.applicationContext
        initialized = true
        restore()
    }

    private fun prefs() =
        appContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun clear() {
        _stops.value = emptyList()
        if (initialized) {
            prefs().edit().remove(KEY_STOPS).apply()
        }
    }

    fun mergeCandidates(candidates: Collection<ImportedPackageCandidate>): Int {
        if (!initialized) return 0

        val map = LinkedHashMap<String, DeliveryStop>()
        _stops.value.forEach { map[it.br] = it }

        var changed = 0

        candidates.forEach { candidate ->
            val old = map[candidate.br]

            val next = if (old == null) {
                changed++
                DeliveryStop(
                    br = candidate.br,
                    recipient = candidate.recipient,
                    phone = candidate.phone,
                    address = candidate.address,
                    neighborhood = candidate.neighborhood,
                    originalOrder = candidate.originalOrder
                )
            } else {
                val merged = old.copy(
                    recipient = old.recipient ?: candidate.recipient,
                    phone = old.phone ?: candidate.phone,
                    address = old.address ?: candidate.address,
                    neighborhood = old.neighborhood ?: candidate.neighborhood,
                    originalOrder = old.originalOrder ?: candidate.originalOrder
                )

                if (merged != old) changed++
                merged
            }

            map[candidate.br] = next
        }

        if (changed > 0) {
            _stops.value = map.values.toList()
            persist()
        }

        return changed
    }

    fun updateStop(updated: DeliveryStop) {
        val list = _stops.value.toMutableList()
        val index = list.indexOfFirst { it.br == updated.br }

        if (index >= 0) {
            list[index] = updated
        } else {
            list.add(updated)
        }

        _stops.value = list
        persist()
    }

    fun replaceAll(stops: List<DeliveryStop>) {
        _stops.value = stops
        persist()
    }

    fun markMessageSent(br: String) {
        _stops.value = _stops.value.map {
            if (it.br == br) it.copy(messageSent = true) else it
        }
        persist()
    }

    fun markNext(br: String) {
        _stops.value = _stops.value.map { stop ->
            when {
                stop.br == br &&
                    stop.status != DeliveryStatus.DELIVERED &&
                    stop.status != DeliveryStatus.OCCURRENCE ->
                    stop.copy(status = DeliveryStatus.NEXT)

                stop.status == DeliveryStatus.NEXT && stop.br != br ->
                    stop.copy(status = DeliveryStatus.PENDING)

                else -> stop
            }
        }
        persist()
    }

    fun recordDelivered(br: String, serviceSeconds: Long?) {
        _stops.value = _stops.value.map {
            if (it.br == br) {
                it.copy(
                    status = DeliveryStatus.DELIVERED,
                    serviceSeconds = serviceSeconds
                )
            } else {
                it
            }
        }
        persist()
    }

    fun averageServiceSeconds(defaultSeconds: Long = 120L): Long {
        val values = _stops.value
            .mapNotNull { it.serviceSeconds }
            .filter { it > 0 }

        return if (values.isEmpty()) {
            defaultSeconds
        } else {
            values.average().toLong().coerceIn(30L, 600L)
        }
    }

    private fun persist() {
        if (!initialized) return

        val array = JSONArray()

        _stops.value.forEach { stop ->
            array.put(
                JSONObject().apply {
                    put("br", stop.br)
                    put("recipient", stop.recipient)
                    put("phone", stop.phone)
                    put("address", stop.address)
                    put("neighborhood", stop.neighborhood)
                    put("latitude", stop.latitude)
                    put("longitude", stop.longitude)
                    put("originalOrder", stop.originalOrder)
                    put("copilotOrder", stop.copilotOrder)
                    put("trackingToken", stop.trackingToken)
                    put("status", stop.status.name)
                    put("serviceSeconds", stop.serviceSeconds)
                    put("messageSent", stop.messageSent)
                }
            )
        }

        prefs().edit()
            .putString(KEY_STOPS, array.toString())
            .apply()
    }

    private fun restore() {
        val raw = prefs().getString(KEY_STOPS, null) ?: return

        runCatching {
            val array = JSONArray(raw)
            val result = mutableListOf<DeliveryStop>()

            for (i in 0 until array.length()) {
                val o = array.getJSONObject(i)

                result += DeliveryStop(
                    br = o.getString("br"),
                    recipient = o.optString("recipient")
                        .takeIf { it.isNotBlank() && it != "null" },
                    phone = o.optString("phone")
                        .takeIf { it.isNotBlank() && it != "null" },
                    address = o.optString("address")
                        .takeIf { it.isNotBlank() && it != "null" },
                    neighborhood = o.optString("neighborhood")
                        .takeIf { it.isNotBlank() && it != "null" },
                    latitude = if (o.isNull("latitude")) null else o.optDouble("latitude"),
                    longitude = if (o.isNull("longitude")) null else o.optDouble("longitude"),
                    originalOrder = if (o.isNull("originalOrder")) null else o.optInt("originalOrder"),
                    copilotOrder = if (o.isNull("copilotOrder")) null else o.optInt("copilotOrder"),
                    trackingToken = o.optString("trackingToken").ifBlank {
                        java.util.UUID.randomUUID().toString()
                    },
                    status = runCatching {
                        DeliveryStatus.valueOf(o.optString("status"))
                    }.getOrDefault(DeliveryStatus.PENDING),
                    serviceSeconds = if (o.isNull("serviceSeconds")) null else o.optLong("serviceSeconds"),
                    messageSent = o.optBoolean("messageSent", false)
                )
            }

            _stops.value = result
        }
    }
}
