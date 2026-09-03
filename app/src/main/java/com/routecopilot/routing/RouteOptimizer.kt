package com.routecopilot.routing

import com.routecopilot.data.model.RoutePoint
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

data class OptimizedRoute(
    val ordered: List<RoutePoint>,
    val approximateKm: Double
)

object RouteOptimizer {
    fun nearestNeighbor(
        startLat: Double,
        startLon: Double,
        points: List<RoutePoint>
    ): OptimizedRoute {
        if (points.isEmpty()) {
            return OptimizedRoute(
                emptyList(),
                0.0
            )
        }

        val remaining = points.toMutableList()
        val ordered = mutableListOf<RoutePoint>()

        var currentLat = startLat
        var currentLon = startLon
        var totalKm = 0.0

        while (remaining.isNotEmpty()) {
            val next = remaining.minBy { point ->
                haversineKm(
                    currentLat,
                    currentLon,
                    point.latitude,
                    point.longitude
                )
            }

            totalKm += haversineKm(
                currentLat,
                currentLon,
                next.latitude,
                next.longitude
            )

            ordered += next
            currentLat = next.latitude
            currentLon = next.longitude
            remaining.remove(next)
        }

        return OptimizedRoute(
            ordered = ordered,
            approximateKm = totalKm
        )
    }

    fun haversineKm(
        aLat: Double,
        aLon: Double,
        bLat: Double,
        bLon: Double
    ): Double {
        val earthRadiusKm = 6371.0

        val dLat = Math.toRadians(
            bLat - aLat
        )

        val dLon = Math.toRadians(
            bLon - aLon
        )

        val value =
            sin(dLat / 2.0).pow(2.0) +
                cos(Math.toRadians(aLat)) *
                cos(Math.toRadians(bLat)) *
                sin(dLon / 2.0).pow(2.0)

        return 2.0 *
            earthRadiusKm *
            asin(
                sqrt(value)
            )
    }
}
