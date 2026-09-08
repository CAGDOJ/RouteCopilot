package com.routecopilot.data

enum class PackageStatus {
    PENDING,
    POSSIBLE_OCCURRENCE,
    OCCURRENCE,
    DELIVERED
}

enum class RouteRunState {
    IDLE,
    IN_PROGRESS,
    PAUSED
}

enum class DeliveryPreferenceType {
    NONE,
    HANDS,
    NEIGHBOR,
    PORTER,
    PORCH_MAILBOX,
    NOBODY_AVAILABLE
}

data class ClientPreference(
    val type: DeliveryPreferenceType = DeliveryPreferenceType.NONE,
    val neighborName: String = "",
    val neighborPhone: String = "",
    val hasKeyword: Boolean = false,
    val keyword: String = "",
    val confirmedAt: Long? = null
)

data class GeoPoint(
    val latitude: Double,
    val longitude: Double
)

data class RomaneioPackage(
    val atId: String,
    val sequence: Int?,
    val stop: Int?,
    val spxTn: String,
    val recipientName: String,
    val phone: String,
    val destinationAddress: String,
    val bairro: String,
    val city: String,
    val zipcode: String,
    val sourceRow: Int
) {
    val navigationAddress: String
        get() = listOf(
            destinationAddress.trim(),
            bairro.trim(),
            city.trim(),
            "PA",
            zipcode.trim(),
            "Brasil"
        )
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString(", ")

    val routeKeyAddress: String
        get() = listOf(
            destinationAddress,
            bairro,
            city,
            zipcode
        )
            .joinToString("|") { it.trim().lowercase() }
}

data class RomaneioRoute(
    val atId: String,
    val loadDate: String?,
    val sourceFileName: String,
    val sourceLastModified: Long,
    val packages: List<RomaneioPackage>
) {
    val routeKey: String
        get() = "${loadDate.orEmpty()}|${atId.uppercase()}"
}

data class RouteSyncResult(
    val routes: List<RomaneioRoute>,
    val sourceDate: String?
)

data class RouteStop(
    val id: String,
    val stopNumber: Int,
    val address: String,
    val bairro: String,
    val city: String,
    val zipcode: String,
    val packages: List<RomaneioPackage>,
    val statuses: Map<String, PackageStatus>,
    val coordinate: GeoPoint? = null
) {
    val activePackages: List<RomaneioPackage>
        get() = packages.filter {
            statuses[it.spxTn] != PackageStatus.DELIVERED
        }

    val hasOccurrence: Boolean
        get() = activePackages.any {
            val status = statuses[it.spxTn] ?: PackageStatus.PENDING
            status == PackageStatus.OCCURRENCE ||
                status == PackageStatus.POSSIBLE_OCCURRENCE
        }

    val pendingCount: Int
        get() = activePackages.count {
            (statuses[it.spxTn] ?: PackageStatus.PENDING) == PackageStatus.PENDING
        }
}

data class ActivityEntry(
    val timestamp: Long,
    val text: String,
    val isWarning: Boolean = false
)
