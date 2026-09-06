package com.routecopilot.route

import kotlin.math.*

object RouteOptimizer {

    fun optimize(
        stops: List<DeliveryStop>,
        startLat: Double?,
        startLon: Double?
    ): List<DeliveryStop> {
        val geo = stops.filter { it.latitude != null && it.longitude != null }.toMutableList()
        val noGeo = stops.filter { it.latitude == null || it.longitude == null }

        if (geo.isEmpty()) return stops.mapIndexed { index, s ->
            s.copy(copilotOrder = index + 1)
        }

        val result = mutableListOf<DeliveryStop>()
        var lat = startLat ?: geo.first().latitude!!
        var lon = startLon ?: geo.first().longitude!!

        while (geo.isNotEmpty()) {
            val next = geo.minByOrNull {
                haversineMeters(lat, lon, it.latitude!!, it.longitude!!)
            }!!
            geo.remove(next)
            result += next
            lat = next.latitude!!
            lon = next.longitude!!
        }

        val ordered = (result + noGeo).mapIndexed { index, s ->
            s.copy(copilotOrder = index + 1)
        }
        return ordered
    }

    fun haversineMeters(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double
    ): Double {
        val r = 6371000.0
        val p1 = Math.toRadians(lat1)
        val p2 = Math.toRadians(lat2)
        val dp = Math.toRadians(lat2 - lat1)
        val dl = Math.toRadians(lon2 - lon1)
        val a = sin(dp / 2).pow(2) +
            cos(p1) * cos(p2) * sin(dl / 2).pow(2)
        return 2 * r * atan2(sqrt(a), sqrt(1 - a))
    }
}
