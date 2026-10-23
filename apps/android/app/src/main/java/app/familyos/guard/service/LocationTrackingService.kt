package app.familyos.guard.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import app.familyos.guard.MainActivity
import app.familyos.guard.R
import app.familyos.guard.api.ApiClient
import app.familyos.guard.data.GuardStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class LocationTrackingService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var store: GuardStore
    private var manager: LocationManager? = null

    private val listener = LocationListener { location: Location ->
        scope.launch {
            runCatching {
                ApiClient(store).sendLocation(location.latitude, location.longitude, location.accuracy)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        runCatching {
            store = GuardStore(this)
            createChannel()
            startForeground(2001, notification())
            startUpdates()
        }.onFailure {
            stopSelf()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        runCatching { startUpdates() }
        return START_STICKY
    }

    private fun startUpdates() {
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val lm = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        manager = lm
        runCatching {
            lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 30_000L, 15f, listener, Looper.getMainLooper())
        }
        runCatching {
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 30_000L, 15f, listener, Looper.getMainLooper())
        }
        runCatching { lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)?.let { listener.onLocationChanged(it) } }
    }

    override fun onDestroy() {
        runCatching { manager?.removeUpdates(listener) }
        scope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(
                NotificationChannel("tracking", "Геолокация", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Передача местоположения родителю"
                },
            )
        }
    }

    private fun notification(): android.app.Notification {
        val intent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, "tracking")
            .setSmallIcon(R.drawable.ic_guard)
            .setContentTitle("Family OS Guard")
            .setContentText("Геолокация включена — родитель видит местоположение")
            .setContentIntent(intent)
            .setOngoing(true)
            .build()
    }
}
