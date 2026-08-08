package com.smax.tracker

import android.app.*
import android.content.Intent
import android.location.Location
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.*

class GpsForegroundService : Service() {

    companion object {
        const val CHANNEL_ID = "smax_gps_tracking"
        const val NOTIF_ID = 1001
        const val ACTION_STOP = "com.smax.tracker.STOP_TRACKING"
    }

    private lateinit var fusedClient: FusedLocationProviderClient
    private var lastLocation: Location? = null
    private var totalDistanceKm: Double = 0.0
    private var currentSpeedKmh: Double = 0.0
    private var gpsConnected: Boolean = false

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val loc = result.lastLocation ?: return
            gpsConnected = true

            lastLocation?.let { prev ->
                val deltaKm = prev.distanceTo(loc) / 1000.0
                if (deltaKm in 0.0005..2.0) {
                    totalDistanceKm += deltaKm
                }
            }
            lastLocation = loc

            currentSpeedKmh = if (loc.hasSpeed()) (loc.speed * 3.6) else 0.0
            if (currentSpeedKmh < 1.5) currentSpeedKmh = 0.0

            TrackingState.distanceKm = totalDistanceKm
            TrackingState.speedKmh = currentSpeedKmh
            TrackingState.lastLat = loc.latitude
            TrackingState.lastLon = loc.longitude
            TrackingState.isTracking = true

            updateNotification()
        }
    }

    override fun onCreate() {
        super.onCreate()
        fusedClient = LocationServices.getFusedLocationProviderClient(this)
        totalDistanceKm = TrackingState.distanceKm
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopTracking()
            return START_NOT_STICKY
        }
        startForeground(NOTIF_ID, buildNotification())
        startLocationUpdates()
        return START_STICKY
    }

    private fun startLocationUpdates() {
        val request = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            2000L
        ).setMinUpdateIntervalMillis(1000L)
            .build()

        try {
            fusedClient.requestLocationUpdates(request, locationCallback, mainLooper)
        } catch (e: SecurityException) {
            gpsConnected = false
            updateNotification()
        }
    }

    private fun stopTracking() {
        fusedClient.removeLocationUpdates(locationCallback)
        TrackingState.isTracking = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun buildNotification(): Notification {
        val stopIntent = Intent(this, GpsForegroundService::class.java).apply { action = ACTION_STOP }
        val stopPending = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val openAppIntent = packageManager.getLaunchIntentForPackage(packageName)
        val openAppPending = PendingIntent.getActivity(
            this, 0, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val statusText = if (gpsConnected) "GPS connecté" else "recherche du signal…"
        val consoText = if (TrackingState.consoL100 > 0)
            "  •  %.1f L/100km".format(TrackingState.consoL100) else ""

        val content = "%.1f km/h  •  %.1f km parcourus  •  %s%s"
            .format(currentSpeedKmh, totalDistanceKm, statusText, consoText)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("SMAX Tracker — suivi actif")
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .setContentIntent(openAppPending)
            .addAction(0, "Arrêter le suivi", stopPending)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIF_ID, buildNotification())
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Suivi GPS SMAX",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notification persistante pendant le suivi GPS en arrière-plan"
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}

object TrackingState {
    var isTracking: Boolean = false
    var distanceKm: Double = 0.0
    var speedKmh: Double = 0.0
    var consoL100: Double = 0.0
    var lastLat: Double = 0.0
    var lastLon: Double = 0.0
}
