package com.routecopilot.data.model

enum class DeliveryStatus {
    PENDING,
    OUT_FOR_DELIVERY,
    DELIVERED,
    OCCURRENCE,
    RETURN_LATER,
    UNKNOWN
}

data class Delivery(
    val trackingCode: String,
    val customerName: String? = null,
    val phone: String? = null,
    val address: String? = null,
    val neighborhood: String? = null,
    val status: DeliveryStatus = DeliveryStatus.PENDING,
    val spxOrder: Int? = null,
    val lastUpdatedAt: Long = System.currentTimeMillis()
)
