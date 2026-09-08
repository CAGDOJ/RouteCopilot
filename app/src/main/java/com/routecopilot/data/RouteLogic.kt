package com.routecopilot.data

import java.text.Normalizer
import java.util.Locale

object RouteLogic {

    fun buildStops(
        route: RomaneioRoute,
        statuses: Map<String, PackageStatus>,
        coordinates: Map<String, GeoPoint> = emptyMap()
    ): List<RouteStop> {
        val active = route.packages.filter {
            statuses[it.spxTn] != PackageStatus.DELIVERED
        }

        val groups = active.groupBy { packageItem ->
            normalizeStopKey(packageItem)
        }

        val orderedGroups = groups.entries.sortedWith(
            compareBy<Map.Entry<String, List<RomaneioPackage>>> { entry ->
                val hasOnlyOccurrence = entry.value.all { pkg ->
                    val status = statuses[pkg.spxTn] ?: PackageStatus.PENDING
                    status == PackageStatus.OCCURRENCE ||
                        status == PackageStatus.POSSIBLE_OCCURRENCE
                }
                if (hasOnlyOccurrence) 1 else 0
            }.thenBy { entry ->
                entry.value.mapNotNull { it.stop }.minOrNull() ?: Int.MAX_VALUE
            }.thenBy { entry ->
                entry.value.mapNotNull { it.sequence }.minOrNull() ?: Int.MAX_VALUE
            }.thenBy { entry ->
                entry.value.minOfOrNull { it.sourceRow } ?: Int.MAX_VALUE
            }
        )

        return orderedGroups.mapIndexed { index, entry ->
            val packages = entry.value.sortedWith(
                compareBy<RomaneioPackage> { it.sequence ?: Int.MAX_VALUE }
                    .thenBy { it.sourceRow }
            )
            val first = packages.first()
            RouteStop(
                id = entry.key,
                stopNumber = index + 1,
                address = first.destinationAddress,
                bairro = first.bairro,
                city = first.city,
                zipcode = first.zipcode,
                packages = packages,
                statuses = statuses,
                coordinate = coordinates[entry.key]
            )
        }
    }

    fun nextStop(
        route: RomaneioRoute,
        statuses: Map<String, PackageStatus>,
        coordinates: Map<String, GeoPoint> = emptyMap()
    ): RouteStop? = buildStops(route, statuses, coordinates)
        .firstOrNull { stop ->
            stop.activePackages.any { pkg ->
                (statuses[pkg.spxTn] ?: PackageStatus.PENDING) == PackageStatus.PENDING
            }
        }

    fun normalizeStopKey(pkg: RomaneioPackage): String {
        val raw = listOf(
            pkg.destinationAddress,
            pkg.bairro,
            pkg.city,
            pkg.zipcode
        ).joinToString("|")

        return Normalizer.normalize(raw.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace("\\p{Mn}+".toRegex(), "")
            .replace("[^a-z0-9|]+".toRegex(), " ")
            .replace("\\s+".toRegex(), " ")
            .trim()
    }
}
