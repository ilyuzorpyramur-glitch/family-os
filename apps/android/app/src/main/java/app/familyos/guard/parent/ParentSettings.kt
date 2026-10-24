package app.familyos.guard.parent

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.familyos.guard.api.ParentApi
import app.familyos.guard.data.Product
import kotlinx.coroutines.launch
import org.json.JSONObject

private val SetAccent = Color(0xFF34D399)
private val SetMuted = Color(0xFF8B9BB0)
private val SetRed = Color(0xFFF87171)

/** Правила сайтов: чёрный и белый списки. */
@Composable
fun SitesRulesDialog(api: () -> ParentApi, onDismiss: () -> Unit) {
    var block by remember { mutableStateOf<List<String>>(emptyList()) }
    var allow by remember { mutableStateOf<List<String>>(emptyList()) }
    var domain by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    suspend fun reload() {
        runCatching {
            val data = api().get("/api/guard/sites/rules").optJSONObject("data")
            fun list(field: String): List<String> {
                val array = data?.optJSONArray(field) ?: return emptyList()
                return (0 until array.length()).map { array.getJSONObject(it).optString("domain") }
            }
            block = list("block")
            allow = list("allow")
        }
    }

    LaunchedEffect(Unit) { reload() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Правила сайтов") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    domain,
                    { domain = it },
                    label = { Text("Домен, например vk.com") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick = {
                            val value = domain.trim()
                            if (value.isNotEmpty()) {
                                scope.launch {
                                    runCatching {
                                        api().post(
                                            "/api/guard/sites/rules",
                                            JSONObject().put("domain", value).put("kind", "block"),
                                        )
                                    }.onSuccess { status = "Заблокирован: $value" }.onFailure { status = it.message }
                                    domain = ""
                                    reload()
                                }
                            }
                        },
                    ) { Text("В чёрный", color = SetRed) }
                    TextButton(
                        onClick = {
                            val value = domain.trim()
                            if (value.isNotEmpty()) {
                                scope.launch {
                                    runCatching {
                                        api().post(
                                            "/api/guard/sites/rules",
                                            JSONObject().put("domain", value).put("kind", "allow"),
                                        )
                                    }.onSuccess { status = "Разрешён: $value" }.onFailure { status = it.message }
                                    domain = ""
                                    reload()
                                }
                            }
                        },
                    ) { Text("В белый", color = SetAccent) }
                }
                status?.let { Text(it, color = SetMuted, fontSize = 11.sp) }

                Text("Заблокированы", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                block.forEach { item ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(item, fontSize = 11.sp, color = SetRed)
                        Text(
                            "убрать",
                            fontSize = 11.sp,
                            color = SetMuted,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                }
                if (block.isEmpty()) Text("Пусто — работает только встроенный фильтр 18+", fontSize = 10.sp, color = SetMuted)

                Text("Разрешены всегда", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                allow.forEach { item ->
                    Text(item, fontSize = 11.sp, color = SetAccent)
                }
                if (allow.isEmpty()) Text("Пусто", fontSize = 10.sp, color = SetMuted)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Закрыть") } },
    )
}

/** Лимит экрана и режим сна. */
@Composable
fun PolicyDialog(
    childId: String,
    childName: String,
    limitMinutes: Int,
    bedtimeStart: String,
    bedtimeEnd: String,
    api: () -> ParentApi,
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
) {
    var limit by remember { mutableStateOf(limitMinutes.toString()) }
    var start by remember { mutableStateOf(bedtimeStart) }
    var end by remember { mutableStateOf(bedtimeEnd) }
    var status by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Лимит и режим · $childName") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    limit,
                    { limit = it },
                    label = { Text("Экран в день, минут") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(start, { start = it }, label = { Text("Сон с") }, singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(end, { end = it }, label = { Text("до") }, singleLine = true, modifier = Modifier.weight(1f))
                }
                status?.let { Text(it, color = SetMuted, fontSize = 11.sp) }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    scope.launch {
                        runCatching {
                            api().patch(
                                "/api/guard/children/$childId/policy",
                                JSONObject()
                                    .put("screenLimitMin", limit.toIntOrNull() ?: limitMinutes)
                                    .put("bedtimeStart", start)
                                    .put("bedtimeEnd", end),
                            )
                        }.onSuccess {
                            status = "Сохранено"
                            onSaved()
                            onDismiss()
                        }.onFailure { status = "Ошибка: ${it.message}" }
                    }
                },
            ) { Text("Сохранить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

/** Новый рецепт из продуктов на складе. */
@Composable
fun RecipeDialog(
    api: () -> ParentApi,
    products: List<Product>,
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var minutes by remember { mutableStateOf("25") }
    var steps by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<Set<String>>(emptySet()) }
    var status by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Новый рецепт") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(title, { title = it }, label = { Text("Название") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(minutes, { minutes = it }, label = { Text("Минут") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    steps,
                    { steps = it },
                    label = { Text("Шаги, каждый с новой строки") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("Ингредиенты", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                products.take(12).forEach { product ->
                    val checked = selected.contains(product.id)
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            (if (checked) "✓ " else "• ") + product.title,
                            fontSize = 11.sp,
                            color = if (checked) SetAccent else SetMuted,
                        )
                        Text(
                            "выбрать",
                            fontSize = 11.sp,
                            color = SetAccent,
                            modifier = Modifier
                                .padding(start = 8.dp),
                        )
                    }
                }
                status?.let { Text(it, color = SetMuted, fontSize = 11.sp) }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val items = org.json.JSONArray()
                    products.filter { selected.contains(it.id) }.forEach { product ->
                        items.put(
                            JSONObject()
                                .put("productId", product.id)
                                .put("title", product.title)
                                .put("quantity", 1.0)
                                .put("unit", product.unit),
                        )
                    }
                    scope.launch {
                        runCatching {
                            api().post(
                                "/api/recipes",
                                JSONObject()
                                    .put("title", title.trim())
                                    .put("minutes", minutes.toIntOrNull() ?: 25)
                                    .put("steps", org.json.JSONArray(steps.split("\n").map { it.trim() }.filter { it.isNotEmpty() }))
                                    .put("items", items),
                            )
                        }.onSuccess {
                            onSaved()
                            onDismiss()
                        }.onFailure { status = "Ошибка: ${it.message}" }
                    }
                },
                enabled = title.isNotBlank() && selected.isNotEmpty(),
            ) { Text("Создать") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}
