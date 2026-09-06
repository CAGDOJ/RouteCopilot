package com.routecopilot.data.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class AppDatabase private constructor(
    context: Context
) : SQLiteOpenHelper(
    context.applicationContext,
    "routecopilot.db",
    null,
    1
) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE routes (
                at TEXT PRIMARY KEY,
                load_date TEXT,
                expected_total INTEGER,
                imported_total INTEGER NOT NULL,
                created_at INTEGER NOT NULL,
                status TEXT NOT NULL
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE packages (
                br TEXT PRIMARY KEY,
                at TEXT NOT NULL,
                address TEXT,
                recipient TEXT,
                phone TEXT,
                latitude REAL,
                longitude REAL,
                spx_order INTEGER,
                copilot_order INTEGER,
                status TEXT NOT NULL
            )
            """.trimIndent()
        )

        db.execSQL(
            "CREATE INDEX idx_packages_at ON packages(at)"
        )
    }

    override fun onUpgrade(
        db: SQLiteDatabase,
        oldVersion: Int,
        newVersion: Int
    ) {
        // Versão inicial.
    }

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase {
            return instance ?: synchronized(this) {
                instance ?: AppDatabase(context).also {
                    instance = it
                }
            }
        }
    }
}
