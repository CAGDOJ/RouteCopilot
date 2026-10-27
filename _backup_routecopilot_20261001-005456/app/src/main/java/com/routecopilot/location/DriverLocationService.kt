package com.routecopilot.location

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
import android.os.Bundle
import android.os.IBinder
import androidx.core.app.ActivityCompat
import com.routecopilot.route.DriverLocation
import com.routecopilot.route.RouteRepository

class DriverLocationService : Service(), LocationListener {

    companion object {
        private const val CHANNEL_ID =
            "routecopilot_location"

        private const val NOTIFICATION_ID =
            2217
    }

    private lateinit var locationManager:
        LocationManager

    override fun onCreate() {
        super.onCreate()

        RouteRepository.initialize(
            applicationContext
        )

        locationManager =
            getSystemService(
                LOCATION_SERVICE
            ) as LocationManager

        createChannel()

        startForeground(
            NOTIFICATION_ID,
            buildNotification()
        )

        startLocationUpdates()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {
        startLocationUpdates()

        return START_STICKY
    }

    private fun startLocationUpdates() {
        if (
            ActivityCompat
                .checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) !=
            PackageManager.PERMISSION_GRANTED &&
            ActivityCompat
                .checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            stopSelf()
            return
        }

        listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER
        ).forEach { provider ->
            runCatching {
                if (
                    locationManager
                        .isProviderEnabled(
                            provider
                        )
                ) {
                    locationManager
                        .requestLocationUpdates(
                            provider,
                            3_000L,
                            5f,
                            this
                        )

                    locationManager
                        .getLastKnownLocation(
                            provider
                        )
                        ?.let(
                            ::publish
                        )
                }
            }
        }
    }

    override fun onLocationChanged(
        location: Location
    ) {
        publish(
            location
        )
    }

    @Deprecated(
        "Deprecated in Android"
    )
    override fun onStatusChanged(
        provider: String?,
        status: Int,
        extras: Bundle?
    ) = Unit

    override fun onProviderEnabled(
        provider: String
    ) = Unit

    override fun onProviderDisabled(
        provider: String
    ) = Unit

    private fun publish(
        location: Location
    ) {
        RouteRepository
            .updateDriverLocation(
                DriverLocation(
                    latitude =
                        location.latitude,
                    longitude =
                        location.longitude,
                    accuracyMeters =
                        location.accuracy
                            .takeIf {
                                location.hasAccuracy()
                            },
                    updatedAt =
                        System.currentTimeMillis()
                )
            )
    }

    private fun createChannel() {
        val manager =
            getSystemService(
                NOTIFICATION_SERVICE
            ) as NotificationManager

        manager
            .createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    "Localização da rota",
                    NotificationManager
                        .IMPORTANCE_LOW
                ).apply {
                    description =
                        "Mantém a posição do entregador atualizada durante a rota."
                }
            )
    }

    private fun buildNotification():
        Notification =
        Notification
            .Builder(
                this,
                CHANNEL_ID
            )
            .setContentTitle(
                "RouteCopilot em rota"
            )
            .setContentText(
                "Atualizando sua posição para a navegação."
            )
            .setSmallIcon(
                android.R.drawable
                    .ic_menu_mylocation
            )
            .setOngoing(
                true
            )
            .build()

    override fun onDestroy() {
        runCatching {
            locationManager
                .removeUpdates(
                    this
                )
        }

        super.onDestroy()
    }

    override fun onBind(
        intent: Intent?
    ): IBinder? =
        null
}
