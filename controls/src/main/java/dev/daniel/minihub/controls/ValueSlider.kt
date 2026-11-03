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
import kotlin.math.abs
import kotlin.math.roundToInt

enum class SliderDirection { Auto, Up, Down }

/** How finger position maps to a value. */
sealed interface SliderMode {
    /** Track pops out to one side; further out = higher value ([ValueScale] from 0 to 1). */
    data object Normal : SliderMode

    /**
     * Opt-in: the track extends both up and down from the button, starting at [start].
     * Sliding up adds `scale(progress)`, sliding down subtracts `downScale(progress)`
     * (defaults to the same scale), so scales should start at 0 here.
     *
     * Each half is `trackLength / 2` long, or up to the screen edge if there is less room.
     * Each half always covers its full range, so with uneven room the two sides differ in
     * sensitivity: placing the control near the middle of the screen is the caller's job.
     * Finger over the button = cancel, as in [Normal].
     */
    data class Bidirectional(val start: Float, val downScale: ValueScale? = null) : SliderMode
}

/**
 * A rounded button that turns into a slider while held.
 *
 * Press it and a track pops out (up or down, whichever side has more room with [SliderDirection.Auto];
 * both ways with [SliderMode.Bidirectional]). Without lifting, slide along the track: [scale] decides
 * range and growth. The value follows the finger at [displayStep] resolution; lifting commits it
 * rounded to [step] ([onRelease]). Lifting while still over the button cancels ([onCancel]).
 *
 * @param text label shown on the button when idle
 * @param step committed values are rounded to multiples of this (0 = no rounding)
 * @param displayStep resolution of the value shown while sliding (default: one decimal finer than [step])
 * @param format how a shown (fine) value is displayed
 * @param direction side the track opens to in [SliderMode.Normal]; ignored for bidirectional
 * @param trackLength total track length (both halves together in bidirectional mode)
 * @param releaseGraceMs the value on screen this long before the lift is committed (absorbs finger roll)
 */
@Composable
fun ValueSlider(
    text: String,
    scale: ValueScale,
    onRelease: (Float) -> Unit,
    modifier: Modifier = Modifier,
    step: Float = 1f,
    displayStep: Float = SliderValueTracker.defaultDisplayStep(step),
    format: (Float) -> String = defaultFormat(displayStep),
    onCancel: () -> Unit = {},
    mode: SliderMode = SliderMode.Normal,
    direction: SliderDirection = SliderDirection.Auto,
    color: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color = MaterialTheme.colorScheme.onPrimary,
    trackLength: Dp = 360.dp,
    releaseGraceMs: Long = 80,
    haptics: Boolean = true,
    enabled: Boolean = true,
) {
    val density = LocalDensity.current
    val view = LocalView.current
    val haptic = LocalHapticFeedback.current
    val insets = WindowInsets.safeDrawing
    val shape = RoundedCornerShape(16.dp)

    val cur = rememberUpdatedState(Params(scale, step, displayStep, mode, onRelease, onCancel, direction, haptics))
    var bounds by remember { mutableStateOf(Rect.Zero) }
    var session by remember { mutableStateOf<Session?>(null) }
    // Signed track position: > 0 up side, < 0 down side (fraction of that half), null = over the button.
    var position by remember { mutableStateOf<Float?>(null) }
    var shown by remember { mutableStateOf<Float?>(null) }

    val active = session != null
    Box(
        modifier
            .clip(shape)
            .background(if (enabled) color else color.copy(alpha = 0.38f))
            .onGloballyPositioned { bounds = it.boundsInWindow() }
            .pointerInput(enabled, trackLength, releaseGraceMs) {
                if (!enabled) return@pointerInput
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    down.consume()
                    val p = cur.value
                    val margin = 8.dp.toPx()
                    val minLen = 48.dp.toPx()
                    val spaceUp = bounds.top - insets.getTop(this) - margin
                    val spaceDown = view.rootView.height - insets.getBottom(this) - bounds.bottom - margin
                    val upLen: Float
                    val downLen: Float
                    when (p.mode) {
                        is SliderMode.Bidirectional -> {
                            val half = trackLength.toPx() / 2f
                            upLen = half.coerceAtMost(spaceUp).coerceAtLeast(minLen)
                            downLen = half.coerceAtMost(spaceDown).coerceAtLeast(minLen)
                        }
                        SliderMode.Normal -> {
                            val up = when (p.direction) {
                                SliderDirection.Up -> true
                                SliderDirection.Down -> false
                                SliderDirection.Auto -> spaceUp >= spaceDown
                            }
                            val len = trackLength.toPx().coerceAtMost(if (up) spaceUp else spaceDown).coerceAtLeast(minLen)
                            upLen = if (up) len else 0f
                            downLen = if (up) 0f else len
                        }
                    }
                    val h = size.height.toFloat()
                    val labelRight = bounds.center.x < view.rootView.width / 2f
                    val tracker = SliderValueTracker(p.step, p.displayStep, releaseGraceMs)

                    // y is relative to the button: the value starts once the finger leaves it.
                    fun positionOf(y: Float): Float? = when {
                        y < 0f && upLen > 0f -> -y / upLen
                        y > h && downLen > 0f -> -(y - h) / downLen
                        else -> null
                    }

                    fun valueOf(pos: Float?): Float? {
                        pos ?: return null
                        return when (val m = p.mode) {
                            SliderMode.Normal -> p.scale.valueAt(abs(pos).coerceAtMost(1f))
                            is SliderMode.Bidirectional ->
                                if (pos >= 0f) m.start + p.scale.valueAt(pos.coerceAtMost(1f))
                                else m.start - (m.downScale ?: p.scale).valueAt((-pos).coerceAtMost(1f))
                        }
                    }

                    position = positionOf(down.position.y)
                    tracker.update(valueOf(position), down.uptimeMillis)
                    shown = tracker.shown
                    session = Session(upLen, downLen, h, labelRight, p.mode is SliderMode.Bidirectional)
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
                            position = positionOf(change.position.y)
                            val before = tracker.committed
                            tracker.update(valueOf(position), change.uptimeMillis)
                            shown = tracker.shown
                            // Tick only when the committed (step) value changes, not on every fine value.
                            if (tracker.committed != before && cur.value.haptics) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            }
                        }
                    } finally {
                        session = null
                        position = null
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
                position = position,
                label = v?.let(format) ?: "✕",
                trackWidth = with(density) { bounds.width.toDp() },
                color = color,
                contentColor = contentColor,
            )
        }
    }
}

private fun defaultFormat(displayStep: Float): (Float) -> String {
    val decimals = SliderValueTracker.decimalsFor(displayStep)
    return { v -> "%.${decimals}f".format(v) }
}

/** Track halves in px; the popup spans up-half + button + down-half (a missing half has length 0). */
@Immutable
private data class Session(
    val upPx: Float,
    val downPx: Float,
    val buttonPx: Float,
    val labelRight: Boolean,
    val bidirectional: Boolean,
)

private class Params(
    val scale: ValueScale,
    val step: Float,
    val displayStep: Float,
    val mode: SliderMode,
    val onRelease: (Float) -> Unit,
    val onCancel: () -> Unit,
    val direction: SliderDirection,
    val haptics: Boolean,
)

private val LabelWidth = 104.dp
private val LabelHeight = 44.dp
private val LabelGap = 8.dp

@Composable
private fun SliderPopup(
    session: Session,
    position: Float?,
    label: String,
    trackWidth: Dp,
    color: Color,
    contentColor: Color,
) {
    val density = LocalDensity.current
    val trackBg = MaterialTheme.colorScheme.surfaceVariant
    val totalPx = session.upPx + session.buttonPx + session.downPx
    val totalDp = with(density) { totalPx.toDp() }
    val provider = remember(session) { SliderPositionProvider(session) }
    val upFill = position?.takeIf { it > 0f }?.coerceAtMost(1f) ?: 0f
    val downFill = position?.takeIf { it < 0f }?.let { (-it).coerceAtMost(1f) } ?: 0f
    val downTop = session.upPx + session.buttonPx
    // Thumb (and label) position in popup px; over the button = button center.
    val thumbPx = when {
        upFill > 0f -> session.upPx * (1f - upFill)
        downFill > 0f -> downTop + session.downPx * downFill
        !session.bidirectional && session.upPx > 0f -> session.upPx
        !session.bidirectional -> downTop
        else -> session.upPx + session.buttonPx / 2f
    }

    Popup(popupPositionProvider = provider, properties = PopupProperties(focusable = false)) {
        Row(Modifier.height(totalDp)) {
            val labelBox = @Composable {
                // Value bubble beside the track, level with the finger so it stays readable.
                val thumbDp = with(density) { thumbPx.toDp() }
                val y = (thumbDp - LabelHeight / 2).coerceIn(0.dp, (totalDp - LabelHeight).coerceAtLeast(0.dp))
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
                            maxLines = 1,
                        )
                    }
                }
            }
            if (!session.labelRight) labelBox()
            // The button area in the middle stays transparent, so the button remains visible.
            Canvas(Modifier.width(trackWidth).fillMaxHeight()) {
                val r = CornerRadius(16.dp.toPx())
                val w = size.width
                val stroke = 4.dp.toPx()
                fun thumb(y: Float) = drawLine(contentColor, Offset(w * 0.2f, y), Offset(w * 0.8f, y), strokeWidth = stroke)
                if (session.upPx > 0f) {
                    drawRoundRect(trackBg, Offset.Zero, Size(w, session.upPx), r)
                    val filled = session.upPx * upFill
                    if (filled > 0f) drawRoundRect(color, Offset(0f, session.upPx - filled), Size(w, filled), r)
                    if (upFill > 0f || !session.bidirectional) thumb(session.upPx - filled)
                }
                if (session.downPx > 0f) {
                    drawRoundRect(trackBg, Offset(0f, downTop), Size(w, session.downPx), r)
                    val filled = session.downPx * downFill
                    if (filled > 0f) drawRoundRect(color, Offset(0f, downTop), Size(w, filled), r)
                    if (downFill > 0f || !session.bidirectional) thumb(downTop + filled)
                }
            }
            if (session.labelRight) labelBox()
        }
    }
}

/** Places the popup so its middle (button-sized) gap lies exactly over the button. */
private class SliderPositionProvider(private val s: Session) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val x = if (s.labelRight) anchorBounds.left else anchorBounds.right - popupContentSize.width
        val y = anchorBounds.top - s.upPx.roundToInt()
        return IntOffset(x, y)
    }
}
