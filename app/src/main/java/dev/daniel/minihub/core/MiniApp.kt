package dev.daniel.minihub.core

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import java.time.LocalDate

/**
 * A self-contained mini application. Each app owns exactly one [JsonStore]
 * (its own file) and never touches another app's data directly.
 */
interface MiniApp {
    val id: String
    val name: String
    /** Emoji or short glyph shown by launchers. */
    val icon: String
    val accent: Color

    @Composable
    fun Content(ctx: AppContext)

    /**
     * Read-only data API for other mini apps (future use).
     * Return null for metrics this app doesn't expose.
     */
    fun readMetric(store: JsonStore, metric: String, date: LocalDate): Int? = null
}

/** A way of choosing a mini app (grid, ring, ...). */
interface Launcher {
    val id: String
    val name: String
    val icon: String

    @Composable
    fun Content(apps: List<MiniApp>, onOpen: (MiniApp) -> Unit, modifier: Modifier)
}

class AppContext(
    val context: Context,
    val store: JsonStore,
    val onBack: () -> Unit,
) {
    /** Ask another mini app for a value it exposes via [MiniApp.readMetric]. */
    fun query(appId: String, metric: String, date: LocalDate): Int? {
        val app = Registry.apps.firstOrNull { it.id == appId } ?: return null
        return app.readMetric(JsonStore(context, app.id), metric, date)
    }
}
