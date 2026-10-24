package app.familyos.guard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import app.familyos.guard.data.CalendarData
import app.familyos.guard.data.GeofenceStatus
import app.familyos.guard.data.HomeData
import app.familyos.guard.data.ProfileData
import coil.compose.AsyncImage

private val Accent = app.familyos.guard.ui.accent
private val Gold = Color(0xFFFBBF24)
private val Muted = Color(0xFF8B9BB0)

@Composable
fun HomeScreen(
    home: HomeData?,
    nutrition: app.familyos.guard.data.Nutrition?,
    pendingPayments: Int,
    onPayByQr: () -> Unit,
    calendar: CalendarData?,
    busy: Boolean,
    message: String?,
    onRefresh: () -> Unit,
    onOpenProfile: () -> Unit,
    onAddMeal: (String, Int) -> Unit,
    onDeleteMeal: (String) -> Unit,
    onAddEvent: (
        title: String,
        description: String,
        geofenceId: String?,
        date: String,
        start: String,
        minutes: Int,
        lat: Double?,
        lng: Double?,
    ) -> Unit,
) {
    var selectedDate by remember { mutableStateOf(java.time.LocalDate.now().toString()) }
    var showAdd by remember { mutableStateOf(false) }
    var showMeal by remember { mutableStateOf(false) }
    if (showMeal) {
        AddMealDialog(
            nutrition = nutrition,
            onAdd = { title, kcal ->
                showMeal = false
                onAddMeal(title, kcal)
            },
            onDelete = onDeleteMeal,
            onDismiss = { showMeal = false },
        )
    }
    Column(
        modifier = Modifier
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

        Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        ) {
            Column(
                modifier = Modifier
                    .background(
                        Brush.linearGradient(
                            listOf(
                                app.familyos.guard.ui.accentDeep,
                                app.familyos.guard.ui.accent.copy(alpha = 0.10f),
                                app.familyos.guard.ui.cardBg,
                            ),
                        ),
                    )
                    .padding(18.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Avatar(home?.avatarUrl, 62)
                    Spacer(Modifier.size(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            home?.nickname?.takeIf { it.isNotBlank() } ?: home?.name ?: "Профиль",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFE6EDF3),
                        )
                        Text(
                            home?.let { "Уровень ${it.level} · ${it.xp} XP" } ?: "Загрузка…",
                            color = Muted,
                            fontSize = 13.sp,
                        )
                    }
                    Text("${home?.coins ?: 0}", color = Gold, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text(" монет", color = Gold, fontSize = 12.sp)
                }
                Spacer(Modifier.height(14.dp))
                LinearProgressIndicator(
                    progress = { home?.progress?.coerceIn(0f, 1f) ?: 0f },
                    modifier = Modifier.fillMaxWidth().height(8.dp),
                    color = Accent,
                    trackColor = Color(0xFF1F2A37),
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    home?.let { "До следующего уровня ${it.xpForNextLevel} XP" } ?: "",
                    color = Muted,
                    fontSize = 12.sp,
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    TextButton(onClick = onRefresh, enabled = !busy) { Text("Обновить") }
                    TextButton(onClick = onOpenProfile) { Text("Профиль") }
                }
            }
        }

        nutrition?.let { day ->
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Калории", fontWeight = FontWeight.SemiBold, color = Color(0xFFE6EDF3))
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "${day.consumed} из ${day.target} ккал",
                                color = if (day.over) Color(0xFFF87171) else app.familyos.guard.ui.accent,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 13.sp,
                            )
                            TextButton(onClick = { showMeal = true }) { Text("+ Еда", fontSize = 12.sp) }
                        }
                    }
                    LinearProgressIndicator(
                        progress = { (day.percent.coerceIn(0, 100)) / 100f },
                        modifier = Modifier.fillMaxWidth().height(8.dp),
                        color = if (day.over) Color(0xFFF87171) else app.familyos.guard.ui.accent,
                        trackColor = Color(0xFF1F2A37),
                    )
                    Text(
                        if (day.over) "Выше нормы на ${day.consumed - day.target} ккал" else "Осталось ${day.left} ккал",
                        fontSize = 11.sp,
                        color = Muted,
                    )
                }
            }
        }

        PayByQrCard(pendingCount = pendingPayments, onOpen = onPayByQr)

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MetricCard("Задания", "${home?.approvedToday ?: 0} сегодня", Modifier.weight(1f))
            MetricCard("Экран", minutesText(home?.screenMinutes ?: 0), Modifier.weight(1f))
            MetricCard("Стрик", "${home?.streak ?: 0} дн.", Modifier.weight(1f))
        }

        val dayInfo = calendar?.days?.firstOrNull { it.date == selectedDate }
        MonthCalendar(calendar = calendar, selected = selectedDate, onSelect = { selectedDate = it })

        val dayEvents = dayInfo?.events.orEmpty()
        if (dayEvents.isNotEmpty()) {
            Text("События на $selectedDate", fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            dayEvents.forEach { event ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Row(
                        Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(event.title)
                            Text(
                                "${event.startTime} · ${event.durationMin} мин",
                                color = Muted,
                                fontSize = 12.sp,
                            )
                        }
                        Text(
                            when (event.status) {
                                "approved" -> "одобрено"
                                "pending" -> "на проверке"
                                else -> "отклонено"
                            },
                            color = when (event.status) {
                                "approved" -> Accent
                                "pending" -> Gold
                                else -> Color(0xFFF87171)
                            },
                            fontSize = 11.sp,
                        )
                    }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { showAdd = true }, modifier = Modifier.weight(1f)) {
                Text("Событие на $selectedDate")
            }
        }

        val fences = home?.geofences.orEmpty()
        if (fences.isNotEmpty()) {
            Text("Геозоны", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            fences.forEach { fence ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Row(
                        Modifier.fillMaxWidth().padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(fence.label)
                        Text(
                            if (fence.inside) "внутри" else if (fence.fineCoins > 0) "вне · -${fence.fineCoins}" else "вне",
                            color = if (fence.inside) Accent else Color(0xFFF87171),
                            fontSize = 12.sp,
                        )
                    }
                }
            }
        }

        Text("Задания на сегодня", fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        val tasks = home?.tasks.orEmpty().filter { it.status == "active" || it.status == "completed" }
        if (tasks.isEmpty()) {
            Text("Нет активных заданий", color = Muted)
        } else {
            tasks.take(6).forEach { task ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Row(
                        Modifier.fillMaxWidth().padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(task.title, fontWeight = FontWeight.Medium)
                            Text("+${task.xp} XP · +${task.coins} монет", color = Accent, fontSize = 12.sp)
                        }
                        Text(
                            if (task.status == "completed") "на проверке" else "активно",
                            color = Muted,
                            fontSize = 12.sp,
                        )
                    }
                }
            }
        }

        if (showAdd) {
            AddEventDialog(
                defaultDate = selectedDate,
                geofences = fences,
                busy = busy,
                onDismiss = { showAdd = false },
                onSubmit = { title, description, geofenceId, date, start, minutes, lat, lng ->
                    onAddEvent(title, description, geofenceId, date, start, minutes, lat, lng)
                    showAdd = false
                },
            )
        }
    }
}

@Composable
private fun MetricCard(title: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(title, color = Muted, fontSize = 11.sp)
            Spacer(Modifier.height(4.dp))
            Text(value, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun ProfileScreen(
    profile: ProfileData?,
    busy: Boolean,
    message: String?,
    onPickAvatar: () -> Unit,
    onSave: (nickname: String, school: String, grade: String) -> Unit,
    onRefresh: () -> Unit,
) {
    var nickname by remember { mutableStateOf("") }
    var school by remember { mutableStateOf("") }
    var grade by remember { mutableStateOf("") }

    LaunchedEffect(profile) {
        if (profile != null) {
            nickname = profile.nickname.orEmpty()
            school = profile.school.orEmpty()
            grade = profile.grade.orEmpty()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (message != null) {
            Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF1C1708))) {
                Text(message, Modifier.padding(12.dp), color = Color(0xFFFDE68A))
            }
        }

        Avatar(profile?.avatarUrl, 104)
        Text(profile?.name ?: "Ребёнок", fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Text(
            profile?.let { "Уровень ${it.level} · ${it.xp} XP · ${it.coins} монет" } ?: "",
            color = Gold,
            fontSize = 14.sp,
        )

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Данные", fontWeight = FontWeight.SemiBold)
                Text("Имя: ${profile?.name ?: "—"}")
                Text("Никнейм: ${profile?.nickname ?: "—"}", color = Muted)
                Text("Школа: ${profile?.school ?: "—"}", color = Muted)
                Text("Класс: ${profile?.grade ?: "—"}", color = Muted)
                profile?.birthDate?.let { Text("Дата рождения: $it", color = Muted) }
                Spacer(Modifier.height(4.dp))
                Text("Данные заполняет родитель в панели Family OS.", color = Muted, fontSize = 12.sp)
            }
        }

        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
            Column(Modifier.padding(16.dp)) {
                Text("Характеристики", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatPill("Сила", profile?.strength ?: 0)
                    StatPill("Ум", profile?.intellect ?: 0)
                    StatPill("Творч.", profile?.creativity ?: 0)
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatPill("Доброта", profile?.kindness ?: 0)
                    StatPill("Дисц.", profile?.discipline ?: 0)
                }
            }
        }

        TextButton(onClick = onRefresh, enabled = !busy) { Text("Обновить профиль") }
        Text(
            "Профиль заполняет родитель. Если что-то неверно — попросите исправить.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp,
        )
    }
}

@Composable
private fun StatPill(label: String, value: Int) {
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF0F151D))) {
        Text("$label $value", Modifier.padding(horizontal = 10.dp, vertical = 6.dp), color = Muted, fontSize = 12.sp)
    }
}

@Composable
private fun Avatar(url: String?, size: Int) {
    if (url.isNullOrBlank()) {
        Card(
            modifier = Modifier.size(size.dp),
            shape = CircleShape,
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1F2A37)),
        ) {
            Column(
                Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("🙂", fontSize = (size / 3).sp)
            }
        }
    } else {
        AsyncImage(
            model = url,
            contentDescription = "Аватар",
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(size.dp).clip(CircleShape),
        )
    }
}

private fun minutesText(minutes: Int): String {
    if (minutes < 60) return "$minutes мин"
    val h = minutes / 60
    val m = minutes % 60
    return if (m == 0) "$h ч" else "$h ч $m м"
}

/** Добавление приёма пищи: название и калории. */
@Composable
fun AddMealDialog(
    nutrition: app.familyos.guard.data.Nutrition?,
    onAdd: (String, Int) -> Unit,
    onDelete: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var kcal by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Что поел?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    title,
                    { title = it },
                    label = { Text("Название, например «Суп»") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    kcal,
                    { kcal = it.filter { ch -> ch.isDigit() } },
                    label = { Text("Калории, ккал") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                nutrition?.meals?.take(6)?.forEach { meal ->
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("${meal.title} · ${meal.kcal} ккал", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        TextButton(onClick = { onDelete(meal.id) }) { Text("убрать", fontSize = 11.sp) }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (title.isNotBlank() && kcal.isNotBlank()) onAdd(title.trim(), kcal.toInt()) },
                enabled = title.isNotBlank() && kcal.isNotBlank(),
            ) { Text("Добавить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Закрыть") } },
    )
}
