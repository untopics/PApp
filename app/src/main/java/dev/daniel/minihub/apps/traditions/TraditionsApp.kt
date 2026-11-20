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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.daniel.minihub.core.AppContext
import dev.daniel.minihub.core.ArmedButton
import dev.daniel.minihub.core.ChoiceRow
import dev.daniel.minihub.core.DatePickScreen
import dev.daniel.minihub.core.MiniApp
import dev.daniel.minihub.core.MiniTopBar
import dev.daniel.minihub.core.SwipeToDelete
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
internal val ShortDate = DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault())
internal val LongDate = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.getDefault())
internal val DayMonth = DateTimeFormatter.ofPattern("d MMMM", Locale.getDefault())
internal val RowDate = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.getDefault())
private val MonthYear = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())

internal const val NEW = "new"

/** Screen stack; routes are "kind/arg/arg…" strings so they survive in saved state. */
internal class Nav(val push: (String) -> Unit, val pop: () -> Unit, val replace: (String) -> Unit)

@Composable
private fun TraditionsScreen(repo: TraditionsRepo, onBack: () -> Unit) {
    val today = rememberToday()
    var tab by rememberSaveable { mutableStateOf(0) }
    // Back stack, newline-separated routes (see Nav).
    var navState by rememberSaveable { mutableStateOf("") }
    val stack = navState.split('\n').filter { it.isNotEmpty() }
    val nav = Nav(
        push = { r -> navState = (stack + r).joinToString("\n") },
        pop = { navState = stack.dropLast(1).joinToString("\n") },
        replace = { r -> navState = (stack.dropLast(1) + r).joinToString("\n") },
    )
    // Hoisted so the lists keep their scroll position while a detail or form replaces them.
    val calendarState = rememberLazyListState(initialFirstVisibleItemIndex = calendarMonthIndex(today))
    val upcomingState = rememberLazyListState()
    val peopleState = rememberLazyListState()
    var importResult by remember { mutableStateOf<ImportResult?>(null) }
    var importError by remember { mutableStateOf<String?>(null) }
    val resolver = LocalContext.current.contentResolver
    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val bytes = runCatching { resolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
        if (bytes == null) importError = "The file couldn't be opened."
        else importResult = BirthdaysCsv.parse(BirthdaysCsv.decode(bytes))
    }

    // Re-derived whenever entries, persons, overrides or the optional toggle change (all snapshot state).
    val snapshot = repo.snapshot()
    val planner = remember(snapshot) { snapshot.planner() }
    // Gifts and todos of past events get flagged used.
    LaunchedEffect(today, planner) { repo.settle(today) }

    val route = stack.lastOrNull()
    val result = importResult
    if (result != null) {
        BackHandler { importResult = null }
        ImportScreen(result, repo.entries, repo.persons, onImport = {
            repo.import(it)
            importResult = null
            nav.pop()
        }, onCancel = { importResult = null })
        return
    }
    if (route == null) {
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            MiniTopBar("Traditions", onBack) {
                if (tab != 2) TextButton(onClick = { repo.toggleOptional() }) {
                    Text(if (repo.showOptional) "All" else "Main")
                }
                TextButton(onClick = { nav.push(if (tab == 2) "personForm/$NEW" else "entry/$NEW") }) { Text("+", fontSize = 24.sp) }
            }
            ChoiceRow(listOf(0, 1, 2), tab, { listOf("Calendar", "Upcoming", "People")[it] }, { tab = it })
            Spacer(Modifier.height(8.dp))
            when (tab) {
                0 -> CalendarList(planner, today, calendarState) { nav.push("occ/$it") }
                1 -> UpcomingList(planner, today, upcomingState) { nav.push("occ/$it") }
                else -> PeopleList(repo, today, peopleState) { nav.push("person/$it") }
            }
        }
        return
    }
    BackHandler { nav.pop() }
    val parts = route.split('/')
    val arg = parts.getOrElse(1) { "" }
    when (parts[0]) {
        "occ" -> {
            val occ = remember(planner, arg) { planner.find(arg) }
            if (occ == null) LaunchedEffect(route) { nav.pop() }
            else DetailScreen(occ, today, repo, nav)
        }
        "entry" -> EntryForm(
            existing = repo.entries.firstOrNull { it.id == arg },
            importError = importError,
            onSave = { cat, name, date, yearKnown ->
                repo.upsertEntry(arg.takeIf { it != NEW }, cat, name, date, yearKnown)
                nav.pop()
            },
            onImport = {
                importError = null
                pickFile.launch(arrayOf("*/*"))
            },
            onCancel = nav.pop,
        )
        "person" -> {
            val person = repo.person(arg)
            if (person == null) LaunchedEffect(route) { nav.pop() }
            else PersonScreen(person, repo, today, nav)
        }
        "personForm" -> PersonForm(
            existing = repo.person(arg),
            onSave = { name, date, yearKnown ->
                val id = repo.upsertPerson(arg.takeIf { it != NEW }, name, date, yearKnown)
                if (arg == NEW) nav.replace("person/$id") else nav.pop()
            },
            onCancel = nav.pop,
        )
        // item/<personId>/<itemId|new>/<kind>/<eventKey|->
        "item" -> {
            val person = repo.person(arg)
            if (person == null) LaunchedEffect(route) { nav.pop() }
            else {
                val itemId = parts.getOrElse(2) { NEW }
                val kind = ItemKind.byId(parts.getOrElse(3) { "gift" })
                val event = parts.getOrElse(4) { "-" }.takeIf { it != "-" }?.let { planner.find(it) }
                ItemForm(person, itemId, kind, event, repo, today, nav)
            }
        }
        "pickPerson" -> {
            val occ = remember(planner, arg) { planner.find(arg) }
            if (occ == null) LaunchedEffect(route) { nav.pop() }
            else PickPersonScreen(occ, repo, nav)
        }
        // pickIdeas/<eventKey>/<personId>
        "pickIdeas" -> {
            val occ = remember(planner, arg) { planner.find(arg) }
            val person = repo.person(parts.getOrElse(2) { "" })
            if (occ == null || person == null) LaunchedEffect(route) { nav.pop() }
            else PickIdeasScreen(occ, person, repo, nav)
        }
        else -> LaunchedEffect(route) { nav.pop() }
    }
}

// ── Calendar: every day as a row, entries in short ──────────────────

private sealed interface CalRow {
    data class Month(val first: LocalDate) : CalRow
    data class Day(val date: LocalDate, val items: List<Occurrence>) : CalRow
}

private fun calendarStart(today: LocalDate): LocalDate = today.withDayOfMonth(1).minusMonths(1)

/** Row index of the current month's header: the previous month's header plus its days. */
private fun calendarMonthIndex(today: LocalDate): Int = 1 + calendarStart(today).lengthOfMonth()

@Composable
private fun CalendarList(planner: Planner, today: LocalDate, state: LazyListState, onOpen: (String) -> Unit) {
    val start = remember(today) { calendarStart(today) }
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
private fun UpcomingList(planner: Planner, today: LocalDate, state: LazyListState, onOpen: (String) -> Unit) {
    val items = remember(planner, today) { planner.occurrences(today, today.plusDays(365)) }
    LazyColumn(Modifier.fillMaxSize(), state = state, verticalArrangement = Arrangement.spacedBy(2.dp)) {
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
private fun DetailScreen(occ: Occurrence, today: LocalDate, repo: TraditionsRepo, nav: Nav) {
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
        MiniTopBar(occ.name, nav.pop)
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Column {
                Text("${occ.date.format(LongDate)} · ${relativeLabel(today, occ.date)}", fontSize = 16.sp)
                Text(
                    "${occ.kind.icon} ${occ.kind.label}" + (if (occ.tradition != null) " · ${occ.tier.label}" else "") +
                        (if (occ.overridden) " · date changed" else ""),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val t = occ.tradition
            val e = occ.entry
            val p = occ.person
            if (t != null) {
                Section("When", t.ruleText)
                Section("What is done", t.what)
                val reminders = t.prep.sortedByDescending { it.daysBefore }
                if (reminders.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Label("Prep & reminders")
                        for (r in reminders) ReminderLine(occ.date, r.daysBefore, r.text, today)
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
            if (p != null) {
                val age = if (p.yearKnown && p.birthDate != null) " · turns ${occ.date.year - p.birthDate.year}" else ""
                Section("Date of birth", personDate(p) + age)
                ReminderEditor(p.id, p.reminders != null, p.effectiveReminders(), occ.date, today, repo) { p.reminderText(it) }
            }
            if (e != null) {
                Section("Name", e.name)
                Section(e.category.dateLabel, entryDate(e))
                ReminderEditor(e.id, e.reminders != null, e.effectiveReminders(), occ.date, today, repo) { e.reminderText(it) }
                if (e.presents.isNotEmpty()) PresentList(e, repo)
            }
            EventPeople(occ, repo, nav)
            if (p != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { nav.push("person/${p.id}") }) { Text("Open ${p.name}") }
                }
            }
            if (e != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { nav.push("entry/${e.id}") }) { Text("Edit") }
                    ArmedButton("Delete", onConfirm = {
                        repo.deleteEntry(e.id)
                        nav.pop()
                    })
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

// ── Legacy present ideas: tap = strike through, swipe = delete ─────

/** Legacy present ideas on anniversary entries (v1); new ideas live on people. */
@Composable
private fun PresentList(entry: UserEntry, repo: TraditionsRepo) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Label("Present ideas (old)")
        for (p in entry.presents) {
            key(p.id) { PresentRow(p, onToggle = { repo.togglePresent(entry.id, p.id) }, onDelete = { repo.deletePresent(entry.id, p.id) }) }
        }
    }
}

@Composable
private fun PresentRow(p: Present, onToggle: () -> Unit, onDelete: () -> Unit) {
    SwipeToDelete(onDelete) {
        Text(
            p.text,
            fontSize = 15.sp,
            textDecoration = if (p.done) TextDecoration.LineThrough else null,
            color = if (p.done) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .clickable(onClick = onToggle)
                .padding(vertical = 10.dp, horizontal = 4.dp),
        )
    }
}

// ── Reminders per entry: defaults until edited, swipe = delete ──────

@Composable
internal fun ReminderEditor(
    ownerId: String,
    edited: Boolean,
    list: List<EntryReminder>,
    date: LocalDate,
    today: LocalDate,
    repo: TraditionsRepo,
    text: (EntryReminder) -> String,
) {
    var days by rememberSaveable(ownerId) { mutableStateOf("") }
    var note by rememberSaveable(ownerId) { mutableStateOf("") }
    val dayCount = days.toIntOrNull()?.takeIf { it in 0..MAX_REMINDER_DAYS }
    val add = {
        if (dayCount != null) {
            repo.addReminder(ownerId, dayCount, note)
            days = ""
            note = ""
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Label("Reminders")
            Spacer(Modifier.weight(1f))
            if (edited) TextButton(onClick = { repo.resetReminders(ownerId) }) { Text("Use defaults") }
        }
        val sorted = list.sortedByDescending { it.daysBefore }
        if (sorted.isEmpty()) Text("No reminders", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        for (r in sorted) {
            key(r.daysBefore, r.note) {
                SwipeToDelete({ repo.deleteReminder(ownerId, r) }) {
                    Box(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(vertical = 8.dp, horizontal = 4.dp)) {
                        ReminderLine(date, r.daysBefore, text(r), today)
                    }
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = days,
                onValueChange = { v -> days = v.filter(Char::isDigit).take(3) },
                label = { Text("Days") },
                singleLine = true,
                isError = days.isNotEmpty() && dayCount == null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                modifier = Modifier.width(88.dp),
            )
            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("Note (optional)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { add() }),
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = add, enabled = dayCount != null) { Text("+", fontSize = 24.sp) }
        }
    }
}

@Composable
internal fun ReminderLine(date: LocalDate, daysBefore: Int, text: String, today: LocalDate) {
    val fire = date.minusDays(daysBefore.toLong())
    val past = fire.isBefore(today)
    Row {
        Text(
            fire.format(ShortDate),
            modifier = Modifier.width(96.dp),
            fontSize = 14.sp,
            color = if (past) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
        )
        Text(
            text,
            modifier = Modifier.weight(1f),
            fontSize = 14.sp,
            color = if (past) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
internal fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
}

@Composable
internal fun Section(title: String, body: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Label(title)
        Text(body, fontSize = 15.sp)
    }
}

// ── Add / edit entry (category decides the fields) ──────────────────

@Composable
private fun EntryForm(
    existing: UserEntry?,
    importError: String?,
    onSave: (Category, String, LocalDate, Boolean) -> Unit,
    onImport: () -> Unit,
    onCancel: () -> Unit,
) {
    var category by rememberSaveable { mutableStateOf(existing?.category ?: Category.BIRTHDAY) }
    var name by rememberSaveable { mutableStateOf(existing?.name ?: "") }
    var date by rememberSaveable { mutableStateOf(existing?.date?.toString()) }
    var yearKnown by rememberSaveable { mutableStateOf(existing?.yearKnown ?: true) }
    var picking by remember { mutableStateOf(false) }

    if (picking) {
        BackHandler { picking = false }
        DatePickScreen(category.dateLabel, date?.let(LocalDate::parse), onPick = {
            date = it.toString()
            picking = false
        }, onCancel = { picking = false })
        return
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        MiniTopBar(if (existing == null) "New entry" else "Edit entry", onCancel)
        if (existing == null) {
            Label("Category")
            ChoiceRow(Category.entries.toList(), category, { "${it.kind.icon} ${it.label}" }, { category = it })
            if (category == Category.BIRTHDAY) {
                Text("Saved as a person — add gift ideas and todos under People.", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedButton(onClick = { picking = true }, modifier = Modifier.fillMaxWidth().height(52.dp)) {
            Text(date?.let { LocalDate.parse(it).format(if (yearKnown) LongDate else DayMonth) } ?: "Choose ${category.dateLabel.lowercase()}")
        }
        Row(Modifier.fillMaxWidth().clickable { yearKnown = !yearKnown }, verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = !yearKnown, onCheckedChange = { yearKnown = !it })
            Text("Year unknown", fontSize = 15.sp)
        }
        Button(
            onClick = { onSave(category, name, LocalDate.parse(date!!), yearKnown) },
            enabled = name.isNotBlank() && date != null,
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) { Text("Save") }
        if (existing == null) {
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = onImport, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Import from Birthdays (CSV)") }
            if (importError != null) Text(importError, color = MaterialTheme.colorScheme.error, fontSize = 14.sp)
        }
    }
}

private fun entryDate(e: UserEntry): String =
    if (e.yearKnown) e.date.format(LongDate) else e.date.format(DayMonth) + " · year unknown"

internal fun personDate(p: Person): String {
    val b = p.birthDate ?: return "—"
    return if (p.yearKnown) b.format(LongDate) else b.format(DayMonth) + " · year unknown"
}

// ── Import preview ──────────────────────────────────────────────────

@Composable
private fun ImportScreen(result: ImportResult, existing: List<UserEntry>, persons: List<Person>, onImport: (List<ImportRow>) -> Unit, onCancel: () -> Unit) {
    val fresh = remember(result, existing.toList(), persons.toList()) { BirthdaysCsv.newRows(result.rows, existing, persons).toSet() }
    val dupes = result.rows.size - fresh.size
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        MiniTopBar("Import Birthdays", onCancel)
        Text(
            buildString {
                append("${fresh.size} new")
                if (dupes > 0) append(" · $dupes already there")
                if (result.unreadableLines.isNotEmpty()) append(" · lines ${result.unreadableLines.joinToString(", ")} unreadable")
            },
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        LazyColumn(Modifier.weight(1f).padding(vertical = 8.dp)) {
            items(result.rows, key = { it.line }) { r ->
                val isNew = r in fresh
                Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "${r.category.kind.icon} ${r.name}",
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontSize = 15.sp,
                        color = if (isNew) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        if (isNew) r.date().format(if (r.year != null) RowDate else DayMonth) else "already there",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Button(
            onClick = { onImport(fresh.toList()) },
            enabled = fresh.isNotEmpty(),
            modifier = Modifier.fillMaxWidth().height(52.dp).padding(bottom = 4.dp),
        ) { Text(if (fresh.isEmpty()) "Nothing new to import" else "Import ${fresh.size}") }
        Spacer(Modifier.height(8.dp))
    }
}
