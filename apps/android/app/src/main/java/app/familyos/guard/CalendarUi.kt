package app.familyos.guard

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.familyos.guard.data.CalendarData
import app.familyos.guard.data.CalendarDay
import app.familyos.guard.data.GeofenceStatus
import java.time.LocalDate
import java.time.YearMonth

private val CalAccent = Color(0xFF34D399)
private val CalGold = Color(0xFFFBBF24)
private val CalMuted = Color(0xFF8B9BB0)
private val CalEmpty = Color(0xFF0F151D)

private val weekdays = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")

@Composable
fun MonthCalendar(
    calendar: CalendarData?,
    selected: String?,
    onSelect: (String) -> Unit,
) {
    val monthKey = calendar?.month?.takeIf { it.length == 7 } ?: LocalDate.now().toString().substring(0, 7)
    val yearMonth = remember(monthKey) { runCatching { YearMonth.parse(monthKey) }.getOrElse { YearMonth.now() } }
    val days: Map<String, CalendarDay> = remember(calendar) { calendar?.days?.associateBy { it.date }.orEmpty() }
    val today = LocalDate.now().toString()
    val offset = (yearMonth.atDay(1).dayOfWeek.value + 6) % 7
    val total = yearMonth.lengthOfMonth()
    val cells = offset + total
    val rows = (cells + 6) / 7

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(monthTitle(yearMonth), fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    LegendSquare(CalAccent, "выполнено")
                    LegendSquare(Color(0xFF2A3442), "план")
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                weekdays.forEach { day ->
                    Text(day, color = CalMuted, fontSize = 11.sp, modifier = Modifier.size(width = 34.dp, height = 16.dp))
                }
            }
            Spacer(Modifier.height(6.dp))
            for (row in 0 until rows) {
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    for (col in 0 until 7) {
                        val index = row * 7 + col
                        if (index < offset || index >= offset + total) {
                            Spacer(Modifier.size(34.dp))
                        } else {
                            val dayNumber = index - offset + 1
                            val date = yearMonth.atDay(dayNumber).toString()
                            val info = days[date]
                            DayCell(
                                day = dayNumber,
                                info = info,
                                isToday = date == today,
                                selected = date == selected,
                                onClick = { onSelect(date) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    day: Int,
    info: CalendarDay?,
    isToday: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val done = (info?.questsDone ?: 0) > 0
    val planned = (info?.questsPlanned ?: 0) > 0
    val hasEvents = info?.events?.isNotEmpty() == true

    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(
                when {
                    done -> Brush.linearGradient(listOf(Color(0xFF34D399), Color(0xFF059669)))
                    planned -> Brush.linearGradient(listOf(Color(0xFF2A3442), Color(0xFF232D3A)))
                    else -> Brush.linearGradient(listOf(CalEmpty, CalEmpty))
                },
            )
            .then(
                if (selected || isToday) {
                    Modifier.border(
                        1.5.dp,
                        if (selected) CalGold else Color(0xFF3B4A5C),
                        RoundedCornerShape(10.dp),
                    )
                } else {
                    Modifier
                },
            )
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "$day",
                fontSize = 12.sp,
                fontWeight = if (done) FontWeight.Bold else FontWeight.Normal,
                color = if (done) Color(0xFF04140E) else if (planned) Color(0xFFE6EDF3) else CalMuted,
            )
            if (hasEvents) {
                Box(
                    Modifier
                        .padding(top = 1.dp)
                        .size(4.dp)
                        .clip(CircleShape)
                        .background(CalGold),
                )
            }
        }
    }
}

@Composable
private fun LegendSquare(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(RoundedCornerShape(3.dp)).background(color))
        Spacer(Modifier.size(4.dp))
        Text(label, color = CalMuted, fontSize = 10.sp)
    }
}

private fun monthTitle(yearMonth: YearMonth): String {
    val names = listOf(
        "январь", "февраль", "март", "апрель", "май", "июнь",
        "июль", "август", "сентябрь", "октябрь", "ноябрь", "декабрь",
    )
    return "${names[yearMonth.monthValue - 1]} ${yearMonth.year}"
}

@Composable
fun AddEventDialog(
    defaultDate: String,
    geofences: List<GeofenceStatus>,
    busy: Boolean,
    onDismiss: () -> Unit,
    onSubmit: (title: String, geofenceId: String?, date: String, start: String, minutes: Int) -> Unit,
) {
    var title by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(defaultDate) }
    var time by remember { mutableStateOf("16:00") }
    var minutes by remember { mutableStateOf("60") }
    var placeId by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Новое событие") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Куда и зачем") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = date,
                    onValueChange = { date = it },
                    label = { Text("Дата, ГГГГ-ММ-ДД") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = time,
                        onValueChange = { time = it },
                        label = { Text("Время") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = minutes,
                        onValueChange = { minutes = it },
                        label = { Text("Минут") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (geofences.isNotEmpty()) {
                    Text("Место", color = CalMuted, fontSize = 12.sp)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        geofences.forEach { fence ->
                            val active = placeId == fence.id
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (active) Color(0xFF1B3A2F) else CalEmpty)
                                    .clickable { placeId = if (active) null else fence.id }
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                            ) {
                                Text(fence.label, fontSize = 13.sp)
                                if (active) Text("выбрано", color = CalAccent, fontSize = 11.sp)
                            }
                        }
                    }
                }
                Text(
                    "Событие уйдёт родителю на подтверждение. После одобрения место и время станут доступны.",
                    color = CalMuted,
                    fontSize = 11.sp,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val dur = minutes.toIntOrNull()?.coerceIn(15, 720) ?: 60
                    if (title.isNotBlank() && date.length == 10) {
                        onSubmit(title.trim(), placeId, date, time, dur)
                    }
                },
                enabled = !busy && title.isNotBlank(),
            ) { Text("Отправить") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } },
    )
}
