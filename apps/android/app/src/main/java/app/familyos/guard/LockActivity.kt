package app.familyos.guard

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import app.familyos.guard.api.ApiClient
import app.familyos.guard.data.GuardStore
import app.familyos.guard.data.Policy
import kotlinx.coroutines.launch

class LockActivity : ComponentActivity() {

    private lateinit var store: GuardStore
    private var policy by mutableStateOf<Policy?>(null)
    private var message by mutableStateOf<String?>(null)
    private var busy by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = GuardStore(this)
        policy = store.policy()

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() = Unit
            },
        )

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFF34D399),
                    background = Color(0xFF0B0F14),
                    surface = Color(0xFF121922),
                    onSurface = Color(0xFFE6EDF3),
                ),
            ) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text("⏳", fontSize = 56.sp)
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "Время на сегодня вышло",
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            policy?.let { "Лимит ${it.limitMinutes} мин · использовано ${it.usedMinutes} мин" }
                                ?: "Лимит экранного времени",
                            color = Color(0xFF8B9BB0),
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(28.dp))

                        Button(
                            onClick = { ask() },
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(if (busy) "Отправляем…" else "Попросить ещё 15 минут")
                        }
                        Spacer(Modifier.height(10.dp))
                        Button(
                            onClick = { refresh() },
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Проверить снова")
                        }

                        message?.let {
                            Spacer(Modifier.height(18.dp))
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF121922)),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    it,
                                    modifier = Modifier.padding(16.dp),
                                    color = Color(0xFFE6EDF3),
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                    }
                }
            }
        }

        refresh()
    }

    private fun refresh() {
        if (!store.isConfigured) return
        lifecycleScope.launch {
            busy = true
            runCatching { ApiClient(store).refreshPolicy() }
                .onSuccess {
                    policy = it
                    if (it.remainMinutes > 0) finish()
                }
                .onFailure { message = it.message }
            busy = false
        }
    }

    private fun ask() {
        lifecycleScope.launch {
            busy = true
            runCatching { ApiClient(store).requestExtension(15, "Запрос с заблокированного экрана") }
                .onSuccess { message = "Запрос отправлен. Ждём ответа родителя — экран обновится сам." }
                .onFailure { message = it.message }
            busy = false
        }
    }
}
