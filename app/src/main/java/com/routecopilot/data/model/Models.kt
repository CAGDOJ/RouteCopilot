package com.routecopilot.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "routes")
data class RouteEntity(
    @PrimaryKey val at: String,
    val loadDate: String?,
    val expectedTotal: Int?,
    val createdAt: Long = System.currentTimeMillis(),
    val status: String = "ATIVA"
)

@Entity(tableName = "packages")
data class PackageEntity(
    @PrimaryKey val br: String,
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

data class RoutePoint(val br: String, val latitude: Double, val longitude: Double)
