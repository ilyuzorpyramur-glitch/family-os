package app.familyos.guard

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import app.familyos.guard.data.GuardStore
import app.familyos.guard.data.OfflineStore
import app.familyos.guard.parent.ParentApp

class ParentActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val store = GuardStore(this)
        val cache = OfflineStore(this)

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(android.graphics.Color.parseColor(BuildConfig.ACCENT)),
                    background = Color(0xFF0B0F14),
                    surface = Color(0xFF121922),
                    onSurface = Color(0xFFE6EDF3),
                    onSurfaceVariant = Color(0xFF8B9BB0),
                ),
            ) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    ParentApp(
                        initialUrl = store.serverUrl.ifBlank { "https://herb-rosa-moscow-checking.trycloudflare.com" },
                        initialToken = store.parentToken,
                        cache = cache,
                        onExit = { finish() },
                        onTokenSaved = { store.parentToken = it },
                    )
                }
            }
        }
    }
}
