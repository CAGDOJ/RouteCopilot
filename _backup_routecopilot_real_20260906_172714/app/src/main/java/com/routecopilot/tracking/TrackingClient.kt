package com.routecopilot.tracking

import com.routecopilot.route.DeliveryStop
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

object TrackingClient {

    private val executor = Executors.newSingleThreadExecutor()

    fun trackingLink(stop: DeliveryStop): String? {
        val base = TrackingConfig.BASE_URL.trim().trimEnd('/')
        if (base.isBlank()) return null
        return "$base/r/${stop.trackingToken}"
    }

    fun update(
        stop: DeliveryStop,
        courierLat: Double,
        courierLon: Double,
        etaMinutes: Int
    ) {
        val base = TrackingConfig.BASE_URL.trim().trimEnd('/')
        if (base.isBlank()) return

        executor.execute {
            runCatching {
                val url = URL("$base/api/track/${stop.trackingToken}")
                val connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 8000
                    readTimeout = 8000
                    doOutput = true
                    setRequestProperty(
                        "Content-Type",
                        "application/json; charset=utf-8"
                    )
                }

                val body = JSONObject().apply {
                    put("courier_lat", courierLat)
                    put("courier_lon", courierLon)
                    put("destination_lat", stop.latitude)
                    put("destination_lon", stop.longitude)
                    put("eta_minutes", etaMinutes)
                    put("status", stop.status.name)
                }.toString()

                connection.outputStream.use {
                    it.write(body.toByteArray(Charsets.UTF_8))
                }

                connection.responseCode
                connection.disconnect()
            }
        }
    }
}
