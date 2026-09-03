package com.routecopilot.location

import android.app.*
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat

class CourierLocationService : Service() {
    override fun onCreate() {
        super.onCreate()
        val id = "routecopilot_location"
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(id, "Localização da rota", NotificationManager.IMPORTANCE_LOW)
        )
        val n = NotificationCompat.Builder(this, id)
            .setContentTitle("RouteCopilot")
            .setContentText("Acompanhando a rota em primeiro plano")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .build()
        startForeground(9001, n)
    }
    override fun onBind(intent: Intent?): IBinder? = null
}
