package dev.daniel.minihub.apps.traditions

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.daniel.minihub.core.AppContext
import dev.daniel.minihub.core.ArmedButton
import dev.daniel.minihub.core.ChoiceRow
import dev.daniel.minihub.core.MiniApp
import dev.daniel.minihub.core.MiniTopBar
import dev.daniel.minihub.core.rememberToday
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

object TraditionsApp : MiniApp {
    override val id = "traditions"
    override val name = "Traditions"
    override val icon = "🎄"
    override val accent = Color(0xFF26A69A)

    @Composable
    override fun Content(ctx: AppContext) {
        val repo = remember { TraditionsRepo(ctx.store) }
        val askPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
            TraditionsNotify.schedule(ctx.context)
        }
        LaunchedEffect(Unit) {
            TraditionsNotify.schedule(ctx.context)
            if (Build.VERSION.SDK_INT >= 33 &&
                ctx.context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) {
                askPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        TraditionsScreen(repo, ctx.onBack)
    }
}

private val locale: Locale get() = Locale.getDefault()
private val ShortDate = DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault())
private val LongDate = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.getDefault())
private val MonthYear = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())

private const val NEW_ENTRY = "new"

@Composable
private fun TraditionsScreen(repo: TraditionsRepo, onBack: () -> Unit) {
    val today = rememberToday()
    var tab by rememberSaveable { mutableStateOf(0) }
    var openKey by rememberSaveable { mutableStateOf<String?>(null) }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }

    // Re-derived whenever entries, overrides or the optional toggle change (all snapshot state).
    val snapshot = repo.snapshot()
    val planner = remember(snapshot) { snapshot.planner() }

    val editing = editingId
    val key = openKey
    when {
        editing != null -> {
            BackHandler { editingId = null }
            EntryForm(
                existing = repo.entries.firstOrNull { it.id == editing },
                onSave = { cat, name, date ->
                    repo.upsert(editing.takeIf { it != NEW_ENTRY }, cat, name, date)
                    editingId = null
                },
                onCancel = { editingId = null },
            )
        }
        key != null -> {
            BackHandler { openKey = null }
            val occ = remember(planner, key) {
                planner.occurrences(today.minusYears(2), today.plusYears(3)).firstOrNull { it.key == key }
            }
            if (occ == null) {
                LaunchedEffect(key) { openKey = null }
            } else {
                DetailScreen(
                    occ, planner, today, repo,
                    onEdit = { editingId = it },
                    onClose = { openKey = null },
                )
            }
        }
        else -> Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            MiniTopBar("Traditions", onBack) {
                TextButton(onClick = { repo.toggleOptional() }) {
                    Text(if (repo.showOptional) "All" else "Main")
                }
                TextButton(onClick = { editingId = NEW_ENTRY }) { Text("+", fontSize = 24.sp) }
            }
            ChoiceRow(listOf(0, 1), tab, { if (it == 0) "Calendar" else "Upcoming" }, { tab = it })
            Spacer(Modifier.height(8.dp))
            if (tab == 0) CalendarList(planner, today) { openKey = it }
            else UpcomingList(planner, today) { openKey = it }
        }
    }
}

// ── Calendar: every day as a row, entries in short ──────────────────

private sealed interface CalRow {
    data class Month(val first: LocalDate) : CalRow
    data class Day(val date: LocalDate, val items: List<Occurrence>) : CalRow
}

@Composable
private fun CalendarList(planner: Planner, today: LocalDate, onOpen: (String) -> Unit) {
    val start = remember(today) { today.withDayOfMonth(1).minusMonths(1) }
    val end = remember(today) { today.plusMonths(13).withDayOfMonth(1).minusDays(1) }
    val rows = remember(planner, start, end) {
        val byDate = planner.occurrences(start, end).groupBy { it.date }
        val out = ArrayList<CalRow>()
        var d = start
        while (!d.isAfter(end)) {
            if (d.dayOfMonth == 1) out += CalRow.Month(d)
            out += CalRow.Day(d, byDate[d].orEmpty())
            d = d.plusDays(1)
        }
        out
    }
    val todayIndex = remember(rows, today) {
        // Start with the current month header on top, so today is visible with a little context.
        rows.indexOfFirst { it is CalRow.Month && it.first == today.withDayOfMonth(1) }.coerceAtLeast(0)
    }
    val state = rememberLazyListState(initialFirstVisibleItemIndex = todayIndex)
    LazyColumn(Modifier.fillMaxSize(), state = state) {
        items(rows, key = { r ->
            when (r) {
                is CalRow.Month -> "m${r.first}"
                is CalRow.Day -> "d${r.date}"
            }
        }) { r ->
            when (r) {
                is CalRow.Month -> Text(
                    r.first.format(MonthYear),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 4.dp),
                )
                is CalRow.Day -> DayRow(r.date, r.items, r.date == today, onOpen)
            }
        }
    }
}

@Composable
private fun DayRow(date: LocalDate, items: List<Occurrence>, isToday: Boolean, onOpen: (String) -> Unit) {
    val weekend = date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY
    val bg = if (isToday) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
    Column {
        Row(
            Modifier.fillMaxWidth()
                .background(bg, RoundedCornerShape(6.dp))
                .padding(vertical = if (items.isEmpty()) 2.dp else 6.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Row(Modifier.width(64.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    date.dayOfWeek.getDisplayName(TextStyle.SHORT, locale),
                    modifier = Modifier.width(34.dp),
                    fontSize = 13.sp,
                    color = if (weekend) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    date.dayOfMonth.toString(),
                    fontSize = 15.sp,
                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                )
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                for (o in items) {
                    Text(
                        "${o.kind.icon} ${o.name}",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontSize = 15.sp,
                        color = if (o.tier == Tier.OPTIONAL) MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.fillMaxWidth().clickable { onOpen(o.key) }.padding(vertical = 2.dp),
                    )
                }
            }
        }
        if (date.dayOfWeek == DayOfWeek.SUNDAY) HorizontalDivider(Modifier.padding(vertical = 2.dp))
    }
}

// ── Upcoming: only entries, from today ───────────────────────────────

@Composable
private fun UpcomingList(planner: Planner, today: LocalDate, onOpen: (String) -> Unit) {
    val items = remember(planner, today) { planner.occurrences(today, today.plusDays(365)) }
    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        items(items, key = { it.key }) { o ->
            Row(
                Modifier.fillMaxWidth().clickable { onOpen(o.key) }.padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.width(104.dp)) {
                    Text(o.date.format(ShortDate), fontSize = 14.sp)
                    Text(relativeLabel(today, o.date), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Text(
                    "${o.kind.icon} ${o.name}",
                    modifier = Modifier.weight(1f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 16.sp,
                    color = if (o.tier == Tier.OPTIONAL) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

// ── Details ─────────────────────────────────────────────────────────

@Composable
private fun DetailScreen(
    occ: Occurrence,
    planner: Planner,
    today: LocalDate,
    repo: TraditionsRepo,
    onEdit: (String) -> Unit,
    onClose: () -> Unit,
) {
    var picking by remember(occ.key) { mutableStateOf(false) }
    if (picking) {
        BackHandler { picking = false }
        DatePickScreen("Date in ${occ.date.year}", occ.date, onPick = {
            repo.setOverride(occ.key, it)
            picking = false
        }, onCancel = { picking = false })
        return
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        MiniTopBar(occ.name, onClose)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Column {
                Text("${occ.date.format(LongDate)} · ${relativeLabel(today, occ.date)}", fontSize = 16.sp)
                Text(
                    "${occ.kind.icon} ${occ.kind.label}" + (if (occ.entry == null) " · ${occ.tier.label}" else "") +
                        (if (occ.overridden) " · date changed" else ""),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val t = occ.tradition
            val e = occ.entry
            if (t != null) {
                Section("When", t.ruleText)
                Section("What is done", t.what)
                val reminders = planner.remindersFor(occ).sortedByDescending { it.daysBefore }
                if (reminders.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Label("Prep & reminders")
                        for (r in reminders) ReminderLine(occ.date, r, today)
                    }
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Label("Date in ${occ.date.year}")
                    if (t.editable) Text("This date differs per year or institution — set it once known.", fontSize = 13.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { picking = true }) { Text("Change date") }
                        if (occ.overridden) {
                            TextButton(onClick = { repo.setOverride(occ.key, null) }) { Text("Reset to ${t.rule.dateIn(occ.date.year).format(ShortDate)}") }
                        }
                    }
                }
            }
            if (e != null) {
                Section("Name", e.name)
                Section("Date of birth", e.date.format(LongDate))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Label("Reminders")
                    for (r in planner.remindersFor(occ)) ReminderLine(occ.date, r, today)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { onEdit(e.id) }) { Text("Edit") }
                    ArmedButton("Delete", onConfirm = {
                        repo.delete(e.id)
                        onClose()
                    })
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ReminderLine(date: LocalDate, r: Reminder, today: LocalDate) {
    val fire = date.minusDays(r.daysBefore.toLong())
    val past = fire.isBefore(today)
    Row {
        Text(
            fire.format(ShortDate),
            modifier = Modifier.width(96.dp),
            fontSize = 14.sp,
            color = if (past) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
        )
        Text(
            r.text,
            modifier = Modifier.weight(1f),
            fontSize = 14.sp,
            color = if (past) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun Section(title: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Label(title)
        Text(body, fontSize = 15.sp)
    }
}

// ── Add / edit entry (category decides the fields) ──────────────────

@Composable
private fun EntryForm(
    existing: UserEntry?,
    onSave: (Category, String, LocalDate) -> Unit,
    onCancel: () -> Unit,
) {
    var category by rememberSaveable { mutableStateOf(existing?.category ?: Category.BIRTHDAY) }
    var name by rememberSaveable { mutableStateOf(existing?.name ?: "") }
    var date by rememberSaveable { mutableStateOf(existing?.date?.toString()) }
    var picking by remember { mutableStateOf(false) }

    if (picking) {
        BackHandler { picking = false }
        DatePickScreen("Date of birth", date?.let(LocalDate::parse), onPick = {
            date = it.toString()
            picking = false
        }, onCancel = { picking = false })
        return
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        MiniTopBar(if (existing == null) "New entry" else "Edit entry", onCancel)
        Label("Category")
        ChoiceRow(Category.entries.toList(), category, { "${it.kind.icon} ${it.label}" }, { category = it })
        when (category) {
            Category.BIRTHDAY -> {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedButton(onClick = { picking = true }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
                    Text(date?.let { LocalDate.parse(it).format(LongDate) } ?: "Choose date of birth")
                }
            }
        }
        Button(
            onClick = { onSave(category, name, LocalDate.parse(date!!)) },
            enabled = name.isNotBlank() && date != null,
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) { Text("Save") }
    }
}

/** Full-screen picker; kept out of scrolling containers so the year grid gets bounded height. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatePickScreen(title: String, initial: LocalDate?, onPick: (LocalDate) -> Unit, onCancel: () -> Unit) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli(),
    )
    Column(Modifier.fillMaxSize().padding(horizontal = 8.dp)) {
        MiniTopBar(title, onCancel) {
            TextButton(
                onClick = {
                    state.selectedDateMillis?.let { onPick(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                },
                enabled = state.selectedDateMillis != null,
            ) { Text("Save") }
        }
        DatePicker(state = state, modifier = Modifier.weight(1f), title = null)
    }
}
