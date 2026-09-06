package com.routecopilot.route

import kotlin.math.ceil

object EtaEngine {

    /*
     * MVP:
     * velocidade média urbana conservadora + tempo de atendimento.
     * O tempo de atendimento é adaptativo: usa a média real das entregas
     * já concluídas; antes disso começa em 120 s por parada.
     */
    fun etaMinutes(
        orderedStops: List<DeliveryStop>,
        targetBr: String,
        courierLat: Double?,
        courierLon: Double?,
        averageServiceSeconds: Long,
        averageUrbanKmh: Double = 24.0
    ): Int {
        val targetIndex = orderedStops.indexOfFirst { it.br == targetBr }
        if (targetIndex < 0) return 0

        val pending = orderedStops
            .filter { it.status != DeliveryStatus.DELIVERED }
            .takeWhileInclusive { it.br != targetBr }

        var distanceMeters = 0.0
        var lastLat = courierLat
        var lastLon = courierLon

        pending.forEach { stop ->
            val lat = stop.latitude
            val lon = stop.longitude
            if (lat != null && lon != null && lastLat != null && lastLon != null) {
                distanceMeters += RouteOptimizer.haversineMeters(lastLat!!, lastLon!!, lat, lon)
                lastLat = lat
                lastLon = lon
            }
        }

        val travelSeconds =
            if (distanceMeters <= 0.0) 0.0
            else (distanceMeters / 1000.0) / averageUrbanKmh * 3600.0

        val stopsBefore = (pending.size - 1).coerceAtLeast(0)
        val serviceSeconds = stopsBefore * averageServiceSeconds

        return ceil((travelSeconds + serviceSeconds) / 60.0)
            .toInt()
            .coerceAtLeast(1)
    }

    private inline fun <T> Iterable<T>.takeWhileInclusive(
        predicate: (T) -> Boolean
    ): List<T> {
        val result = mutableListOf<T>()
        for (item in this) {
            result += item
            if (!predicate(item)) break
        }
        return result
    }
}
