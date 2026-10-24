package app.familyos.guard

import androidx.compose.foundation.background
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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
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
    onClaim: (String) -> Unit,
    onScan: (QuestItem) -> Unit,
    onCook: (QuestItem) -> Unit,
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
                QuestCard(quest, busy, onComplete, onClaim, onScan, onCook)
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
private fun QuestCard(
    quest: QuestItem,
    busy: Boolean,
    onComplete: (String, Boolean) -> Unit,
    onClaim: (String) -> Unit,
    onScan: (QuestItem) -> Unit,
    onCook: (QuestItem) -> Unit,
) {
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
            if (quest.questType == "shopping") {
                Button(
                    onClick = { onScan(quest) },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Сканировать покупки") }
            } else if (quest.questType == "cooking") {
                Button(
                    onClick = { onCook(quest) },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Готовить по шагам") }
            } else if (quest.shared) {
                Button(
                    onClick = { onClaim(quest.id) },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Взять задание") }
            } else {
                Button(
                    onClick = { onComplete(quest.id, quest.photosRequired > 0) },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (quest.photosRequired > 0) "Добавить фото (${quest.photosRequired})" else "Выполнено")
                }
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
    var showProposal by remember { mutableStateOf(false) }
    val coins = shop?.profile?.coins ?: 0
    val items = shop?.items.orEmpty()

    Box(Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (message != null) {
                Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1708))) {
                    Text(message, Modifier.padding(12.dp), color = Color(0xFFFDE68A))
                }
            }

            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent),
            ) {
                Column(
                    Modifier
                        .background(Brush.linearGradient(listOf(Color(0xFF3A2A08), Color(0xFF151B24))))
                        .padding(18.dp),
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column {
                            Text("Магазин", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Color(0xFFE6EDF3))
                            Text("Трать монеты на награды", color = Color(0xFFB9A472), fontSize = 12.sp)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("$coins", color = Color(0xFFFBBF24), fontSize = 28.sp, fontWeight = FontWeight.Bold)
                            Text("монет", color = Color(0xFFFBBF24), fontSize = 11.sp)
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    TextButton(onClick = onRefresh, enabled = !busy) { Text("Обновить") }
                }
            }

            if (items.isEmpty()) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Text(
                        "Пока пусто. Нажми «Предложить», чтобы попросить награду.",
                        Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                items.chunked(2).forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { item ->
                            ShopTile(item, coins, busy, onBuy, Modifier.weight(1f))
                        }
                        if (row.size == 1) Spacer(Modifier.weight(1f))
                    }
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

        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .padding(18.dp),
        ) {
            AddFab { showProposal = true }
        }
    }

    if (showProposal) {
        ProposalDialog(
            photo = proposalPhoto,
            busy = busy,
            onPickPhoto = onPickPhoto,
            onDismiss = { showProposal = false },
            onSubmit = { title, description ->
                onPropose(title, description)
                showProposal = false
            },
        )
    }
}

@Composable
private fun ProposalDialog(
    photo: String?,
    busy: Boolean,
    onPickPhoto: () -> Unit,
    onDismiss: () -> Unit,
    onSubmit: (String, String) -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Предложить награду") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Что хочешь купить") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Описание (необязательно)") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(onClick = onPickPhoto, enabled = !busy) {
                    Text(if (photo == null) "Добавить фото" else "Фото добавлено")
                }
                Text(
                    "OpenClaw посмотрит товар и назначит цену в монетах, родитель подтвердит.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSubmit(title, description) },
                enabled = !busy && title.isNotBlank(),
            ) { Text("Отправить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}

@Composable
private fun ShopTile(
    item: ShopItem,
    coins: Int,
    busy: Boolean,
    onBuy: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val canAfford = coins >= item.priceCoins
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(12.dp)) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(96.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF0F151D)),
                contentAlignment = Alignment.Center,
            ) {
                val url = item.photoUrl
                if (url.isNullOrBlank()) {
                    Text("🎁", fontSize = 34.sp)
                } else {
                    AsyncImage(
                        model = url,
                        contentDescription = item.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(item.title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 2)
            item.description?.let {
                Text(it, color = Color(0xFF8B9BB0), fontSize = 11.sp, maxLines = 2)
            }
            Spacer(Modifier.height(6.dp))
            Text(
                "${item.priceCoins} монет",
                color = if (canAfford) Color(0xFFFBBF24) else Color(0xFF6E7E92),
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
            )
            item.stock?.let { Text("осталось $it", color = Color(0xFF6E7E92), fontSize = 10.sp) }
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = { onBuy(item.id) },
                enabled = !busy && canAfford,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text(if (canAfford) "Купить" else "дорого", fontSize = 12.sp)
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
