package com.routecopilot.route

import android.content.Context
import android.location.Geocoder
import java.util.Locale
import java.util.concurrent.Executors

object GeocodingManager {

    private val executor = Executors.newSingleThreadExecutor()

    fun geocodeMissing(
        context: Context,
        defaultArea: String = "Belém, PA, Brasil",
        onFinished: (() -> Unit)? = null
    ) {
        RouteRepository.initialize(context)

        executor.execute {
            val geocoder = Geocoder(context, Locale("pt", "BR"))
            RouteRepository.stops.value.forEach { stop ->
                if (
                    stop.latitude == null &&
                    stop.longitude == null &&
                    !stop.address.isNullOrBlank()
                ) {
                    val query = buildString {
                        append(stop.address)
                        if (
                            !stop.address.contains("PA", ignoreCase = true) &&
                            !stop.address.contains("Belém", ignoreCase = true) &&
                            !stop.address.contains("Ananindeua", ignoreCase = true)
                        ) {
                            append(", ")
                            append(defaultArea)
                        }
                    }

                    val result = runCatching {
                        @Suppress("DEPRECATION")
                        geocoder.getFromLocationName(query, 1)
                    }.getOrNull()?.firstOrNull()

                    if (result != null) {
                        RouteRepository.updateStop(
                            stop.copy(
                                latitude = result.latitude,
                                longitude = result.longitude
                            )
                        )
                    }
                }
            }
            onFinished?.invoke()
        }
    }
}
