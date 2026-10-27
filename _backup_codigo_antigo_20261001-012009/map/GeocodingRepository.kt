package com.routecopilot.map

import android.content.Context
import android.location.Geocoder
import com.routecopilot.data.GeoPoint
import com.routecopilot.data.RouteStop
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

object GeocodingRepository {
    private const val PREFS = "routecopilot_geocode_cache"

    suspend fun resolveStops(
        context: Context,
        stops: List<RouteStop>,
        onProgress: (Int, Int, Map<String, GeoPoint>) -> Unit
    ): Map<String, GeoPoint> = withContext(Dispatchers.IO) {
        val cache = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val result = linkedMapOf<String, GeoPoint>()
        var uncachedRequestMade = false

        stops.forEachIndexed { index, stop ->
            val address = buildAddress(stop)
            val key = address.lowercase(Locale.ROOT).trim()
            val cached = cache.getString(key, null)?.let(::parsePoint)

            val point = cached ?: run {
                if (uncachedRequestMade) delay(250L)
                uncachedRequestMade = true
                resolveWithAndroidGeocoder(context, address)
                    ?: resolveWithNominatim(address)
            }

            if (point != null) {
                result[stop.id] = point
                cache.edit()
                    .putString(key, "${point.latitude},${point.longitude}")
                    .apply()
            }

            withContext(Dispatchers.Main) {
                onProgress(index + 1, stops.size, result.toMap())
            }
        }

        result
    }

    private fun buildAddress(stop: RouteStop): String = listOf(
        stop.address,
        stop.bairro,
        stop.city,
        "PA",
        stop.zipcode,
        "Brasil"
    ).filter { it.isNotBlank() }.distinct().joinToString(", ")

    @Suppress("DEPRECATION")
    private fun resolveWithAndroidGeocoder(context: Context, address: String): GeoPoint? {
        return runCatching {
            if (!Geocoder.isPresent()) return null
            val geocoder = Geocoder(context, Locale("pt", "BR"))
            val match = geocoder.getFromLocationName(address, 1)?.firstOrNull() ?: return null
            GeoPoint(match.latitude, match.longitude)
        }.getOrNull()
    }

    private fun resolveWithNominatim(address: String): GeoPoint? {
        return runCatching {
            val q = URLEncoder.encode(address, Charsets.UTF_8.name())
            val url = URL(
                "https://nominatim.openstreetmap.org/search?format=jsonv2&limit=1&countrycodes=br&q=$q"
            )
            val connection = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 6000
                readTimeout = 6000
                requestMethod = "GET"
                setRequestProperty("User-Agent", "RouteCopilot/1.0 Android")
                setRequestProperty("Accept-Language", "pt-BR")
            }

            connection.inputStream.bufferedReader().use { reader ->
                val array = JSONArray(reader.readText())
                if (array.length() == 0) return null
                val item = array.getJSONObject(0)
                val lat = item.optString("lat").toDoubleOrNull() ?: return null
                val lon = item.optString("lon").toDoubleOrNull() ?: return null
                GeoPoint(lat, lon)
            }
        }.getOrNull()
    }

    private fun parsePoint(raw: String): GeoPoint? {
        val parts = raw.split(',')
        if (parts.size != 2) return null
        val lat = parts[0].toDoubleOrNull() ?: return null
        val lon = parts[1].toDoubleOrNull() ?: return null
        return GeoPoint(lat, lon)
    }
}
