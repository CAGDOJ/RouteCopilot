package com.routecopilot.location

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder

class CourierLocationService : Service() {
    companion object {
        const val CHANNEL_ID =
            "routecopilot_location"

        const val NOTIFICATION_ID =
            9001
    }

    override fun onCreate() {
        super.onCreate()

        val manager =
            getSystemService(
                NotificationManager::class.java
            )

        if (
            Build.VERSION.SDK_INT >=
            Build.VERSION_CODES.O
        ) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Rota em andamento",
                    NotificationManager.IMPORTANCE_LOW
                )
            )
        }

        val builder =
            if (
                Build.VERSION.SDK_INT >=
                Build.VERSION_CODES.O
            ) {
                Notification.Builder(
                    this,
                    CHANNEL_ID
                )
            } else {
                @Suppress("DEPRECATION")
                Notification.Builder(this)
            }

        val notification =
            builder
                .setContentTitle(
                    "RouteCopilot"
                )
                .setContentText(
                    "Rota ativa."
                )
                .setSmallIcon(
                    android.R.drawable
                        .ic_menu_mylocation
                )
                .build()

        startForeground(
            NOTIFICATION_ID,
            notification
        )
    }

    override fun onBind(
        intent: Intent?
    ): IBinder? = null
}
