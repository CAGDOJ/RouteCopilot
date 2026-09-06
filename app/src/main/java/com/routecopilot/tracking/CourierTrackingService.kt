package com.routecopilot.tracking

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.IBinder
import androidx.core.content.ContextCompat
import com.routecopilot.R
import com.routecopilot.route.EtaEngine
import com.routecopilot.route.RouteRepository

class CourierTrackingService : Service(), LocationListener {

    companion object {
        private const val CHANNEL_ID = "routecopilot_tracking"
        private const val NOTIFICATION_ID = 7101
    }

    override fun onCreate() {
        super.onCreate()

        RouteRepository.initialize(this)
        createChannel()

        startForeground(
            NOTIFICATION_ID,
            buildNotification()
        )

        startLocation()
    }

    private fun createChannel() {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Rastreamento da rota",
            NotificationManager.IMPORTANCE_LOW
        )
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("RouteCopilot")
            .setContentText("Rastreamento da rota ativo")
            .setSmallIcon(R.drawable.ic_location)
            .setOngoing(true)
            .build()
    }

    private fun startLocation() {
        val fine = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        )

        val coarse = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )

        if (
            fine != PackageManager.PERMISSION_GRANTED &&
            coarse != PackageManager.PERMISSION_GRANTED
        ) {
            stopSelf()
            return
        }

        val manager = getSystemService(LOCATION_SERVICE) as LocationManager

        runCatching {
            manager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                5000L,
                10f,
                this
            )
        }

        runCatching {
            manager.requestLocationUpdates(
                LocationManager.NETWORK_PROVIDER,
                7000L,
                20f,
                this
            )
        }
    }

    override fun onLocationChanged(location: Location) {
        val stops = RouteRepository.stops.value
            .sortedBy { it.copilotOrder ?: Int.MAX_VALUE }

        val averageService = RouteRepository.averageServiceSeconds(
            TrackingConfig.DEFAULT_SERVICE_SECONDS
        )

        val pending = stops.filter {
            it.status != com.routecopilot.route.DeliveryStatus.DELIVERED &&
                it.status != com.routecopilot.route.DeliveryStatus.OCCURRENCE
        }

        stops
            .filter { it.messageSent }
            .forEach { stop ->
                val eta = EtaEngine.etaMinutes(
                    orderedStops = stops,
                    targetBr = stop.br,
                    courierLat = location.latitude,
                    courierLon = location.longitude,
                    averageServiceSeconds = averageService
                )

                val remainingStops = pending
                    .indexOfFirst { it.br == stop.br }
                    .coerceAtLeast(0)

                TrackingClient.update(
                    stop = stop,
                    courierLat = location.latitude,
                    courierLon = location.longitude,
                    etaMinutes = eta,
                    remainingStops = remainingStops
                )
            }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
