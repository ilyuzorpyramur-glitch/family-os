package app.familyos.guard

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog

private val BarBg = Color(0xFF0E141C)
private val BarMuted = Color(0xFF6E7E92)

private data class BarItem(val label: String, val icon: ImageVector)

private val barItems = listOf(
    BarItem("Главная", Icons.Filled.Home),
    BarItem("Задания", Icons.Filled.List),
    BarItem("Магазин", Icons.Filled.ShoppingCart),
    BarItem("Еда", Icons.Filled.Favorite),
    BarItem("Щит", Icons.Filled.Lock),
    BarItem("Профиль", Icons.Filled.Person),
)

@Composable
fun BottomBar(selected: Int, onSelect: (Int) -> Unit) {
    Surface(color = BarBg, tonalElevation = 0.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            barItems.forEachIndexed { index, item ->
                BarButton(
                    item = item,
                    selected = selected == index,
                    modifier = Modifier.weight(1f),
                    onClick = { onSelect(index) },
                )
            }
        }
    }
}

@Composable
private fun BarButton(item: BarItem, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(
                    if (selected) Color(0xFF17352B) else Color.Transparent,
                )
                .padding(horizontal = 12.dp, vertical = 5.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = item.icon,
                contentDescription = item.label,
                tint = if (selected) Color(0xFF34D399) else BarMuted,
                modifier = Modifier.size(20.dp),
            )
        }
        Text(
            item.label,
            color = if (selected) Color(0xFF34D399) else BarMuted,
            fontSize = 9.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
        )
    }
}

@Composable
fun LevelUpDialog(
    level: Int,
    xp: Int,
    strength: Int,
    intellect: Int,
    creativity: Int,
    kindness: Int,
    discipline: Int,
    onDismiss: () -> Unit,
) {
    val scale = remember { Animatable(0.75f) }
    LaunchedEffect(Unit) {
        scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
    }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .scale(scale.value)
                .clip(RoundedCornerShape(26.dp))
                .background(Brush.linearGradient(listOf(Color(0xFF1B4B3A), Color(0xFF101820))))
                .padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("🎉", fontSize = 40.sp)
            Text(
                "НОВЫЙ УРОВЕНЬ",
                color = Color(0xFFFBBF24),
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 2.sp,
            )
            Text(
                "$level",
                color = Color(0xFFE6EDF3),
                fontSize = 52.sp,
                fontWeight = FontWeight.Bold,
            )
            Text("$xp XP всего", color = Color(0xFF8B9BB0), fontSize = 12.sp)
            Spacer(Modifier.height(4.dp))
            StatRow("Сила", strength, Color(0xFFF87171))
            StatRow("Интеллект", intellect, Color(0xFF60A5FA))
            StatRow("Творчество", creativity, Color(0xFFC084FC))
            StatRow("Доброта", kindness, Color(0xFFFBBF24))
            StatRow("Дисциплина", discipline, Color(0xFF34D399))
            Spacer(Modifier.height(6.dp))
            Text(
                "Нажми, чтобы продолжить",
                color = Color(0xFF8B9BB0),
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().clickable { onDismiss() },
            )
        }
    }
}

@Composable
private fun StatRow(label: String, value: Int, color: Color) {
    val animated by animateFloatAsState(
        targetValue = (value.coerceIn(0, 100)) / 100f,
        animationSpec = tween(700),
        label = label,
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = Color(0xFFC6D3E1), fontSize = 11.sp, modifier = Modifier.size(width = 88.dp, height = 16.dp))
        Box(
            Modifier
                .weight(1f)
                .height(7.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color(0xFF1F2A37)),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(animated)
                    .height(7.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(color),
            )
        }
        Text(" $value", color = Color(0xFFC6D3E1), fontSize = 11.sp)
    }
}

@Composable
fun AddFab(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = Color(0xFF34D399),
        shadowElevation = 6.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                Icons.Filled.Add,
                contentDescription = "Добавить",
                tint = Color(0xFF05221A),
                modifier = Modifier.size(20.dp),
            )
            Text("Предложить", color = Color(0xFF05221A), fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        }
    }
}
