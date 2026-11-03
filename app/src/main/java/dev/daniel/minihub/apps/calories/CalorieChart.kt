package dev.daniel.minihub.apps.calories

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import java.time.LocalDate
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

private val OverColor = Color(0xFFE53935)

/**
 * One chart, two stacked bands sharing the day axis:
 * - top (~35 %): weight. Logged days as dots, the 7-day average as a line, [weightGoal] dashed.
 *   The band zooms to the visible weights; a goal far outside is pinned to the edge with an arrow.
 *   Hidden when no weight falls into the period.
 * - bottom: kcal bars, red above [limit], limit as a red line.
 */
@Composable
fun CalorieChart(
    dates: List<LocalDate>,
    values: List<Int>,
    limit: Int?,
    barColor: Color,
    weights: List<Float?>,
    weightAvg: List<Float?>,
    weightGoal: Float?,
    weightColor: Color,
    modifier: Modifier = Modifier,
) {
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val weightLabelStyle = labelStyle.copy(color = weightColor)
    val axisColor = MaterialTheme.colorScheme.outlineVariant
    val locale = Locale.getDefault()

    Canvas(modifier) {
        val n = values.size
        val dp = density
        val labelH = 20f * dp
        // Too small to draw anything sensible (e.g. squeezed by the keyboard).
        if (n == 0 || size.height < labelH + 40f * dp || size.width <= 0f) return@Canvas
        val slot = size.width / n
        val x = { i: Int -> i * slot + slot / 2f }
        val week = n <= 7

        // The weight band needs ~40dp to be readable; below that it's dropped and the bars get the space.
        val hasWeight = (weights.any { it != null } || weightAvg.any { it != null }) &&
            (size.height - labelH) * 0.35f >= 40f * dp
        val weightH = if (hasWeight) (size.height - labelH) * 0.35f else 0f
        val bandGap = if (hasWeight) 10f * dp else 0f

        // --- Weight band ---
        if (hasWeight) {
            val top = 14f * dp
            val bottom = weightH - 4f * dp
            val shown = (weights + weightAvg).filterNotNull()
            var lo = shown.min()
            var hi = shown.max()
            // Pull a nearby goal into the band; a far one would flatten the line, so it gets pinned instead.
            if (weightGoal != null) {
                val span = max(hi - lo, 1f)
                if (weightGoal >= lo - span && weightGoal <= hi + span) {
                    lo = min(lo, weightGoal)
                    hi = max(hi, weightGoal)
                }
            }
            val pad = max((hi - lo) * 0.15f, 0.3f)
            lo -= pad
            hi += pad
            val y = { kg: Float -> bottom - (bottom - top) * (kg - lo) / (hi - lo) }

            if (weightGoal != null) {
                val gy = y(weightGoal).clamp(top, bottom)
                val pinned = weightGoal < lo || weightGoal > hi
                drawLine(
                    weightColor.copy(alpha = 0.7f), Offset(0f, gy), Offset(size.width, gy),
                    strokeWidth = 1.5f * dp,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f * dp, 5f * dp)),
                )
                val arrow = when {
                    !pinned -> ""
                    weightGoal < lo -> " ↓"
                    else -> " ↑"
                }
                val t = measure(measurer, "goal %.1f$arrow".format(weightGoal), weightLabelStyle)
                val ty = if (gy - t.size.height < 0f) gy + 2f * dp else gy - t.size.height
                drawText(t, topLeft = Offset(size.width - t.size.width, ty))
            }

            // 7-day average: continuous line through days that have one.
            val avgPath = Path()
            var started = false
            weightAvg.forEachIndexed { i, v ->
                if (v == null) { started = false; return@forEachIndexed }
                if (!started) avgPath.moveTo(x(i), y(v)) else avgPath.lineTo(x(i), y(v))
                started = true
            }
            drawPath(avgPath, weightColor, style = Stroke(2.5f * dp, cap = StrokeCap.Round, join = StrokeJoin.Round))

            // Daily weigh-ins as dots; lighter than the average so the trend reads first.
            val r = if (week) 3.5f * dp else 2f * dp
            weights.forEachIndexed { i, v -> if (v != null) drawCircle(weightColor.copy(alpha = 0.45f), r, Offset(x(i), y(v))) }

            // Value of the latest average in the period.
            val last = weightAvg.indexOfLast { it != null }
            if (last >= 0) {
                val v = weightAvg[last]!!
                val t = measure(measurer, "%.1f".format(v), weightLabelStyle)
                val tx = (x(last) - t.size.width / 2f).clamp(0f, size.width - t.size.width)
                drawText(t, topLeft = Offset(tx, (y(v) - t.size.height - 4f * dp).coerceAtLeast(0f)))
            }
            drawText(measure(measurer, "kg · Ø7d", weightLabelStyle), topLeft = Offset.Zero)
            drawLine(axisColor, Offset(0f, weightH + bandGap / 2f), Offset(size.width, weightH + bandGap / 2f), strokeWidth = dp * 0.5f)
        }

        // --- kcal band ---
        val topPad = weightH + bandGap + 18f * dp
        val chartH = size.height - labelH - topPad
        val baseY = topPad + chartH
        val maxV = maxOf(values.maxOrNull() ?: 0, limit ?: 0).coerceAtLeast(1) * 1.1f
        val barW = slot * 0.7f

        drawLine(axisColor, Offset(0f, baseY), Offset(size.width, baseY), strokeWidth = dp)

        values.forEachIndexed { i, v ->
            val h = chartH * v / maxV
            val bx = i * slot + (slot - barW) / 2f
            if (v > 0) {
                val color = if (limit != null && v > limit) OverColor else barColor
                drawRect(color, Offset(bx, baseY - h), Size(barW, h))
                if (week) {
                    val t = measure(measurer, v.toString(), labelStyle)
                    drawText(t, topLeft = Offset(bx + barW / 2f - t.size.width / 2f, baseY - h - t.size.height))
                }
            }
            val d = dates[i]
            if (week || d.dayOfMonth % 5 == 0 || i == n - 1) {
                val text = if (week) d.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, locale) else d.dayOfMonth.toString()
                val t = measure(measurer, text, labelStyle)
                drawText(t, topLeft = Offset(x(i) - t.size.width / 2f, baseY + 4f * dp))
            }
        }

        if (limit != null) {
            val ly = baseY - chartH * limit / maxV
            drawLine(OverColor, Offset(0f, ly), Offset(size.width, ly), strokeWidth = 2f * dp)
        }
    }
}

private fun DrawScope.measure(measurer: TextMeasurer, text: String, style: TextStyle): TextLayoutResult =
    measurer.measure(AnnotatedString(text), style)

/** Like coerceIn, but never throws: an empty range (max < min) resolves to [min]. */
private fun Float.clamp(min: Float, max: Float): Float = if (max < min) min else coerceIn(min, max)
