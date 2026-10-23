package app.familyos.guard

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity

/**
 * Точка входа: детская сборка открывает детский экран,
 * родительская — родительский.
 */
class LauncherActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val target = if (BuildConfig.ROLE == "parent") ParentActivity::class.java else MainActivity::class.java
        startActivity(Intent(this, target))
        finish()
    }
}
