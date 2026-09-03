package com.routecopilot.data.repository

import com.routecopilot.data.db.RouteDao
import com.routecopilot.data.model.PackageEntity
import com.routecopilot.data.model.RouteEntity

class RouteRepository(private val dao: RouteDao) {
    fun observeRoutes() = dao.observeRoutes()
    fun observePackages(at: String) = dao.observePackages(at)

    suspend fun saveImportedRoute(at: String, loadDate: String?, expectedTotal: Int?, brs: Collection<String>) {
        dao.upsertRoute(RouteEntity(at, loadDate, expectedTotal))
        dao.upsertPackages(brs.map { PackageEntity(br = it, at = at) })
    }

    suspend fun updateStatus(br: String, status: String) = dao.updateStatus(br, status)
}
