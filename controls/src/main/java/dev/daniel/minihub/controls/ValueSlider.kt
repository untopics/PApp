package dev.daniel.minihub.controls

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import kotlin.math.roundToInt

enum class SliderDirection { Auto, Up, Down }

/**
 * A rounded button that turns into a slider while held.
 *
 * Press it and a track pops out (up or down, whichever side has more room with [SliderDirection.Auto]).
 * Without lifting, slide along the track: the further out, the higher the value ([scale] decides
 * range and growth). Lift to commit: [onRelease] gets the value that was on screen.
 * Lifting while still over the button cancels ([onCancel]).
 *
 * @param text label shown on the button when idle
 * @param step values are rounded to multiples of this (0 = no rounding)
 * @param format how a value is displayed
 */
@Composable
fun ValueSlider(
    text: String,
    scale: ValueScale,
    onRelease: (Float) -> Unit,
    modifier: Modifier = Modifier,
    step: Float = 1f,
    format: (Float) -> String = { v -> if (step >= 1f) v.roundToInt().toString() else "%.1f".format(v) },
    onCancel: () -> Unit = {},
    direction: SliderDirection = SliderDirection.Auto,
    color: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color = MaterialTheme.colorScheme.onPrimary,
    trackLength: Dp = 360.dp,
    hysteresis: Dp = 8.dp,
    releaseGraceMs: Long = 80,
    haptics: Boolean = true,
    enabled: Boolean = true,
) {
    val density = LocalDensity.current
    val view = LocalView.current
    val haptic = LocalHapticFeedback.current
    val insets = WindowInsets.safeDrawing
    val shape = RoundedCornerShape(16.dp)

    val cur = rememberUpdatedState(Params(scale, step, onRelease, onCancel, direction, haptics))
    var bounds by remember { mutableStateOf(Rect.Zero) }
    var session by remember { mutableStateOf<Session?>(null) }
    var progress by remember { mutableFloatStateOf(0f) }
    var shown by remember { mutableStateOf<Float?>(null) }

    val active = session != null
    Box(
        modifier
            .clip(shape)
            .background(if (enabled) color else color.copy(alpha = 0.38f))
            .onGloballyPositioned { bounds = it.boundsInWindow() }
            .pointerInput(enabled, trackLength, hysteresis, releaseGraceMs) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    val p = cur.value
                    val margin = 8.dp.toPx()
                    val spaceUp = bounds.top - insets.getTop(this) - margin
                    val spaceDown = view.rootView.height - insets.getBottom(this) - bounds.bottom - margin
                    val up = when (p.direction) {
                        SliderDirection.Up -> true
                        SliderDirection.Down -> false
                        SliderDirection.Auto -> spaceUp >= spaceDown
                    }
                    val room = if (up) spaceUp else spaceDown
                    val length = trackLength.toPx().coerceAtMost(room).coerceAtLeast(48.dp.toPx())
                    // Distance from finger to the button edge the track grows from:
                    // the value starts once the finger leaves the button.
                    val edge = if (up) down.position.y else size.height - down.position.y
                    val labelRight = bounds.center.x < view.rootView.width / 2f
                    val tracker = SliderValueTracker(p.scale, p.step, hysteresis.toPx() / length, releaseGraceMs)

                    fun progressOf(y: Float) = ((if (up) down.position.y - y else y - down.position.y) - edge) / length

                    progress = progressOf(down.position.y)
                    shown = tracker.update(progress, down.uptimeMillis)
                    session = Session(up, length, labelRight)
                    try {
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id }
                            if (change == null) { // gesture lost (e.g. system took it over)
                                cur.value.onCancel()
                                break
                            }
                            if (!change.pressed) {
                                val v = tracker.release(change.uptimeMillis)
                                if (v != null) cur.value.onRelease(v) else cur.value.onCancel()
                                break
                            }
                            change.consume()
                            progress = progressOf(change.position.y)
                            val before = shown
                            shown = tracker.update(progress, change.uptimeMillis)
                            if (shown != before && cur.value.haptics) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                        }
                    } finally {
                        session = null
                        shown = null
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        val v = shown
        Text(
            if (active && v != null) format(v) else text,
            color = if (active && v == null) contentColor.copy(alpha = 0.6f) else contentColor,
            fontSize = 20.sp,
            fontWeight = if (active && v != null) FontWeight.Bold else FontWeight.Medium,
        )
        // Inside the Box so the popup anchors to the button.
        session?.let { s ->
            SliderPopup(
                session = s,
                progress = progress.coerceIn(0f, 1f),
                label = v?.let(format) ?: "✕",
                trackWidth = with(density) { bounds.width.toDp() },
                color = color,
                contentColor = contentColor,
            )
        }
    }

}

@Immutable
private data class Session(val up: Boolean, val lengthPx: Float, val labelRight: Boolean)

private class Params(
    val scale: ValueScale,
    val step: Float,
    val onRelease: (Float) -> Unit,
    val onCancel: () -> Unit,
    val direction: SliderDirection,
    val haptics: Boolean,
)

private val LabelWidth = 96.dp
private val LabelHeight = 44.dp
private val LabelGap = 8.dp

@Composable
private fun SliderPopup(
    session: Session,
    progress: Float,
    label: String,
    trackWidth: Dp,
    color: Color,
    contentColor: Color,
) {
    val density = LocalDensity.current
    val trackBg = MaterialTheme.colorScheme.surfaceVariant
    val lengthDp = with(density) { session.lengthPx.toDp() }
    val provider = remember(session) { SliderPositionProvider(session) }

    Popup(popupPositionProvider = provider, properties = PopupProperties(focusable = false)) {
        Row(Modifier.height(lengthDp)) {
            val labelBox = @Composable {
                // Value bubble beside the track, level with the finger so it stays readable.
                val thumbY = if (session.up) lengthDp * (1f - progress) else lengthDp * progress
                val y = (thumbY - LabelHeight / 2).coerceIn(0.dp, (lengthDp - LabelHeight).coerceAtLeast(0.dp))
                Box(Modifier.width(LabelWidth + LabelGap).fillMaxHeight()) {
                    Box(
                        Modifier
                            .offset(x = if (session.labelRight) LabelGap else 0.dp, y = y)
                            .width(LabelWidth)
                            .height(LabelHeight)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.inverseSurface),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            label,
                            color = MaterialTheme.colorScheme.inverseOnSurface,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
            if (!session.labelRight) labelBox()
            Canvas(Modifier.width(trackWidth).fillMaxHeight()) {
                val r = CornerRadius(16.dp.toPx())
                drawRoundRect(trackBg, cornerRadius = r)
                val filled = size.height * progress
                val top = if (session.up) size.height - filled else 0f
                if (filled > 0f) drawRoundRect(color, Offset(0f, top), Size(size.width, filled), r)
                val thumbY = if (session.up) top else filled
                drawLine(
                    contentColor,
                    Offset(size.width * 0.2f, thumbY),
                    Offset(size.width * 0.8f, thumbY),
                    strokeWidth = 4.dp.toPx(),
                )
            }
            if (session.labelRight) labelBox()
        }
    }
}

/** Places the track flush against the button edge, on the chosen side. */
private class SliderPositionProvider(private val s: Session) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val x = if (s.labelRight) anchorBounds.left else anchorBounds.right - popupContentSize.width
        val y = if (s.up) anchorBounds.top - popupContentSize.height else anchorBounds.bottom
        return IntOffset(x, y)
    }
}
