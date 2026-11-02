package dev.daniel.minihub.apps.lawn

import android.Manifest
import android.content.Context
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import dev.daniel.minihub.core.AppContext
import dev.daniel.minihub.core.ArmedButton
import dev.daniel.minihub.core.ChoiceRow
import dev.daniel.minihub.core.MiniApp
import dev.daniel.minihub.core.MiniTopBar
import dev.daniel.minihub.core.StepRow
import dev.daniel.minihub.core.rememberToday
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.roundToInt

object LawnApp : MiniApp {
    override val id = LawnStore.APP_ID
    override val name = "Lawn"
    override val icon = "🌱"
    override val accent = Color(0xFF66BB6A)

    override fun onHubStart(context: Context) {
        LawnCheck.schedule(context)
    }

    @Composable
    override fun Content(ctx: AppContext) = LawnScreen(ctx)
}

private val DATE = DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault())
private val DATE_Y = DateTimeFormatter.ofPattern("EEE d MMM yyyy", Locale.getDefault())
private val TIME = DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())

private fun relDays(d: LocalDate, today: LocalDate): String = when (val n = ChronoUnit.DAYS.between(today, d)) {
    0L -> "today"
    1L -> "tomorrow"
    -1L -> "yesterday"
    in 2L..Long.MAX_VALUE -> "in $n d"
    else -> "${-n} d ago"
}

@Composable
private fun LawnScreen(ctx: AppContext) {
    val today = rememberToday()
    var data by remember { mutableStateOf(LawnStore.load(ctx.store)) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun edit(f: (LawnData) -> LawnData) {
        data = LawnStore.update(ctx.store, f)
    }

    fun refresh() {
        if (loading) return
        loading = true
        scope.launch {
            val err = withContext(Dispatchers.IO) { runCatching { LawnCheck.run(ctx.context) }.getOrElse { it.message } }
            data = LawnStore.load(ctx.store)
            error = err
            loading = false
        }
    }

    // Fetch whenever the app is opened; re-read on resume (the background job may have written).
    LaunchedEffect(Unit) { refresh() }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { data = LawnStore.load(ctx.store) }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) refresh()
    }

    fun toggleNotify(s: Stage) {
        val adding = s !in data.notify
        edit { it.copy(notify = if (adding) it.notify + s else it.notify - s) }
        // Background scheduling must never take the UI down; worst case the check runs on next start.
        runCatching { LawnCheck.updatePeriodic(ctx.context) }
            .onFailure { error = "background check not scheduled (${it.javaClass.simpleName})" }
        if (!adding) return
        if (!LawnCheck.canNotify(ctx.context) && Build.VERSION.SDK_INT >= 33) {
            runCatching { permission.launch(Manifest.permission.POST_NOTIFICATIONS) }
        } else refresh()
    }

    val stage = data.view
    val range = forecastRange(stage, today, data.sowDate)
    val days = data.days.filter { !it.date.isBefore(today) }.take(range)

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        MiniTopBar("Lawn", ctx.onBack) {
            TextButton(onClick = { refresh() }, enabled = !loading) { Text(if (loading) "…" else "↻", fontSize = 20.sp) }
        }
        ForecastStrip(data.days, days, stage, today)
        StatusLine(data.place, days, error, loading)
        Spacer(Modifier.height(8.dp))
        ChoiceRow(Stage.entries, stage, { it.label }, { s -> edit { it.copy(view = s) } })
        Spacer(Modifier.height(8.dp))
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { WeatherWarnings(data.days, days, stage, today) }
            when (stage) {
                Stage.BUILD -> item { BuildView(data, today, ::edit) }
                Stage.DEVELOP, Stage.ESTABLISHED -> item { CareView(data, stage, today) }
            }
            item {
                NotifySection(
                    data.notify,
                    blocked = data.notify.isNotEmpty() && !LawnCheck.canNotify(ctx.context),
                    onToggle = ::toggleNotify,
                    onAllow = { if (Build.VERSION.SDK_INT >= 33) permission.launch(Manifest.permission.POST_NOTIFICATIONS) },
                )
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

// ---------------------------------------------------------------- forecast

@Composable
private fun ForecastStrip(all: List<ForecastDay>, shown: List<ForecastDay>, stage: Stage, today: LocalDate) {
    if (shown.isEmpty()) {
        Text("No forecast yet – needs internet.", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 12.dp))
        return
    }
    val locale = Locale.getDefault()
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        for (d in shown) {
            val events = eventsFor(all, all.indexOf(d), stage)
            val critical = events.any { it != Wx.DRY }
            val shape = RoundedCornerShape(10.dp)
            Column(
                Modifier
                    .width(54.dp)
                    .border(
                        if (critical) BorderStroke(2.dp, MaterialTheme.colorScheme.error)
                        else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        shape,
                    )
                    .padding(vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                val dow = if (d.date == today) "Today" else d.date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale)
                Text(dow, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                Text("${d.date.dayOfMonth}.", style = MaterialTheme.typography.labelSmall)
                Text(weatherEmoji(d.code), fontSize = 20.sp)
                Text("${d.tMax.roundToInt()}°", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                Text("${d.tMin.roundToInt()}°", style = MaterialTheme.typography.bodySmall)
                Text(if (d.rain >= 0.5) "${d.rain.roundToInt()} mm" else " ", style = MaterialTheme.typography.labelSmall)
                Text(if (d.gust >= 40) "💨${d.gust.roundToInt()}" else " ", style = MaterialTheme.typography.labelSmall)
                Text(events.joinToString("") { it.symbol }.ifEmpty { " " }, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun StatusLine(place: Place, shown: List<ForecastDay>, error: String?, loading: Boolean) {
    val oldest = shown.minByOrNull { it.fetchedAt }?.fetchedAt
    val updated = oldest?.let {
        val hours = ChronoUnit.HOURS.between(it, Instant.now())
        val at = it.atZone(ZoneId.systemDefault())
        if (hours < 24) "updated ${at.format(TIME)}" else "⚠ data from ${at.format(DATE)} – outdated"
    }
    val text = listOfNotNull(
        place.name,
        when {
            loading -> "updating…"
            error != null -> "update failed ($error)"
            else -> updated
        },
    ).joinToString(" · ")
    Text(text, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp))
}

/** First occurrence of each critical event in the shown range, with advice for this stage. */
@Composable
private fun WeatherWarnings(all: List<ForecastDay>, shown: List<ForecastDay>, stage: Stage, today: LocalDate) {
    val firsts = linkedMapOf<Wx, ForecastDay>()
    for (d in shown) for (e in eventsFor(all, all.indexOf(d), stage)) firsts.putIfAbsent(e, d)
    if (firsts.isEmpty()) return
    Card {
        Text("Watch out", style = MaterialTheme.typography.titleSmall)
        for ((kind, d) in firsts) {
            val v = when (kind) {
                Wx.FROST -> "${d.tMin.roundToInt()}°"
                Wx.HEAT -> "${d.tMax.roundToInt()}°"
                Wx.WIND -> "${d.gust.roundToInt()} km/h"
                Wx.STORM_RAIN -> "${d.rain.roundToInt()} mm"
                Wx.DRY -> "dry"
            }
            Text("${kind.symbol} ${kind.label} ${relDays(d.date, today)} ($v)", fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 6.dp))
            Text(LawnRules.advice(kind, stage), style = MaterialTheme.typography.bodySmall)
        }
    }
}

// ---------------------------------------------------------------- new lawn

@Composable
private fun BuildView(data: LawnData, today: LocalDate, edit: ((LawnData) -> LawnData) -> Unit) {
    val sow = data.sowDate
    val (suggested, window) = remember(today) { suggestSowing(today) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Card {
            Text("Sowing date", style = MaterialTheme.typography.titleSmall)
            if (sow == null) {
                Text("Suggested: ${suggested.format(DATE_Y)} – ${window.label}, ${relDays(suggested, today)}.",
                    modifier = Modifier.padding(vertical = 4.dp))
                Button(onClick = { edit { it.copy(sowDate = suggested) } }, modifier = Modifier.fillMaxWidth()) {
                    Text("Plan for ${suggested.format(DATE)}")
                }
            } else {
                Text("${sow.format(DATE_Y)} (${relDays(sow, today)})", fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(vertical = 4.dp))
                if (windowContaining(sow) == null) {
                    Text("Outside the recommended windows – germination will be slow or need lots of water.",
                        color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                Spacer(Modifier.height(6.dp))
                StepRow(listOf(-7, -1, 1, 7), { n -> edit { it.copy(sowDate = sow.plusDays(n.toLong())) } }, height = 44.dp)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (sow != suggested && sow.isAfter(today)) {
                        OutlinedButton(onClick = { edit { it.copy(sowDate = suggested) } }, modifier = Modifier.weight(1f)) {
                            Text("Use ${suggested.format(DATE)}")
                        }
                    }
                    ArmedButton("Clear plan", { edit { it.copy(sowDate = null, done = emptySet()) } }, Modifier.weight(1f))
                }
            }
            Text(
                "Windows here: mid-Apr – mid-May (soil ≥ 10 °C) and late Aug – mid-Sep (best: warm soil, autumn rain, fewer weeds). " +
                    "Summer sowing needs watering several times a day; from Oct, germination stalls.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
            data.days.firstOrNull { it.date == today }?.soil?.let { s ->
                val ok = s >= 10
                Text("Soil (6 cm) today: ${s.roundToInt()} °C – " + if (ok) "warm enough to germinate" else "too cold, seed needs ≥ 10 °C",
                    style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
            }
        }

        if (sow != null && !today.isBefore(sow.minusDays(1))) {
            Card {
                Text("Watering", style = MaterialTheme.typography.titleSmall)
                Text(waterAdvice(Stage.BUILD, data.days, today, sow))
            }
        }

        if (sow != null) {
            val organise = BUILD_STEPS.filter { it.prep && it.id !in data.done && !it.due(sow).isAfter(today.plusDays(14)) }
            if (organise.isNotEmpty()) {
                Card {
                    Text("Organise now", style = MaterialTheme.typography.titleSmall)
                    for (s in organise) Text("• ${s.title} – due ${relDays(s.due(sow), today)}", modifier = Modifier.padding(top = 2.dp))
                }
            }
        }

        Card {
            Text("Steps", style = MaterialTheme.typography.titleSmall)
            for (s in BUILD_STEPS) {
                val done = s.id in data.done
                val due = sow?.let { s.due(it) }
                val overdue = due != null && !done && due.isBefore(today) && s.offset <= 0
                Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    Checkbox(checked = done, onCheckedChange = { c ->
                        edit { it.copy(done = if (c) it.done + s.id else it.done - s.id) }
                    })
                    Column(Modifier.weight(1f).padding(top = 10.dp)) {
                        val whenText = due?.let { "${it.format(DATE)} · ${relDays(it, today)}" } ?: offsetText(s.offset)
                        Text(
                            whenText + if (s.prep) " · organise" else "",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(s.title, fontWeight = FontWeight.Bold,
                            color = if (done) MaterialTheme.colorScheme.onSurfaceVariant else Color.Unspecified)
                        if (!done) Text(s.detail, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

private fun offsetText(o: Int): String = when {
    o == 0 -> "sowing day"
    o < 0 && o % 7 == 0 -> "${-o / 7} weeks before"
    o < 0 -> "${-o} days before"
    o % 7 == 0 -> "${o / 7} weeks after"
    else -> "${o} days after"
}

// ---------------------------------------------------------------- developing / established

@Composable
private fun CareView(data: LawnData, stage: Stage, today: LocalDate) {
    val sow = data.sowDate?.takeIf { !it.isAfter(today) }
    val tasks = SEASON_TASKS.filter { stage in it.stages }
    val active = tasks.mapNotNull { t -> t.occurrence(today)?.let { t to it.second } }
    val upcoming = tasks.filter { it.occurrence(today) == null }.sortedBy { it.nextStart(today) }.take(4)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if (stage == Stage.DEVELOP) {
            Text(
                sow?.let { "Sown ${it.format(DATE_Y)} – ${ChronoUnit.DAYS.between(it, today)} days old." }
                    ?: "Set a sowing date under 'New lawn' for age-based advice.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Card {
            Text("Watering", style = MaterialTheme.typography.titleSmall)
            Text(waterAdvice(stage, data.days, today, sow))
        }
        Card {
            Text("Now", style = MaterialTheme.typography.titleSmall)
            if (active.isEmpty()) Text("Nothing scheduled right now.")
            for ((t, end) in active) {
                val young = t.tooYoung(sow, today)
                Text(t.title + if (young) " (not yet – lawn too young)" else "", fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 6.dp))
                Text("until ${end.format(DATE)}", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(t.detail, style = MaterialTheme.typography.bodySmall)
            }
        }
        Card {
            Text("Coming up", style = MaterialTheme.typography.titleSmall)
            for (t in upcoming) {
                val start = t.nextStart(today)
                Text("${start.format(DATE)} · ${relDays(start, today)}", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 6.dp))
                Text(t.title, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ---------------------------------------------------------------- notifications

@Composable
private fun NotifySection(enabled: Set<Stage>, blocked: Boolean, onToggle: (Stage) -> Unit, onAllow: () -> Unit) {
    Card {
        Text("Notifications", style = MaterialTheme.typography.titleSmall)
        Text("For any combination of stages – independent of the view above.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (s in Stage.entries) {
                val m = Modifier.weight(1f).height(40.dp)
                val label = (if (s in enabled) "✓ " else "") + s.label
                if (s in enabled) {
                    Button(onClick = { onToggle(s) }, modifier = m, contentPadding = PaddingValues(0.dp)) { Text(label, fontSize = 12.sp, textAlign = TextAlign.Center) }
                } else {
                    OutlinedButton(onClick = { onToggle(s) }, modifier = m, contentPadding = PaddingValues(0.dp)) { Text(label, fontSize = 12.sp, textAlign = TextAlign.Center) }
                }
            }
        }
        Text(
            "New lawn: plan steps, germination watering, frost, heat, wind, heavy rain around the sowing date. " +
                "Developing: frost (spring/autumn), heat, dry spells, seasonal tasks. " +
                "Established: heat, dry spells, seasonal tasks. Max. one notification per event per day.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp),
        )
        if (blocked) {
            Text("Notifications are blocked for Mini Hub – tap to allow.",
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 6.dp).clickable { onAllow() })
        }
    }
}

@Composable
private fun Card(content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
            .padding(12.dp),
        content = content,
    )
}
