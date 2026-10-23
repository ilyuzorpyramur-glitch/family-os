package app.familyos.guard

import android.app.Application
import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import app.familyos.guard.work.GuardSyncWorker
import java.util.concurrent.TimeUnit

class GuardApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashReporter.install(this)
        runCatching { scheduleSync(this) }
    }

    companion object {
        const val SYNC_WORK = "guard-sync"

        fun scheduleSync(context: Context) {
            val request = PeriodicWorkRequestBuilder<GuardSyncWorker>(15, TimeUnit.MINUTES)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build(),
                )
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                SYNC_WORK,
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        }
    }
}
