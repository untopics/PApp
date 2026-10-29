package dev.daniel.minihub.apps.sports

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.daniel.minihub.core.AppContext
import dev.daniel.minihub.core.ArmedButton
import dev.daniel.minihub.core.ChoiceRow
import dev.daniel.minihub.core.JsonStore
import dev.daniel.minihub.core.MiniApp
import dev.daniel.minihub.core.MiniTopBar
import dev.daniel.minihub.core.StepRow
import dev.daniel.minihub.core.rememberToday
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.IsoFields
import java.time.temporal.TemporalAdjusters
import java.util.Locale

object SportsApp : MiniApp {
    override val id = "sports"
    override val name = "Training"
    override val icon = "💪"
    override val accent = Color(0xFF42A5F5)

    /** Exposed metrics: "done:<exerciseId>" and "plan:<exerciseId>" for a date. */
    override fun readMetric(store: JsonStore, metric: String, date: LocalDate): Int? {
        val repo = SportsRepo(store)
        return when {
            metric.startsWith("done:") -> repo.doneFor(date)[metric.removePrefix("done:")] ?: 0
            metric.startsWith("plan:") -> repo.planFor(date)[metric.removePrefix("plan:")] ?: 0
            else -> null
        }
    }

    @Composable
    override fun Content(ctx: AppContext) {
        val repo = remember { SportsRepo(ctx.store) }
        SportsScreen(repo, ctx.onBack)
    }
}

private enum class Tab(val label: String) { TRACK("Track"), PLAN("Plan"), HISTORY("History") }

@Composable
private fun SportsScreen(repo: SportsRepo, onBack: () -> Unit) {
    val today = rememberToday()
    var tab by rememberSaveable { mutableStateOf(Tab.TRACK) }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        MiniTopBar("Training", onBack)
        ChoiceRow(Tab.entries, tab, { it.label }, { tab = it })
        Spacer(Modifier.height(8.dp))
        when (tab) {
            Tab.TRACK -> TrackTab(repo, today)
            Tab.PLAN -> PlanTab(repo, today)
            Tab.HISTORY -> HistoryTab(repo, today)
        }
    }
}

// ---------------------------------------------------------------- Track / Plan

private data class CounterRow(val exercise: Exercise, val value: Int, val target: Int)

@Composable
private fun ColumnScope.TrackTab(repo: SportsRepo, today: LocalDate) {
    val plan = repo.planFor(today)
    val done = repo.doneFor(today)
    val fmt = remember { DateTimeFormatter.ofPattern("EEEE, d MMM", Locale.getDefault()) }
    Text(today.format(fmt), style = MaterialTheme.typography.labelLarge)
    Spacer(Modifier.height(6.dp))
    CounterBoard(
        rows = EXERCISES.map { CounterRow(it, done[it.id] ?: 0, plan[it.id] ?: 0) },
        showProgress = true,
        onStep = { e, d -> repo.addDone(today, e.id, d) },
        onReset = { repo.resetDone(today, it.id) },
        onResetAll = { repo.resetAllDone(today) },
    )
}

@Composable
private fun ColumnScope.PlanTab(repo: SportsRepo, today: LocalDate) {
    var dow by rememberSaveable { mutableIntStateOf(today.dayOfWeek.value) }
    val locale = Locale.getDefault()
    ChoiceRow(
        (1..7).toList(),
        dow,
        { DayOfWeek.of(it).getDisplayName(TextStyle.SHORT, locale).take(2) },
        { dow = it },
    )
    Text(
        "Changes count from today. Past days keep the plan they had.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 6.dp),
    )
    val t = repo.templateFor(dow)
    CounterBoard(
        rows = EXERCISES.map { CounterRow(it, t[it.id] ?: 0, 0) },
        showProgress = false,
        onStep = { e, d -> repo.addTarget(today, dow, e.id, d) },
        onReset = { repo.resetTarget(today, dow, it.id) },
        onResetAll = { repo.resetAllTargets(today, dow) },
        footer = {
            ArmedButton(
                "Copy ${DayOfWeek.of(dow).getDisplayName(TextStyle.FULL, locale)} to all days",
                { repo.copyToAllDays(today, dow) },
                Modifier.fillMaxWidth(),
            )
        },
    )
}

@Composable
private fun ColumnScope.CounterBoard(
    rows: List<CounterRow>,
    showProgress: Boolean,
    onStep: (Exercise, Int) -> Unit,
    onReset: (Exercise) -> Unit,
    onResetAll: () -> Unit,
    footer: @Composable () -> Unit = {},
) {
    var selectedId by rememberSaveable { mutableStateOf(rows.first().exercise.id) }
    val selected = rows.firstOrNull { it.exercise.id == selectedId } ?: rows.first()

    LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items(rows, key = { it.exercise.id }) { r ->
            CounterRowView(r, r.exercise.id == selected.exercise.id, showProgress) { selectedId = r.exercise.id }
        }
        item { footer() }
    }

    Spacer(Modifier.height(8.dp))
    Text(selected.exercise.name, style = MaterialTheme.typography.titleMedium)
    Spacer(Modifier.height(6.dp))
    val steps = if (selected.exercise.kind == Kind.SESSION) listOf(1) else listOf(1, 5, 10, 100)
    StepRow(steps, { onStep(selected.exercise, it) })
    Spacer(Modifier.height(6.dp))
    StepRow(steps.map { -it }, { onStep(selected.exercise, it) })
    Spacer(Modifier.height(6.dp))
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { onReset(selected.exercise) }, modifier = Modifier.weight(1f)) { Text("Reset to 0") }
        ArmedButton("Reset all", onResetAll, Modifier.weight(1f))
    }
    Spacer(Modifier.height(12.dp))
}

@Composable
private fun CounterRowView(r: CounterRow, selected: Boolean, showProgress: Boolean, onClick: () -> Unit) {
    val bg = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh
    val target = r.target
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(r.exercise.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(
                if (showProgress && target > 0) "${r.value} / $target" else "${r.value}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }
        if (showProgress && target > 0) {
            val left = target - r.value
            Text(
                if (left > 0) "$left left" else if (left == 0) "✓ done" else "✓ done (+${-left})",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            LinearProgressIndicator(
                progress = { (r.value.toFloat() / target).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            )
        }
    }
}

// ---------------------------------------------------------------- History

private enum class Period(val label: String) { DAY("Days"), WEEK("Weeks"), MONTH("Months") }

private data class Bucket(val title: String, val plan: Counts, val done: Counts)

@Composable
private fun ColumnScope.HistoryTab(repo: SportsRepo, today: LocalDate) {
    var period by rememberSaveable { mutableStateOf(Period.DAY) }
    ChoiceRow(Period.entries, period, { it.label }, { period = it })
    Text(
        "Planned vs. done. Current week/month counts plan up to today.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 6.dp),
    )
    val versions = repo.versions
    val done = repo.done
    val buckets = remember(period, today, versions, done) { buildBuckets(repo, period, today) }
    LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items(buckets) { b -> BucketCard(b) }
        item { Spacer(Modifier.height(12.dp)) }
    }
}

private fun earlier(a: LocalDate, b: LocalDate): LocalDate = if (a.isBefore(b)) a else b

private fun buildBuckets(repo: SportsRepo, period: Period, today: LocalDate): List<Bucket> {
    val locale = Locale.getDefault()
    val dayFmt = DateTimeFormatter.ofPattern("EEE d MMM", locale)
    val shortFmt = DateTimeFormatter.ofPattern("d MMM", locale)
    val monthFmt = DateTimeFormatter.ofPattern("MMMM yyyy", locale)

    val ranges: List<Triple<String, LocalDate, LocalDate>> = when (period) {
        Period.DAY -> (0 until 14).map {
            val d = today.minusDays(it.toLong())
            val title = when (it) { 0 -> "Today"; 1 -> "Yesterday"; else -> d.format(dayFmt) }
            Triple(title, d, d)
        }
        Period.WEEK -> {
            val start = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            (0 until 8).map {
                val s = start.minusWeeks(it.toLong())
                val e = s.plusDays(6)
                Triple("Week ${s.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)} · ${s.format(shortFmt)} – ${e.format(shortFmt)}", s, earlier(e, today))
            }
        }
        Period.MONTH -> {
            val start = today.withDayOfMonth(1)
            (0 until 6).map {
                val s = start.minusMonths(it.toLong())
                Triple(s.format(monthFmt), s, earlier(s.plusMonths(1).minusDays(1), today))
            }
        }
    }

    return ranges.map { (title, from, to) ->
        val plan = mutableMapOf<String, Int>()
        val done = mutableMapOf<String, Int>()
        var d = from
        while (!d.isAfter(to)) {
            repo.planFor(d).forEach { (k, v) -> plan[k] = (plan[k] ?: 0) + v }
            repo.doneFor(d).forEach { (k, v) -> done[k] = (done[k] ?: 0) + v }
            d = d.plusDays(1)
        }
        Bucket(title, plan, done)
    }
}

@Composable
private fun BucketCard(b: Bucket) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Text(b.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
        val shown = EXERCISES.filter { (b.plan[it.id] ?: 0) > 0 || (b.done[it.id] ?: 0) > 0 }
        if (shown.isEmpty()) {
            Text("—", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        for (e in shown) {
            val p = b.plan[e.id] ?: 0
            val d = b.done[e.id] ?: 0
            Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                Text(e.name, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                Text(if (p > 0) "$d / $p" else "$d", style = MaterialTheme.typography.bodyMedium)
                Text(
                    if (p > 0) "  ${d * 100 / p}%" else "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (p > 0 && d >= p) Color(0xFF43A047) else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
