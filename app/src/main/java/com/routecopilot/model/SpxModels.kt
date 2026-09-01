package com.routecopilot.model

data class DeliveryItem(
    val tracking: String,
    val stop: String? = null,
    val address: String? = null,
    val neighborhood: String? = null,
    val city: String? = null,
    val state: String? = null,
    val zipCode: String? = null,
    val recipient: String? = null,
    val phone: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val rawColumns: Map<String, String> = emptyMap()
)

data class RoadPoint(
    val latitude: Double,
    val longitude: Double
)

data class RouteStop(
    val order: Int,
    val delivery: DeliveryItem
) {
    val latitude: Double?
        get() = delivery.latitude

    val longitude: Double?
        get() = delivery.longitude
}

data class RoutePlan(
    val stops: List<RouteStop> = emptyList(),
    val polyline: List<RoadPoint> = emptyList(),
    val distanceMeters: Double = 0.0,
    val durationSeconds: Double = 0.0,
    val unresolvedCount: Int = 0,
    val roadRoutingAvailable: Boolean = false
)

data class RouteData(
    val at: String,
    val loadDate: String? = null,
    val deliveries: List<DeliveryItem> = emptyList(),
    val sourceFileName: String? = null,
    val loadedAt: Long = System.currentTimeMillis(),
    val plan: RoutePlan? = null
) {
    val totalDeliveries: Int
        get() = deliveries.size

    val neighborhoods: List<String>
        get() = deliveries
            .mapNotNull { it.neighborhood?.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()
}