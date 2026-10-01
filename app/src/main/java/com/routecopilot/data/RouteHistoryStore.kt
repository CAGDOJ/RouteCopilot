package com.routecopilot.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

data class RouteHistoryEntry(
    val atId: String,
    val startedAt: Long,
    val finishedAt: Long,
    val distanceMeters: Double,
    val totalPackages: Int,
    val deliveredPackages: Int,
    val occurrences: Int
)

object RouteHistoryStore {

    private const val PREFS = "routecopilot_history_v1"
    private const val KEY_HISTORY = "history_json"
    private const val MAX_ENTRIES = 100

    private var appContext: Context? = null

    private val _entries = MutableStateFlow<List<RouteHistoryEntry>>(emptyList())
    val entries: StateFlow<List<RouteHistoryEntry>> = _entries.asStateFlow()

    fun initialize(context: Context) {
        if (appContext != null) return
        appContext = context.applicationContext

        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_HISTORY, null)

        _entries.value = parse(raw)
    }

    fun record(
        context: Context,
        entry: RouteHistoryEntry
    ) {
        initialize(context)

        val next = (
            listOf(entry) +
                _entries.value.filterNot {
                    it.atId.equals(entry.atId, ignoreCase = true) &&
                        it.startedAt == entry.startedAt
                }
            )
            .sortedByDescending { it.finishedAt }
            .take(MAX_ENTRIES)

        _entries.value = next
        persist()
    }

    private fun persist() {
        val context = appContext ?: return
        val array = JSONArray()

        _entries.value.forEach { entry ->
            array.put(
                JSONObject().apply {
                    put("atId", entry.atId)
                    put("startedAt", entry.startedAt)
                    put("finishedAt", entry.finishedAt)
                    put("distanceMeters", entry.distanceMeters)
                    put("totalPackages", entry.totalPackages)
                    put("deliveredPackages", entry.deliveredPackages)
                    put("occurrences", entry.occurrences)
                }
            )
        }

        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_HISTORY, array.toString())
            .apply()
    }

    private fun parse(raw: String?): List<RouteHistoryEntry> {
        if (raw.isNullOrBlank()) return emptyList()

        return runCatching {
            val array = JSONArray(raw)
            val result = mutableListOf<RouteHistoryEntry>()

            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)

                result += RouteHistoryEntry(
                    atId = item.optString("atId", ""),
                    startedAt = item.optLong("startedAt", 0L),
                    finishedAt = item.optLong("finishedAt", 0L),
                    distanceMeters = item.optDouble("distanceMeters", 0.0),
                    totalPackages = item.optInt("totalPackages", 0),
                    deliveredPackages = item.optInt("deliveredPackages", 0),
                    occurrences = item.optInt("occurrences", 0)
                )
            }

            result.sortedByDescending { it.finishedAt }
        }.getOrDefault(emptyList())
    }
}
