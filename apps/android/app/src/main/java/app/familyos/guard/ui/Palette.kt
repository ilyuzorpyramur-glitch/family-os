package app.familyos.guard.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import app.familyos.guard.BuildConfig

private fun hex(value: String): Color = runCatching {
    Color(android.graphics.Color.parseColor(value))
}.getOrDefault(Color(0xFF3B82F6))

/** Акцент приходит из flavor: синий у ребёнка, оранжевый у родителя. */
val accent: Color = hex(BuildConfig.ACCENT)

/** Тёмный оттенок акцента — для мягких подложек и градиентов. */
val accentDeep: Color = hex(BuildConfig.ACCENT_DARK)

/** Второй акцент — для «сияния» на фоне и подсветки. */
val accent2: Color = runCatching {
    Color(android.graphics.Color.parseColor(BuildConfig.ACCENT_2))
}.getOrDefault(Color(0xFF22D3EE))

/** Полупрозрачная подложка под активный элемент. */
val accentSoft: Color = accent.copy(alpha = 0.18f)

/** Мягкая заливка карточки-акцента. */
val accentCard: Color = accentDeep.copy(alpha = 0.55f)

val bg: Color = Color(0xFF080A0F)

/** Карточки полупрозрачные: сквозь них видно сияние фона. */
val cardBg: Color = Color(0xD9161B24)
val cardBgAlt: Color = Color(0xC71E2430)
val cardBorder: Color = Color(0x1FFFFFFF)
val textPrimary: Color = Color(0xFFE9EEF5)
val textMuted: Color = Color(0xFF8B94A6)
val danger: Color = Color(0xFFF87171)
val warning: Color = Color(0xFFFBBF24)

val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(32.dp),
)
