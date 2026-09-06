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
        if (initialized) prefs().edit().remove(KEY_STOPS).apply()
    }

    fun mergeCandidates(candidates: Collection<ImportedPackageCandidate>): Int {
        if (!initialized) return 0

        val map = LinkedHashMap<String, DeliveryStop>()
        _stops.value.forEach { map[it.br] = it }

        var changed = 0
        candidates.forEach { c ->
            val old = map[c.br]
            val next = if (old == null) {
                changed++
                DeliveryStop(
                    br = c.br,
                    recipient = c.recipient,
                    phone = c.phone,
                    address = c.address,
                    originalOrder = c.originalOrder
                )
            } else {
                val merged = old.copy(
                    recipient = old.recipient ?: c.recipient,
                    phone = old.phone ?: c.phone,
                    address = old.address ?: c.address,
                    originalOrder = old.originalOrder ?: c.originalOrder
                )
                if (merged != old) changed++
                merged
            }
            map[c.br] = next
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
        if (index >= 0) list[index] = updated else list.add(updated)
        _stops.value = list
        persist()
    }

    fun replaceAll(stops: List<DeliveryStop>) {
        _stops.value = stops
        persist()
    }

    fun markNext(br: String?) {
        _stops.value = _stops.value.map { stop ->
            when {
                br != null && stop.br == br && stop.status == DeliveryStatus.PENDING ->
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
            if (it.br == br) it.copy(
                status = DeliveryStatus.DELIVERED,
                serviceSeconds = serviceSeconds
            ) else it
        }
        persist()
    }

    fun averageServiceSeconds(defaultSeconds: Long = 120L): Long {
        val values = _stops.value.mapNotNull { it.serviceSeconds }.filter { it > 0 }
        if (values.isEmpty()) return defaultSeconds
        return values.average().toLong().coerceIn(30L, 600L)
    }

    private fun persist() {
        if (!initialized) return
        val array = JSONArray()
        _stops.value.forEach { s ->
            array.put(JSONObject().apply {
                put("br", s.br)
                put("recipient", s.recipient)
                put("phone", s.phone)
                put("address", s.address)
                put("lat", s.latitude)
                put("lon", s.longitude)
                put("originalOrder", s.originalOrder)
                put("copilotOrder", s.copilotOrder)
                put("trackingToken", s.trackingToken)
                put("status", s.status.name)
                put("serviceSeconds", s.serviceSeconds)
            })
        }
        prefs().edit().putString(KEY_STOPS, array.toString()).apply()
    }

    private fun restore() {
        val raw = prefs().getString(KEY_STOPS, null) ?: return
        runCatching {
            val array = JSONArray(raw)
            val list = mutableListOf<DeliveryStop>()
            for (i in 0 until array.length()) {
                val o = array.getJSONObject(i)
                list += DeliveryStop(
                    br = o.getString("br"),
                    recipient = o.optString("recipient").takeIf { it.isNotBlank() && it != "null" },
                    phone = o.optString("phone").takeIf { it.isNotBlank() && it != "null" },
                    address = o.optString("address").takeIf { it.isNotBlank() && it != "null" },
                    latitude = if (o.isNull("lat")) null else o.optDouble("lat"),
                    longitude = if (o.isNull("lon")) null else o.optDouble("lon"),
                    originalOrder = if (o.isNull("originalOrder")) null else o.optInt("originalOrder"),
                    copilotOrder = if (o.isNull("copilotOrder")) null else o.optInt("copilotOrder"),
                    trackingToken = o.optString("trackingToken").ifBlank {
                        java.util.UUID.randomUUID().toString()
                    },
                    status = runCatching {
                        DeliveryStatus.valueOf(o.optString("status"))
                    }.getOrDefault(DeliveryStatus.PENDING),
                    serviceSeconds = if (o.isNull("serviceSeconds")) null else o.optLong("serviceSeconds")
                )
            }
            _stops.value = list
        }
    }
}

data class ImportedPackageCandidate(
    val br: String,
    val recipient: String? = null,
    val phone: String? = null,
    val address: String? = null,
    val originalOrder: Int? = null
)
