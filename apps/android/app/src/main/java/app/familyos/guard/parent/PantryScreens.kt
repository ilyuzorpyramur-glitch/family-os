package app.familyos.guard.parent

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import app.familyos.guard.scan.CodeScannerDialog
import app.familyos.guard.data.Product
import kotlinx.coroutines.launch
import org.json.JSONObject

private val PntAccent = app.familyos.guard.ui.accent
private val PntMuted = Color(0xFF8B9BB0)
private val PntRed = Color(0xFFF87171)

private data class SuggestItem(val title: String, val reason: String, val unit: String)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ParentPantryScreen(
    busy: Boolean,
    onRefresh: () -> Unit,
    api: () -> ParentApi,
) {
    var products by remember { mutableStateOf<List<Product>>(emptyList()) }
    var status by remember { mutableStateOf<String?>(null) }
    var shopping by remember { mutableStateOf(false) }
    var usage by remember { mutableStateOf(false) }
    var recipe by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    suspend fun reload() {
        runCatching { products = Product.listFrom(api().get("/api/products")) }
            .onFailure { status = it.message }
    }

    LaunchedEffect(Unit) { reload() }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Склад", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                TextButton(onClick = { scope.launch { onRefresh(); reload() } }, enabled = !busy) { Text("Обновить") }
            }
            status?.let { Text(it, color = PntAccent, fontSize = 12.sp) }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("На складе: ${products.size}", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                TextButton(onClick = { recipe = true }) { Text("Новый рецепт") }
            }
            products.forEach { product ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Row(
                        Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(product.title, fontSize = 13.sp, maxLines = 1)
                            val kbju = listOfNotNull(
                                product.kcal?.let { "%.0f ккал".format(it) },
                                product.protein?.let { "Б %.0f".format(it) },
                                product.fat?.let { "Ж %.0f".format(it) },
                                product.carbs?.let { "У %.0f".format(it) },
                            ).joinToString(" · ")
                            if (kbju.isNotBlank()) Text(kbju, color = PntMuted, fontSize = 10.sp)
                        }
                        Text(
                            "%.1f %s".format(product.quantity, product.unit),
                            color = if (product.quantity > 0) PntAccent else PntRed,
                            fontSize = 12.sp,
                        )
                    }
                }
            }
            if (products.isEmpty()) {
                Text("Склад пуст. Нажми кнопку внизу и отсканируй товар.", color = PntMuted, fontSize = 12.sp)
            }
        }

        Box(Modifier.align(Alignment.BottomEnd).padding(18.dp)) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = PntAccent,
                shadowElevation = 8.dp,
                modifier = Modifier.combinedClickable(
                    onClick = { shopping = true },
                    onLongClick = { usage = true },
                ),
            ) {
                Row(
                    Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("🛒", fontSize = 20.sp)
                    Text("Скан", color = app.familyos.guard.ui.accentCard, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
        }
    }

    if (shopping) {
        ShoppingModeDialog(
            api = api,
            onDismiss = { shopping = false },
            onChanged = { status = it; scope.launch { reload() } },
        )
    }
    if (recipe) {
        RecipeDialog(
            api = api,
            products = products,
            onDismiss = { recipe = false },
            onSaved = { scope.launch { reload() } },
        )
    }
    if (usage) {
        UsageModeDialog(
            api = api,
            onDismiss = { usage = false },
            onChanged = { status = it; scope.launch { reload() } },
        )
    }
}

/** Короткое нажатие: режим покупок со списком от OpenClaw. */
@Composable
private fun ShoppingModeDialog(
    api: () -> ParentApi,
    onDismiss: () -> Unit,
    onChanged: (String) -> Unit,
) {
    var items by remember { mutableStateOf<List<SuggestItem>>(emptyList()) }
    var bought by remember { mutableStateOf<Set<String>>(emptySet()) }
    var barcode by remember { mutableStateOf("") }
    var status by remember { mutableStateOf<String?>(null) }
    var scanning by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        runCatching {
            val root = api().get("/api/shopping/suggest").optJSONObject("data")
            val array = root?.optJSONArray("items") ?: org.json.JSONArray()
            items = (0 until array.length()).map { index ->
                val item = array.getJSONObject(index)
                SuggestItem(
                    title = item.optString("title"),
                    reason = item.optString("reason"),
                    unit = item.optString("unit", "шт"),
                )
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Режим покупок") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Список собрал OpenClaw по составу семьи и остаткам", color = PntMuted, fontSize = 11.sp)
                items.take(10).forEach { item ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            (if (bought.contains(item.title)) "✓ " else "• ") + item.title,
                            fontSize = 12.sp,
                            color = if (bought.contains(item.title)) PntAccent else PntMuted,
                        )
                        Text(item.unit, fontSize = 11.sp, color = PntMuted)
                    }
                }
                Button(onClick = { scanning = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("📷 Сканировать товар")
                }
                TextButton(onClick = { barcode = "" }) { Text("ввести вручную", fontSize = 11.sp) }
                if (scanning) {
                    CodeScannerDialog(
                        title = "Сканировать товар",
                        hint = "Наведите камеру на штрихкод упаковки",
                        onResult = { code ->
                            scanning = false
                            barcode = code
                            scope.launch {
                                val result = runCatching {
                                    api().post("/api/products/scan", JSONObject().put("barcode", code).put("quantity", 1))
                                }
                                status = result.fold(
                                    { root ->
                                        val product = root.optJSONObject("data")?.optJSONObject("product")
                                        val name = product?.optString("title") ?: "товар"
                                        bought = bought + items.filter { it.title.equals(name, true) }.map { it.title }
                                        "Куплено: $name"
                                    },
                                    { "Ошибка: ${it.message}" },
                                )
                                onChanged(status ?: "")
                            }
                        },
                        onDismiss = { scanning = false },
                    )
                }
                status?.let { Text(it, color = PntAccent, fontSize = 11.sp) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Готово") } },
    )
}

/** Долгое нажатие: режим использования — скан и количество. */
@Composable
private fun UsageModeDialog(
    api: () -> ParentApi,
    onDismiss: () -> Unit,
    onChanged: (String) -> Unit,
) {
    var barcode by remember { mutableStateOf("") }
    var product by remember { mutableStateOf<Product?>(null) }
    var amount by remember { mutableStateOf("1") }
    var unit by remember { mutableStateOf("шт") }
    var status by remember { mutableStateOf<String?>(null) }
    var scanning by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun lookup(code: String) {
        scope.launch {
            runCatching {
                val root = api().get("/api/products/barcode/$code").optJSONObject("data")
                val local = root?.optJSONObject("product")
                if (local != null && root.optString("source") == "local") {
                    product = Product.fromJson(local)
                    unit = product?.unit ?: "шт"
                    status = null
                } else {
                    status = "Товара нет на складе — сначала добавь его"
                }
            }.onFailure { status = "Ошибка: ${it.message}" }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Режим использования") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Отсканируй товар, который используешь, и укажи количество", color = PntMuted, fontSize = 11.sp)
                Button(onClick = { scanning = true }, modifier = Modifier.fillMaxWidth()) {
                    Text("📷 Сканировать товар")
                }
                if (scanning) {
                    CodeScannerDialog(
                        title = "Что используешь?",
                        hint = "Наведите камеру на штрихкод",
                        onResult = { code ->
                            scanning = false
                            barcode = code
                            lookup(code)
                        },
                        onDismiss = { scanning = false },
                    )
                }

                product?.let { found ->
                    Text(found.title, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                    Text("Сейчас: %.1f %s".format(found.quantity, found.unit), color = PntMuted, fontSize = 11.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            amount,
                            { amount = it },
                            label = { Text("Сколько") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            unit,
                            { unit = it },
                            label = { Text("Ед.") },
                            singleLine = true,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Button(
                        onClick = {
                            val value = amount.replace(',', '.').toDoubleOrNull() ?: 0.0
                            if (value > 0) {
                                scope.launch {
                                    val result = runCatching {
                                        api().post("/api/products/${found.id}/consume", JSONObject().put("amount", value))
                                    }
                                    status = result.fold({ "Списано %.1f %s".format(value, unit) }, { "Ошибка: ${it.message}" })
                                    product = null
                                    barcode = ""
                                    onChanged(status ?: "")
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Списать") }
                }
                status?.let { Text(it, color = if (it.startsWith("Ошибка")) PntRed else PntAccent, fontSize = 11.sp) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Готово") } },
    )
}
