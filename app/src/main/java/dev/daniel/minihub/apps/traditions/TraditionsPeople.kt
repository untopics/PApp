package dev.daniel.minihub.apps.traditions

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.daniel.minihub.core.ArmedButton
import dev.daniel.minihub.core.ChoiceRow
import dev.daniel.minihub.core.DatePickScreen
import dev.daniel.minihub.core.MiniTopBar
import java.time.LocalDate

// People with gift ideas and todos, and the people attached to an event.

internal fun nextBirthday(p: Person, today: LocalDate): LocalDate? {
    val b = p.birthDate ?: return null
    val thisYear = anniversaryIn(b, today.year)
    return if (thisYear.isBefore(today)) anniversaryIn(b, today.year + 1) else thisYear
}

private fun birthdayLine(p: Person, today: LocalDate): String {
    val b = p.birthDate ?: return "No birthday"
    val next = nextBirthday(p, today)!!
    val age = if (p.yearKnown) " · turns ${next.year - b.year}" else ""
    return "🎂 ${next.format(ShortDate)} · ${relativeLabel(today, next)}$age"
}

private fun plannedLabel(i: PersonItem): String =
    "📅 ${i.eventName.ifBlank { "Event" }}" + (i.eventDate?.let { " · ${it.format(ShortDate)}" } ?: "")

private fun usedLabel(i: PersonItem): String =
    "✓ " + (if (i.eventName.isNotBlank()) "${i.eventName} · " else "used · ") + (i.usedOn?.format(RowDate) ?: "")

// ── People tab ──────────────────────────────────────────────────────

@Composable
internal fun PeopleList(repo: TraditionsRepo, today: LocalDate, state: LazyListState, onOpen: (String) -> Unit) {
    val people = repo.persons.sortedBy { it.name.lowercase() }
    if (people.isEmpty()) {
        Text(
            "No people yet. Tap + to add someone. With a date of birth they also show up in the calendar as a birthday.",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 16.dp),
        )
        return
    }
    LazyColumn(Modifier.fillMaxSize(), state = state) {
        items(people, key = { it.id }) { p ->
            Row(
                Modifier.fillMaxWidth().clickable { onOpen(p.id) }.padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(p.name, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(birthdayLine(p, today), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                val ideas = p.items.count { it.open }
                val planned = p.items.count { it.planned }
                Text(
                    listOfNotNull(if (ideas > 0) "💡$ideas" else null, if (planned > 0) "📅$planned" else null).joinToString("  "),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ── Person ──────────────────────────────────────────────────────────

@Composable
internal fun PersonScreen(person: Person, repo: TraditionsRepo, today: LocalDate, nav: Nav) {
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        MiniTopBar(person.name, nav.pop) {
            TextButton(onClick = { nav.push("personForm/${person.id}") }) { Text("Edit") }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            val next = nextBirthday(person, today)
            if (next != null) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Section("Date of birth", personDate(person))
                    Text(
                        birthdayLine(person, today),
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable { nav.push("occ/${person.id}@${next.year}") }.padding(vertical = 4.dp),
                    )
                }
                ReminderEditor(person.id, person.reminders != null, person.effectiveReminders(), next, today, repo) { person.reminderText(it) }
            } else {
                Text(
                    "No date of birth. Add one with Edit to show the birthday in the calendar.",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Label("Ideas & todos")
                val open = person.items.filter { it.open }.sortedByDescending { it.created }
                if (open.isEmpty()) Text("Nothing collected yet", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                for (i in open) key(i.id) {
                    ItemRow(i, null, onToggle = { repo.toggleDone(person.id, i.id) }) { nav.push(itemRoute(person.id, i)) }
                }
                AddItemButtons { kind -> nav.push("item/${person.id}/$NEW/${kind.id}/-") }
            }

            val planned = person.items.filter { it.planned }.sortedBy { it.eventDate }
            if (planned.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Label("Planned")
                    for (i in planned) key(i.id) {
                        ItemRow(i, plannedLabel(i), onToggle = { repo.toggleDone(person.id, i.id) }) { nav.push(itemRoute(person.id, i)) }
                    }
                }
            }

            val used = person.items.filter { it.used }.sortedByDescending { it.usedOn }
            if (used.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Label("History")
                    for (i in used) key(i.id) {
                        ItemRow(i, usedLabel(i), onToggle = null) { nav.push(itemRoute(person.id, i)) }
                    }
                }
            }

            ArmedButton("Delete person", onConfirm = {
                repo.deletePerson(person.id)
                nav.pop()
            })
            Spacer(Modifier.height(16.dp))
        }
    }
}

private fun itemRoute(personId: String, i: PersonItem) = "item/$personId/${i.id}/${i.kind.id}/-"

@Composable
private fun AddItemButtons(onAdd: (ItemKind) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (k in ItemKind.entries) TextButton(onClick = { onAdd(k) }) { Text("+ ${k.label}") }
    }
}

/** One gift / todo. [onToggle] null = no checkbox (history). */
@Composable
internal fun ItemRow(item: PersonItem, label: String?, onToggle: (() -> Unit)?, onOpen: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onOpen),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onToggle != null) Checkbox(checked = item.done, onCheckedChange = { onToggle() })
        else Text(item.kind.icon, fontSize = 18.sp, modifier = Modifier.width(48.dp).padding(start = 12.dp))
        Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
            Text(
                (if (onToggle != null) "${item.kind.icon} " else "") + item.title,
                fontSize = 15.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textDecoration = if (item.done && onToggle != null) TextDecoration.LineThrough else null,
                color = if (item.done || item.used) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            )
            val sub = listOfNotNull(
                label,
                item.description.lineSequence().firstOrNull()?.takeIf { it.isNotBlank() },
                if (item.links.isNotEmpty()) "🔗 ${item.links.size}" else null,
            ).joinToString(" · ")
            if (sub.isNotEmpty()) {
                Text(sub, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

// ── Person form ─────────────────────────────────────────────────────

@Composable
internal fun PersonForm(existing: Person?, onSave: (String, LocalDate?, Boolean) -> Unit, onCancel: () -> Unit) {
    var name by rememberSaveable { mutableStateOf(existing?.name ?: "") }
    var date by rememberSaveable { mutableStateOf(existing?.birthDate?.toString()) }
    var yearKnown by rememberSaveable { mutableStateOf(existing?.yearKnown ?: true) }
    var picking by remember { mutableStateOf(false) }
    val focus = remember { FocusRequester() }

    if (picking) {
        BackHandler { picking = false }
        DatePickScreen("Date of birth", date?.let(LocalDate::parse), onPick = {
            date = it.toString()
            picking = false
        }, onCancel = { picking = false })
        return
    }
    if (existing == null) LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        MiniTopBar(if (existing == null) "New person" else "Edit person", onCancel)
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Name") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
            modifier = Modifier.fillMaxWidth().focusRequester(focus),
        )
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { picking = true }, modifier = Modifier.weight(1f).height(52.dp)) {
                Text(date?.let { LocalDate.parse(it).format(if (yearKnown) LongDate else DayMonth) } ?: "Date of birth (optional)")
            }
            if (date != null) TextButton(onClick = { date = null }) { Text("✕") }
        }
        if (date != null) {
            Row(Modifier.fillMaxWidth().clickable { yearKnown = !yearKnown }, verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = !yearKnown, onCheckedChange = { yearKnown = !it })
                Text("Year unknown", fontSize = 15.sp)
            }
            Text(
                "Shows up in the calendar as a birthday, with reminders.",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Button(
            onClick = { onSave(name, date?.let(LocalDate::parse), yearKnown) },
            enabled = name.isNotBlank(),
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) { Text("Save") }
    }
}

// ── Gift / todo form ────────────────────────────────────────────────

/**
 * New or existing item of [person]. A new item opened from an event ([event] != null) is planned
 * for that event and lands on the person as well.
 */
@Composable
internal fun ItemForm(person: Person, itemId: String, kindArg: ItemKind, event: Occurrence?, repo: TraditionsRepo, today: LocalDate, nav: Nav) {
    val existing = person.items.firstOrNull { it.id == itemId }
    val base = remember(itemId) { existing ?: repo.newItem(kindArg, today) }
    var kind by rememberSaveable { mutableStateOf(base.kind) }
    var title by rememberSaveable { mutableStateOf(base.title) }
    var description by rememberSaveable { mutableStateOf(base.description) }
    // Newline-separated, so the list survives in saved state; "" = one empty field.
    var linksText by rememberSaveable { mutableStateOf(base.links.joinToString("\n")) }
    val links = linksText.split('\n')
    val focus = remember { FocusRequester() }
    val uri = LocalUriHandler.current
    if (existing == null) LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    val current = existing ?: base
    fun edited(): PersonItem {
        var out = current.copy(
            kind = kind,
            title = title.trim(),
            description = description.trim(),
            links = links.map { it.trim() }.filter { it.isNotEmpty() },
        )
        if (existing == null && event != null) out = out.copy(eventKey = event.key, eventName = event.name, eventDate = event.date)
        return out
    }
    fun finish(item: PersonItem) {
        repo.saveItem(person.id, item)
        nav.pop()
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        MiniTopBar(if (existing == null) "New ${kind.label.lowercase()} · ${person.name}" else person.name, nav.pop) {
            TextButton(onClick = { finish(edited()) }, enabled = title.isNotBlank()) { Text("Save") }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            ChoiceRow(ItemKind.entries.toList(), kind, { "${it.icon} ${it.label}" }, { kind = it })
            if (existing == null && event != null) {
                Text("For ${event.name} · ${event.date.format(ShortDate)}", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Title") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
            OutlinedTextField(
                value = description,
                onValueChange = { description = it },
                label = { Text("Description (optional)") },
                minLines = 2,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Label("Links")
                links.forEachIndexed { i, link ->
                    key(i) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = link,
                                onValueChange = { v ->
                                    linksText = links.toMutableList().also { it[i] = v.replace("\n", "").trim() }.joinToString("\n")
                                },
                                placeholder = { Text("https://…") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = {}),
                                modifier = Modifier.weight(1f),
                            )
                            if (link.isNotBlank()) {
                                TextButton(onClick = { runCatching { uri.openUri(withScheme(link)) } }) { Text("↗") }
                            }
                            if (links.size > 1 || link.isNotEmpty()) {
                                TextButton(onClick = {
                                    linksText = links.toMutableList().also { it.removeAt(i) }.joinToString("\n")
                                }) { Text("✕") }
                            }
                        }
                    }
                }
                TextButton(onClick = { linksText = if (links.last().isBlank()) linksText else linksText + "\n" }) { Text("+ Link") }
            }

            if (existing != null) {
                HorizontalDivider()
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Label("Status")
                    when {
                        existing.used -> {
                            Text(usedLabel(existing), fontSize = 14.sp)
                            Text(
                                "Used items stay in ${person.name}'s history.",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            OutlinedButton(onClick = {
                                finish(edited().copy(usedOn = null, eventKey = null, eventName = "", eventDate = null))
                            }) { Text("Not used — back to ideas") }
                        }
                        existing.planned -> {
                            Text(plannedLabel(existing), fontSize = 14.sp)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = {
                                    finish(edited().copy(eventKey = null, eventName = "", eventDate = null))
                                }) { Text("Back to ideas") }
                                TextButton(onClick = { finish(edited().copy(usedOn = today)) }) { Text("Mark used") }
                            }
                        }
                        else -> {
                            Text("💡 Idea · added ${existing.created.format(RowDate)}", fontSize = 14.sp)
                            OutlinedButton(onClick = { finish(edited().copy(usedOn = today)) }) { Text("Mark used") }
                        }
                    }
                }
                ArmedButton("Delete", onConfirm = {
                    repo.deleteItem(person.id, existing.id)
                    nav.pop()
                })
            } else {
                Button(
                    onClick = { finish(edited()) },
                    enabled = title.isNotBlank(),
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) { Text("Save") }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

private fun withScheme(link: String): String = if ("://" in link) link else "https://$link"

// ── People on an event ──────────────────────────────────────────────

@Composable
internal fun EventPeople(occ: Occurrence, repo: TraditionsRepo, nav: Nav) {
    val plan = repo.resolvedPlan(occ)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Label("People · gifts & todos")
            Spacer(Modifier.weight(1f))
            if (plan.carriedFrom != null) {
                TextButton(onClick = { repo.newList(occ) }) { Text("New list") }
            } else if (plan.own && plan.previousYear != null && plan.people.isEmpty()) {
                TextButton(onClick = { repo.copyFromPrevious(occ) }) { Text("Copy from ${plan.previousYear}") }
            }
        }
        if (plan.carriedFrom != null) {
            Text("People carried over from ${plan.carriedFrom}", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (plan.people.isEmpty()) {
            Text("Nobody attached yet", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        for (pid in plan.people) {
            val p = repo.person(pid) ?: continue
            key(pid) { EventPersonBlock(occ, p, repo, nav) }
        }
        OutlinedButton(onClick = { nav.push("pickPerson/${occ.key}") }) { Text("+ Add person") }
    }
}

@Composable
private fun EventPersonBlock(occ: Occurrence, p: Person, repo: TraditionsRepo, nav: Nav) {
    val items = p.items.filter { it.eventKey == occ.key }
    Column(
        Modifier.fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                p.name,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).clickable { nav.push("person/${p.id}") }.padding(vertical = 8.dp),
            )
            ArmedButton("Remove", onConfirm = { repo.removePersonFromEvent(occ, p.id) })
        }
        for (i in items) key(i.id) {
            ItemRow(i, if (i.used) "used" else null, onToggle = { repo.toggleDone(p.id, i.id) }) {
                nav.push("item/${p.id}/${i.id}/${i.kind.id}/-")
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            for (k in ItemKind.entries) {
                TextButton(onClick = {
                    repo.ensurePlan(occ)
                    nav.push("item/${p.id}/$NEW/${k.id}/${occ.key}")
                }) { Text("+ ${k.label}") }
            }
            val ideas = p.items.count { it.open }
            if (ideas > 0) {
                TextButton(onClick = {
                    repo.ensurePlan(occ)
                    nav.push("pickIdeas/${occ.key}/${p.id}")
                }) { Text("💡 Ideas ($ideas)") }
            }
        }
    }
}

/** Tap a person to attach them (the screen stays open for more); or create a new one by name. */
@Composable
internal fun PickPersonScreen(occ: Occurrence, repo: TraditionsRepo, nav: Nav) {
    var name by rememberSaveable { mutableStateOf("") }
    val attached = repo.resolvedPlan(occ).people.toSet()
    val others = repo.persons.filter { it.id !in attached }.sortedBy { it.name.lowercase() }
    val create = {
        if (name.isNotBlank()) {
            val id = repo.upsertPerson(null, name, null, true)
            repo.addPersonToEvent(occ, id)
            name = ""
        }
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        MiniTopBar("Add to ${occ.name}", nav.pop) {
            TextButton(onClick = nav.pop) { Text("Done") }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                placeholder = { Text("New person") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { create() }),
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = create, enabled = name.isNotBlank()) { Text("+", fontSize = 24.sp) }
        }
        Spacer(Modifier.height(8.dp))
        if (others.isEmpty()) {
            Text("Everybody is attached.", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        LazyColumn(Modifier.weight(1f)) {
            items(others, key = { it.id }) { p ->
                Row(
                    Modifier.fillMaxWidth().clickable { repo.addPersonToEvent(occ, p.id) }.padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(p.name, fontSize = 16.sp, modifier = Modifier.weight(1f))
                    val ideas = p.items.count { it.open }
                    if (ideas > 0) Text("💡$ideas", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** The person's open ideas; tap plans one for this event (it leaves the list). */
@Composable
internal fun PickIdeasScreen(occ: Occurrence, person: Person, repo: TraditionsRepo, nav: Nav) {
    val open = person.items.filter { it.open }.sortedByDescending { it.created }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        MiniTopBar("${person.name}'s ideas", nav.pop) {
            TextButton(onClick = nav.pop) { Text("Done") }
        }
        Text("Tap to use for ${occ.name} ${occ.date.year}", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        if (open.isEmpty()) Text("No open ideas left.", fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        LazyColumn(Modifier.weight(1f)) {
            items(open, key = { it.id }) { i ->
                ItemRow(i, null, onToggle = null) { repo.assign(person.id, i.id, occ) }
            }
        }
    }
}
