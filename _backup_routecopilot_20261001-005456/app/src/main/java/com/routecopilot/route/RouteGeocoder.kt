package com.routecopilot.route

import android.content.Context
import android.location.Geocoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.Locale

object RouteGeocoder {

    @Suppress("DEPRECATION")
    suspend fun geocodeMissing(
        context: Context,
        onProgress: (done: Int, total: Int) -> Unit = { _, _ -> }
    ) {
        val route = RouteRepository.activeRoute.value ?: return

        val targets = route.packages.filter {
            !it.address.isNullOrBlank() &&
                (it.latitude == null || it.longitude == null)
        }

        if (targets.isEmpty()) return

        withContext(Dispatchers.IO) {
            val geocoder = Geocoder(
                context.applicationContext,
                Locale("pt", "BR")
            )

            targets.forEachIndexed { index, pkg ->
                val query = "${pkg.address}, PA, Brasil"

                runCatching {
                    val result =
                        geocoder.getFromLocationName(query, 1)
                            ?.firstOrNull()

                    if (result != null) {
                        RouteRepository.updatePackageCoordinates(
                            pkg.br,
                            result.latitude,
                            result.longitude
                        )
                    }
                }

                onProgress(index + 1, targets.size)
                delay(250L)
            }
        }
    }
}
