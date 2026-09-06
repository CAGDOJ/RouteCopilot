package com.routecopilot.route

data class DeliveryStop(
    val br: String,
    val recipient: String? = null,
    val phone: String? = null,
    val address: String? = null,
    val neighborhood: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val originalOrder: Int? = null,
    val copilotOrder: Int? = null,
    val trackingToken: String = java.util.UUID.randomUUID().toString(),
    val status: DeliveryStatus = DeliveryStatus.PENDING,
    val serviceSeconds: Long? = null,
    val messageSent: Boolean = false
)

enum class DeliveryStatus {
    PENDING,
    NEXT,
    DELIVERED,
    OCCURRENCE,
    ADDRESS_REVIEW
}

data class ImportedPackageCandidate(
    val br: String,
    val recipient: String? = null,
    val phone: String? = null,
    val address: String? = null,
    val neighborhood: String? = null,
    val originalOrder: Int? = null
)
