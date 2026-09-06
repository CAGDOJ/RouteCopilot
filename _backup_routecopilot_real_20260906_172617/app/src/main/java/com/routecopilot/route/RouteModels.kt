package com.routecopilot.route

data class DeliveryStop(
    val br: String,
    val recipient: String? = null,
    val phone: String? = null,
    val address: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val originalOrder: Int? = null,
    val copilotOrder: Int? = null,
    val trackingToken: String = java.util.UUID.randomUUID().toString(),
    val status: DeliveryStatus = DeliveryStatus.PENDING,
    val serviceSeconds: Long? = null
)

enum class DeliveryStatus {
    PENDING,
    NEXT,
    DELIVERED,
    OCCURRENCE,
    ADDRESS_REVIEW
}

data class RoutePlan(
    val at: String? = null,
    val loadedAt: String? = null,
    val stops: List<DeliveryStop> = emptyList(),
    val optimized: Boolean = false
)
