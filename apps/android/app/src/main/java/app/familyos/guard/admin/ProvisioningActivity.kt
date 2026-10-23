package app.familyos.guard.admin

import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle

/**
 * Точка входа для выдачи приложению прав Device Owner:
 * QR-провижининг (android.app.action.PROVISION_MANAGED_DEVICE).
 */
class ProvisioningActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val dpm = getSystemService(DevicePolicyManager::class.java)

        // Если система ещё не сделала нас владельцем, активируем админа как fallback.
        if (!dpm.isDeviceOwnerApp(packageName)) {
            val admin = ComponentName(this, GuardDeviceAdmin::class.java)
            if (!dpm.isAdminActive(admin)) {
                runCatching {
                    startActivityForResult(
                        Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                            .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, admin)
                            .putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Family OS Guard"),
                        1001,
                    )
                }
            }
        }

        setResult(if (dpm.isDeviceOwnerApp(packageName)) RESULT_OK else RESULT_CANCELED)
        finish()
    }
}
