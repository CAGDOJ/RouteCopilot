package com.routecopilot.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object RoutePersistence {
    private const val PREFS = "routecopilot_state_v4"
    private const val KEY_STATE = "state_json"

    data class Snapshot(
        val route: RomaneioRoute?,
        val statuses: Map<String, PackageStatus>,
        val preferences: Map<String, ClientPreference>,
        val runState: RouteRunState,
        val pauseReason: String,
        val lastSync: Long
    )

    fun save(
        context: Context,
        route: RomaneioRoute?,
        statuses: Map<String, PackageStatus>,
        preferences: Map<String, ClientPreference>,
        runState: RouteRunState,
        pauseReason: String,
        lastSync: Long
    ) {
        val root = JSONObject()
        root.put("runState", runState.name)
        root.put("pauseReason", pauseReason)
        root.put("lastSync", lastSync)

        if (route != null) {
            root.put("route", routeToJson(route))
        }

        val statusJson = JSONObject()
        statuses.forEach { (br, status) -> statusJson.put(br, status.name) }
        root.put("statuses", statusJson)

        val prefJson = JSONObject()
        preferences.forEach { (br, pref) ->
            prefJson.put(br, JSONObject().apply {
                put("type", pref.type.name)
                put("neighborName", pref.neighborName)
                put("neighborPhone", pref.neighborPhone)
                put("hasKeyword", pref.hasKeyword)
                put("keyword", pref.keyword)
                if (pref.confirmedAt != null) put("confirmedAt", pref.confirmedAt)
            })
        }
        root.put("preferences", prefJson)

        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_STATE, root.toString())
            .apply()
    }

    fun load(context: Context): Snapshot? {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_STATE, null)
            ?: return null

        return runCatching {
            val root = JSONObject(raw)
            val route = if (root.has("route")) routeFromJson(root.getJSONObject("route")) else null

            val statuses = linkedMapOf<String, PackageStatus>()
            root.optJSONObject("statuses")?.let { obj ->
                obj.keys().forEach { key ->
                    val value = obj.optString(key)
                    runCatching { PackageStatus.valueOf(value) }
                        .getOrNull()
                        ?.let { statuses[key] = it }
                }
            }

            val preferences = linkedMapOf<String, ClientPreference>()
            root.optJSONObject("preferences")?.let { obj ->
                obj.keys().forEach { key ->
                    val p = obj.optJSONObject(key) ?: return@forEach
                    val type = runCatching {
                        DeliveryPreferenceType.valueOf(p.optString("type", "NONE"))
                    }.getOrDefault(DeliveryPreferenceType.NONE)

                    preferences[key] = ClientPreference(
                        type = type,
                        neighborName = p.optString("neighborName", ""),
                        neighborPhone = p.optString("neighborPhone", ""),
                        hasKeyword = p.optBoolean("hasKeyword", false),
                        keyword = p.optString("keyword", ""),
                        confirmedAt = if (p.has("confirmedAt")) p.optLong("confirmedAt") else null
                    )
                }
            }

            Snapshot(
                route = route,
                statuses = statuses,
                preferences = preferences,
                runState = runCatching {
                    RouteRunState.valueOf(root.optString("runState", "IDLE"))
                }.getOrDefault(RouteRunState.IDLE),
                pauseReason = root.optString("pauseReason", ""),
                lastSync = root.optLong("lastSync", 0L)
            )
        }.getOrNull()
    }

    private fun routeToJson(route: RomaneioRoute): JSONObject = JSONObject().apply {
        put("atId", route.atId)
        put("loadDate", route.loadDate)
        put("sourceFileName", route.sourceFileName)
        put("sourceLastModified", route.sourceLastModified)
        put("packages", JSONArray().apply {
            route.packages.forEach { pkg ->
                put(JSONObject().apply {
                    put("atId", pkg.atId)
                    put("sequence", pkg.sequence)
                    put("stop", pkg.stop)
                    put("spxTn", pkg.spxTn)
                    put("recipientName", pkg.recipientName)
                    put("phone", pkg.phone)
                    put("destinationAddress", pkg.destinationAddress)
                    put("bairro", pkg.bairro)
                    put("city", pkg.city)
                    put("zipcode", pkg.zipcode)
                    put("sourceRow", pkg.sourceRow)
                })
            }
        })
    }

    private fun routeFromJson(obj: JSONObject): RomaneioRoute {
        val packages = mutableListOf<RomaneioPackage>()
        val array = obj.optJSONArray("packages") ?: JSONArray()
        for (i in 0 until array.length()) {
            val p = array.getJSONObject(i)
            packages += RomaneioPackage(
                atId = p.optString("atId", ""),
                sequence = if (p.isNull("sequence")) null else p.optInt("sequence"),
                stop = if (p.isNull("stop")) null else p.optInt("stop"),
                spxTn = p.optString("spxTn", ""),
                recipientName = p.optString("recipientName", ""),
                phone = p.optString("phone", ""),
                destinationAddress = p.optString("destinationAddress", ""),
                bairro = p.optString("bairro", ""),
                city = p.optString("city", ""),
                zipcode = p.optString("zipcode", ""),
                sourceRow = p.optInt("sourceRow", 0)
            )
        }

        return RomaneioRoute(
            atId = obj.optString("atId", "AT não identificada"),
            loadDate = obj.optString("loadDate", "").takeIf { it.isNotBlank() && it != "null" },
            sourceFileName = obj.optString("sourceFileName", ""),
            sourceLastModified = obj.optLong("sourceLastModified", 0L),
            packages = packages
        )
    }
}
