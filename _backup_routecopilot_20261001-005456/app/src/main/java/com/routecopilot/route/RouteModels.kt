package com.routecopilot.route

enum class DeliveryStatus {
    IMPORTED,
    NEXT,
    TRACKING_ACTIVE,
    DELIVERED,
    OCCURRENCE,
    RETURN_LATER,
    ADDRESS_DOUBTFUL,
    OUT_OF_ROUTE
}

data class ImportedPackageCandidate(
    val br: String,
    val recipient: String? = null,
    val phone: String? = null,
    val address: String? = null,
    val originalOrder: Int = 0
)

data class RoutePackage(
    val br: String,
    val recipient: String? = null,
    val phone: String? = null,
    val address: String? = null,
    val sequence: Int,
    val status: DeliveryStatus = DeliveryStatus.IMPORTED,
    val latitude: Double? = null,
    val longitude: Double? = null
)

data class DriverLocation(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float? = null,
    val updatedAt: Long = System.currentTimeMillis()
)

data class ActiveRoute(
    val at: String,
    val loadingDate: String? = null,
    val expectedTotal: Int? = null,
    val packages: List<RoutePackage> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val ready: Boolean = false,
    val partial: Boolean = false
)

data class RouteHistoryItem(
    val at: String,
    val loadingDate: String? = null,
    val packageCount: Int,
    val finishedAt: Long
)
