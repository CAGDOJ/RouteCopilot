package com.routecopilot.route

import kotlin.math.*

object RouteOptimizer {

    fun optimize(
        stops: List<DeliveryStop>,
        startLat: Double?,
        startLon: Double?
    ): List<DeliveryStop> {
        val geocoded = stops
            .filter {
                it.latitude != null &&
                    it.longitude != null &&
                    it.status != DeliveryStatus.DELIVERED
            }
            .toMutableList()

        val missingGeo = stops
            .filter {
                it.latitude == null ||
                    it.longitude == null
            }

        if (geocoded.isEmpty()) {
            return stops.mapIndexed { index, stop ->
                stop.copy(copilotOrder = index + 1)
            }
        }

        val result = mutableListOf<DeliveryStop>()

        var lat = startLat ?: geocoded.first().latitude!!
        var lon = startLon ?: geocoded.first().longitude!!

        while (geocoded.isNotEmpty()) {
            val next = geocoded.minByOrNull {
                haversineMeters(
                    lat,
                    lon,
                    it.latitude!!,
                    it.longitude!!
                )
            }!!

            geocoded.remove(next)
            result += next

            lat = next.latitude!!
            lon = next.longitude!!
        }

        return (result + missingGeo)
            .mapIndexed { index, stop ->
                stop.copy(copilotOrder = index + 1)
            }
    }

    fun haversineMeters(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double
    ): Double {
        val r = 6_371_000.0
        val p1 = Math.toRadians(lat1)
        val p2 = Math.toRadians(lat2)
        val dp = Math.toRadians(lat2 - lat1)
        val dl = Math.toRadians(lon2 - lon1)

        val a =
            sin(dp / 2).pow(2) +
                cos(p1) * cos(p2) * sin(dl / 2).pow(2)

        return 2 * r * atan2(sqrt(a), sqrt(1 - a))
    }
}
