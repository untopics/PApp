package dev.daniel.minihub.apps.calories

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

private val OverColor = Color(0xFFE53935)

/** Simple bar chart; bars above [limit] are red, limit drawn as a red line. */
@Composable
fun CalorieChart(
    dates: List<LocalDate>,
    values: List<Int>,
    limit: Int?,
    barColor: Color,
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val axisColor = MaterialTheme.colorScheme.outlineVariant
    val locale = Locale.getDefault()

    Canvas(modifier) {
        val n = values.size
        if (n == 0) return@Canvas
        val labelH = 20f * density
        val topPad = 18f * density
        val chartH = size.height - labelH - topPad
        val baseY = topPad + chartH
        val maxV = maxOf(values.maxOrNull() ?: 0, limit ?: 0).coerceAtLeast(1) * 1.1f
        val slot = size.width / n
        val barW = slot * 0.7f

        drawLine(axisColor, Offset(0f, baseY), Offset(size.width, baseY), strokeWidth = density)

        values.forEachIndexed { i, v ->
            val h = chartH * v / maxV
            val x = i * slot + (slot - barW) / 2f
            if (v > 0) {
                val color = if (limit != null && v > limit) OverColor else barColor
                drawRect(color, Offset(x, baseY - h), Size(barW, h))
                if (n <= 7) {
                    val t = measurer.measure(AnnotatedString(v.toString()), labelStyle)
                    drawText(t, topLeft = Offset(x + barW / 2f - t.size.width / 2f, baseY - h - t.size.height))
                }
            }
            val d = dates[i]
            val showLabel = n <= 7 || d.dayOfMonth % 5 == 0 || i == n - 1
            if (showLabel) {
                val text = if (n <= 7) d.dayOfWeek.getDisplayName(TextStyle.SHORT, locale) else d.dayOfMonth.toString()
                val t = measurer.measure(AnnotatedString(text), labelStyle)
                drawText(t, topLeft = Offset(i * slot + slot / 2f - t.size.width / 2f, baseY + 4f * density))
            }
        }

        if (limit != null) {
            val y = baseY - chartH * limit / maxV
            drawLine(OverColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 2f * density)
        }
    }
}
