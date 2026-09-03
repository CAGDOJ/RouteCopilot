package com.routecopilot.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.routecopilot.data.model.PackageEntity
import com.routecopilot.data.model.RouteEntity

@Database(entities = [RouteEntity::class, PackageEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun routeDao(): RouteDao

    companion object {
        @Volatile private var instance: AppDatabase? = null
        fun get(context: Context): AppDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, "routecopilot.db")
                .build().also { instance = it }
        }
    }
}
