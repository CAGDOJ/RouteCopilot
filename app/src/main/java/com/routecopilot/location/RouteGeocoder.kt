package com.routecopilot.location

import android.content.Context
import android.location.Geocoder

import com.routecopilot.model.ImportedRoute

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

import java.util.Locale

object RouteGeocoder {

    @Suppress("DEPRECATION")
    suspend fun geocode(
        context: Context,
        route: ImportedRoute
    ): ImportedRoute =
        withContext(
            Dispatchers.IO
        ) {

            /*
             * O Excel do SPX já possui Latitude/Longitude.
             *
             * Portanto esses pontos NÃO passam pelo
             * geocoder.
             */
            if (
                route.packages.all {

                    it.latitude != null &&
                        it.longitude != null
                }
            ) {

                return@withContext route
            }

            /*
             * Só usamos geocoder como fallback para
             * algum Excel futuro que venha sem coordenadas.
             */
            if (
                !Geocoder.isPresent()
            ) {

                return@withContext route
            }

            val geocoder =
                Geocoder(
                    context,
                    Locale(
                        "pt",
                        "BR"
                    )
                )

            val cache =
                mutableMapOf<
                    String,
                    Pair<Double, Double>?
                    >()

            val updated =
                route.packages.map {
                        item ->

                    /*
                     * Já tem Latitude e Longitude?
                     *
                     * Não mexe.
                     */
                    if (
                        item.latitude != null &&
                        item.longitude != null
                    ) {

                        item

                    } else {

                        val query =
                            item.fullAddress
                                .trim()

                        if (
                            query.isBlank()
                        ) {

                            item

                        } else {

                            val key =
                                query.lowercase(
                                    Locale.ROOT
                                )

                            val coordinates =
                                if (
                                    cache.containsKey(
                                        key
                                    )
                                ) {

                                    cache[
                                        key
                                    ]

                                } else {

                                    val result =
                                        try {

                                            geocoder
                                                .getFromLocationName(
                                                    query,
                                                    1
                                                )
                                                ?.firstOrNull()
                                                ?.let {

                                                    it.latitude to
                                                        it.longitude
                                                }

                                        } catch (
                                            _: Exception
                                        ) {

                                            null
                                        }

                                    cache[
                                        key
                                    ] =
                                        result

                                    result
                                }

                            if (
                                coordinates != null
                            ) {

                                item.copy(

                                    latitude =
                                        coordinates.first,

                                    longitude =
                                        coordinates.second
                                )

                            } else {

                                item
                            }
                        }
                    }
                }

            route.copy(
                packages =
                    updated
            )
        }
}