package com.routecopilot.routing

import com.routecopilot.data.model.RoutePoint
import kotlin.math.*

data class OptimizedRoute(val ordered: List<RoutePoint>, val approximateKm: Double)

object RouteOptimizer {
    fun nearestNeighbor(startLat: Double, startLon: Double, points: List<RoutePoint>): OptimizedRoute {
        val remaining = points.toMutableList()
        val ordered = mutableListOf<RoutePoint>()
        var lat = startLat
        var lon = startLon
        var km = 0.0
        while (remaining.isNotEmpty()) {
            val next = remaining.minBy { haversineKm(lat, lon, it.latitude, it.longitude) }
            km += haversineKm(lat, lon, next.latitude, next.longitude)
            ordered += next
            lat = next.latitude
            lon = next.longitude
            remaining.remove(next)
        }
        return OptimizedRoute(ordered, km)
    }

    fun haversineKm(aLat: Double, aLon: Double, bLat: Double, bLon: Double): Double {
        val r = 6371.0
        val dLat = Math.toRadians(bLat - aLat)
        val dLon = Math.toRadians(bLon - aLon)
        val s = sin(dLat / 2).pow(2.0) + cos(Math.toRadians(aLat)) * cos(Math.toRadians(bLat)) * sin(dLon / 2).pow(2.0)
        return 2 * r * asin(sqrt(s))
    }
}
