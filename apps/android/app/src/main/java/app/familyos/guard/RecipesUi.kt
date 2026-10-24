package app.familyos.guard

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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.familyos.guard.data.Product
import app.familyos.guard.data.Recipe

private val RAccent = app.familyos.guard.ui.accent
private val RGold = Color(0xFFFBBF24)
private val RMuted = Color(0xFF8B9BB0)

@Composable
fun RecipesScreen(
    recipes: List<Recipe>,
    products: List<Product>,
    busy: Boolean,
    message: String?,
    onCook: (String) -> Unit,
    onRefresh: () -> Unit,
) {
    var opened by remember { mutableStateOf<Recipe?>(null) }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (message != null) {
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1708))) {
                Text(message, Modifier.padding(12.dp), color = Color(0xFFFDE68A))
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column {
                Text("Рецепты", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text("Из того, что есть на складе", color = RMuted, fontSize = 12.sp)
            }
            TextButton(onClick = onRefresh, enabled = !busy) { Text("Обновить") }
        }

        val lowStock = products.filter { it.quantity <= 0 }
        if (lowStock.isNotEmpty()) {
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF2A2308))) {
                Text(
                    "Закончилось: " + lowStock.take(4).joinToString(", ") { it.title },
                    Modifier.padding(12.dp),
                    color = RGold,
                    fontSize = 12.sp,
                )
            }
        }

        if (recipes.isEmpty()) {
            Text("Пока нет рецептов. Родитель добавит их в панели.", color = RMuted)
        }

        recipes.forEach { recipe ->
            Card(
                shape = RoundedCornerShape(18.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                modifier = Modifier.clickable { opened = recipe },
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(recipe.title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            if (recipe.canCook) "можно готовить" else "не хватает ${recipe.missingCount}",
                            color = if (recipe.canCook) RAccent else RGold,
                            fontSize = 11.sp,
                        )
                    }
                    Text(
                        "${recipe.minutes} мин · ${recipe.servings} порц. · ${recipe.steps.size} шагов",
                        color = RMuted,
                        fontSize = 11.sp,
                    )
                    recipe.description?.takeIf { it.isNotBlank() }?.let {
                        Text(it, color = RMuted, fontSize = 12.sp)
                    }
                    recipe.items.take(4).forEach { item ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(item.title, fontSize = 11.sp, color = if (item.enough) Color(0xFFE6EDF3) else RMuted)
                            Text(
                                "${trimNumber(item.quantity)} ${item.unit}" +
                                    if (item.enough) "" else " (есть ${trimNumber(item.available)})",
                                fontSize = 11.sp,
                                color = if (item.enough) RAccent else Color(0xFFF87171),
                            )
                        }
                    }
                }
            }
        }
    }

    opened?.let { recipe ->
        CookingDialog(
            recipe = recipe,
            busy = busy,
            onDismiss = { opened = null },
            onCook = {
                onCook(recipe.id)
                opened = null
            },
        )
    }
}

@Composable
private fun CookingDialog(
    recipe: Recipe,
    busy: Boolean,
    onDismiss: () -> Unit,
    onCook: () -> Unit,
) {
    var step by remember { mutableStateOf(0) }
    val total = recipe.steps.size.coerceAtLeast(1)
    val finished = step >= total

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(recipe.title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    if (finished) "Готово! Отметь, что приготовил — спишем продукты." else "Шаг ${step + 1} из $total",
                    color = RMuted,
                    fontSize = 12.sp,
                )
                LinearProgressIndicator(
                    progress = { (step.toFloat() / total).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(6.dp),
                    color = RAccent,
                    trackColor = Color(0xFF1F2A37),
                )
                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF0F151D))) {
                    Text(
                        if (finished) "Приятного аппетита 🍽" else recipe.steps.getOrElse(step) { "" },
                        Modifier.padding(14.dp),
                        fontSize = 15.sp,
                    )
                }
                if (!recipe.canCook) {
                    Text(
                        "Не хватает продуктов: " + recipe.items.filter { !it.enough }.joinToString(", ") { it.title },
                        color = Color(0xFFF87171),
                        fontSize = 11.sp,
                    )
                }
                recipe.items.take(6).forEach { item ->
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("• ${item.title}", fontSize = 11.sp, color = RMuted)
                        Text("${trimNumber(item.quantity)} ${item.unit}", fontSize = 11.sp, color = RMuted)
                    }
                }
            }
        },
        confirmButton = {
            if (finished) {
                TextButton(onClick = onCook, enabled = !busy) { Text("Приготовил") }
            } else {
                TextButton(onClick = { step += 1 }) { Text("Далее") }
            }
        },
        dismissButton = {
            if (step > 0 && !finished) {
                TextButton(onClick = { step -= 1 }) { Text("Назад") }
            } else {
                TextButton(onClick = onDismiss) { Text("Закрыть") }
            }
        },
    )
}

private fun trimNumber(value: Double): String {
    return if (value % 1.0 == 0.0) value.toInt().toString() else String.format("%.1f", value)
}
