package com.routecopilot.data.repository

import android.content.Context
import com.routecopilot.data.db.AppDatabase
import com.routecopilot.data.db.RouteDao
import com.routecopilot.data.model.PackageRecord
import com.routecopilot.data.model.RouteRecord

class RouteRepository private constructor(
    private val dao: RouteDao
) {
    fun saveImportedRoute(
        at: String,
        loadDate: String?,
        expectedTotal: Int?,
        brs: Collection<String>
    ) {
        dao.upsertRoute(
            RouteRecord(
                at = at,
                loadDate = loadDate,
                expectedTotal = expectedTotal,
                importedTotal = brs.size
            )
        )

        dao.upsertPackages(
            brs.map { br ->
                PackageRecord(
                    br = br,
                    at = at
                )
            }
        )
    }

    fun getRoutes() = dao.getRoutes()

    fun getPackages(at: String) = dao.getPackages(at)

    fun updateStatus(br: String, status: String) {
        dao.updatePackageStatus(br, status)
    }

    companion object {
        @Volatile
        private var instance: RouteRepository? = null

        fun get(context: Context): RouteRepository {
            return instance ?: synchronized(this) {
                instance ?: RouteRepository(
                    RouteDao(
                        AppDatabase.get(context)
                    )
                ).also {
                    instance = it
                }
            }
        }
    }
}
