package com.routecopilot.route

import kotlin.math.ceil

object EtaEngine {

    fun etaMinutes(
        orderedStops: List<DeliveryStop>,
        targetBr: String,
        courierLat: Double?,
        courierLon: Double?,
        averageServiceSeconds: Long,
        averageUrbanKmh: Double = 24.0,
        fallbackTravelSecondsPerLeg: Long = 180L
    ): Int {
        val pending = orderedStops.filter {
            it.status != DeliveryStatus.DELIVERED &&
                it.status != DeliveryStatus.OCCURRENCE
        }

        val targetIndex = pending.indexOfFirst { it.br == targetBr }
        if (targetIndex < 0) return 0

        val untilTarget = pending.take(targetIndex + 1)

        var travelSeconds = 0.0
        var lastLat = courierLat
        var lastLon = courierLon

        untilTarget.forEach { stop ->
            val lat = stop.latitude
            val lon = stop.longitude

            if (
                lat != null &&
                lon != null &&
                lastLat != null &&
                lastLon != null
            ) {
                val meters = RouteOptimizer.haversineMeters(
                    lastLat!!,
                    lastLon!!,
                    lat,
                    lon
                )

                val roadApproxKm = meters / 1000.0 * 1.25
                travelSeconds += roadApproxKm / averageUrbanKmh * 3600.0

                lastLat = lat
                lastLon = lon
            } else {
                travelSeconds += fallbackTravelSecondsPerLeg
            }
        }

        val stopsBefore = targetIndex
        val serviceSeconds = stopsBefore * averageServiceSeconds

        return ceil(
            (travelSeconds + serviceSeconds) / 60.0
        ).toInt().coerceAtLeast(1)
    }
}
