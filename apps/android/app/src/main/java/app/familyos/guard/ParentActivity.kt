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
                    primary = app.familyos.guard.ui.accent,
                    onPrimary = Color(0xFF120800),
                    primaryContainer = app.familyos.guard.ui.accentDeep,
                    onPrimaryContainer = app.familyos.guard.ui.textPrimary,
                    background = app.familyos.guard.ui.bg,
                    surface = app.familyos.guard.ui.cardBg,
                    surfaceVariant = app.familyos.guard.ui.cardBgAlt,
                    onSurface = app.familyos.guard.ui.textPrimary,
                    onSurfaceVariant = app.familyos.guard.ui.textMuted,
                    outline = app.familyos.guard.ui.cardBorder,
                ),
                shapes = app.familyos.guard.ui.AppShapes,
            ) {
                Surface(Modifier.fillMaxSize(), color = Color.Transparent) {
                    app.familyos.guard.ui.AuroraBackground()
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
