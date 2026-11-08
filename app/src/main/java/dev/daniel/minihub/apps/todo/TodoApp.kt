package dev.daniel.minihub.apps.todo

import android.Manifest
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.daniel.minihub.controls.Dial
import dev.daniel.minihub.controls.DialItem
import dev.daniel.minihub.controls.SliderDirection
import dev.daniel.minihub.controls.ValueScale
import dev.daniel.minihub.controls.ValueSlider
import dev.daniel.minihub.core.AppContext
import dev.daniel.minihub.core.ArmedButton
import dev.daniel.minihub.core.DatePickScreen
import dev.daniel.minihub.core.MiniApp
import dev.daniel.minihub.core.MiniTopBar
import dev.daniel.minihub.core.SwipeToDelete
import dev.daniel.minihub.core.rememberToday
import java.time.LocalDate
import kotlin.math.roundToInt

object TodoApp : MiniApp {
    override val id = "todo"
    override val name = "Todo"
    override val icon = "✅"
    override val accent = Color(0xFF5C6BC0)

    override fun onHubStart(context: Context) = TodoNotify.schedule(context)

    @Composable
    override fun Content(ctx: AppContext) {
        val repo = remember { TodoRepo(ctx.store) }
        val context = ctx.context
        val askPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
            TodoNotify.schedule(context)
        }
        TodoScreen(
            repo = repo,
            onBack = ctx.onBack,
            onReminderSaved = {
                if (TodoNotify.hasPermission(context)) TodoNotify.schedule(context)
                else askPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            },
            onEntryGone = { TodoNotify.cancel(context, it) },
        )
    }
}

private const val NEW_ENTRY = "new"
private const val NEW_LIST = "newList"
private const val EDIT_LIST = "editList"

/** Due slider: 0–60 days, finer near the start (first two weeks take about half the track). */
private val DueScale = ValueScale.power(0f, MAX_DUE_DAYS.toFloat(), 1.6f)

@Composable
private fun TodoScreen(repo: TodoRepo, onBack: () -> Unit, onReminderSaved: () -> Unit, onEntryGone: (String) -> Unit) {
    val today = rememberToday()
    var editing by rememberSaveable { mutableStateOf<String?>(null) }
    // Bumped by "Save + next" so the form starts fresh (and focuses the title again).
    var round by rememberSaveable { mutableIntStateOf(0) }
    var listMode by rememberSaveable { mutableStateOf<String?>(null) }

    val editId = editing
    val mode = listMode
    when {
        editId != null -> {
            BackHandler { editing = null }
            val existing = repo.entry(editId)
            key(editId, round) {
                EntryForm(
                    existing = existing,
                    listName = repo.current.name,
                    today = today,
                    onSave = { title, due, remind, next ->
                        repo.upsert(existing?.id, title, due, remind)
                        if (remind != null) onReminderSaved()
                        if (next) round++ else editing = null
                    },
                    onDelete = {
                        existing?.let { repo.delete(it.id); onEntryGone(it.id) }
                        editing = null
                    },
                    onCancel = { editing = null },
                )
            }
        }
        mode != null -> {
            BackHandler { listMode = null }
            val existing = if (mode == EDIT_LIST) repo.current else null
            ListForm(
                existing = existing,
                entryCount = existing?.let { repo.entriesOf(it.id).size } ?: 0,
                canDelete = repo.lists.size > 1,
                onSave = { name ->
                    if (existing == null) repo.addList(name) else repo.renameList(existing.id, name)
                    listMode = null
                },
                onDelete = {
                    existing?.let { l -> repo.entriesOf(l.id).forEach { onEntryGone(it.id) }; repo.deleteList(l.id) }
                    listMode = null
                },
                onCancel = { listMode = null },
            )
        }
        else -> ListScreen(
            repo = repo,
            today = today,
            onBack = onBack,
            onAdd = { editing = NEW_ENTRY },
            onOpen = { editing = it },
            onNewList = { listMode = NEW_LIST },
            onEditList = { listMode = EDIT_LIST },
            onEntryGone = onEntryGone,
        )
    }
}

// ── List: entries, "+" bottom right, list dial bottom center ────────

@Composable
private fun ListScreen(
    repo: TodoRepo,
    today: LocalDate,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onOpen: (String) -> Unit,
    onNewList: () -> Unit,
    onEditList: () -> Unit,
    onEntryGone: (String) -> Unit,
) {
    val list = repo.current
    val all = repo.entriesOf(list.id)
    val open = all.openSorted()
    val done = all.doneSorted()
    var showDone by rememberSaveable { mutableStateOf(false) }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
            MiniTopBar(list.name, onBack) {
                TextButton(onClick = onEditList) { Text("✎", fontSize = 20.sp) }
            }
            if (all.isEmpty()) {
                Text(
                    "Nothing here yet.\nTap + to add a todo.",
                    modifier = Modifier.fillMaxWidth().padding(top = 48.dp),
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 128.dp)) {
                items(open, key = { it.id }) { e ->
                    EntryRow(e, today, onOpen = { onOpen(e.id) }, onToggle = {
                        repo.toggleDone(e.id)
                        onEntryGone(e.id)
                    }, onDelete = { repo.delete(e.id); onEntryGone(e.id) })
                }
                if (done.isNotEmpty()) {
                    item(key = "doneHeader") {
                        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            TextButton(onClick = { showDone = !showDone }) {
                                Text("${if (showDone) "▾" else "▸"} Done (${done.size})")
                            }
                            Spacer(Modifier.weight(1f))
                            if (showDone) ArmedButton("Clear done", { repo.clearDone(list.id) })
                        }
                    }
                    if (showDone) {
                        items(done, key = { it.id }) { e ->
                            EntryRow(e, today, onOpen = { onOpen(e.id) }, onToggle = { repo.toggleDone(e.id) }, onDelete = { repo.delete(e.id) })
                        }
                    }
                }
            }
        }

        Button(
            onClick = onAdd,
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 20.dp).size(72.dp),
            shape = CircleShape,
            contentPadding = PaddingValues(0.dp),
        ) { Text("+", fontSize = 34.sp) }

        // Lists dial: half circle above a button at the bottom (the hub's ring sits mid-screen).
        val accent = TodoApp.accent
        val newColor = MaterialTheme.colorScheme.tertiary
        val dialItems = repo.lists.map { DialItem(listIcon(it.name), it.name, accent, marked = it.id == list.id) } +
            DialItem("+", "New list", newColor)
        val ids = repo.lists.map { it.id }
        Dial(
            items = dialItems,
            onSelect = { i -> if (i < ids.size) repo.select(ids[i]) else onNewList() },
            anchor = Alignment.BottomCenter,
            anchorPadding = PaddingValues(bottom = 16.dp),
            startAngle = -90f,
            sweepAngle = 180f,
            radius = 120.dp,
            maxRadius = 150.dp,
            buttonSize = 80.dp,
            buttonColor = accent,
            openHint = "Slide to a list",
            labelDistance = 64.dp,
        ) {
            Text(listIcon(list.name), color = Color.White, fontSize = 26.sp)
        }
    }
}

@Composable
private fun EntryRow(e: TodoEntry, today: LocalDate, onOpen: () -> Unit, onToggle: () -> Unit, onDelete: () -> Unit) {
    val dim = MaterialTheme.colorScheme.onSurfaceVariant
    SwipeToDelete(onDelete) {
        Row(
            Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).clickable(onClick = onOpen).padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = e.done, onCheckedChange = { onToggle() })
            Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
                Text(
                    e.title,
                    fontSize = 17.sp,
                    maxLines = 3,
                    color = if (e.done) dim else MaterialTheme.colorScheme.onSurface,
                    textDecoration = if (e.done) TextDecoration.LineThrough else null,
                )
                if (!e.done && (e.due != null || e.remind != null)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        e.due?.let { due ->
                            val d = daysUntil(today, due)
                            Text(
                                dueText(due, today),
                                fontSize = 13.sp,
                                color = when {
                                    d < 0 -> MaterialTheme.colorScheme.error
                                    d <= 1 -> MaterialTheme.colorScheme.primary
                                    else -> dim
                                },
                            )
                        }
                        e.remind?.let { r ->
                            Text("🔔 ${dayText(r, today)}", fontSize = 13.sp, color = if (r.isBefore(today)) dim.copy(alpha = 0.6f) else dim)
                        }
                    }
                }
            }
        }
    }
}

// ── Add / edit entry: title focused, due slider + picker, reminder ───

/** Reminder shortcuts: (days, label) relative to the due date, or to today without one. */
private val BeforeDue = listOf(0 to "On due day", 1 to "1 day before", 3 to "3 days before", 7 to "1 week before")
private val FromToday = listOf(0 to "Today", 1 to "Tomorrow", 3 to "In 3 days", 7 to "In a week")

@Composable
private fun EntryForm(
    existing: TodoEntry?,
    listName: String,
    today: LocalDate,
    onSave: (title: String, due: LocalDate?, remind: LocalDate?, next: Boolean) -> Unit,
    onDelete: () -> Unit,
    onCancel: () -> Unit,
) {
    var title by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        val t = existing?.title.orEmpty()
        mutableStateOf(TextFieldValue(t, TextRange(t.length)))
    }
    var dueS by rememberSaveable { mutableStateOf(existing?.due?.toString()) }
    var remindS by rememberSaveable { mutableStateOf(existing?.remind?.toString()) }
    // Set while the reminder is "N days before due": it then moves along when the due date changes.
    var remindOffset by rememberSaveable {
        val d = existing?.due
        val r = existing?.remind
        mutableStateOf(if (d != null && r != null && !r.isAfter(d)) daysUntil(r, d).toInt() else null)
    }
    var picking by rememberSaveable { mutableStateOf<String?>(null) }
    var focused by rememberSaveable { mutableStateOf(existing != null) }

    val due = dueS?.let(LocalDate::parse)
    val remind = remindS?.let(LocalDate::parse)

    fun setDue(d: LocalDate?) {
        dueS = d?.toString()
        val off = remindOffset
        if (d == null) remindOffset = null
        else if (off != null) {
            val r = d.minusDays(off.toLong())
            remindS = (if (r.isBefore(today)) today else r).toString()
        }
    }

    fun setRemind(r: LocalDate?, offset: Int?) {
        remindS = r?.toString()
        remindOffset = offset
    }

    val p = picking
    if (p != null) {
        BackHandler { picking = null }
        DatePickScreen(if (p == "due") "Due date" else "Reminder", if (p == "due") due else remind ?: due, onPick = {
            if (p == "due") setDue(it) else setRemind(it, null)
            picking = null
        }, onCancel = { picking = null })
        return
    }

    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        if (!focused) {
            focus.requestFocus()
            keyboard?.show()
            focused = true
        }
    }
    val canSave = title.text.isNotBlank()
    val save = { next: Boolean -> if (canSave) onSave(title.text, due, remind, next) }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        MiniTopBar(if (existing == null) "Add to $listName" else "Edit todo", onCancel)
        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text("What needs doing?") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { save(false) }),
            modifier = Modifier.fillMaxWidth().focusRequester(focus),
        )
        // Controls sit at the bottom: right above the keyboard while typing, so the due slider
        // opens upwards over the free space.
        Spacer(Modifier.weight(1f))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Label("Reminder")
            Spacer(Modifier.weight(1f))
            Text(remind?.let { "🔔 ${dayText(it, today)}" } ?: "none", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (due != null) {
                for ((days, label) in BeforeDue) {
                    val date = due.minusDays(days.toLong())
                    QuickChip(label, selected = remind == date, enabled = !date.isBefore(today)) { setRemind(date, days) }
                }
            } else {
                for ((days, label) in FromToday) {
                    val date = today.plusDays(days.toLong())
                    QuickChip(label, selected = remind == date) { setRemind(date, null) }
                }
            }
            QuickChip("📅", selected = false) { picking = "remind" }
            if (remind != null) QuickChip("✕", selected = false) { setRemind(null, null) }
        }

        Label("Due")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            ValueSlider(
                text = due?.let { dueText(it, today) } ?: "Slide ↑ for due date",
                scale = DueScale,
                step = 1f,
                displayStep = 1f,
                format = { sliderDayText(it.roundToInt(), today) },
                onRelease = { setDue(today.plusDays(it.roundToInt().toLong())) },
                direction = SliderDirection.Up,
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.weight(1f).height(64.dp),
            )
            OutlinedButton(onClick = { picking = "due" }, modifier = Modifier.size(64.dp), contentPadding = PaddingValues(0.dp)) {
                Text("📅", fontSize = 22.sp)
            }
            if (due != null) {
                OutlinedButton(onClick = { setDue(null) }, modifier = Modifier.size(64.dp), contentPadding = PaddingValues(0.dp)) {
                    Text("✕", fontSize = 20.sp)
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (existing == null) {
                FilledTonalButton(onClick = { save(true) }, enabled = canSave, modifier = Modifier.weight(1f).height(56.dp)) {
                    Text("Save + next")
                }
            } else {
                ArmedButton("Delete", onDelete, Modifier.weight(1f).height(56.dp))
            }
            Button(onClick = { save(false) }, enabled = canSave, modifier = Modifier.weight(1f).height(56.dp)) { Text("Save") }
        }
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun QuickChip(label: String, selected: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    val m = Modifier.height(40.dp)
    val pad = PaddingValues(horizontal = 12.dp)
    if (selected) Button(onClick = onClick, enabled = enabled, modifier = m, contentPadding = pad) { Text(label) }
    else OutlinedButton(onClick = onClick, enabled = enabled, modifier = m, contentPadding = pad) { Text(label) }
}

// ── New / rename list ───────────────────────────────────────────────

@Composable
private fun ListForm(
    existing: TodoList?,
    entryCount: Int,
    canDelete: Boolean,
    onSave: (String) -> Unit,
    onDelete: () -> Unit,
    onCancel: () -> Unit,
) {
    var name by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        val t = existing?.name.orEmpty()
        mutableStateOf(TextFieldValue(t, TextRange(0, t.length)))
    }
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) {
        focus.requestFocus()
        keyboard?.show()
    }
    val canSave = name.text.isNotBlank()
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        MiniTopBar(if (existing == null) "New list" else "Edit list", onCancel)
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("List name") },
            supportingText = { Text("Its first character (an emoji works too) is shown on the dial") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (canSave) onSave(name.text) }),
            modifier = Modifier.fillMaxWidth().focusRequester(focus),
        )
        Button(onClick = { onSave(name.text) }, enabled = canSave, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Save") }
        if (existing != null && canDelete) {
            Spacer(Modifier.height(8.dp))
            ArmedButton(
                if (entryCount == 0) "Delete list" else "Delete list and its $entryCount todos",
                onDelete,
                Modifier.fillMaxWidth().height(52.dp),
            )
        }
    }
}

@Composable
private fun Label(text: String) {
    Text(text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
}
