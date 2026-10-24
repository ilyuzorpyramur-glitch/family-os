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
    private var busyRefresh by mutableStateOf(false)
    private var busyAsk by mutableStateOf(false)

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
                shapes = app.familyos.guard.ui.AppShapes,
                colorScheme = darkColorScheme(
                    primary = app.familyos.guard.ui.accent,
                    background = Color(0xFF0B0F14),
                    surface = Color(0xFF121922),
                    onSurface = Color(0xFFE6EDF3),
                ),
            ) {
                Surface(Modifier.fillMaxSize(), color = Color.Transparent) {
                    app.familyos.guard.ui.AuroraBackground()
                    val reason = policy?.blockReasonAt()
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text(if (reason == "bedtime") "🌙" else "⏳", fontSize = 56.sp)
                        Spacer(Modifier.height(16.dp))
                        Text(
                            if (reason == "bedtime") "Сейчас время отдыха" else "Время на сегодня вышло",
                            fontSize = 26.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            when (reason) {
                                "bedtime" -> policy?.let { "Экран недоступен с ${it.bedtimeStart} до ${it.bedtimeEnd}" }
                                else -> policy?.let { "Лимит ${it.limitMinutes} мин · использовано ${it.usedMinutes} мин" }
                            } ?: "Проверяем политику…",
                            color = Color(0xFF8B9BB0),
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(28.dp))

                        if (reason != "bedtime") {
                            val cost = policy?.extensionCost ?: 50
                            val minutes = policy?.extensionMinutes ?: 30
                            val coins = policy?.coins ?: 0
                            Text(
                                "У тебя $coins монет",
                                color = if (coins >= cost) Color(0xFFFBBF24) else Color(0xFF8B9BB0),
                                fontSize = 13.sp,
                            )
                            Spacer(Modifier.height(8.dp))
                            Button(
                                onClick = { ask() },
                                enabled = !busyAsk && !busyRefresh && coins >= cost,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    if (busyAsk) "Отправляем…"
                                    else "Продлить на $minutes мин · $cost монет",
                                )
                            }
                            Spacer(Modifier.height(10.dp))
                        } else {
                            Text(
                                "Лимит на сегодня не тратится: действует расписание.",
                                color = Color(0xFF8B9BB0),
                                fontSize = 13.sp,
                                textAlign = TextAlign.Center,
                            )
                            Spacer(Modifier.height(18.dp))
                        }

                        Button(
                            onClick = { refresh() },
                            enabled = !busyRefresh && !busyAsk,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(if (busyRefresh) "Проверяем…" else "Проверить снова")
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

    override fun onResume() {
        super.onResume()
        visible = true
    }

    override fun onPause() {
        visible = false
        super.onPause()
    }

    private fun refresh() {
        if (!store.isConfigured || busyRefresh) return
        lifecycleScope.launch {
            busyRefresh = true
            runCatching { ApiClient(store).refreshPolicy() }
                .onSuccess { updated ->
                    policy = updated
                    if (updated.blockReasonAt() == null) finish()
                }
                .onFailure { message = it.message ?: "Нет связи с сервером" }
            busyRefresh = false
        }
    }

    private fun ask() {
        if (busyAsk) return
        lifecycleScope.launch {
            busyAsk = true
            runCatching { ApiClient(store).requestExtension(policy?.extensionMinutes ?: 30, "Запрос с заблокированного экрана") }
                .onSuccess { message = "Запрос отправлен. Экран обновится после ответа родителя." }
                .onFailure { message = it.message ?: "Не удалось отправить запрос" }
            busyAsk = false
        }
    }

    companion object {
        @Volatile
        var visible: Boolean = false
    }
}
