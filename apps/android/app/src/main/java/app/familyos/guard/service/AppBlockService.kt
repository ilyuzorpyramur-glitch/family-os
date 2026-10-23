package app.familyos.guard.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import app.familyos.guard.LockActivity
import app.familyos.guard.data.GuardStore

/**
 * Блокирует приложения по лимиту и расписанию.
 * Не читает содержимое экрана: используется только имя пакета активного окна.
 */
class AppBlockService : AccessibilityService() {

    private val TAG = "GuardBlock"

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
        Log.i(TAG, "service connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val packageName = event.packageName?.toString() ?: return
        if (packageName == this.packageName || packageName in allowedPackages) return

        if (LockActivity.visible) return

        val policy = store.policy() ?: return
        val reason = policy.blockReasonAt()
        if (reason == null) return
        Log.i(TAG, "blocking $packageName, reason=$reason")

        val now = System.currentTimeMillis()
        if (now - lastBlockAt < 5_000) return
        lastBlockAt = now

        val intent = Intent(this, LockActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        runCatching { startActivity(intent) }
    }

    override fun onInterrupt() = Unit

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
