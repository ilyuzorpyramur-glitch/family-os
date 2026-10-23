package app.familyos.guard.work

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import app.familyos.guard.api.ApiClient
import app.familyos.guard.data.GuardStore
import app.familyos.guard.data.UsageCollector

class GuardSyncWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val store = GuardStore(applicationContext)
        if (!store.isConfigured) return Result.success()

        val api = ApiClient(store)
        return try {
            val policy = api.refreshPolicy()
            val snapshot = UsageCollector.collect(applicationContext)
            api.sendUsage(snapshot)
            api.heartbeat()
            syncGeofences(api)
            if (policy.remainMinutes <= 0 && policy.limitMinutes > 0) {
                GuardNotifications.notifyLimitReached(applicationContext, policy)
            }
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }

    private suspend fun syncGeofences(api: ApiClient) {
        val context = applicationContext
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val location = runCatching { manager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER) }.getOrNull()
            ?: runCatching { manager.getLastKnownLocation(LocationManager.GPS_PROVIDER) }.getOrNull()
            ?: return

        val fences = runCatching { api.geofences() }.getOrElse { return }
        val prefs = context.getSharedPreferences("geo", Context.MODE_PRIVATE)
        for (fence in fences) {
            val result = FloatArray(1)
            Location.distanceBetween(location.latitude, location.longitude, fence.lat, fence.lng, result)
            val inside = result[0] <= fence.radiusM
            val key = "in_${fence.id}"
            val previous = prefs.getBoolean(key, false)
            if (inside != previous) {
                if ((inside && fence.notifyEnter) || (!inside && fence.notifyExit)) {
                    runCatching { api.geofenceEvent(fence.id, if (inside) "enter" else "exit") }
                }
                prefs.edit().putBoolean(key, inside).apply()
            }
        }
    }
}
