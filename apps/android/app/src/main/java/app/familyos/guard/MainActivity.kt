package app.familyos.guard

import android.Manifest
import android.app.AppOpsManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import app.familyos.guard.admin.GuardDeviceAdmin
import app.familyos.guard.api.ApiClient
import app.familyos.guard.data.GuardStore
import app.familyos.guard.data.Policy
import app.familyos.guard.data.QuestData
import app.familyos.guard.data.ShopData
import app.familyos.guard.service.AppBlockService
import app.familyos.guard.work.GuardNotifications
import kotlinx.coroutines.launch

private data class UiState(
    val configured: Boolean = false,
    val policy: Policy? = null,
    val deviceOwner: Boolean = false,
    val usageAccess: Boolean = false,
    val accessibility: Boolean = false,
    val serverUrl: String = "",
)

class MainActivity : ComponentActivity() {

    private lateinit var store: GuardStore
    private var state by mutableStateOf(UiState())
    private var busy by mutableStateOf(false)
    private var message by mutableStateOf<String?>(null)
    private var tab by mutableStateOf(0)
    private var quests by mutableStateOf<QuestData?>(null)
    private var shop by mutableStateOf<ShopData?>(null)

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = GuardStore(this)
        GuardNotifications.ensureChannel(this)

        val extraUrl = intent?.getStringExtra("server_url")
        val extraKey = intent?.getStringExtra("device_token")
        if (!extraUrl.isNullOrBlank() && !extraKey.isNullOrBlank()) {
            store.configure(extraUrl, extraKey)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFF34D399),
                    background = Color(0xFF0B0F14),
                    surface = Color(0xFF121922),
                    onSurface = Color(0xFFE6EDF3),
                    onSurfaceVariant = Color(0xFF8B9BB0),
                ),
            ) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    if (state.configured) {
                        Column(Modifier.fillMaxSize()) {
                            TabRow(selectedTabIndex = tab) {
                                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Щит") })
                                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Задания") })
                                Tab(selected = tab == 2, onClick = { tab = 2 }, text = { Text("Магазин") })
                            }
                            Box(Modifier.weight(1f)) {
                                when (tab) {
                                    0 -> Dashboard(
                                        state = state,
                                        busy = busy,
                                        message = message,
                                        onRefresh = { refresh() },
                                        onAsk = { askForTime() },
                                        onUsageSettings = { openUsageSettings() },
                                        onAccessibilitySettings = { openAccessibilitySettings() },
                                        onAdmin = { requestAdmin() },
                                        onReset = { reset() },
                                    )
                                    1 -> QuestsScreen(
                                        quests = quests,
                                        busy = busy,
                                        message = message,
                                        onComplete = { completeQuest(it) },
                                        onRefresh = { refresh() },
                                    )
                                    else -> ShopScreen(
                                        shop = shop,
                                        busy = busy,
                                        message = message,
                                        onBuy = { buyItem(it) },
                                        onRefresh = { refresh() },
                                    )
                                }
                            }
                        }
                    } else {
                        Setup(
                            initialUrl = state.serverUrl,
                            message = message,
                            onSave = { url, token -> saveConfig(url, token) },
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (::store.isInitialized) refresh()
    }

    private fun saveConfig(url: String, key: String) {
        if (url.isBlank() || key.isBlank()) {
            message = "Заполните адрес сервера и токен"
            return
        }
        store.configure(url, key)
        message = null
        refresh()
    }

    private fun reset() {
        store.clear()
        state = UiState()
        message = "Настройки удалены"
    }

    private fun refresh() {
        val configured = store.isConfigured
        state = state.copy(
            configured = configured,
            deviceOwner = isDeviceOwner(),
            usageAccess = hasUsageAccess(),
            accessibility = isAccessibilityEnabled(),
            serverUrl = store.serverUrl,
        )
        if (!configured) return

        lifecycleScope.launch {
            busy = true
            val api = ApiClient(store)
            runCatching { api.refreshPolicy() }
                .onSuccess { state = state.copy(policy = it) }
                .onFailure { message = it.message }
            runCatching { api.quests() }.onSuccess { quests = it }
            runCatching { api.shop() }.onSuccess { shop = it }
            busy = false
        }
    }

    private fun askForTime() {
        lifecycleScope.launch {
            busy = true
            runCatching { ApiClient(store).requestExtension(15, "Запрос из приложения") }
                .onSuccess { message = "Запрос отправлен. Родитель увидит его в панели." }
                .onFailure { message = it.message }
            busy = false
        }
    }

    private fun completeQuest(questId: String) {
        lifecycleScope.launch {
            busy = true
            val api = ApiClient(store)
            runCatching {
                api.completeQuest(questId)
                quests = api.quests()
            }.onSuccess {
                message = "Отправлено родителю на проверку"
            }.onFailure {
                message = it.message
            }
            busy = false
        }
    }

    private fun buyItem(itemId: String) {
        lifecycleScope.launch {
            busy = true
            val api = ApiClient(store)
            runCatching {
                api.buyItem(itemId)
                shop = api.shop()
            }.onSuccess {
                message = "Запрос на покупку отправлен родителю"
            }.onFailure {
                message = it.message
            }
            busy = false
        }
    }

    private fun openUsageSettings() {
        runCatching { startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
            .onFailure { Toast.makeText(this, "Не удалось открыть настройки", Toast.LENGTH_SHORT).show() }
    }

    private fun openAccessibilitySettings() {
        runCatching { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
            .onFailure { Toast.makeText(this, "Не удалось открыть настройки", Toast.LENGTH_SHORT).show() }
    }

    private fun requestAdmin() {
        val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
            putExtra(
                DevicePolicyManager.EXTRA_DEVICE_ADMIN,
                ComponentName(this@MainActivity, GuardDeviceAdmin::class.java),
            )
            putExtra(
                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "Нужен, чтобы приложение нельзя было удалить без пароля родителя.",
            )
        }
        runCatching { startActivity(intent) }
    }

    private fun isDeviceOwner(): Boolean {
        val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        return dpm.isDeviceOwnerApp(packageName)
    }

    private fun hasUsageAccess(): Boolean {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), packageName)
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun isAccessibilityEnabled(): Boolean {
        val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
        return enabled.contains("$packageName/${AppBlockService::class.java.name}", ignoreCase = true)
    }
}

@Composable
private fun Setup(initialUrl: String, message: String?, onSave: (String, String) -> Unit) {
    var url by remember { mutableStateOf(initialUrl) }
    var token by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Family OS Guard", fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            "Введите адрес сервера и токен устройства. Токен выдаёт родитель в панели Guard.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))

        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            label = { Text("Адрес сервера") },
            placeholder = { Text("https://family.example.com") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = token,
            onValueChange = { token = it },
            label = { Text("Токен устройства") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(20.dp))
        Button(onClick = { onSave(url, token) }, modifier = Modifier.fillMaxWidth()) {
            Text("Подключить")
        }

        message?.let {
            Spacer(Modifier.height(18.dp))
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF1F0D0D))) {
                Text(it, modifier = Modifier.padding(14.dp), color = Color(0xFFFCA5A5))
            }
        }
    }
}

@Composable
private fun Dashboard(
    state: UiState,
    busy: Boolean,
    message: String?,
    onRefresh: () -> Unit,
    onAsk: () -> Unit,
    onUsageSettings: () -> Unit,
    onAccessibilitySettings: () -> Unit,
    onAdmin: () -> Unit,
    onReset: () -> Unit,
) {
    val policy = state.policy
    val fraction = if (policy != null && policy.limitMinutes > 0) {
        (policy.usedMinutes.toFloat() / policy.limitMinutes).coerceIn(0f, 1f)
    } else {
        0f
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text("Family OS Guard", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text(
                    policy?.childName?.takeIf { it.isNotBlank() } ?: "Устройство подключено",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TextButton(onClick = onReset) { Text("Сброс") }
        }

        if (message != null) {
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1708))) {
                Text(message, modifier = Modifier.padding(14.dp), color = Color(0xFFFDE68A))
            }
        }

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(18.dp)) {
                Text("Экран сегодня", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                Spacer(Modifier.height(6.dp))
                Text(
                    policy?.let { "${minutesText(it.usedMinutes)} из ${minutesText(it.limitMinutes)}" } ?: "Нет данных",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier.fillMaxWidth().height(8.dp),
                    color = if (fraction >= 1f) Color(0xFFF87171) else Color(0xFF34D399),
                    trackColor = Color(0xFF1F2A37),
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    policy?.let { "Осталось: ${minutesText(it.remainMinutes)}" } ?: "Нажмите «Обновить»",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onRefresh, enabled = !busy, modifier = Modifier.weight(1f)) {
                        Text(if (busy) "Обновляем…" else "Обновить")
                    }
                    Button(onClick = onAsk, enabled = !busy, modifier = Modifier.weight(1f)) {
                        Text("+15 минут")
                    }
                }
            }
        }

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Доступы", fontWeight = FontWeight.SemiBold)
                PermissionRow("Статистика использования", state.usageAccess, onUsageSettings)
                PermissionRow("Специальные возможности (блокировка)", state.accessibility, onAccessibilitySettings)
                PermissionRow("Device Owner (защита от удаления)", state.deviceOwner, onAdmin)
                Text(
                    "Device Owner назначается при первоначальной настройке телефона через QR или ADB. " +
                        "Без него лимиты работают, но приложение можно удалить.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                )
            }
        }

        Text(
            "Family OS Guard не читает переписки, не делает скриншоты и не слушает микрофон. " +
                "Он видит только имя активного приложения и время использования.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun PermissionRow(title: String, granted: Boolean, onOpen: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp)
            Text(
                if (granted) "включено" else "не включено",
                color = if (granted) Color(0xFF34D399) else Color(0xFFF87171),
                fontSize = 12.sp,
            )
        }
        TextButton(onClick = onOpen) { Text(if (granted) "Открыть" else "Включить") }
    }
}

private fun minutesText(minutes: Int): String {
    if (minutes < 60) return "$minutes мин"
    val hours = minutes / 60
    val rest = minutes % 60
    return if (rest == 0) "$hours ч" else "$hours ч $rest мин"
}
