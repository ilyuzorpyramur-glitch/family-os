package app.familyos.guard.parent

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.familyos.guard.data.ChildCard
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

private val Accent = app.familyos.guard.ui.accent
private val AccentSoft = app.familyos.guard.ui.accentSoft
private val Muted = app.familyos.guard.ui.textMuted
private val Surface1 = app.familyos.guard.ui.cardBg
private val Danger = app.familyos.guard.ui.danger
private val Line = app.familyos.guard.ui.cardBorder
private val Green = Color(0xFF34D399)

/** Питание ребёнка за сегодня: калории и БЖУ. */
data class ChildNutrition(
    val consumed: Int = 0,
    val target: Int = 2000,
    val protein: Int = 0,
    val fat: Int = 0,
    val carbs: Int = 0,
)

/** Вкладка «Дети»: карточки детей и добавление нового профиля. */
@Composable
fun ChildrenTab(
    children: List<ChildCard>,
    nutrition: Map<String, ChildNutrition>,
    blockedCount: Int,
    busy: Boolean,
    onOpen: (ChildCard) -> Unit,
    onLimit: (ChildCard) -> Unit,
    onCreate: (String) -> Unit,
    onRefresh: () -> Unit,
) {
    var adding by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }

    if (adding) {
        AlertDialog(
            onDismissRequest = { adding = false },
            title = { Text("Новый профиль") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        newName,
                        { newName = it },
                        label = { Text("Имя ребёнка") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text(
                        "После создания подключите телефон ребёнка по QR.",
                        fontSize = 11.sp,
                        color = Muted,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { onCreate(newName.trim()); adding = false; newName = "" },
                    enabled = newName.trim().length >= 1,
                ) { Text("Создать") }
            },
            dismissButton = { TextButton(onClick = { adding = false }) { Text("Отмена") } },
        )
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(horizontal = 16.dp)
            .padding(top = 18.dp, bottom = 110.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Дети", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = app.familyos.guard.ui.textPrimary)
                Text(
                    "${children.size} ${profilesWord(children.size)} · Guard активен",
                    fontSize = 12.sp,
                    color = Muted,
                )
            }
            Box(
                Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(Surface1)
                    .border(1.dp, Line, CircleShape)
                    .clickable { onRefresh() },
                contentAlignment = Alignment.Center,
            ) { Text("👻", fontSize = 18.sp) }
        }

        children.forEach { child ->
            ChildCardLarge(
                child = child,
                nutrition = nutrition[child.id] ?: ChildNutrition(target = child.kcalTarget),
                onOpen = { onOpen(child) },
                onLimit = { onLimit(child) },
            )
        }

        AddChildCard(onClick = { adding = true })
    }
}

@Composable
private fun ChildCardLarge(
    child: ChildCard,
    nutrition: ChildNutrition,
    onOpen: () -> Unit,
    onLimit: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = Surface1),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, Line.copy(alpha = 0.6f), RoundedCornerShape(26.dp)),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Avatar(child)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        child.nickname ?: child.name,
                        fontSize = 21.sp,
                        fontWeight = FontWeight.Bold,
                        color = app.familyos.guard.ui.textPrimary,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("📍", fontSize = 11.sp)
                        Spacer(Modifier.width(4.dp))
                        Text(
                            child.place?.let { "Сейчас в $it" } ?: "Геопозиция не передана",
                            fontSize = 12.sp,
                            color = app.familyos.guard.ui.textPrimary.copy(alpha = 0.85f),
                            maxLines = 2,
                        )
                    }
                    Text(
                        "Геопозиция · " + (lastSeenText(child.lastSeenAt) ?: "нет данных"),
                        fontSize = 11.sp,
                        color = Muted,
                    )
                }
                OnlineBadge(child.online)
            }

            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                CalorieRing(
                    consumed = nutrition.consumed,
                    target = nutrition.target.let { if (it > 0) it else child.kcalTarget },
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(
                    onClick = onLimit,
                    shape = RoundedCornerShape(22.dp),
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = app.familyos.guard.ui.textPrimary),
                ) {
                    Text("🛡", fontSize = 12.sp)
                    Spacer(Modifier.width(6.dp))
                    Text("Лимит", fontSize = 13.sp)
                }
                Button(
                    onClick = onOpen,
                    shape = RoundedCornerShape(22.dp),
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Color(0xFF120800)),
                ) { Text("Профиль", fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
                Text(
                    "›",
                    fontSize = 22.sp,
                    color = Muted,
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable { onOpen() }
                        .padding(horizontal = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun Avatar(child: ChildCard) {
    Box(
        Modifier
            .size(62.dp)
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(Accent, Accent.copy(alpha = 0.62f)))),
        contentAlignment = Alignment.Center,
    ) {
        if (child.avatarUrl != null) {
            coil.compose.AsyncImage(
                model = child.avatarUrl,
                contentDescription = child.name,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier.size(62.dp).clip(CircleShape),
            )
        } else {
            Text(
                child.initial,
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF120800),
            )
        }
    }
}

@Composable
private fun OnlineBadge(online: Boolean) {
    Row(
        Modifier
            .clip(RoundedCornerShape(20.dp))
            .background((if (online) Green else Muted).copy(alpha = 0.16f))
            .border(1.dp, (if (online) Green else Muted).copy(alpha = 0.45f), RoundedCornerShape(20.dp))
            .padding(horizontal = 9.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(if (online) Green else Muted),
        )
        Spacer(Modifier.width(5.dp))
        Text(
            if (online) "Онлайн" else "Офлайн",
            fontSize = 10.sp,
            color = if (online) Green else Muted,
            fontWeight = FontWeight.Medium,
        )
    }
}

/** Кольцо калорий: 270° дуги, как в макете. */
@Composable
fun CalorieRing(consumed: Int, target: Int, diameter: Dp = 136.dp, thickness: Dp = 11.dp) {
    val ratio = if (target > 0) (consumed.toFloat() / target).coerceIn(0f, 1f) else 0f
    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(diameter)) {
        Canvas(Modifier.fillMaxSize()) {
            val strokeWidth = thickness.toPx()
            val inset = strokeWidth / 2 + 2.dp.toPx()
            val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
            drawArc(
                color = Color(0xFF232A36),
                startAngle = 135f,
                sweepAngle = 270f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
            )
            if (ratio > 0f) {
                drawArc(
                    color = Accent.copy(alpha = 0.22f),
                    startAngle = 135f,
                    sweepAngle = 270f * ratio,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(width = strokeWidth * 2.1f, cap = StrokeCap.Round),
                )
                drawArc(
                    brush = Brush.sweepGradient(listOf(Accent, Accent.copy(alpha = 0.72f), Accent)),
                    startAngle = 135f,
                    sweepAngle = 270f * ratio,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "$consumed",
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
                color = app.familyos.guard.ui.textPrimary,
            )
            Text("из $target ккал", fontSize = 11.sp, color = Muted)
        }
    }
}

@Composable
private fun AddChildCard(onClick: () -> Unit) {
    val dash = Modifier.dashedBorder(Accent.copy(alpha = 0.35f), 26.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .then(dash)
            .clickable { onClick() }
            .padding(18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(62.dp)
                .clip(CircleShape)
                .dashedBorder(Muted.copy(alpha = 0.5f), 31.dp),
            contentAlignment = Alignment.Center,
        ) { Text("+", fontSize = 26.sp, color = Muted) }
        Spacer(Modifier.width(14.dp))
        Column {
            Text(
                "Добавить ребёнка",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = app.familyos.guard.ui.textPrimary,
            )
            Text("Профиль и подключение телефона", fontSize = 11.sp, color = Muted)
        }
    }
}

/** Экран настроек ребёнка. */
@Composable
fun ChildProfileScreen(
    child: ChildCard,
    nutrition: ChildNutrition,
    blockedCount: Int,
    onBack: () -> Unit,
    onLimit: () -> Unit,
    onSites: () -> Unit,
    onNorm: () -> Unit,
    onNightMode: (Boolean) -> Unit,
    onConnect: () -> Unit,
    onDelete: () -> Unit,
    onEvents: () -> Unit,
    onSaveProfile: (String, String, String) -> Unit,
) {
    var nightMode by remember(child.id, child.bedtimeStart) { mutableStateOf(child.nightModeOn) }
    var confirmingDelete by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }

    if (confirmingDelete) {
        AlertDialog(
            onDismissRequest = { confirmingDelete = false },
            title = { Text("Удалить профиль?") },
            text = {
                Text(
                    "Пропадут задания, события, история экрана и склада по этому ребёнку. " +
                        "Телефон придётся подключить заново.",
                    fontSize = 12.sp,
                )
            },
            confirmButton = {
                TextButton(onClick = { confirmingDelete = false; onDelete() }) {
                    Text("Удалить", color = Danger)
                }
            },
            dismissButton = { TextButton(onClick = { confirmingDelete = false }) { Text("Отмена") } },
        )
    }

    if (editing) {
        var nickname by remember { mutableStateOf(child.nickname.orEmpty()) }
        var school by remember { mutableStateOf(child.school.orEmpty()) }
        var grade by remember { mutableStateOf(child.grade.orEmpty()) }
        AlertDialog(
            onDismissRequest = { editing = false },
            title = { Text("Имя и школа") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(nickname, { nickname = it }, label = { Text("Как звать") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(school, { school = it }, label = { Text("Школа") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(grade, { grade = it }, label = { Text("Класс") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    editing = false
                    onSaveProfile(nickname.trim(), school.trim(), grade.trim())
                }) { Text("Сохранить") }
            },
            dismissButton = { TextButton(onClick = { editing = false }) { Text("Отмена") } },
        )
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(horizontal = 16.dp)
            .padding(top = 10.dp, bottom = 26.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Surface1)
                    .clickable { onBack() },
                contentAlignment = Alignment.Center,
            ) { Text("‹", fontSize = 20.sp, color = app.familyos.guard.ui.textPrimary) }
            Text(
                "Профиль ребёнка",
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
            )
            TextButton(onClick = onBack) { Text("Готово", color = Accent, fontSize = 13.sp) }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(child)
            Spacer(Modifier.width(14.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        child.nickname ?: child.name,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "✏️",
                        fontSize = 12.sp,
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable { editing = true }
                            .padding(4.dp),
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("📍", fontSize = 11.sp)
                    Spacer(Modifier.width(4.dp))
                    Text(
                        child.place?.let { "Сейчас в $it" } ?: "Геопозиция не передана",
                        fontSize = 12.sp,
                        color = app.familyos.guard.ui.textPrimary.copy(alpha = 0.85f),
                    )
                }
                Text("Геопозиция · " + (lastSeenText(child.lastSeenAt) ?: "нет данных"), fontSize = 11.sp, color = Muted)
                if (child.online) {
                    Row(
                        Modifier
                            .padding(top = 2.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(Green.copy(alpha = 0.16f))
                            .padding(horizontal = 9.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(6.dp).clip(CircleShape).background(Green))
                        Spacer(Modifier.width(5.dp))
                        Text("Телефон подключён", fontSize = 10.sp, color = Green)
                    }
                } else {
                    TextButton(onClick = onConnect) { Text("📷 Подключить телефон", fontSize = 11.sp) }
                }
            }
        }

        Section("ПИТАНИЕ") {
            val target = if (nutrition.target > 0) nutrition.target else child.kcalTarget
            SettingRow(
                emoji = "🔥",
                emojiBg = Color(0xFF3A1D08),
                title = "Калории",
                value = "${nutrition.consumed} из $target ккал",
                progress = if (target > 0) (nutrition.consumed.toFloat() / target).coerceIn(0f, 1f) else 0f,
                danger = nutrition.consumed > target,
                onClick = onNorm,
            )
            Divider()
            SettingRow(
                emoji = "🥗",
                emojiBg = Color(0xFF2A1B3A),
                title = "БЖУ",
                value = "Б ${nutrition.protein} · Ж ${nutrition.fat} · У ${nutrition.carbs} г",
                onClick = onNorm,
            )
            Divider()
            SettingRow(
                emoji = "🎯",
                emojiBg = Color(0xFF10321F),
                title = "Норма",
                value = "$target ккал",
                onClick = onNorm,
            )
        }

        Section("ЭКРАННОЕ ВРЕМЯ") {
            SettingRow(
                emoji = "🕐",
                emojiBg = Color(0xFF0F2A44),
                title = "Лимит экранного времени",
                value = "${hours(child.usedMinutes)} из ${hours(child.limitMinutes)}",
                onClick = onLimit,
            )
            Divider()
            SettingRow(
                emoji = "🌙",
                emojiBg = Color(0xFF221A3A),
                title = "Расписание",
                value = if (child.nightModeOn) "${child.bedtimeStart} – ${child.bedtimeEnd}" else "не задано",
                onClick = onLimit,
            )
            Divider()
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { nightMode = !nightMode; onNightMode(nightMode) }
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                EmojiTile("🛏", Color(0xFF3A2415))
                Spacer(Modifier.width(12.dp))
                Text(
                    "Ночной режим",
                    modifier = Modifier.weight(1f),
                    fontSize = 14.sp,
                    color = app.familyos.guard.ui.textPrimary,
                )
                Switch(
                    checked = nightMode,
                    onCheckedChange = { nightMode = it; onNightMode(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color(0xFF120800),
                        checkedTrackColor = Accent,
                        uncheckedTrackColor = Color(0xFF232A36),
                    ),
                )
            }
        }

        Section("САЙТЫ И ПРИЛОЖЕНИЯ") {
            SettingRow(
                emoji = "🌐",
                emojiBg = Color(0xFF0C3330),
                title = "Заблокированные сайты",
                value = "$blockedCount",
                onClick = onSites,
            )
            Divider()
            SettingRow(
                emoji = "▦",
                emojiBg = Color(0xFF10243F),
                title = "Приложения",
                value = if (child.appsCount > 0) "${child.appsCount} ограничено" else "не настроены",
                onClick = onSites,
            )
            Divider()
            SettingRow(
                emoji = "🔔",
                emojiBg = Color(0xFF2A1B3A),
                title = "События и запросы",
                value = "",
                badge = child.pendingRequests,
                subtitle = if (child.pendingRequests > 0) "Ждут ответа родителя" else null,
                onClick = onEvents,
            )
        }

        OutlinedButton(
            onClick = { confirmingDelete = true },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Danger),
        ) {
            Text("🗑", fontSize = 12.sp)
            Spacer(Modifier.width(8.dp))
            Text("Удалить профиль", fontSize = 13.sp)
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, fontSize = 10.sp, color = Muted, letterSpacing = 1.sp)
        Card(
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = Surface1),
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, Line.copy(alpha = 0.5f), RoundedCornerShape(22.dp)),
        ) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 6.dp)) { content() }
        }
    }
}

@Composable
private fun Divider() {
    Box(Modifier.fillMaxWidth().height(1.dp).background(Line.copy(alpha = 0.45f)))
}

@Composable
private fun EmojiTile(emoji: String, bg: Color) {
    Box(
        Modifier
            .size(34.dp)
            .clip(RoundedCornerShape(11.dp))
            .background(bg),
        contentAlignment = Alignment.Center,
    ) { Text(emoji, fontSize = 15.sp) }
}

@Composable
private fun SettingRow(
    emoji: String,
    emojiBg: Color,
    title: String,
    value: String,
    onClick: () -> Unit,
    progress: Float? = null,
    danger: Boolean = false,
    badge: Int = 0,
    subtitle: String? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        EmojiTile(emoji, emojiBg)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(title, fontSize = 14.sp, color = app.familyos.guard.ui.textPrimary)
            subtitle?.let { Text(it, fontSize = 10.sp, color = Muted) }
            progress?.let {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color(0xFF232A36)),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(it)
                            .height(3.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(if (danger) Danger else Accent),
                    )
                }
            }
        }
        if (value.isNotBlank()) {
            Text(value, fontSize = 11.sp, color = Muted, textAlign = TextAlign.End)
        }
        if (badge > 0) {
            Spacer(Modifier.width(6.dp))
            Box(
                Modifier
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(Accent),
                contentAlignment = Alignment.Center,
            ) {
                Text("$badge", fontSize = 10.sp, color = Color(0xFF120800), fontWeight = FontWeight.Bold)
            }
        }
        Text("›", fontSize = 18.sp, color = Muted, modifier = Modifier.padding(start = 6.dp))
    }
}

/** Пунктирная рамка: в Compose нет готовой, рисуем сами. */
private fun Modifier.dashedBorder(color: Color, radius: Dp): Modifier = drawBehind {
    drawRoundRect(
        color = color,
        cornerRadius = CornerRadius(radius.toPx()),
        style = Stroke(
            width = 1.5.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(9.dp.toPx(), 7.dp.toPx())),
        ),
    )
}

private fun profilesWord(count: Int): String {
    val tail = count % 100
    if (tail in 11..14) return "профилей"
    return when (count % 10) {
        1 -> "профиль"
        2, 3, 4 -> "профиля"
        else -> "профилей"
    }
}

private fun hours(minutes: Int): String {
    val h = minutes / 60
    val m = minutes % 60
    return when {
        h == 0 -> "$m м"
        m == 0 -> "$h ч"
        else -> "$h ч $m м"
    }
}

/** «5 минут назад» из отметки времени устройства. */
private fun lastSeenText(raw: String?): String? {
    if (raw.isNullOrBlank()) return null
    val formats = listOf("yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd'T'HH:mm:ss")
    var stamp: Date? = null
    for (pattern in formats) {
        val format = SimpleDateFormat(pattern, Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }
        stamp = runCatching { format.parse(raw) }.getOrNull()
        if (stamp != null) break
    }
    val moment = stamp ?: return null
    val minutes = ((System.currentTimeMillis() - moment.time) / 60_000).coerceAtLeast(0)
    return when {
        minutes < 1 -> "только что"
        minutes < 60 -> "$minutes ${minutesWord(minutes.toInt())} назад"
        minutes < 1440 -> "${minutes / 60} ч назад"
        else -> "${minutes / 1440} дн. назад"
    }
}

private fun minutesWord(minutes: Int): String {
    val tail = minutes % 100
    if (tail in 11..14) return "минут"
    return when (minutes % 10) {
        1 -> "минуту"
        2, 3, 4 -> "минуты"
        else -> "минут"
    }
}
