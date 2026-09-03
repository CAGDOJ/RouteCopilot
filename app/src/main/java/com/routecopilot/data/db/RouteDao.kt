package com.routecopilot.data.db

import androidx.room.*
import com.routecopilot.data.model.PackageEntity
import com.routecopilot.data.model.RouteEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RouteDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRoute(route: RouteEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPackages(packages: List<PackageEntity>)

    @Query("SELECT * FROM routes ORDER BY createdAt DESC")
    fun observeRoutes(): Flow<List<RouteEntity>>

    @Query("SELECT * FROM packages WHERE at = :at ORDER BY COALESCE(copilotOrder, spxOrder, 999999)")
    fun observePackages(at: String): Flow<List<PackageEntity>>

    @Query("UPDATE packages SET status = :status WHERE br = :br")
    suspend fun updateStatus(br: String, status: String)
}
