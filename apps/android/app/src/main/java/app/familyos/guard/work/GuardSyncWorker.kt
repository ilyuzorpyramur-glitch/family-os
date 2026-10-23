package app.familyos.guard.work

import android.content.Context
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
            if (policy.remainMinutes <= 0 && policy.limitMinutes > 0) {
                GuardNotifications.notifyLimitReached(applicationContext, policy)
            }
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        }
    }
}
