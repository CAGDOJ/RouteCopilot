package com.routecopilot.routing

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.LocationManager
import com.routecopilot.model.DeliveryItem
import com.routecopilot.model.RoadPoint
import com.routecopilot.model.RouteData
import com.routecopilot.model.RoutePlan
import com.routecopilot.model.RouteStop
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

object RoutePlanner {

    fun plan(
        context: Context,
        route: RouteData,
        onProgress: (String) -> Unit
    ): RouteData {

        val geocoder =
            Geocoder(
                context,
                Locale("pt", "BR")
            )

        val geocoded =
            route.deliveries
                .mapIndexed { index, item ->

                    onProgress(
                        "Localizando pedidos: ${index + 1}/${route.deliveries.size}"
                    )

                    if (
                        item.latitude != null &&
                        item.longitude != null
                    ) {

                        item

                    } else {

                        geocodeDelivery(
                            geocoder,
                            item
                        )
                    }
                }

        val start =
            getLastKnownLocation(
                context
            )

        onProgress(
            "Calculando melhor sequência..."
        )

        val ordered =
            optimizeOrder(
                geocoded,
                start
            )

        val stops =
            ordered
                .mapIndexed { index, item ->

                    RouteStop(
                        order =
                            index + 1,

                        delivery =
                            item
                    )
                }

        val resolved =
            stops.filter {
                it.latitude != null &&
                    it.longitude != null
            }

        val unresolved =
            stops.size -
                resolved.size

        onProgress(
            "Montando trajeto pelas ruas..."
        )

        val road =
            buildRoadRoute(
                resolved
            )

        val plan =
            RoutePlan(
                stops =
                    stops,

                polyline =
                    road.points,

                distanceMeters =
                    road.distance,

                durationSeconds =
                    road.duration,

                unresolvedCount =
                    unresolved,

                roadRoutingAvailable =
                    road.roadRoutingAvailable
            )

        return route.copy(
            deliveries =
                ordered,

            plan =
                plan
        )
    }

    private fun geocodeDelivery(
        geocoder: Geocoder,
        delivery: DeliveryItem
    ): DeliveryItem {

        val parts =
            listOfNotNull(
                delivery.address,
                delivery.neighborhood,
                delivery.city,
                delivery.state,
                delivery.zipCode,
                "Brasil"
            )
                .map {
                    it.trim()
                }
                .filter {
                    it.isNotBlank()
                }
                .distinct()

        if (parts.size <= 1) {
            return delivery
        }

        val query =
            parts.joinToString(", ")

        return try {

            @Suppress("DEPRECATION")
            val result =
                geocoder
                    .getFromLocationName(
                        query,
                        1
                    )
                    ?.firstOrNull()

            if (result != null) {

                delivery.copy(
                    latitude =
                        result.latitude,

                    longitude =
                        result.longitude
                )

            } else {

                delivery
            }

        } catch (_: Exception) {

            delivery
        }
    }

    private fun getLastKnownLocation(
        context: Context
    ): Pair<Double, Double>? {

        val fine =
            context.checkSelfPermission(
                Manifest.permission.ACCESS_FINE_LOCATION
            ) ==
                PackageManager.PERMISSION_GRANTED

        val coarse =
            context.checkSelfPermission(
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) ==
                PackageManager.PERMISSION_GRANTED

        if (
            !fine &&
            !coarse
        ) {
            return null
        }

        val manager =
            context.getSystemService(
                Context.LOCATION_SERVICE
            ) as LocationManager

        return try {

            manager
                .getProviders(true)
                .mapNotNull { provider ->

                    try {
                        manager.getLastKnownLocation(
                            provider
                        )
                    } catch (_: Exception) {
                        null
                    }
                }
                .maxByOrNull {
                    it.time
                }
                ?.let {
                    Pair(
                        it.latitude,
                        it.longitude
                    )
                }

        } catch (_: Exception) {

            null
        }
    }

    private fun optimizeOrder(
        deliveries: List<DeliveryItem>,
        start: Pair<Double, Double>?
    ): List<DeliveryItem> {

        val resolved =
            deliveries
                .filter {
                    it.latitude != null &&
                        it.longitude != null
                }
                .toMutableList()

        val unresolved =
            deliveries.filter {
                it.latitude == null ||
                    it.longitude == null
            }

        if (resolved.isEmpty()) {
            return deliveries
        }

        val result =
            mutableListOf<DeliveryItem>()

        var currentLat =
            start?.first
                ?: resolved.first().latitude!!

        var currentLon =
            start?.second
                ?: resolved.first().longitude!!

        while (
            resolved.isNotEmpty()
        ) {

            val next =
                resolved.minByOrNull {

                    haversine(
                        currentLat,
                        currentLon,
                        it.latitude!!,
                        it.longitude!!
                    )
                }
                    ?: break

            result.add(
                next
            )

            currentLat =
                next.latitude!!

            currentLon =
                next.longitude!!

            resolved.remove(
                next
            )
        }

        result.addAll(
            unresolved
        )

        return result
    }

    private data class RoadResult(
        val points: List<RoadPoint>,
        val distance: Double,
        val duration: Double,
        val roadRoutingAvailable: Boolean
    )

    private fun buildRoadRoute(
        stops: List<RouteStop>
    ): RoadResult {

        if (stops.isEmpty()) {

            return RoadResult(
                points =
                    emptyList(),

                distance =
                    0.0,

                duration =
                    0.0,

                roadRoutingAvailable =
                    false
            )
        }

        if (stops.size == 1) {

            return RoadResult(
                points =
                    listOf(
                        RoadPoint(
                            latitude =
                                stops.first().latitude!!,

                            longitude =
                                stops.first().longitude!!
                        )
                    ),

                distance =
                    0.0,

                duration =
                    0.0,

                roadRoutingAvailable =
                    false
            )
        }

        val coordinates =
            stops.joinToString(
                ";"
            ) {

                "${it.longitude},${it.latitude}"
            }

        val url =
            URL(
                "https://router.project-osrm.org/route/v1/driving/" +
                    coordinates +
                    "?overview=full&geometries=geojson&steps=false"
            )

        return try {

            val connection =
                url.openConnection()
                    as HttpURLConnection

            connection.requestMethod =
                "GET"

            connection.connectTimeout =
                15_000

            connection.readTimeout =
                20_000

            connection.setRequestProperty(
                "User-Agent",
                "RouteCopilot/1.0"
            )

            val response =
                connection
                    .inputStream
                    .bufferedReader()
                    .use {
                        it.readText()
                    }

            connection.disconnect()

            val root =
                JSONObject(
                    response
                )

            val routes =
                root.getJSONArray(
                    "routes"
                )

            if (
                routes.length() == 0
            ) {
                throw IllegalStateException(
                    "Sem rota"
                )
            }

            val route =
                routes.getJSONObject(
                    0
                )

            val distance =
                route.optDouble(
                    "distance",
                    0.0
                )

            val duration =
                route.optDouble(
                    "duration",
                    0.0
                )

            val coordinatesJson =
                route
                    .getJSONObject(
                        "geometry"
                    )
                    .getJSONArray(
                        "coordinates"
                    )

            val points =
                mutableListOf<RoadPoint>()

            for (
                i in 0 until
                    coordinatesJson.length()
            ) {

                val pair =
                    coordinatesJson
                        .getJSONArray(
                            i
                        )

                points.add(
                    RoadPoint(
                        latitude =
                            pair.getDouble(
                                1
                            ),

                        longitude =
                            pair.getDouble(
                                0
                            )
                    )
                )
            }

            RoadResult(
                points =
                    points,

                distance =
                    distance,

                duration =
                    duration,

                roadRoutingAvailable =
                    true
            )

        } catch (_: Exception) {

            val points =
                stops.map {

                    RoadPoint(
                        latitude =
                            it.latitude!!,

                        longitude =
                            it.longitude!!
                    )
                }

            var distance =
                0.0

            for (
                i in 1 until
                    points.size
            ) {

                distance +=
                    haversine(
                        points[i - 1].latitude,
                        points[i - 1].longitude,
                        points[i].latitude,
                        points[i].longitude
                    )
            }

            RoadResult(
                points =
                    points,

                distance =
                    distance,

                duration =
                    if (distance > 0) {
                        distance /
                            25_000.0 *
                            3600.0
                    } else {
                        0.0
                    },

                roadRoutingAvailable =
                    false
            )
        }
    }

    private fun haversine(
        lat1: Double,
        lon1: Double,
        lat2: Double,
        lon2: Double
    ): Double {

        val earthRadius =
            6_371_000.0

        val dLat =
            Math.toRadians(
                lat2 - lat1
            )

        val dLon =
            Math.toRadians(
                lon2 - lon1
            )

        val a =
            sin(dLat / 2)
                .pow(2) +
                cos(
                    Math.toRadians(lat1)
                ) *
                cos(
                    Math.toRadians(lat2)
                ) *
                sin(dLon / 2)
                    .pow(2)

        val c =
            2 *
                asin(
                    sqrt(a)
                )

        return earthRadius *
            c
    }
}