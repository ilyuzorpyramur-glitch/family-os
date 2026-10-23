package app.familyos.guard.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.content.pm.PackageManager
import android.view.accessibility.AccessibilityEvent
import app.familyos.guard.LockActivity
import app.familyos.guard.data.GuardStore
import app.familyos.guard.data.Policy
import java.time.LocalTime

/**
 * Блокирует приложения по лимиту и расписанию.
 * Не читает содержимое экрана: используется только имя пакета активного окна.
 */
class AppBlockService : AccessibilityService() {

    private lateinit var store: GuardStore
    private var allowedPackages: Set<String> = emptySet()
    private var lastBlockAt = 0L

    override fun onCreate() {
        super.onCreate()
        store = GuardStore(this)
        allowedPackages = buildAllowlist()
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        allowedPackages = buildAllowlist()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val packageName = event.packageName?.toString() ?: return
        if (packageName == this.packageName || packageName in allowedPackages) return

        val policy = store.policy() ?: return
        if (!shouldBlock(policy, LocalTime.now())) return

        val now = System.currentTimeMillis()
        if (now - lastBlockAt < 3_000) return
        lastBlockAt = now

        val intent = Intent(this, LockActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        runCatching { startActivity(intent) }
    }

    override fun onInterrupt() = Unit

    private fun shouldBlock(policy: Policy, now: LocalTime): Boolean {
        if (policy.limitMinutes > 0 && policy.usedMinutes >= policy.limitMinutes) return true
        return isBedtime(policy.bedtimeStart, policy.bedtimeEnd, now)
    }

    private fun isBedtime(start: String, end: String, now: LocalTime): Boolean {
        val from = parseTime(start) ?: return false
        val to = parseTime(end) ?: return false
        return if (from <= to) now >= from && now < to else now >= from || now < to
    }

    private fun parseTime(value: String): LocalTime? = runCatching {
        val parts = value.split(":")
        LocalTime.of(parts[0].toInt(), parts[1].toInt())
    }.getOrNull()

    private fun buildAllowlist(): Set<String> {
        val result = mutableSetOf(
            "com.android.systemui",
            "com.android.settings",
            "com.android.packageinstaller",
            "com.google.android.packageinstaller",
            "com.android.permissioncontroller",
            "com.google.android.permissioncontroller",
            "com.android.dialer",
            "com.google.android.dialer",
            "com.android.phone",
        )
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        packageManager
            .resolveActivity(home, PackageManager.MATCH_DEFAULT_ONLY)
            ?.activityInfo
            ?.packageName
            ?.let { result.add(it) }
        return result
    }
}
