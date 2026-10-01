package com.traillink.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import com.traillink.app.R
import com.traillink.app.data.LocationPoint
import com.traillink.app.net.FirebaseRestClient
import com.traillink.app.ui.MainActivity
import com.traillink.app.util.Prefs
import java.util.concurrent.TimeUnit

class LocationShareService : android.app.Service() {

    private lateinit var locationManager: LocationManager
    private var wakeLock: PowerManager.WakeLock? = null
    private var lastSentAt = 0L

    private val listener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            handleFix(location)
        }
        override fun onProviderDisabled(provider: String) {}
        override fun onProviderEnabled(provider: String) {}
        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        ensureChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!Prefs.sharingActive || Prefs.mySharingCode.isBlank() || !Prefs.isConfigured()) {
            stopSelf()
            return START_NOT_STICKY
        }

        // 12-hour safety auto-stop, in case the user forgets it's running.
        val startedAt = if (Prefs.sharingStartedAt == 0L) System.currentTimeMillis() else Prefs.sharingStartedAt
        Prefs.sharingStartedAt = startedAt
        if (System.currentTimeMillis() - startedAt > TimeUnit.HOURS.toMillis(12)) {
            Prefs.sharingActive = false
            stopSelf()
            return START_NOT_STICKY
        }

        startForeground(NOTIFICATION_ID, buildNotification(getString(R.string.waiting_for_fix)))
        requestUpdates()
        return START_STICKY
    }

    private fun requestUpdates() {
        val intervalMs = Prefs.updateIntervalSeconds * 1000L
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION)
            != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            stopSelf()
            return
        }
        try {
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, intervalMs, 0f, listener)
            }
            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, intervalMs, 0f, listener)
            }
            // Prime with the best last-known fix immediately instead of waiting for the first update.
            val last = bestLastKnown()
            if (last != null) handleFix(last)
        } catch (_: SecurityException) {
            stopSelf()
        }
    }

    private fun bestLastKnown(): Location? {
        val providers = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
        return providers.mapNotNull {
            try { locationManager.getLastKnownLocation(it) } catch (_: SecurityException) { null }
        }.maxByOrNull { it.time }
    }

    private fun handleFix(location: Location) {
        val now = System.currentTimeMillis()
        val minGapMs = (Prefs.updateIntervalSeconds * 1000L) - 2000L // small slack
        if (now - lastSentAt < minGapMs) return
        lastSentAt = now

        val point = LocationPoint(
            lat = location.latitude,
            lng = location.longitude,
            accuracyMeters = if (location.hasAccuracy()) location.accuracy else 0f,
            speedKmh = if (location.hasSpeed()) location.speed * 3.6f else 0f,
            bearing = if (location.hasBearing()) location.bearing else 0f,
            timestampMillis = now,
            batteryPercent = batteryPercent()
        )

        withWakeLock {
            FirebaseRestClient.putLocation(Prefs.mySharingCode, point) { success ->
                if (success) Prefs.lastSendSuccessAt = System.currentTimeMillis()
                val status = if (success) getString(R.string.sharing_active) else getString(R.string.updated_just_now)
                updateNotification(status)
            }
        }
    }

    private fun withWakeLock(block: () -> Unit) {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        val wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "TrailLink:locationSend")
        wl.acquire(10_000L)
        wakeLock = wl
        try {
            block()
        } finally {
            if (wl.isHeld) wl.release()
        }
    }

    private fun batteryPercent(): Int {
        val bm = getSystemService(Context.BATTERY_SERVICE) as? BatteryManager ?: return -1
        return bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(
                CHANNEL_ID, "Location sharing", NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows while TrailLink is sharing your live location"
                setShowBadge(false)
            }
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(statusText: String): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val code = Prefs.mySharingCode
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_share_loc)
            .setContentTitle("Sharing location · $code")
            .setContentText(statusText)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateNotification(statusText: String) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIFICATION_ID, buildNotification(statusText))
    }

    override fun onDestroy() {
        try { locationManager.removeUpdates(listener) } catch (_: Exception) {}
        wakeLock?.let { if (it.isHeld) it.release() }
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "location_sharing"
        private const val NOTIFICATION_ID = 42

        fun start(context: Context) {
            val intent = Intent(context, LocationShareService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, LocationShareService::class.java))
        }
    }
}
