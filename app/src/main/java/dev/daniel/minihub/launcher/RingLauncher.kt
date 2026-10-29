package dev.daniel.minihub.launcher

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.daniel.minihub.core.Launcher
import dev.daniel.minihub.core.MiniApp
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Center button + ring menu.
 *  - Press & hold the button, slide onto an app, release → opens it. Release on the center cancels.
 *  - Quick tap → ring stays open; tap an app (or anywhere else to close).
 * Selection is by angle, so you don't have to hit the icon exactly.
 */
object RingLauncher : Launcher {
    override val id = "ring"
    override val name = "Ring"
    override val icon = "◎"

    @Composable
    override fun Content(apps: List<MiniApp>, onOpen: (MiniApp) -> Unit, modifier: Modifier) {
        val haptic = LocalHapticFeedback.current
        val density = LocalDensity.current
        val centerR = with(density) { 44.dp.toPx() }
        val ringR = with(density) { 120.dp.toPx() }

        var open by remember { mutableStateOf(false) }
        var sticky by remember { mutableStateOf(false) }
        var highlighted by remember { mutableIntStateOf(-1) }
        val currentApps by rememberUpdatedState(apps)
        val currentOnOpen by rememberUpdatedState(onOpen)

        fun close() {
            open = false
            sticky = false
            highlighted = -1
        }

        Box(
            modifier.fillMaxSize().pointerInput(centerR) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val n = currentApps.size

                    fun hit(p: Offset): Int {
                        if (n == 0) return -1
                        val v = p - center
                        if (v.getDistance() < centerR) return -1
                        var a = atan2(v.x, -v.y) // 0 at top, clockwise
                        if (a < 0f) a += (2 * PI).toFloat()
                        val sector = (2 * PI / n).toFloat()
                        return ((a + sector / 2f) / sector).toInt() % n
                    }

                    val wasSticky = sticky
                    if (!wasSticky && (down.position - center).getDistance() > centerR * 1.2f) {
                        return@awaitEachGesture
                    }
                    down.consume()
                    if (!wasSticky) {
                        open = true
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                    highlighted = if (wasSticky) hit(down.position) else -1
                    var moved = false

                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id }
                        if (change == null) {
                            close()
                            break
                        }
                        if (!change.pressed) {
                            change.consume()
                            val idx = highlighted
                            val quickTap = !moved && change.uptimeMillis - down.uptimeMillis < 300
                            when {
                                idx in 0 until n -> {
                                    close()
                                    currentOnOpen(currentApps[idx])
                                }
                                !wasSticky && quickTap -> sticky = true
                                else -> close()
                            }
                            break
                        }
                        if ((change.position - down.position).getDistance() > viewConfiguration.touchSlop) moved = true
                        val idx = hit(change.position)
                        if (idx != highlighted) {
                            highlighted = idx
                            if (idx >= 0) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        }
                        change.consume()
                    }
                }
            },
        ) {
            if (open) {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.15f)))
                apps.forEachIndexed { i, app ->
                    val a = 2 * PI * i / apps.size
                    val ox = (ringR * sin(a)).roundToInt()
                    val oy = (-ringR * cos(a)).roundToInt()
                    val hl = i == highlighted
                    Box(
                        Modifier
                            .align(Alignment.Center)
                            .offset { IntOffset(ox, oy) }
                            .size(if (hl) 84.dp else 68.dp)
                            .clip(CircleShape)
                            .background(if (hl) app.accent else MaterialTheme.colorScheme.surfaceContainerHighest),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(app.icon, fontSize = if (hl) 36.sp else 28.sp)
                    }
                }
            }
            Box(
                Modifier
                    .align(Alignment.Center)
                    .size(88.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Text("●", color = MaterialTheme.colorScheme.onPrimary, fontSize = 24.sp)
            }
            val label = apps.getOrNull(highlighted)?.name
                ?: if (open) "Slide or tap an app" else "Hold & slide · or tap"
            Text(
                label,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.align(Alignment.Center).offset(y = 200.dp),
            )
        }
    }
}
