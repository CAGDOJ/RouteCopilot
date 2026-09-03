package com.routecopilot.data.db

import android.content.ContentValues
import com.routecopilot.data.model.PackageRecord
import com.routecopilot.data.model.RouteRecord

class RouteDao(
    private val database: AppDatabase
) {
    fun upsertRoute(route: RouteRecord) {
        val values = ContentValues().apply {
            put("at", route.at)
            put("load_date", route.loadDate)
            put("expected_total", route.expectedTotal)
            put("imported_total", route.importedTotal)
            put("created_at", route.createdAt)
            put("status", route.status)
        }

        database.writableDatabase.insertWithOnConflict(
            "routes",
            null,
            values,
            android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    fun upsertPackages(packages: Collection<PackageRecord>) {
        val db = database.writableDatabase

        db.beginTransaction()
        try {
            packages.forEach { item ->
                val values = ContentValues().apply {
                    put("br", item.br)
                    put("at", item.at)
                    put("address", item.address)
                    put("recipient", item.recipient)
                    put("phone", item.phone)
                    put("latitude", item.latitude)
                    put("longitude", item.longitude)
                    put("spx_order", item.spxOrder)
                    put("copilot_order", item.copilotOrder)
                    put("status", item.status)
                }

                db.insertWithOnConflict(
                    "packages",
                    null,
                    values,
                    android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE
                )
            }

            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun getRoutes(): List<RouteRecord> {
        val result = mutableListOf<RouteRecord>()

        database.readableDatabase.query(
            "routes",
            null,
            null,
            null,
            null,
            null,
            "created_at DESC"
        ).use { cursor ->
            while (cursor.moveToNext()) {
                result += RouteRecord(
                    at = cursor.getString(cursor.getColumnIndexOrThrow("at")),
                    loadDate = cursor.getString(cursor.getColumnIndexOrThrow("load_date")),
                    expectedTotal = cursor.getIntOrNull("expected_total"),
                    importedTotal = cursor.getInt(cursor.getColumnIndexOrThrow("imported_total")),
                    createdAt = cursor.getLong(cursor.getColumnIndexOrThrow("created_at")),
                    status = cursor.getString(cursor.getColumnIndexOrThrow("status"))
                )
            }
        }

        return result
    }

    fun getPackages(at: String): List<PackageRecord> {
        val result = mutableListOf<PackageRecord>()

        database.readableDatabase.query(
            "packages",
            null,
            "at = ?",
            arrayOf(at),
            null,
            null,
            "COALESCE(copilot_order, spx_order, 999999), br"
        ).use { cursor ->
            while (cursor.moveToNext()) {
                result += PackageRecord(
                    br = cursor.getString(cursor.getColumnIndexOrThrow("br")),
                    at = cursor.getString(cursor.getColumnIndexOrThrow("at")),
                    address = cursor.getString(cursor.getColumnIndexOrThrow("address")),
                    recipient = cursor.getString(cursor.getColumnIndexOrThrow("recipient")),
                    phone = cursor.getString(cursor.getColumnIndexOrThrow("phone")),
                    latitude = cursor.getDoubleOrNull("latitude"),
                    longitude = cursor.getDoubleOrNull("longitude"),
                    spxOrder = cursor.getIntOrNull("spx_order"),
                    copilotOrder = cursor.getIntOrNull("copilot_order"),
                    status = cursor.getString(cursor.getColumnIndexOrThrow("status"))
                )
            }
        }

        return result
    }

    fun updatePackageStatus(br: String, status: String) {
        val values = ContentValues().apply {
            put("status", status)
        }

        database.writableDatabase.update(
            "packages",
            values,
            "br = ?",
            arrayOf(br)
        )
    }

    private fun android.database.Cursor.getIntOrNull(column: String): Int? {
        val index = getColumnIndexOrThrow(column)
        return if (isNull(index)) null else getInt(index)
    }

    private fun android.database.Cursor.getDoubleOrNull(column: String): Double? {
        val index = getColumnIndexOrThrow(column)
        return if (isNull(index)) null else getDouble(index)
    }
}
