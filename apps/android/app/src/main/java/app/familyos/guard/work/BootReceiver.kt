package app.familyos.guard.work

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.familyos.guard.GuardApp

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED) {
            GuardApp.scheduleSync(context)
        }
    }
}
