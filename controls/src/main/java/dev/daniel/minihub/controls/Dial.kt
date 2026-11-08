package dev.daniel.minihub.controls

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** One choice on a [Dial]. [marked] draws a ring around it (e.g. the current selection). */
@Immutable
data class DialItem(val icon: String, val label: String, val accent: Color, val marked: Boolean = false)

/**
 * Center button + items on a ring or arc.
 *  - Press & hold the button, slide toward an item (selection by angle, haptic tick), release → [onSelect].
 *    Release over the button cancels.
 *  - Quick tap → the dial stays open; tap an item (or anywhere else / the button to close).
 *
 * The dial fills [modifier]; the button sits at [anchor] (inset by [anchorPadding]) and everything
 * else is drawn around it, so e.g. `anchor = Alignment.BottomCenter, startAngle = -90f, sweepAngle = 180f`
 * gives a half circle above a button at the bottom of the screen.
 *
 * @param startAngle where the items start, degrees: 0 = up, clockwise (-90 = left, 90 = right)
 * @param sweepAngle 360 = full circle; less = an arc from [startAngle] clockwise
 * @param radius ring radius; grows up to [maxRadius] when the items wouldn't fit, then items shrink
 * @param idleHint label while closed (null = none); [openHint] while open and nothing is highlighted
 */
@Composable
fun Dial(
    items: List<DialItem>,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    anchor: Alignment = Alignment.Center,
    anchorPadding: PaddingValues = PaddingValues(0.dp),
    startAngle: Float = 0f,
    sweepAngle: Float = 360f,
    radius: Dp = 120.dp,
    maxRadius: Dp = 170.dp,
    buttonSize: Dp = 88.dp,
    buttonColor: Color = MaterialTheme.colorScheme.primary,
    idleHint: String? = null,
    openHint: String? = null,
    labelDistance: Dp = 80.dp,
    button: @Composable BoxScope.() -> Unit = {
        Text("●", color = MaterialTheme.colorScheme.onPrimary, fontSize = 24.sp)
    },
) {
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current

    val geometry = remember(items.size, startAngle, sweepAngle) { DialGeometry(items.size, startAngle, sweepAngle) }
    val ringDp = geometry.radius(radius.value, maxRadius.value, ItemSpacing.value).dp
    val itemDp = geometry.itemSize(ringDp.value, ItemSize.value, MinItemSize.value, ItemGap.value).dp
    val ringPx = with(density) { ringDp.toPx() }
    val buttonR = with(density) { buttonSize.toPx() / 2f }

    var open by remember { mutableStateOf(false) }
    var sticky by remember { mutableStateOf(false) }
    var highlighted by remember { mutableIntStateOf(-1) }
    // Button center in the dial's own coordinates; known after the first layout.
    var center by remember { mutableStateOf<Offset?>(null) }
    val coords = remember { Coords() }
    val currentItems by rememberUpdatedState(items)
    val currentOnSelect by rememberUpdatedState(onSelect)
    val currentGeometry by rememberUpdatedState(geometry)

    fun updateCenter() {
        val box = coords.box ?: return
        val b = coords.button ?: return
        if (!box.isAttached || !b.isAttached) return
        center = box.localPositionOf(b, Offset(b.size.width / 2f, b.size.height / 2f))
    }

    fun close() {
        open = false
        sticky = false
        highlighted = -1
    }

    fun hit(v: Offset): Int =
        if (v.getDistance() < buttonR) -1 else currentGeometry.indexAt(DialGeometry.angleOf(v.x, v.y))

    fun highlight(i: Int) {
        if (i == highlighted) return
        highlighted = i
        if (i >= 0) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    fun commit(i: Int) {
        close()
        if (i in currentItems.indices) currentOnSelect(i)
    }

    /** Follows one pointer until it lifts; [origin] = button center in the pointer's coordinates. */
    suspend fun PointerInputScope.track(
        origin: () -> Offset?,
        onDown: (Offset) -> Boolean,
        onLift: (moved: Boolean, longPress: Boolean) -> Unit,
    ) {
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            down.consume()
            if (!onDown(down.position)) {
                waitForUpOrCancellation()
                return@awaitEachGesture
            }
            var moved = false
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id }
                if (change == null) { // gesture lost (e.g. system took it over)
                    close()
                    break
                }
                if (!change.pressed) {
                    change.consume()
                    onLift(moved, change.uptimeMillis - down.uptimeMillis >= QuickTapMs)
                    break
                }
                if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) moved = true
                origin()?.let { highlight(hit(change.position - it)) }
                change.consume()
            }
        }
    }

    Box(modifier.fillMaxSize().onGloballyPositioned { coords.box = it; updateCenter() }) {
        val c = center
        if (open) {
            // Scrim; while the dial stays open after a tap it also takes the taps on items / outside.
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.15f))
                    .then(
                        if (sticky) Modifier.pointerInput(Unit) {
                            val origin = { center }
                            track(
                                origin,
                                onDown = { pos -> origin()?.let { highlight(hit(pos - it)) }; true },
                                onLift = { _, _ -> commit(highlighted) },
                            )
                        } else Modifier,
                    ),
            )
            if (c != null) {
                items.forEachIndexed { i, item ->
                    val p = c + polar(geometry.angleOf(i), ringPx)
                    val hl = i == highlighted
                    val size = if (hl) itemDp + 16.dp else itemDp
                    Box(
                        Modifier
                            .centeredAt(p)
                            .size(size)
                            .clip(CircleShape)
                            .background(if (hl) item.accent else MaterialTheme.colorScheme.surfaceContainerHighest)
                            .then(if (item.marked && !hl) Modifier.border(3.dp, item.accent, CircleShape) else Modifier),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(item.icon, fontSize = (size.value * 0.42f).sp)
                    }
                }
            }
        }
        val label = items.getOrNull(highlighted)?.label ?: if (open) openHint else idleHint
        if (label != null && c != null) {
            val p = c + polar(geometry.labelAngle, ringPx + with(density) { labelDistance.toPx() })
            Text(label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.centeredAt(p))
        }
        Box(
            Modifier
                .align(anchor)
                .padding(anchorPadding)
                .onGloballyPositioned { coords.button = it; updateCenter() }
                .size(buttonSize)
                .clip(CircleShape)
                .background(buttonColor)
                .pointerInput(Unit) {
                    val origin = { Offset(size.width / 2f, size.height / 2f) }
                    track(
                        origin,
                        onDown = {
                            if (sticky) {
                                close() // tap on the button while open = close
                                false
                            } else {
                                open = true
                                highlighted = -1
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                true
                            }
                        },
                        onLift = { moved, longPress ->
                            val idx = highlighted
                            when {
                                idx >= 0 -> commit(idx)
                                !moved && !longPress -> sticky = true
                                else -> close()
                            }
                        },
                    )
                },
            contentAlignment = Alignment.Center,
            content = button,
        )
    }
}

private val ItemSize = 68.dp
private val MinItemSize = 40.dp
private val ItemSpacing = 76.dp
private val ItemGap = 8.dp
private const val QuickTapMs = 300L

private fun polar(angleDeg: Float, r: Float): Offset {
    val a = Math.toRadians(angleDeg.toDouble())
    return Offset((r * sin(a)).toFloat(), (-r * cos(a)).toFloat())
}

/** Places the content with its center at [p] (parent coordinates), taking no space itself. */
private fun Modifier.centeredAt(p: Offset) = layout { measurable, constraints ->
    val pl = measurable.measure(constraints.copy(minWidth = 0, minHeight = 0))
    layout(0, 0) { pl.place((p.x - pl.width / 2f).roundToInt(), (p.y - pl.height / 2f).roundToInt()) }
}

private class Coords {
    var box: LayoutCoordinates? = null
    var button: LayoutCoordinates? = null
}
