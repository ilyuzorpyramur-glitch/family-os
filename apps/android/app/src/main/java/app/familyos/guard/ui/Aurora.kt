package app.familyos.guard.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Фон «северное сияние»: тёмная основа и несколько мягких цветных пятен.
 * Пятна рисуются радиальными градиентами — это дешевле размытия и не требует
 * ни картинок, ни RenderEffect, поэтому работает на всех API начиная с 26.
 */
@Composable
fun AuroraBackground(modifier: Modifier = Modifier, content: @Composable () -> Unit = {}) {
    Box(modifier.fillMaxSize().background(bg)) {
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height

            // Верхнее левое пятно — основной акцент.
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(accent.copy(alpha = 0.52f), Color.Transparent),
                    center = Offset(w * 0.10f, h * 0.02f),
                    radius = w * 0.95f,
                ),
                radius = w * 0.95f,
                center = Offset(w * 0.10f, h * 0.02f),
            )

            // Верхнее правое — второй акцент, даёт перелив.
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(accent2.copy(alpha = 0.30f), Color.Transparent),
                    center = Offset(w * 1.02f, h * 0.14f),
                    radius = w * 0.78f,
                ),
                radius = w * 0.78f,
                center = Offset(w * 1.02f, h * 0.14f),
            )

            // Нижнее свечение — глубина под контентом.
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(accent.copy(alpha = 0.22f), Color.Transparent),
                    center = Offset(w * 0.5f, h * 1.05f),
                    radius = w * 1.1f,
                ),
                radius = w * 1.1f,
                center = Offset(w * 0.5f, h * 1.05f),
            )

            // Среднее пятно — перелив по центру.
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(accent2.copy(alpha = 0.10f), Color.Transparent),
                    center = Offset(w * 0.0f, h * 0.55f),
                    radius = w * 0.7f,
                ),
                radius = w * 0.7f,
                center = Offset(w * 0.0f, h * 0.55f),
            )

            // Легкое затемнение снизу, чтобы нижняя панель читалась.
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(Color.Transparent, Color(0xCC000000)),
                    startY = h * 0.62f,
                    endY = h,
                ),
            )
        }
        content()
    }
}

/** Мягкая подсветка карточки: тонкая светящаяся рамка сверху. */
@Composable
fun glowBorder(): Brush = Brush.verticalGradient(
    colors = listOf(accent.copy(alpha = 0.35f), accent.copy(alpha = 0.04f), Color.Transparent),
)
