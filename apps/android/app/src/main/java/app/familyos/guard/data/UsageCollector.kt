package app.familyos.guard.data

import android.content.Context
import java.time.LocalDate
import java.time.ZoneId
import java.util.concurrent.TimeUnit

object UsageCollector {
    data class AppUsage(val packageId: String, val label: String, val minutes: Int)

    data class Snapshot(val totalMinutes: Int, val apps: List<AppUsage>)

    fun collect(context: Context, now: Long = System.currentTimeMillis()): Snapshot {
        val zone = ZoneId.systemDefault()
        val start = LocalDate.now(zone).atStartOfDay(zone).toInstant().toEpochMilli()
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as android.app.usage.UsageStatsManager
        val stats = usm.queryAndAggregateUsageStats(start, now)
        val pm = context.packageManager

        val apps = stats.values.mapNotNull { usage ->
            val minutes = TimeUnit.MILLISECONDS.toMinutes(usage.totalTimeInForeground)
            if (minutes <= 0) return@mapNotNull null
            val label = runCatching {
                pm.getApplicationLabel(pm.getApplicationInfo(usage.packageName, 0)).toString()
            }.getOrDefault(usage.packageName)
            AppUsage(usage.packageName, label, minutes.toInt())
        }.sortedByDescending { it.minutes }

        return Snapshot(totalMinutes = apps.sumOf { it.minutes }, apps = apps.take(60))
    }
}
