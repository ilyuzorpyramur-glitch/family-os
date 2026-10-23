package app.familyos.guard

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.familyos.guard.data.Profile
import app.familyos.guard.data.QuestData
import app.familyos.guard.data.QuestItem
import app.familyos.guard.data.ShopData
import app.familyos.guard.data.ShopItem
import coil.compose.AsyncImage

@Composable
fun QuestsScreen(
    quests: QuestData?,
    busy: Boolean,
    message: String?,
    onComplete: (String, Boolean) -> Unit,
    onRefresh: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (message != null) {
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1708))) {
                Text(message, modifier = Modifier.padding(12.dp), color = Color(0xFFFDE68A))
            }
        }

        val profile = quests?.profile
        if (profile != null) {
            ProfileCard(profile)
            Spacer(Modifier.height(4.dp))
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Задания", fontSize = 18.sp, fontWeight = FontWeight.Bold)
            TextButton(onClick = onRefresh, enabled = !busy) { Text(if (busy) "Обновляем…" else "Обновить") }
        }

        val active = quests?.active.orEmpty()
        if (active.isEmpty()) {
            Text("Новых заданий нет.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            active.forEach { quest ->
                QuestCard(quest, busy) { id, photos -> onComplete(id, photos) }
            }
        }

        val completed = quests?.completed.orEmpty()
        if (completed.isNotEmpty()) {
            Text("На проверке", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            completed.forEach { quest ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.padding(14.dp)) {
                        Text(quest.title, fontWeight = FontWeight.Medium)
                        Text(
                            "Ждёт подтверждения родителя · ${quest.xp} XP · ${quest.coins} монет",
                            color = Color(0xFF8B9BB0),
                            fontSize = 12.sp,
                        )
                    }
                }
            }
        }

        val purchases = quests?.purchases.orEmpty().filter { it.status == "pending" }
        if (purchases.isNotEmpty()) {
            Text("Покупки на подтверждении", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            purchases.forEach { buy ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Row(
                        Modifier.fillMaxWidth().padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(buy.title)
                        Text("${buy.priceCoins} монет", color = Color(0xFFFBBF24))
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileCard(profile: Profile) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Уровень ${profile.level}", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text("${profile.coins} монет", color = Color(0xFFFBBF24), fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(10.dp))
            LinearProgressIndicator(
                progress = { profile.progress.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(8.dp),
                color = Color(0xFF34D399),
                trackColor = Color(0xFF1F2A37),
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "${profile.xp} XP · до следующего уровня ${profile.xpForNextLevel} XP",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                StatChip("Сила", profile.strength)
                StatChip("Ум", profile.intellect)
                StatChip("Творч.", profile.creativity)
            }
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                StatChip("Доброта", profile.kindness)
                StatChip("Дисц.", profile.discipline)
            }
        }
    }
}

@Composable
private fun StatChip(label: String, value: Int) {
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF0F151D))) {
        Text(
            "$label $value",
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            fontSize = 12.sp,
            color = Color(0xFF9FB0C4),
        )
    }
}

@Composable
private fun QuestCard(quest: QuestItem, busy: Boolean, onComplete: (String, Boolean) -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(14.dp)) {
            Text(quest.title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            quest.description?.let {
                Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "+${quest.xp} XP · +${quest.coins} монет" +
                    (quest.stat?.let { " · " + statLabel(it) } ?: ""),
                color = Color(0xFF34D399),
                fontSize = 12.sp,
            )
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = { onComplete(quest.id, quest.photosRequired > 0) },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    if (quest.photosRequired > 0) "Добавить фото (${quest.photosRequired})" else "Выполнено",
                )
            }
        }
    }
}

@Composable
fun ShopScreen(
    shop: ShopData?,
    busy: Boolean,
    message: String?,
    proposalPhoto: String?,
    onBuy: (String) -> Unit,
    onRefresh: () -> Unit,
    onPickPhoto: () -> Unit,
    onPropose: (String, String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (message != null) {
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1708))) {
                Text(message, modifier = Modifier.padding(12.dp), color = Color(0xFFFDE68A))
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text("Магазин", fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(
                    "У тебя ${shop?.profile?.coins ?: 0} монет",
                    color = Color(0xFFFBBF24),
                    fontSize = 13.sp,
                )
            }
            TextButton(onClick = onRefresh, enabled = !busy) { Text("Обновить") }
        }

        val items = shop?.items.orEmpty()
        if (items.isEmpty()) {
            Text("Пока пусто. Родитель скоро добавит награды.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            items.forEach { item ->
                ShopItemCard(item, shop?.profile?.coins ?: 0, busy, onBuy)
            }
        }

        Text("Предложить свой товар", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        var title by remember { mutableStateOf("") }
        var description by remember { mutableStateOf("") }
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(14.dp)) {
                androidx.compose.material3.OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Что хочешь купить") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                androidx.compose.material3.OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Описание (необязательно)") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = onPickPhoto, enabled = !busy) {
                        Text(if (proposalPhoto == null) "Фото" else "Фото добавлено")
                    }
                    Button(
                        onClick = { onPropose(title, description); title = ""; description = "" },
                        enabled = !busy && title.isNotBlank(),
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("Отправить OpenClaw")
                    }
                }
                Text(
                    "OpenClaw посмотрит товар и назначит цену в монетах.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                )
            }
        }

        val proposals = shop?.proposals.orEmpty()
        if (proposals.isNotEmpty()) {
            Text("Мои предложения", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            proposals.forEach { proposal ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Row(
                        Modifier.fillMaxWidth().padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(proposal.title)
                            proposal.description?.let {
                                Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                            }
                        }
                        Text(
                            when (proposal.status) {
                                "pending" -> "ждёт цену"
                                "priced" -> "${proposal.priceCoins ?: 0} монет"
                                else -> "отклонено"
                            },
                            color = Color(0xFFFBBF24),
                            fontSize = 12.sp,
                        )
                    }
                }
            }
        }

        val pending = shop?.pending.orEmpty()
        if (pending.isNotEmpty()) {
            Text("Ожидают подтверждения", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            pending.forEach { buy ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Row(
                        Modifier.fillMaxWidth().padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(buy.title)
                        Text("${buy.priceCoins} монет", color = Color(0xFFFBBF24))
                    }
                }
            }
        }
    }
}

@Composable
private fun ShopItemCard(item: ShopItem, coins: Int, busy: Boolean, onBuy: (String) -> Unit) {
    val canAfford = coins >= item.priceCoins
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(14.dp)) {
            item.photoUrl?.let { url ->
                AsyncImage(
                    model = url,
                    contentDescription = item.title,
                    modifier = Modifier.fillMaxWidth().height(140.dp),
                )
                Spacer(Modifier.height(6.dp))
            }
            Text(item.title, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            item.description?.let {
                Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "${item.priceCoins} монет" + (item.stock?.let { " · осталось $it" } ?: ""),
                color = if (canAfford) Color(0xFFFBBF24) else Color(0xFF8B9BB0),
                fontSize = 12.sp,
            )
            Spacer(Modifier.height(10.dp))
            Button(
                onClick = { onBuy(item.id) },
                enabled = !busy && canAfford,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (canAfford) "Купить" else "Не хватает монет")
            }
        }
    }
}

private fun statLabel(stat: String): String = when (stat) {
    "strength" -> "Сила"
    "intellect" -> "Интеллект"
    "creativity" -> "Творчество"
    "kindness" -> "Доброта"
    "discipline" -> "Дисциплина"
    else -> stat
}
