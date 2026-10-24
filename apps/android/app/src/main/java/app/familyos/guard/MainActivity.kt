package app.familyos.guard

import android.Manifest
import android.app.AppOpsManager
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.net.Uri
import android.os.Bundle
import android.os.Process
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickMultipleVisualMedia
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.ui.platform.LocalContext
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
import app.familyos.guard.data.OfflineStore
import androidx.lifecycle.Lifecycle
import android.content.ClipData
import android.content.ClipboardManager
import app.familyos.guard.data.Policy
import app.familyos.guard.data.CalendarData
import app.familyos.guard.data.HomeData
import app.familyos.guard.data.Product
import app.familyos.guard.data.ProfileData
import app.familyos.guard.data.Recipe
import app.familyos.guard.data.QuestData
import app.familyos.guard.data.QuestItem
import app.familyos.guard.data.ShopData
import app.familyos.guard.service.AppBlockService
import app.familyos.guard.work.GuardNotifications
import app.familyos.guard.service.LocationTrackingService
import kotlinx.coroutines.launch
import java.time.YearMonth

private data class UiState(
    val configured: Boolean = false,
    val policy: Policy? = null,
    val deviceOwner: Boolean = false,
    val usageAccess: Boolean = false,
    val accessibility: Boolean = false,
    val serverUrl: String = "",
    val familyLink: Boolean = false,
)

class MainActivity : ComponentActivity() {

    private lateinit var store: GuardStore
    private lateinit var offline: OfflineStore
    private var state by mutableStateOf(UiState())
    private var busy by mutableStateOf(false)
    private var message by mutableStateOf<String?>(null)
    private var tab by mutableStateOf(0)
    private var quests by mutableStateOf<QuestData?>(null)
    private var shop by mutableStateOf<ShopData?>(null)
    private var home by mutableStateOf<HomeData?>(null)
    private var profile by mutableStateOf<ProfileData?>(null)
    private var calendar by mutableStateOf<CalendarData?>(null)
    private var scanQuest by mutableStateOf<QuestItem?>(null)
    private var cookQuest by mutableStateOf<QuestItem?>(null)
    private var session by mutableStateOf<org.json.JSONObject?>(null)
    private var recipes by mutableStateOf<List<Recipe>>(emptyList())
    private var products by mutableStateOf<List<Product>>(emptyList())
    private var levelUpLevel by mutableStateOf<Int?>(null)
    private var lastLevel by mutableStateOf<Int?>(null)
    private var pendingQuestId by mutableStateOf<String?>(null)
    private var proposalPhoto by mutableStateOf<String?>(null)

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private val backgroundLocationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private val locationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                backgroundLocationPermission.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                startTracking()
            }
        }

    private val questPhotoPicker =
        registerForActivityResult(PickMultipleVisualMedia(3)) { uris ->
            val questId = pendingQuestId
            if (questId != null && uris.isNotEmpty()) uploadQuestPhotos(questId, uris)
        }

    private val proposalPhotoPicker =
        registerForActivityResult(PickVisualMedia()) { uri ->
            if (uri != null) {
                proposalPhoto = ImageUtils.toBase64(this, uri)
                message = if (proposalPhoto != null) "Фото добавлено" else "Не удалось прочитать фото"
            }
        }

    private val avatarPicker =
        registerForActivityResult(PickVisualMedia()) { uri ->
            if (uri != null) uploadAvatar(uri)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = GuardStore(this)
        offline = OfflineStore(this)
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

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            locationPermission.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }

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
                    if (state.configured) {
                        Column(Modifier.fillMaxSize().statusBarsPadding()) {
                            Box(Modifier.weight(1f)) {
                                val active = session
                                if (active != null) {
                                    SessionBlockScreen(
                                        title = active.optString("title", "Задание"),
                                        endsAt = active.optString("endsAt").let { raw ->
                                            val part = if (raw.contains('T')) raw.substringAfter('T') else raw.substringAfter(' ')
                                            part.take(5).ifBlank { "конца задания" }
                                        },
                                        onFinish = { finishSessionNow(active.optString("questId")) },
                                    )
                                } else when (tab) {
                                    0 -> HomeScreen(
                                        home = home,
                                        busy = busy,
                                        message = message,
                                        onRefresh = { refresh() },
                                        onOpenProfile = { tab = 4 },
                                        calendar = calendar,
                                        onAddEvent = { title, description, geofenceId, date, start, minutes, lat, lng ->
                                            addEvent(title, description, geofenceId, date, start, minutes, lat, lng)
                                        },
                                    )
                                    1 -> QuestsScreen(
                                        quests = quests,
                                        busy = busy,
                                        message = message,
                                        onComplete = { id, photos -> startQuest(id, photos) },
                                        onClaim = { claimQuest(it) },
                                        onScan = { scanQuest = it },
                                        onCook = { cookQuest = it },
                                        onRefresh = { refresh() },
                                    )
                                    2 -> ShopScreen(
                                        shop = shop,
                                        busy = busy,
                                        message = message,
                                        proposalPhoto = proposalPhoto,
                                        onBuy = { buyItem(it) },
                                        onRefresh = { refresh() },
                                        onPickPhoto = { startProposalPhoto() },
                                        onPropose = { title, description -> proposeItem(title, description) },
                                    )
                                    3 -> Dashboard(
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
                                    else -> ProfileScreen(
                                        profile = profile,
                                        busy = busy,
                                        message = message,
                                        onPickAvatar = { avatarPicker.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly)) },
                                        onSave = { nickname, school, grade -> saveProfile(nickname, school, grade) },
                                        onRefresh = { refresh() },
                                        onOpenParent = {
                                            startActivity(Intent(this@MainActivity, ParentActivity::class.java))
                                        },
                                    )
                                }
                                scanQuest?.let { quest ->
                                    ShoppingQuestDialog(
                                        questId = quest.id,
                                        title = quest.title,
                                        busy = busy,
                                        loadDetails = { id -> ApiClient(store).questDetails(id) },
                                        onScan = { id, code -> ApiClient(store).scanQuestItem(id, code) },
                                        onFinish = {
                                            val id = quest.id
                                            scanQuest = null
                                            completeAndClose(id)
                                        },
                                        onDismiss = { scanQuest = null },
                                    )
                                }
                                cookQuest?.let { quest ->
                                    CookingQuestDialog(
                                        questId = quest.id,
                                        title = quest.title,
                                        busy = busy,
                                        loadDetails = { id -> ApiClient(store).questDetails(id) },
                                        onFinish = {
                                            val id = quest.id
                                            cookQuest = null
                                            completeAndClose(id)
                                        },
                                        onDismiss = { cookQuest = null },
                                    )
                                }
                                levelUpLevel?.let { level ->
                                    LevelUpDialog(
                                        level = level,
                                        xp = home?.xp ?: 0,
                                        strength = home?.strength ?: 0,
                                        intellect = home?.intellect ?: 0,
                                        creativity = home?.creativity ?: 0,
                                        kindness = home?.kindness ?: 0,
                                        discipline = home?.discipline ?: 0,
                                        onDismiss = { levelUpLevel = null },
                                    )
                                }
                            }
                            BottomBar(tab) { tab = it }
                        }
                    } else {
                        Setup(
                            initialUrl = state.serverUrl,
                            message = message,
                            onSave = { url, token -> saveConfig(url, token) },
                            onParent = { startActivity(Intent(this@MainActivity, ParentActivity::class.java)) },
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
            familyLink = isFamilyLinkInstalled(),
        )
        blockUninstallIfOwner()
        if (!configured) return

        lifecycleScope.launch {
            busy = true
            val api = ApiClient(store)
            runCatching { api.refreshPolicy() }
                .onSuccess { state = state.copy(policy = it) }
                .onFailure { message = it.message }
            runCatching { api.quests() }.onSuccess {
                quests = it
                offline.cache("quests", org.json.JSONObject().put("data", org.json.JSONObject()).toString())
            }
            runCatching { api.shop() }.onSuccess { shop = it }
            runCatching { api.home() }.onSuccess { next ->
                val previous = lastLevel
                if (previous != null && next.level > previous) levelUpLevel = next.level
                lastLevel = next.level
                home = next
            }.onFailure {
                message = "Нет связи с сервером — показаны последние данные"
            }
            runCatching { api.profile() }.onSuccess { profile = it }
            runCatching { api.calendar(YearMonth.now().toString()) }.onSuccess { calendar = it }
            runCatching { api.policySession() }.onSuccess {
                session = it
                store.sessionActive = it != null
            }
            runCatching { api.recipes() }.onSuccess { recipes = it }
            runCatching { api.products() }.onSuccess { products = it }
            flushOutbox(api)
            reportPendingCrash()
            busy = false
            startTracking()
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

    private fun flushOutbox(api: ApiClient) {
        val pending = offline.pending()
        if (pending.isEmpty()) return
        lifecycleScope.launch {
            for (item in pending) {
                val ok = runCatching {
                    api.rawCall(item.optString("method", "POST"), item.optString("path"), item.optString("body"))
                }.isSuccess
                if (ok) offline.remove(item) else break
            }
        }
    }

    private fun cookRecipe(id: String) {
        lifecycleScope.launch {
            busy = true
            val api = ApiClient(store)
            runCatching {
                val result = api.cookRecipe(id)
                recipes = api.recipes()
                products = api.products()
                val missing = result.optJSONArray("missing")
                message = if (missing != null && missing.length() > 0) {
                    "Не всё нашлось: " + (0 until missing.length()).joinToString(", ") { missing.optString(it) }
                } else {
                    "Приготовлено, продукты списаны"
                }
            }.onFailure { message = it.message }
            busy = false
        }
    }

    private fun addEvent(
        title: String,
        description: String,
        geofenceId: String?,
        date: String,
        start: String,
        minutes: Int,
        lat: Double?,
        lng: Double?,
    ) {
        lifecycleScope.launch {
            busy = true
            val api = ApiClient(store)
            runCatching {
                api.createEvent(title, description, geofenceId, date, start, minutes, lat, lng)
                calendar = api.calendar(YearMonth.now().toString())
            }.onSuccess { message = "Событие отправлено родителю" }
                .onFailure {
                    if (offline != null) {
                        offline!!.enqueue(
                            "event",
                            "/api/device/child-events",
                            org.json.JSONObject()
                                .put("title", title)
                                .put("description", description)
                                .put("date", date)
                                .put("startTime", start)
                                .put("durationMin", minutes),
                        )
                        message = "Нет связи — событие отправится позже"
                    } else {
                        message = it.message
                    }
                }
            busy = false
        }
    }

    private fun finishSessionNow(questId: String) {
        if (questId.isBlank()) return
        lifecycleScope.launch {
            busy = true
            val api = ApiClient(store)
            runCatching { api.finishQuest(questId) }
            completeAndClose(questId)
            busy = false
        }
    }

    private fun completeAndClose(questId: String) {
        lifecycleScope.launch {
            busy = true
            val api = ApiClient(store)
            runCatching {
                api.completeQuest(questId)
                api.finishQuest(questId)
                store.sessionActive = false
                session = null
                quests = api.quests()
                home = api.home()
                message = "Задание отмечено"
            }.onFailure { message = it.message }
            busy = false
        }
    }

    private fun claimQuest(questId: String) {
        lifecycleScope.launch {
            busy = true
            val api = ApiClient(store)
            runCatching {
                api.claimQuest(questId)
                quests = api.quests()
                home = api.home()
            }.onSuccess { message = "Задание твоё" }.onFailure { message = it.message }
            busy = false
        }
    }

    private fun startQuest(questId: String, requiresPhotos: Boolean) {
        if (requiresPhotos) {
            pendingQuestId = questId
            questPhotoPicker.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly))
        } else {
            completeQuest(questId)
        }
    }

    private fun uploadQuestPhotos(questId: String, uris: List<Uri>) {
        lifecycleScope.launch {
            busy = true
            val images = uris.mapNotNull { ImageUtils.toBase64(this@MainActivity, it) }
            if (images.isEmpty()) {
                message = "Не удалось прочитать фото"
                busy = false
                return@launch
            }
            val api = ApiClient(store)
            runCatching {
                api.completeQuest(questId, images)
                quests = api.quests()
            }.onSuccess {
                message = "Фото отправлены, ждём проверки"
            }.onFailure {
                message = it.message
            }
            busy = false
        }
    }

    private fun startProposalPhoto() {
        proposalPhotoPicker.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly))
    }

    private fun proposeItem(title: String, description: String) {
        lifecycleScope.launch {
            busy = true
            val api = ApiClient(store)
            runCatching {
                api.proposeShopItem(title, description, proposalPhoto)
                shop = api.shop()
                proposalPhoto = null
            }.onSuccess {
                message = "Предложение отправлено. OpenClaw оценит товар."
            }.onFailure {
                message = it.message
            }
            busy = false
        }
    }

    private fun uploadAvatar(uri: Uri) {
        lifecycleScope.launch {
            busy = true
            val encoded = ImageUtils.toBase64(this@MainActivity, uri, maxDim = 800)
            if (encoded == null) {
                message = "Не удалось прочитать фото"
                busy = false
                return@launch
            }
            val api = ApiClient(store)
            runCatching {
                api.uploadAvatar(encoded)
                profile = api.profile()
                home = api.home()
            }.onSuccess {
                message = "Фото профиля обновлено"
            }.onFailure {
                message = it.message
            }
            busy = false
        }
    }

    private fun saveProfile(nickname: String, school: String, grade: String) {
        lifecycleScope.launch {
            busy = true
            val api = ApiClient(store)
            runCatching {
                api.updateProfile(nickname.ifBlank { null }, school.ifBlank { null }, grade.ifBlank { null })
                profile = api.profile()
                home = api.home()
            }.onSuccess {
                message = "Профиль сохранён"
            }.onFailure {
                message = it.message
            }
            busy = false
        }
    }

    private fun startTracking() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        // Запуск из фона на Android 12+ запрещён — не роняем приложение.
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
            runCatching {
                ContextCompat.startForegroundService(this, Intent(this, LocationTrackingService::class.java))
            }
        }
    }

    private fun reportPendingCrash() {
        val report = CrashReporter.pending(this) ?: return
        lifecycleScope.launch {
            val lines = report.lineSequence().take(3).joinToString(" | ")
            val ok = runCatching {
                ApiClient(store).reportCrash(lines.take(480), report)
            }.isSuccess
            if (ok) CrashReporter.clear(this@MainActivity)
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

    private fun isFamilyLinkInstalled(): Boolean {
        val packages = listOf(
            "com.google.android.apps.kids.familylink",
            "com.google.android.gms.supervision",
            "com.google.android.apps.kids.familylinkhelper",
        )
        return packages.any { name ->
            runCatching { packageManager.getPackageInfo(name, 0) }.isSuccess
        }
    }

    private fun blockUninstallIfOwner() {
        val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        if (dpm.isDeviceOwnerApp(packageName)) {
            runCatching {
                val admin = ComponentName(this, GuardDeviceAdmin::class.java)
                dpm.setUninstallBlocked(admin, packageName, true)
                dpm.addUserRestriction(admin, android.os.UserManager.DISALLOW_SAFE_BOOT)
                dpm.addUserRestriction(admin, android.os.UserManager.DISALLOW_FACTORY_RESET)
                dpm.addUserRestriction(admin, android.os.UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES)
            }
        }
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
private fun Setup(
    initialUrl: String,
    message: String?,
    onSave: (String, String) -> Unit,
    onParent: () -> Unit,
) {
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
            Text("Подключить как ребёнок")
        }
        Spacer(Modifier.height(8.dp))
        Button(onClick = onParent, modifier = Modifier.fillMaxWidth()) {
            Text("Я родитель")
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
    val context = LocalContext.current
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
                if (!state.usageAccess) PermissionRow("Статистика использования", false, onUsageSettings)
                if (!state.accessibility) PermissionRow("Специальные возможности (блокировка)", false, onAccessibilitySettings)
                if (!state.deviceOwner) PermissionRow("Device Owner (защита от удаления)", false, onAdmin)
                if (state.usageAccess && state.accessibility && state.deviceOwner) {
                    Text("Все доступы выданы", color = Color(0xFF34D399), fontWeight = FontWeight.SemiBold)
                }
                Text(
                    if (state.familyLink)
                        "Google Family Link обнаружен. Если он управляет устройством, наш Device Owner недоступен — используйте Family Link для базовых лимитов, а наши задания и награды поверх."
                    else "Google Family Link не обнаружен.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                )
                Text(
                    "Device Owner назначается при первоначальной настройке телефона через QR или ADB. " +
                        "Без него лимиты работают, но приложение можно удалить.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                )
                if (!state.deviceOwner) {
                    Text(
                        "Команда для ПК (телефон без аккаунтов):",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                    )
                    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF0F151D))) {
                        Text(
                            "adb shell dpm set-device-owner app.familyos.guard/.admin.GuardDeviceAdmin",
                            Modifier.padding(10.dp),
                            fontSize = 10.sp,
                            color = Color(0xFF9AE6B4),
                        )
                    }
                    Button(
                        onClick = {
                            val clipboard = context.getSystemService(ClipboardManager::class.java)
                            clipboard.setPrimaryClip(
                                ClipData.newPlainText(
                                    "device owner",
                                    "adb shell dpm set-device-owner app.familyos.guard/.admin.GuardDeviceAdmin",
                                ),
                            )
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Скопировать команду") }
                }
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

@Composable
private fun TabButton(label: String, selected: Boolean, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
    ) {
        Text(
            label,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            fontSize = 11.sp,
            maxLines = 1,
            softWrap = false,
        )
    }
}
