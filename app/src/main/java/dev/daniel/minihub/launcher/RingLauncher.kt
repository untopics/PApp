package dev.daniel.minihub.launcher

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import dev.daniel.minihub.controls.Dial
import dev.daniel.minihub.controls.DialItem
import dev.daniel.minihub.core.Launcher
import dev.daniel.minihub.core.MiniApp

/**
 * Center button + ring menu (full-circle [Dial] in the middle of the screen).
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
        val items = remember(apps) { apps.map { DialItem(it.icon, it.name, it.accent) } }
        Dial(
            items = items,
            onSelect = { onOpen(apps[it]) },
            modifier = modifier,
            idleHint = "Hold & slide · or tap",
            openHint = "Slide or tap an app",
        )
    }
}
