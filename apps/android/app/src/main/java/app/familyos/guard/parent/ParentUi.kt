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
import app.familyos.guard.api.ParentApi
import app.familyos.guard.data.ChildCard
import app.familyos.guard.data.MoneyOverview
import app.familyos.guard.data.PendingEvent
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import org.json.JSONObject

private val PAccent = Color(0xFF34D399)
private val PGold = Color(0xFFFBBF24)
private val PMuted = Color(0xFF8B9BB0)
private val PRed = Color(0xFFF87171)

@Composable
fun ParentApp(
    initialUrl: String,
    initialToken: String,
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

    suspend fun loadAll() {
        val client = api()
        runCatching {
            val overview = client.get("/api/guard/overview")
            publicUrl = baseUrl
            children = ChildCard.listFromOverview(overview, baseUrl)
        }
        runCatching {
            events = PendingEvent.listFrom(client.get("/api/guard/child-events?status=pending"))
        }
        runCatching {
            money = MoneyOverview.fromJson(client.get("/api/money/overview"))
        }
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
            onExit = onExit,
        )
        return
    }

    LaunchedEffect(loggedIn) { if (loggedIn) loadAll() }

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) {
            when (tab) {
                1 -> ParentOverview(children, events.size, busy) {
                    scope.launch { busy = true; loadAll(); busy = false }
                }
                2 -> ParentMoneyScreen(
                    money = money,
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
                else -> ParentChildrenScreen(
                    children = children,
                    busy = busy,
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
            PTab("Дети", tab == 0) { tab = 0 }
        }
    }
}

@Composable
private fun PTab(label: String, selected: Boolean, onClick: () -> Unit) {
    Column(
        Modifier.clip(RoundedCornerShape(12.dp)).clickable { onClick() }.padding(horizontal = 14.dp, vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            label,
            color = if (selected) PAccent else PMuted,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
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
    onExit: () -> Unit,
) {
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
            Text("Войти")
        }
        TextButton(onClick = onExit) { Text("Выйти в детский режим") }
        if (error != null) Text(error, color = PRed, fontSize = 12.sp)
    }
}

@Composable
private fun ParentOverview(children: List<ChildCard>, pendingEvents: Int, busy: Boolean, onRefresh: () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Обзор", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            TextButton(onClick = onRefresh, enabled = !busy) { Text("Обновить") }
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
private fun ParentMoneyScreen(
    money: MoneyOverview?,
    busy: Boolean,
    currency: String,
    onAdd: (Double, String, String, String?, String) -> Unit,
    onAddAccount: (String, Double) -> Unit,
    onRefresh: () -> Unit,
) {
    var showTx by remember { mutableStateOf(false) }
    var showAccount by remember { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Деньги", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                TextButton(onClick = onRefresh, enabled = !busy) { Text("Обновить") }
            }

            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent),
            ) {
                Column(
                    Modifier.background(Brush.linearGradient(listOf(Color(0xFF10352A), Color(0xFF151B24)))).padding(16.dp),
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
            .background(if (selected) Color(0xFF17352B) else Color(0xFF101820))
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
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Дети", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            TextButton(onClick = onRefresh, enabled = !busy) { Text("Обновить") }
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
                    Button(onClick = { onSave(child.id, nickname, school, grade) }, enabled = !busy) {
                        Text("Сохранить")
                    }
                }
            }
        }
    }
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
