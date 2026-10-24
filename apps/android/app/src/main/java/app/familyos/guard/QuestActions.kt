package app.familyos.guard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import org.json.JSONObject

private val QAccent = Color(0xFF34D399)
private val QMuted = Color(0xFF8B9BB0)

/** Задание-список покупок: отметка только сканированием штрихкода. */
@Composable
fun ShoppingQuestDialog(
    questId: String,
    title: String,
    busy: Boolean,
    loadDetails: suspend (String) -> JSONObject,
    onScan: suspend (String, String) -> JSONObject,
    onFinish: () -> Unit,
    onDismiss: () -> Unit,
) {
    var barcode by remember { mutableStateOf("") }
    var scanning by remember { mutableStateOf(false) }
    var total by remember { mutableStateOf(0) }
    var done by remember { mutableStateOf(0) }
    var items by remember { mutableStateOf<List<Pair<String, Boolean>>>(emptyList()) }
    var status by remember { mutableStateOf<String?>(null) }

    suspend fun reload() {
        val details = loadDetails(questId)
        val shopping = details.optJSONObject("shopping") ?: return
        total = shopping.optInt("total", 0)
        done = shopping.optInt("done", 0)
        val array = shopping.optJSONArray("items") ?: return
        items = (0 until array.length()).map { index ->
            val item = array.getJSONObject(index)
            item.optString("title") to !item.isNull("checked_at")
        }
    }

    LaunchedEffect(questId) { reload() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Отметь покупки сканированием штрихкода", color = QMuted, fontSize = 11.sp)
                LinearProgressIndicator(
                    progress = { if (total == 0) 0f else done.toFloat() / total },
                    modifier = Modifier.fillMaxWidth().height(6.dp),
                    color = QAccent,
                    trackColor = Color(0xFF1F2A37),
                )
                Text("$done из $total", color = QMuted, fontSize = 11.sp)

                Button(
                    onClick = { scanning = true },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("📷 Сканировать покупку") }

                if (scanning) {
                    app.familyos.guard.scan.CodeScannerDialog(
                        title = "Сканировать",
                        hint = "Наведите камеру на штрихкод товара из списка",
                        onResult = { code ->
                            scanning = false
                            status = null
                            kotlinx.coroutines.MainScope().launch {
                                val result = runCatching { onScan(questId, code) }
                                status = result.fold(
                                    { data ->
                                        val item = data.optJSONObject("item")
                                        if (item == null) "Такого товара нет в списке" else "Отмечено: " + item.optString("title")
                                    },
                                    { "Ошибка: ${it.message}" },
                                )
                                barcode = ""
                                reload()
                            }
                        },
                        onDismiss = { scanning = false },
                    )
                }

                status?.let { Text(it, color = QAccent, fontSize = 11.sp) }

                items.forEach { (name, checked) ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text((if (checked) "✓ " else "• ") + name, fontSize = 12.sp, color = if (checked) QAccent else QMuted)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onFinish, enabled = !busy) { Text("Задание выполнено") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Позже") } },
    )
}

/** Задание-готовка: пошаговый рецепт. */
@Composable
fun CookingQuestDialog(
    questId: String,
    title: String,
    busy: Boolean,
    loadDetails: suspend (String) -> JSONObject,
    onFinish: () -> Unit,
    onDismiss: () -> Unit,
) {
    var steps by remember { mutableStateOf<List<String>>(emptyList()) }
    var index by remember { mutableStateOf(0) }
    var canCook by remember { mutableStateOf(true) }

    LaunchedEffect(questId) {
        val details = loadDetails(questId)
        val recipe = details.optJSONObject("recipe")
        if (recipe != null) {
            val array = recipe.optJSONArray("steps") ?: org.json.JSONArray()
            steps = (0 until array.length()).map { array.optString(it) }
            canCook = recipe.optBoolean("canCook", true)
        }
    }

    val total = steps.size.coerceAtLeast(1)
    val finished = index >= total

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (finished) "Всё готово — отметь задание" else "Шаг ${index + 1} из $total",
                    color = QMuted,
                    fontSize = 11.sp,
                )
                LinearProgressIndicator(
                    progress = { (index.toFloat() / total).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(6.dp),
                    color = QAccent,
                    trackColor = Color(0xFF1F2A37),
                )
                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF0F151D))) {
                    Text(
                        if (finished) "Приятного аппетита 🍽" else steps.getOrElse(index) { "Нет шагов" },
                        Modifier.padding(12.dp),
                        fontSize = 14.sp,
                    )
                }
                if (!canCook) {
                    Text("Не хватает продуктов на складе", color = Color(0xFFF87171), fontSize = 11.sp)
                }
            }
        },
        confirmButton = {
            if (finished) {
                TextButton(onClick = onFinish, enabled = !busy && canCook) { Text("Приготовил") }
            } else {
                TextButton(onClick = { index += 1 }) { Text("Далее") }
            }
        },
        dismissButton = {
            if (index > 0 && !finished) TextButton(onClick = { index -= 1 }) { Text("Назад") }
            else TextButton(onClick = onDismiss) { Text("Позже") }
        },
    )
}

/** Полноэкранная блокировка на время задания. */
@Composable
fun SessionBlockScreen(
    title: String,
    endsAt: String,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Идёт задание", color = QMuted, fontSize = 12.sp)
        Spacer(Modifier.height(6.dp))
        Text(title, fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text("Телефон заблокирован до $endsAt. Доступны только звонки и SMS.", color = Color(0xFFE6EDF3), fontSize = 13.sp)
        Spacer(Modifier.height(18.dp))
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(14.dp)) {
                Text("Так видно, что задание выполняется — без гаданий.", color = QMuted, fontSize = 12.sp)
            }
        }
        Spacer(Modifier.height(18.dp))
        Text("Блокировка снимется сама в $endsAt", color = QMuted, fontSize = 12.sp)
    }
}
