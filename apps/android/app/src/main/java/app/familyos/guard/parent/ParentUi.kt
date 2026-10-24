package app.familyos.guard.parent

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.familyos.guard.api.PairClient
import app.familyos.guard.api.ParentApi
import app.familyos.guard.scan.CodeScannerDialog
import app.familyos.guard.scan.QrImage
import app.familyos.guard.data.ChildCard
import app.familyos.guard.data.MoneyOverview
import app.familyos.guard.data.PendingEvent
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import org.json.JSONObject

private val PAccent = Color(android.graphics.Color.parseColor(app.familyos.guard.BuildConfig.ACCENT))
private val PGold = Color(0xFFFBBF24)
private val PMuted = Color(0xFF8B9BB0)
private val PRed = Color(0xFFF87171)

@Composable
fun ParentApp(
    initialUrl: String,
    initialToken: String,
    cache: app.familyos.guard.data.OfflineStore,
    onExit: () -> Unit,
    onTokenSaved: (String) -> Unit = {},
) {
    var baseUrl by remember { mutableStateOf(initialUrl) }
    var token by remember { mutableStateOf(initialToken) }
    var loggedIn by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var tab by remember { mutableStateOf(1) }

    var children by remember { mutableStateOf<List<ChildCard>>(emptyList()) }
    var events by remember { mutableStateOf<List<PendingEvent>>(emptyList()) }
    var money by remember { mutableStateOf<MoneyOverview?>(null) }
    var publicUrl by remember { mutableStateOf(baseUrl) }
    val scope = rememberCoroutineScope()

    fun api() = ParentApi(baseUrl, token)

    fun applyBootstrap(data: JSONObject) {
        runCatching {
            data.optJSONObject("overview")?.let { overview ->
                publicUrl = baseUrl
                children = ChildCard.listFromOverview(JSONObject().put("data", overview), baseUrl)
            }
        }
        runCatching {
            data.optJSONArray("events")?.let { array ->
                events = PendingEvent.listFrom(JSONObject().put("data", array))
            }
        }
        runCatching {
            data.optJSONObject("money")?.let { moneyData ->
                money = MoneyOverview.fromJson(JSONObject().put("data", moneyData))
            }
        }
    }

    suspend fun loadAll(force: Boolean = false) {
        val cached = cache.cached("guard_bootstrap")
        if (cached != null) runCatching { applyBootstrap(JSONObject(cached)) }
        if (!force && cached != null && cache.cacheFresh("guard_bootstrap", 5 * 60_000L)) return

        runCatching { api().bootstrap() }
            .onSuccess { data ->
                cache.cache("guard_bootstrap", data.toString())
                applyBootstrap(data)
                error = null
            }
            .onFailure { if (cached == null) error = it.message }
    }

    if (!loggedIn) {
        ParentLogin(
            baseUrl = baseUrl,
            token = token,
            busy = busy,
            error = error,
            onUrl = { baseUrl = it },
            onToken = { token = it },
            onLogin = {
                scope.launch {
                    busy = true
                    error = null
                    runCatching {
                        ParentApi(baseUrl, token).get("/api/accounts")
                    }.onSuccess {
                        onTokenSaved(token)
                        loggedIn = true
                        loadAll()
                    }.onFailure {
                        error = it.message
                    }
                    busy = false
                }
            },
            onQrLogin = { code ->
                scope.launch {
                    busy = true
                    error = null
                    val target = baseUrl.ifBlank { app.familyos.guard.BuildConfig.SERVER_HINT }
                    runCatching {
                        val claimed = PairClient.claim(target, code, "parent")
                        if (claimed.role != "parent") error("Этот QR для детского приложения")
                        baseUrl = claimed.serverUrl.ifBlank { target }
                        token = claimed.token
                        onTokenSaved(claimed.token)
                        ParentApi(baseUrl, token).get("/api/accounts")
                    }.onSuccess {
                        loggedIn = true
                        loadAll()
                    }.onFailure { error = it.message ?: "Не удалось войти" }
                    busy = false
                }
            },
            onExit = onExit,
        )
        return
    }

    LaunchedEffect(loggedIn) { if (loggedIn) loadAll() }

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) {
            when (tab) {
                1 -> ParentOverview(children, events.size, busy, api = { api() }) {
                    scope.launch { busy = true; loadAll(); busy = false }
                }
                2 -> ParentMoneyScreen(
                    money = money,
                    api = { api() },
                    busy = busy,
                    currency = money?.currency ?: "RUB",
                    onAdd = { amount, kind, accountId, categoryId, payee ->
                        scope.launch {
                            busy = true
                            runCatching {
                                api().post(
                                    "/api/transactions",
                                    JSONObject()
                                        .put("amount", amount)
                                        .put("kind", kind)
                                        .put("accountId", accountId)
                                        .put("categoryId", categoryId)
                                        .put("payee", payee),
                                )
                                money = MoneyOverview.fromJson(api().get("/api/money/overview"))
                            }.onFailure { error = it.message }
                            busy = false
                        }
                    },
                    onAddAccount = { title, balance ->
                        scope.launch {
                            busy = true
                            runCatching {
                                api().post("/api/accounts", JSONObject().put("title", title).put("balance", balance))
                                money = MoneyOverview.fromJson(api().get("/api/money/overview"))
                            }.onFailure { error = it.message }
                            busy = false
                        }
                    },
                    onRefresh = { scope.launch { busy = true; loadAll(); busy = false } },
                )
                3 -> ParentEventsScreen(
                    events = events,
                    busy = busy,
                    onDecide = { id, approve, time, minutes ->
                        scope.launch {
                            busy = true
                            runCatching {
                                api().post(
                                    "/api/guard/child-events/$id/decide",
                                    JSONObject()
                                        .put("approve", approve)
                                        .put("startTime", time)
                                        .put("durationMin", minutes),
                                )
                                events = PendingEvent.listFrom(api().get("/api/guard/child-events?status=pending"))
                            }.onFailure { error = it.message }
                            busy = false
                        }
                    },
                    onRefresh = { scope.launch { busy = true; loadAll(); busy = false } },
                )
                4 -> ParentPantryScreen(
                    busy = busy,
                    onRefresh = { scope.launch { busy = true; loadAll(); busy = false } },
                    api = { api() },
                )
                else -> ParentChildrenScreen(
                    children = children,
                    busy = busy,
                    api = { api() },
                    onSave = { id, nickname, school, grade ->
                        scope.launch {
                            busy = true
                            runCatching {
                                api().patch(
                                    "/api/guard/children/$id/profile",
                                    JSONObject()
                                        .put("nickname", nickname)
                                        .put("school", school)
                                        .put("grade", grade),
                                )
                                loadAll()
                            }.onFailure { error = it.message }
                            busy = false
                        }
                    },
                    onRefresh = { scope.launch { busy = true; loadAll(); busy = false } },
                )
            }
            if (busy) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                    CircularProgressIndicator(Modifier.padding(top = 12.dp).size(22.dp), strokeWidth = 2.dp)
                }
            }
        }
        error?.let {
            Row(
                Modifier.fillMaxWidth().background(Color(0xFF2A1214)).padding(10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(it, color = Color(0xFFFCA5A5), fontSize = 11.sp, modifier = Modifier.weight(1f))
                Text("ок", color = PMuted, fontSize = 11.sp, modifier = Modifier.clickable { error = null })
            }
        }
        Row(
            Modifier.fillMaxWidth().background(Color(0xFF0E141C)).padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            PTab("Обзор", tab == 1) { tab = 1 }
            PTab("Деньги", tab == 2) { tab = 2 }
            PTab("События", tab == 3) { tab = 3 }
            PTab("Склад", tab == 4) { tab = 4 }
            PTab("Дети", tab == 0) { tab = 0 }
        }
    }
}

@Composable
@Composable
private fun BigMetric(value: String, label: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(value, fontSize = 26.sp, fontWeight = FontWeight.Bold, color = PAccent)
            Text(label, fontSize = 11.sp, color = PMuted)
        }
    }
}

private fun PTab(label: String, selected: Boolean, onClick: () -> Unit) {
    Column(
        Modifier.clip(RoundedCornerShape(12.dp)).clickable { onClick() }.padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            label,
            color = if (selected) PAccent else PMuted,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            softWrap = false,
        )
    }
}

@Composable
private fun ParentLogin(
    baseUrl: String,
    token: String,
    busy: Boolean,
    error: String?,
    onUrl: (String) -> Unit,
    onToken: (String) -> Unit,
    onLogin: () -> Unit,
    onQrLogin: (String) -> Unit,
    onExit: () -> Unit,
) {
    var scanning by remember { mutableStateOf(false) }
    if (scanning) {
        CodeScannerDialog(
            title = "Вход по QR",
            hint = "Откройте панель Family OS → «Вход по QR» и отсканируйте код",
            qrOnly = true,
            onResult = { raw ->
                scanning = false
                val parsed = app.familyos.guard.api.PairPayload.parse(raw)
                if (parsed.code.length >= 6) onQrLogin(parsed.code)
            },
            onDismiss = { scanning = false },
        )
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Spacer(Modifier.height(20.dp))
        Text("Родительский вход", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text("Токен администратора выдаётся сервером Family OS", color = PMuted, fontSize = 13.sp)
        OutlinedTextField(
            value = baseUrl,
            onValueChange = onUrl,
            label = { Text("Адрес сервера") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = token,
            onValueChange = onToken,
            label = { Text("Токен родителя") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )
        Button(onClick = onLogin, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
            Text(if (busy) "Проверяем…" else "Войти по токену")
        }
        Button(onClick = { scanning = true }, enabled = !busy, modifier = Modifier.fillMaxWidth()) {
            Text("📷 Войти по QR")
        }
        if (error != null) Text(error, color = PRed, fontSize = 12.sp)
    }
}

@Composable
private fun ParentOverview(
    children: List<ChildCard>,
    pendingEvents: Int,
    busy: Boolean,
    api: () -> ParentApi,
    onRefresh: () -> Unit,
) {
    var sitesOpen by remember { mutableStateOf(false) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Обзор", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            TextButton(onClick = onRefresh, enabled = !busy) { Text("Обновить") }
        }
        val screenTotal = children.sumOf { it.usedMinutes }
        val requestTotal = children.sumOf { it.pendingRequests }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BigMetric("$screenTotal", "минут экрана", Modifier.weight(1f))
                BigMetric("$requestTotal", "запросов", Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                BigMetric("$pendingEvents", "событий", Modifier.weight(1f))
                BigMetric("${children.size}", "детей", Modifier.weight(1f))
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.weight(1f)) {
                ParentQuestCreator(children = children, api = { api() }, onDone = onRefresh)
            }
            TextButton(onClick = { sitesOpen = true }) { Text("Сайты") }
        }
        if (sitesOpen) {
            SitesRulesDialog(api = { api() }, onDismiss = { sitesOpen = false })
        }

        if (pendingEvents > 0) {
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF2A2308))) {
                Text("$pendingEvents событий ждут подтверждения", Modifier.padding(14.dp), color = PGold)
            }
        }
        children.forEach { child ->
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Column(Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (child.avatarUrl != null) {
                            AsyncImage(
                                model = child.avatarUrl,
                                contentDescription = child.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(44.dp).clip(CircleShape),
                            )
                        } else {
                            Box(
                                Modifier.size(44.dp).clip(CircleShape).background(Color(0xFF1F2A37)),
                                contentAlignment = Alignment.Center,
                            ) { Text("🙂") }
                        }
                        Spacer(Modifier.size(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(child.nickname ?: child.name, fontWeight = FontWeight.SemiBold)
                            Text(
                                listOfNotNull(child.school, child.grade).joinToString(" · ").ifBlank { "школа не указана" },
                                color = PMuted,
                                fontSize = 11.sp,
                            )
                        }
                        if (child.pendingRequests > 0) {
                            Text("${child.pendingRequests} запрос", color = PGold, fontSize = 11.sp)
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    LinearProgressIndicator(
                        progress = {
                            if (child.limitMinutes <= 0) 0f
                            else (child.usedMinutes.toFloat() / child.limitMinutes).coerceIn(0f, 1f)
                        },
                        modifier = Modifier.fillMaxWidth().height(7.dp),
                        color = if (child.remainingMinutes > 0) PAccent else PRed,
                        trackColor = Color(0xFF1F2A37),
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "${child.usedMinutes} из ${child.limitMinutes} мин · осталось ${child.remainingMinutes}",
                        color = PMuted,
                        fontSize = 11.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun ParentQuestCreator(
    children: List<ChildCard>,
    api: () -> ParentApi,
    onDone: () -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    var childIndex by remember { mutableStateOf(0) }
    var title by remember { mutableStateOf("") }
    var type by remember { mutableStateOf("task") }
    var shoppingRaw by remember { mutableStateOf("") }
    var startTime by remember { mutableStateOf("15:00") }
    var endTime by remember { mutableStateOf("16:00") }
    var recipes by remember { mutableStateOf<List<app.familyos.guard.data.Recipe>>(emptyList()) }
    var recipeId by remember { mutableStateOf<String?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Button(onClick = { open = true; scope.launch { runCatching { recipes = app.familyos.guard.data.Recipe.listFrom(api().get("/api/recipes")) } } }, modifier = Modifier.fillMaxWidth()) {
        Text("Новое задание")
    }
    status?.let { Text(it, color = PAccent, fontSize = 11.sp) }

    if (!open) return

    AlertDialog(
        onDismissRequest = { open = false },
        title = { Text("Новое задание") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Кому", color = PMuted, fontSize = 11.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    children.forEachIndexed { index, child ->
                        FilterPill(child.nickname ?: child.name, childIndex == index) { childIndex = index }
                    }
                }
                OutlinedTextField(title, { title = it }, label = { Text("Название") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Text("Тип", color = PMuted, fontSize = 11.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterPill("Обычное", type == "task") { type = "task" }
                    FilterPill("Покупки", type == "shopping") { type = "shopping" }
                    FilterPill("Готовка", type == "cooking") { type = "cooking" }
                }
                if (type == "shopping") {
                    OutlinedTextField(
                        shoppingRaw,
                        { shoppingRaw = it },
                        label = { Text("Список покупок, по строке") },
                        minLines = 4,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text("Ребёнок отметит их сканированием штрихкодов.", color = PMuted, fontSize = 10.sp)
                }
                if (type == "cooking") {
                    Text("Рецепты из остатков", color = PMuted, fontSize = 11.sp)
                    val available = recipes.filter { it.canCook }
                    if (available.isEmpty()) {
                        Text("Нет рецептов, на которые хватает продуктов", color = Color(0xFFF87171), fontSize = 11.sp)
                    }
                    available.forEach { recipe ->
                        FilterPill(recipe.title, recipeId == recipe.id) { recipeId = recipe.id }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(startTime, { startTime = it }, label = { Text("С") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(endTime, { endTime = it }, label = { Text("До") }, singleLine = true, modifier = Modifier.weight(1f))
                }
                Text("В это время телефон ребёнка блокируется (кроме звонков и SMS).", color = PMuted, fontSize = 10.sp)
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val child = children.getOrNull(childIndex) ?: return@TextButton
                    val body = JSONObject()
                        .put("title", title.trim())
                        .put("questType", type)
                        .put("xp", 20)
                        .put("coins", 10)
                        .put("approval", "parent")
                        .put("sessionStart", startTime)
                        .put("sessionEnd", endTime)
                    if (type == "shopping") {
                        val items = org.json.JSONArray()
                        shoppingRaw.split("\n").map { it.trim() }.filter { it.isNotEmpty() }.forEach { line ->
                            items.put(JSONObject().put("title", line))
                        }
                        body.put("shoppingItems", items)
                    }
                    if (type == "cooking" && recipeId != null) {
                        body.put("recipeId", recipeId)
                    }
                    scope.launch {
                        val result = runCatching {
                            api().post("/api/guard/children/${'$'}{child.id}/quests", body)
                        }
                        status = result.fold({ "Задание создано" }, { "Ошибка: ${'$'}{it.message}" })
                        if (result.isSuccess) {
                            open = false
                            title = ""
                            shoppingRaw = ""
                            onDone()
                        }
                    }
                },
                enabled = title.isNotBlank(),
            ) { Text("Создать") }
        },
        dismissButton = { TextButton(onClick = { open = false }) { Text("Отмена") } },
    )
}

@Composable
private fun ParentMoneyScreen(
    money: MoneyOverview?,
    busy: Boolean,
    currency: String,
    api: () -> ParentApi,
    onAdd: (Double, String, String, String?, String) -> Unit,
    onAddAccount: (String, Double) -> Unit,
    onRefresh: () -> Unit,
) {
    var showTx by remember { mutableStateOf(false) }
    var showAccount by remember { mutableStateOf(false) }
    var showCsv by remember { mutableStateOf(false) }
    var showBank by remember { mutableStateOf(false) }
    var showZen by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Деньги", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                TextButton(onClick = onRefresh, enabled = !busy) { Text("Обновить") }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterPill("Импорт выписки", false) { showCsv = true }
                FilterPill("Банк", false) { showBank = true }
                FilterPill("Zen Money", false) { showZen = true }
            }

            status?.let {
                Text(it, color = PAccent, fontSize = 12.sp)
            }

            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent),
            ) {
                Column(
                    Modifier.background(Brush.linearGradient(listOf(app.familyos.guard.ui.accentDeep, Color(0xFF151B24)))).padding(16.dp),
                ) {
                    Text("Баланс", color = PMuted, fontSize = 11.sp)
                    Text(
                        formatMoney(money?.accounts?.sumOf { it.balance } ?: 0.0, currency),
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text("Доход ${formatMoney(money?.income ?: 0.0, currency)}", color = PAccent, fontSize = 12.sp)
                        Text("Расход ${formatMoney(money?.expense ?: 0.0, currency)}", color = PRed, fontSize = 12.sp)
                    }
                }
            }

            Text("Счета", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            money?.accounts?.forEach { account ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Row(Modifier.fillMaxWidth().padding(13.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(account.title)
                        Text(formatMoney(account.balance, account.currency), color = PGold)
                    }
                }
            }
            TextButton(onClick = { showAccount = true }) { Text("+ Счёт") }

            val categories = money?.byCategory.orEmpty().filter { it.total > 0 }
            if (categories.isNotEmpty()) {
                Text("Расходы по категориям", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                val max = categories.maxOf { it.total }
                categories.take(6).forEach { item ->
                    Column {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(item.title, fontSize = 12.sp)
                            Text(formatMoney(item.total, currency), color = PMuted, fontSize = 12.sp)
                        }
                        Spacer(Modifier.height(3.dp))
                        Box(
                            Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(4.dp)).background(Color(0xFF1F2A37)),
                        ) {
                            Box(
                                Modifier
                                    .fillMaxWidth((item.total / max).toFloat().coerceIn(0.02f, 1f))
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(PAccent),
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                    }
                }
            }

            Text("Последние операции", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            val recent = money?.recent.orEmpty()
            if (recent.isEmpty()) {
                Text("Пока нет операций", color = PMuted, fontSize = 12.sp)
            }
            recent.take(10).forEach { tx ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(Modifier.weight(1f)) {
                            Text(tx.payee ?: if (tx.kind == "income") "Доход" else "Расход", fontSize = 13.sp)
                            Text(tx.date, color = PMuted, fontSize = 11.sp)
                        }
                        Text(
                            (if (tx.kind == "income") "+" else "−") + formatMoney(kotlin.math.abs(tx.amount), tx.currency),
                            color = if (tx.kind == "income") PAccent else Color(0xFFE6EDF3),
                            fontSize = 13.sp,
                        )
                    }
                }
            }
            Spacer(Modifier.height(60.dp))
        }

        Box(Modifier.align(Alignment.BottomEnd).padding(18.dp)) {
            Button(onClick = { showTx = true }, shape = RoundedCornerShape(16.dp)) { Text("+ Операция") }
        }
    }

    if (showTx) {
        AddTransactionDialog(
            money = money,
            currency = currency,
            onDismiss = { showTx = false },
            onSubmit = { amount, kind, accountId, categoryId, payee ->
                onAdd(amount, kind, accountId, categoryId, payee)
                showTx = false
            },
        )
    }
    if (showCsv) {
        var csv by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showCsv = false },
            title = { Text("Импорт выписки") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Строки: дата;сумма;контрагент;комментарий", color = PMuted, fontSize = 11.sp)
                    OutlinedTextField(
                        csv,
                        { csv = it },
                        label = { Text("CSV") },
                        minLines = 5,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val text = csv.trim()
                        showCsv = false
                        if (text.isNotEmpty()) {
                            scope.launch {
                                val result = runCatching {
                                    api().post("/api/money/import/csv", JSONObject().put("csv", text))
                                }
                                status = result.fold(
                                    { "Импортировано: " + it.optJSONObject("data")?.optInt("imported") },
                                    { "Ошибка: ${it.message}" },
                                )
                                onRefresh()
                            }
                        }
                    },
                ) { Text("Импортировать") }
            },
            dismissButton = { TextButton(onClick = { showCsv = false }) { Text("Отмена") } },
        )
    }

    if (showZen) {
        ZenMoneyDialog(api = { api() }, onClose = { showZen = false })
    }

    if (showBank) {
        var token by remember { mutableStateOf<String?>(null) }
        var info by remember { mutableStateOf<String?>(null) }
        LaunchedEffect(Unit) {
            runCatching { api().get("/api/bank/status") }.onSuccess {
                val data = it.optJSONObject("data")
                info = if (data?.optBoolean("connected") == true) "Подключён: ${data.optString("masked")}" else "Не подключён"
            }
        }
        AlertDialog(
            onDismissRequest = { showBank = false },
            title = { Text("Привязка банка") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(info ?: "Проверяем…", color = PMuted, fontSize = 12.sp)
                    Text(
                        "Создай токен и передай его агрегатору или своему скрипту: он будет присылать операции POST-запросом.",
                        color = PMuted,
                        fontSize = 11.sp,
                    )
                    token?.let {
                        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF0F151D))) {
                            Text(it, Modifier.padding(10.dp), fontSize = 11.sp, color = PAccent)
                        }
                        Text("/api/bank/import", color = PMuted, fontSize = 11.sp)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            runCatching { api().post("/api/bank/token") }.onSuccess {
                                token = it.optJSONObject("data")?.optString("token")
                                info = "Токен создан"
                            }.onFailure { info = "Ошибка: ${it.message}" }
                        }
                    },
                ) { Text(if (token == null) "Создать токен" else "Пересоздать") }
            },
            dismissButton = { TextButton(onClick = { showBank = false }) { Text("Закрыть") } },
        )
    }

    if (showAccount) {
        var title by remember { mutableStateOf("") }
        var balance by remember { mutableStateOf("0") }
        AlertDialog(
            onDismissRequest = { showAccount = false },
            title = { Text("Новый счёт") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(title, { title = it }, label = { Text("Название") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(balance, { balance = it }, label = { Text("Баланс") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val value = balance.toDoubleOrNull() ?: 0.0
                        if (title.isNotBlank()) onAddAccount(title.trim(), value)
                        showAccount = false
                    },
                ) { Text("Создать") }
            },
            dismissButton = { TextButton(onClick = { showAccount = false }) { Text("Отмена") } },
        )
    }
}

@Composable
private fun AddTransactionDialog(
    money: MoneyOverview?,
    currency: String,
    onDismiss: () -> Unit,
    onSubmit: (Double, String, String, String?, String) -> Unit,
) {
    var amount by remember { mutableStateOf("") }
    var payee by remember { mutableStateOf("") }
    var kind by remember { mutableStateOf("expense") }
    var accountId by remember { mutableStateOf(money?.accounts?.firstOrNull()?.id.orEmpty()) }
    var categoryId by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Новая операция") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterPill("Расход", kind == "expense") { kind = "expense"; categoryId = null }
                    FilterPill("Доход", kind == "income") { kind = "income"; categoryId = null }
                }
                OutlinedTextField(
                    amount,
                    { amount = it },
                    label = { Text("Сумма, $currency") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    payee,
                    { payee = it },
                    label = { Text("Куда / откуда") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("Счёт", color = PMuted, fontSize = 11.sp)
                money?.accounts.orEmpty().forEach { account ->
                    FilterPill(account.title, accountId == account.id) { accountId = account.id }
                }
                Text("Категория", color = PMuted, fontSize = 11.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterPill("Без", categoryId == null) { categoryId = null }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val value = amount.replace(',', '.').toDoubleOrNull()
                    if (value != null && value > 0 && accountId.isNotBlank()) {
                        onSubmit(value, kind, accountId, categoryId, payee.trim())
                    }
                },
            ) { Text("Добавить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

@Composable
private fun FilterPill(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) app.familyos.guard.ui.accentSoft else Color(0xFF101820))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(label, color = if (selected) PAccent else PMuted, fontSize = 12.sp)
    }
}

@Composable
private fun ParentEventsScreen(
    events: List<PendingEvent>,
    busy: Boolean,
    onDecide: (String, Boolean, String, Int) -> Unit,
    onRefresh: () -> Unit,
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("События", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            TextButton(onClick = onRefresh, enabled = !busy) { Text("Обновить") }
        }
        if (events.isEmpty()) {
            Text("Нет заявок", color = PMuted)
        }
        events.forEach { event ->
            var time by remember(event.id) { mutableStateOf(event.startTime) }
            var minutes by remember(event.id) { mutableStateOf(event.durationMin.toString()) }
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(event.title, fontWeight = FontWeight.SemiBold)
                    event.description?.takeIf { it.isNotBlank() }?.let {
                        Text(it, color = PMuted, fontSize = 12.sp)
                    }
                    Text("${event.date} · ${event.startTime} · ${event.durationMin} мин", color = PMuted, fontSize = 11.sp)
                    if (event.lat != null && event.lng != null) {
                        Text("Место на карте: ${"%.4f".format(event.lat)}, ${"%.4f".format(event.lng)}", color = PMuted, fontSize = 11.sp)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedTextField(time, { time = it }, label = { Text("Время") }, singleLine = true, modifier = Modifier.weight(1f))
                        OutlinedTextField(minutes, { minutes = it }, label = { Text("Мин") }, singleLine = true, modifier = Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                onDecide(event.id, true, time, minutes.toIntOrNull()?.coerceIn(15, 720) ?: event.durationMin)
                            },
                            enabled = !busy,
                            modifier = Modifier.weight(1f),
                        ) { Text("Одобрить") }
                        Button(
                            onClick = { onDecide(event.id, false, event.startTime, event.durationMin) },
                            enabled = !busy,
                            colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Color(0xFF3A1A1D)),
                        ) { Text("Отклонить", color = Color(0xFFFCA5A5)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ParentChildrenScreen(
    children: List<ChildCard>,
    busy: Boolean,
    onSave: (String, String, String, String) -> Unit,
    onRefresh: () -> Unit,
    api: () -> ParentApi,
) {
    var policyChild by remember { mutableStateOf<ChildCard?>(null) }
    var pairChild by remember { mutableStateOf<ChildCard?>(null) }
    var pairCode by remember { mutableStateOf<String?>(null) }
    var pairPayload by remember { mutableStateOf<String?>(null) }
    var pairError by remember { mutableStateOf<String?>(null) }
    var kcal by remember { mutableStateOf<Map<String, Pair<Int, Int>>>(emptyMap()) }
    LaunchedEffect(api) {
        runCatching {
            val data = api().get("/api/guard/nutrition").optJSONObject("data")
            val array = data?.optJSONArray("children") ?: return@runCatching
            kcal = (0 until array.length()).associate { index ->
                val item = array.getJSONObject(index)
                item.optString("childId") to (item.optInt("consumed") to item.optInt("target"))
            }
        }
    }
    pairChild?.let { child ->
        QrPairDialog(
            child = child,
            code = pairCode,
            payload = pairPayload,
            error = pairError,
            onStart = {
                pairChild = child
                pairCode = null
                pairPayload = null
                pairError = null
                kotlinx.coroutines.MainScope().launch {
                    runCatching {
                        api().post("/api/guard/pair/device", JSONObject().put("childId", child.id))
                    }.onSuccess { root ->
                        val data = root.optJSONObject("data")
                        pairCode = data?.optString("code")
                        pairPayload = data?.optString("payload")
                    }.onFailure { pairError = it.message }
                }
            },
            onDismiss = { pairChild = null; pairCode = null; pairPayload = null },
        )
    }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Дети", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            TextButton(onClick = onRefresh, enabled = !busy) { Text("Обновить") }
        }
        policyChild?.let { child ->
            PolicyDialog(
                childId = child.id,
                childName = child.nickname ?: child.name,
                limitMinutes = child.limitMinutes,
                bedtimeStart = "21:00",
                bedtimeEnd = "07:00",
                api = api,
                onDismiss = { policyChild = null },
                onSaved = onRefresh,
            )
        }
        children.forEach { child ->
            var nickname by remember(child.id, child.nickname) { mutableStateOf(child.nickname.orEmpty()) }
            var school by remember(child.id, child.school) { mutableStateOf(child.school.orEmpty()) }
            var grade by remember(child.id, child.grade) { mutableStateOf(child.grade.orEmpty()) }
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(child.name, fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(nickname, { nickname = it }, label = { Text("Никнейм") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(school, { school = it }, label = { Text("Школа") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(grade, { grade = it }, label = { Text("Класс") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    kcal[child.id]?.let { (eaten, target) ->
                        val percent = if (target > 0) (eaten * 100 / target).coerceIn(0, 100) else 0
                        Text("Калории сегодня: $eaten из $target ккал", fontSize = 12.sp, color = PMuted)
                        LinearProgressIndicator(
                            progress = { percent / 100f },
                            modifier = Modifier.fillMaxWidth().height(6.dp),
                            color = if (eaten > target) PRed else PAccent,
                            trackColor = Color(0xFF1F2A37),
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { onSave(child.id, nickname, school, grade) }, enabled = !busy) {
                            Text("Сохранить")
                        }
                        TextButton(onClick = { policyChild = child }) { Text("Лимит") }
                    }
                    OutlinedButton(onClick = { pairChild = child }, modifier = Modifier.fillMaxWidth()) {
                        Text("📷 Подключить телефон ребёнка")
                    }
                }
            }
        }
    }
}

/** Zen Money: статус, подключение, синхронизация, отключение. */
@Composable
private fun ZenMoneyDialog(api: () -> ParentApi, onClose: () -> Unit) {
    var connected by remember { mutableStateOf<Boolean?>(null) }
    var masked by remember { mutableStateOf<String?>(null) }
    var lastSync by remember { mutableStateOf<String?>(null) }
    var token by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    suspend fun reload() {
        runCatching { api().get("/api/zenmoney/status").optJSONObject("data") }
            .onSuccess { data ->
                connected = data?.optBoolean("connected") ?: false
                masked = data?.optString("masked")?.takeIf { it.isNotBlank() }
                lastSync = data?.optString("lastSync")?.takeIf { it.isNotBlank() && it != "null" }
            }
            .onFailure { connected = false }
    }

    LaunchedEffect(Unit) { reload() }

    AlertDialog(
        onDismissRequest = onClose,
        title = { Text("Zen Money") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                when (connected) {
                    null -> Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                    ) { CircularProgressIndicator(Modifier.size(24.dp)) }

                    true -> {
                        Card(colors = CardDefaults.cardColors(containerColor = app.familyos.guard.ui.accentCard)) {
                            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("Подключено", color = PAccent, fontWeight = FontWeight.SemiBold)
                                masked?.let { Text("Токен $it", fontSize = 11.sp, color = PMuted) }
                                Text(
                                    lastSync?.let { "Последняя синхронизация: " + it.replace("T", " ").take(16) }
                                        ?: "Ещё не синхронизировали",
                                    fontSize = 11.sp,
                                    color = PMuted,
                                )
                            }
                        }
                        Button(
                            onClick = {
                                scope.launch {
                                    busy = true
                                    message = null
                                    error = null
                                    runCatching { api().post("/api/zenmoney/sync") }
                                        .onSuccess { root ->
                                            val stats = root.optJSONObject("data")
                                            val accounts = stats?.optInt("accounts", 0) ?: 0
                                            val transactions = stats?.optInt("transactions", 0) ?: 0
                                            message = "Готово: счетов $accounts, операций $transactions"
                                            reload()
                                        }
                                        .onFailure { error = it.message }
                                    busy = false
                                }
                            },
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(if (busy) "Синхронизируем…" else "Синхронизировать") }
                        TextButton(
                            onClick = {
                                scope.launch {
                                    busy = true
                                    runCatching { api().delete("/api/zenmoney/token") }
                                        .onSuccess { reload(); message = "Токен удалён" }
                                        .onFailure { error = it.message }
                                    busy = false
                                }
                            },
                            enabled = !busy,
                        ) { Text("Отключить", color = PRed) }
                    }

                    false -> {
                        Text(
                            "1. В Zen Money добавьте банк и счета.\n" +
                                "2. Получите токен на zerro.app/token.\n" +
                                "3. Вставьте токен сюда — Family OS подтянет операции.",
                            fontSize = 12.sp,
                            color = PMuted,
                        )
                        OutlinedTextField(
                            token,
                            { token = it.trim() },
                            label = { Text("Токен") },
                            placeholder = { Text("вставьте токен из Zerro") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Button(
                            onClick = {
                                scope.launch {
                                    busy = true
                                    message = null
                                    error = null
                                    runCatching {
                                        api().post("/api/zenmoney/token", JSONObject().put("token", token))
                                        api().post("/api/zenmoney/sync")
                                    }.onSuccess { root ->
                                        val stats = root.optJSONObject("data")
                                        val accounts = stats?.optInt("accounts", 0) ?: 0
                                        val transactions = stats?.optInt("transactions", 0) ?: 0
                                        token = ""
                                        message = "Подключено и синхронизировано: счетов $accounts, операций $transactions"
                                        reload()
                                    }.onFailure { error = "Не получилось: ${it.message}" }
                                    busy = false
                                }
                            },
                            enabled = !busy && token.length >= 10,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(if (busy) "Подключаем…" else "Подключить") }
                    }
                }

                message?.let {
                    Card(colors = CardDefaults.cardColors(containerColor = app.familyos.guard.ui.accentCard)) {
                        Text(it, Modifier.padding(10.dp), color = PAccent, fontSize = 12.sp)
                    }
                }
                error?.let {
                    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF2A1214))) {
                        Text(it, Modifier.padding(10.dp), color = PRed, fontSize = 12.sp)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onClose) { Text("Закрыть") } },
    )
}

@Composable
private fun QrPairDialog(
    child: ChildCard,
    code: String?,
    payload: String?,
    error: String?,
    onStart: () -> Unit,
    onDismiss: () -> Unit,
) {
    LaunchedEffect(child.id) { onStart() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Подключить телефон") },
        text = {
            Column(
                Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("Откройте Family OS на телефоне ${child.nickname ?: child.name} и отсканируйте код", fontSize = 12.sp, color = PMuted)
                when {
                    payload != null -> {
                        QrImage(payload)
                        code?.let { Text("Код: $it", fontWeight = FontWeight.SemiBold) }
                        Text("Код действует 15 минут и срабатывает один раз", fontSize = 11.sp, color = PMuted)
                    }
                    error != null -> Text("Ошибка: $error", color = PRed, fontSize = 12.sp)
                    else -> CircularProgressIndicator(Modifier.size(28.dp))
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Готово") } },
    )
}

private fun formatMoney(value: Double, currency: String): String {
    val symbol = when (currency) {
        "RUB" -> "₽"
        "USD" -> "$"
        "EUR" -> "€"
        else -> currency
    }
    return "%.2f %s".format(value, symbol)
}
