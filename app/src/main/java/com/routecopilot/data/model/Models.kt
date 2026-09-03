package com.routecopilot.data.model

data class RouteRecord(
    val at: String,
    val loadDate: String?,
    val expectedTotal: Int?,
    val importedTotal: Int,
    val createdAt: Long = System.currentTimeMillis(),
    val status: String = "ATIVA"
)

data class PackageRecord(
    val br: String,
    val at: String,
    val address: String? = null,
    val recipient: String? = null,
    val phone: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val spxOrder: Int? = null,
    val copilotOrder: Int? = null,
    val status: String = "EM_ROTA"
)

data class RoutePoint(
    val br: String,
    val latitude: Double,
    val longitude: Double
)
