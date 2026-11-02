package dev.daniel.minihub

import android.content.Context
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.daniel.minihub.core.AppContext
import dev.daniel.minihub.core.CrashLog
import dev.daniel.minihub.core.JsonStore
import dev.daniel.minihub.core.Registry

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        CrashLog.install(this)
        enableEdgeToEdge()
        val start = intent?.getStringExtra(EXTRA_MINIAPP)
        Registry.apps.forEach { runCatching { it.onHubStart(applicationContext) } }
        setContent { MiniHubTheme { HubRoot(start) } }
    }

    companion object {
        /** Intent extra: id of a mini app to open directly (e.g. from a notification). */
        const val EXTRA_MINIAPP = "miniapp"
    }
}

@Composable
fun MiniHubTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val ctx = LocalContext.current
    val scheme = when {
        Build.VERSION.SDK_INT >= 31 -> if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    MaterialTheme(colorScheme = scheme, content = content)
}

@Composable
private fun HubRoot(startAppId: String?) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("hub", Context.MODE_PRIVATE) }
    var launcherId by remember {
        mutableStateOf(prefs.getString("launcher", null) ?: Registry.launchers.first().id)
    }
    var openAppId by rememberSaveable { mutableStateOf(startAppId) }
    val app = Registry.apps.firstOrNull { it.id == openAppId }

    Surface(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().safeDrawingPadding()) {
            if (app != null) {
                BackHandler { openAppId = null }
                val appCtx = remember(app.id) {
                    AppContext(context.applicationContext, JsonStore(context, app.id)) { openAppId = null }
                }
                app.Content(appCtx)
            } else {
                val launcher = Registry.launchers.firstOrNull { it.id == launcherId } ?: Registry.launchers.first()
                Column(Modifier.fillMaxSize()) {
                    Row(
                        Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Mini Hub", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                        for (l in Registry.launchers) {
                            if (l.id == launcher.id) {
                                FilledTonalButton(onClick = {}) { Text(l.icon, fontSize = 18.sp) }
                            } else {
                                TextButton(onClick = {
                                    launcherId = l.id
                                    prefs.edit().putString("launcher", l.id).apply()
                                }) { Text(l.icon, fontSize = 18.sp) }
                            }
                        }
                    }
                    CrashBanner()
                    launcher.Content(Registry.apps, { openAppId = it.id }, Modifier.weight(1f))
                }
            }
        }
    }
}

/** Shows the last crash (if any) on the home screen: copy the trace, then dismiss. */
@Composable
private fun CrashBanner() {
    val context = LocalContext.current
    var trace by remember { mutableStateOf(CrashLog.read(context)) }
    val text = trace ?: return
    val clipboard = LocalClipboardManager.current
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
            .background(MaterialTheme.colorScheme.errorContainer, RoundedCornerShape(12.dp))
            .padding(12.dp),
    ) {
        Text("Last crash", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onErrorContainer)
        Text(
            text.lines().drop(1).take(3).joinToString("\n"),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onErrorContainer,
            maxLines = 4,
        )
        Row {
            TextButton(onClick = { clipboard.setText(AnnotatedString(text)) }) { Text("Copy") }
            TextButton(onClick = { CrashLog.clear(context); trace = null }) { Text("Dismiss") }
        }
    }
}
