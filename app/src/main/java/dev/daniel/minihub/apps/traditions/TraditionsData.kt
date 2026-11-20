package dev.daniel.minihub.apps.traditions

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.daniel.minihub.core.JsonStore
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.util.UUID

/**
 * File format (traditions.json):
 * {
 *   "showOptional": true,
 *   "overrides": { "kg-nikolo@2026": "2026-12-07" },        // built-in id @ year -> date
 *   "entries": [ { "id": "...", "category": "anniversary", "name": "Wedding", "date": "2015-06-20", "yearKnown": true, ... } ],
 *   "persons": [ { "id": "...", "name": "Anna", "birthDate": "2020-05-04", "yearKnown": true, "reminders": [...],
 *                  "items": [ { "id", "kind": "gift"|"todo", "title", "description", "links": [..], "done",
 *                               "created", "eventKey", "eventName", "eventDate", "usedOn" } ] } ],
 *   "plans": { "christmas@2026": { "people": ["<person id>", ...] } }
 * }
 * Migration: "birthday" entries (v1) become persons with the same id, so occurrence keys
 * ("<id>@<year>") and reminder notifications stay the same; their present ideas become gift items
 * (struck-through ones go to the history). Unknown keys on entries and persons are kept.
 */
data class TraditionsSnapshot(
    val showOptional: Boolean,
    val overrides: Map<String, LocalDate>,
    val entries: List<UserEntry>,
    val persons: List<Person> = emptyList(),
    val plans: Map<String, EventPlan> = emptyMap(),
    /** True when [TraditionsData.load] converted legacy birthday entries; the caller should save. */
    val migrated: Boolean = false,
) {
    fun planner() = Planner(CATALOG, entries, persons, overrides, showOptional)
}

object TraditionsData {
    private val KNOWN_ENTRY = setOf("id", "category", "name", "date", "yearKnown", "presents", "reminders")
    private val KNOWN_PERSON = setOf("id", "name", "birthDate", "yearKnown", "reminders", "items")

    fun load(store: JsonStore, today: LocalDate = LocalDate.now()): TraditionsSnapshot = parse(store.read(), today)

    /** Pure JSON → snapshot (incl. migration), kept separate from [JsonStore] for tests. */
    fun parse(root: JSONObject, today: LocalDate): TraditionsSnapshot {
        val overrides = HashMap<String, LocalDate>()
        root.optJSONObject("overrides")?.let { o ->
            o.keys().forEach { k -> runCatching { overrides[k] = LocalDate.parse(o.getString(k)) } }
        }
        val entries = ArrayList<UserEntry>()
        root.optJSONArray("entries")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val cat = Category.byId(o.optString("category")) ?: continue
                val date = parseDate(o.optString("date")) ?: continue
                val extra = JSONObject()
                o.keys().forEach { k -> if (k !in KNOWN_ENTRY) extra.put(k, o.get(k)) }
                val presents = ArrayList<Present>()
                o.optJSONArray("presents")?.let { pa ->
                    for (j in 0 until pa.length()) {
                        val p = pa.optJSONObject(j) ?: continue
                        val text = p.optString("text")
                        if (text.isBlank()) continue
                        presents += Present(p.optString("id").ifBlank { UUID.randomUUID().toString() }, text, p.optBoolean("done"))
                    }
                }
                entries += UserEntry(
                    o.optString("id"), cat, o.optString("name"), date, o.optBoolean("yearKnown", true),
                    presents, parseReminders(o), extra.toString(),
                )
            }
        }

        val persons = ArrayList<Person>()
        root.optJSONArray("persons")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val id = o.optString("id")
                if (id.isBlank() || persons.any { it.id == id }) continue
                val extra = JSONObject()
                o.keys().forEach { k -> if (k !in KNOWN_PERSON) extra.put(k, o.get(k)) }
                val items = ArrayList<PersonItem>()
                o.optJSONArray("items")?.let { ia ->
                    for (j in 0 until ia.length()) parseItem(ia.optJSONObject(j) ?: continue, today)?.let { items += it }
                }
                persons += Person(
                    id, o.optString("name"), parseDate(o.optString("birthDate")), o.optBoolean("yearKnown", true),
                    parseReminders(o), items, extra.toString(),
                )
            }
        }

        // ── Migration: birthday entries → persons ──
        val legacy = entries.filter { it.category == Category.BIRTHDAY }
        for (e in legacy) {
            if (persons.any { it.id == e.id }) continue // already migrated (e.g. an older app wrote the entry back)
            persons += Person(
                id = e.id,
                name = e.name,
                birthDate = e.date,
                yearKnown = e.yearKnown,
                reminders = e.reminders,
                items = e.presents.map { p ->
                    PersonItem(p.id, ItemKind.GIFT, p.text, created = today, usedOn = if (p.done) today else null)
                },
                extraJson = e.extraJson,
            )
        }
        entries.removeAll { it.category == Category.BIRTHDAY }

        val plans = HashMap<String, EventPlan>()
        root.optJSONObject("plans")?.let { o ->
            o.keys().forEach { k ->
                val ids = o.optJSONObject(k)?.optJSONArray("people") ?: return@forEach
                plans[k] = EventPlan((0 until ids.length()).map { ids.optString(it) }.filter { it.isNotBlank() }.distinct())
            }
        }
        return TraditionsSnapshot(root.optBoolean("showOptional", true), overrides, entries, persons, plans, migrated = legacy.isNotEmpty())
    }

    private fun parseDate(s: String?): LocalDate? = s?.takeIf { it.isNotBlank() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

    private fun parseReminders(o: JSONObject): List<EntryReminder>? = o.optJSONArray("reminders")?.let { ra ->
        (0 until ra.length()).mapNotNull { j ->
            ra.optJSONObject(j)?.let { r -> EntryReminder(r.optInt("days").coerceIn(0, MAX_REMINDER_DAYS), r.optString("note")) }
        }
    }

    private fun parseItem(o: JSONObject, today: LocalDate): PersonItem? {
        val title = o.optString("title")
        if (title.isBlank()) return null
        val links = o.optJSONArray("links")?.let { la -> (0 until la.length()).map { la.optString(it) }.filter { it.isNotBlank() } }.orEmpty()
        return PersonItem(
            id = o.optString("id").ifBlank { UUID.randomUUID().toString() },
            kind = ItemKind.byId(o.optString("kind")),
            title = title,
            description = o.optString("description"),
            links = links,
            done = o.optBoolean("done"),
            created = parseDate(o.optString("created")) ?: today,
            eventKey = o.optString("eventKey").ifBlank { null },
            eventName = o.optString("eventName"),
            eventDate = parseDate(o.optString("eventDate")),
            usedOn = parseDate(o.optString("usedOn")),
        )
    }

    fun save(store: JsonStore, s: TraditionsSnapshot) {
        store.write(serialize(store.read(), s)) // keep top-level keys this version doesn't know about
    }

    fun serialize(root: JSONObject, s: TraditionsSnapshot): JSONObject {
        root.put("showOptional", s.showOptional)
        root.put("overrides", JSONObject().apply { s.overrides.forEach { (k, v) -> put(k, v.toString()) } })
        root.put("entries", JSONArray().apply {
            for (e in s.entries) {
                val o = runCatching { JSONObject(e.extraJson) }.getOrDefault(JSONObject())
                o.put("id", e.id).put("category", e.category.id).put("name", e.name).put("date", e.date.toString())
                    .put("yearKnown", e.yearKnown)
                o.put("presents", JSONArray().apply {
                    for (p in e.presents) put(JSONObject().put("id", p.id).put("text", p.text).put("done", p.done))
                })
                putReminders(o, e.reminders)
                put(o)
            }
        })
        root.put("persons", JSONArray().apply {
            for (p in s.persons) {
                val o = runCatching { JSONObject(p.extraJson) }.getOrDefault(JSONObject())
                o.put("id", p.id).put("name", p.name).put("yearKnown", p.yearKnown)
                if (p.birthDate == null) o.remove("birthDate") else o.put("birthDate", p.birthDate.toString())
                putReminders(o, p.reminders)
                o.put("items", JSONArray().apply {
                    for (i in p.items) {
                        val io = JSONObject().put("id", i.id).put("kind", i.kind.id).put("title", i.title)
                            .put("description", i.description).put("links", JSONArray(i.links)).put("done", i.done)
                            .put("created", i.created.toString())
                        i.eventKey?.let { io.put("eventKey", it) }
                        if (i.eventName.isNotEmpty()) io.put("eventName", i.eventName)
                        i.eventDate?.let { io.put("eventDate", it.toString()) }
                        i.usedOn?.let { io.put("usedOn", it.toString()) }
                        put(io)
                    }
                })
                put(o)
            }
        })
        root.put("plans", JSONObject().apply {
            s.plans.forEach { (k, v) -> put(k, JSONObject().put("people", JSONArray(v.people))) }
        })
        return root
    }

    private fun putReminders(o: JSONObject, rs: List<EntryReminder>?) {
        if (rs == null) o.remove("reminders")
        else o.put("reminders", JSONArray().apply {
            for (r in rs) put(JSONObject().put("days", r.daysBefore).put("note", r.note))
        })
    }
}

/** Compose-observable state on top of [TraditionsData]. */
class TraditionsRepo(private val store: JsonStore) {
    var showOptional by mutableStateOf(true)
        private set
    val overrides = mutableStateMapOf<String, LocalDate>()
    val entries = mutableStateListOf<UserEntry>()
    val persons = mutableStateListOf<Person>()
    val plans = mutableStateMapOf<String, EventPlan>()

    init {
        val s = TraditionsData.load(store)
        showOptional = s.showOptional
        overrides.putAll(s.overrides)
        entries.addAll(s.entries)
        persons.addAll(s.persons)
        plans.putAll(s.plans)
        if (s.migrated) save() // write the migrated file right away
    }

    fun snapshot() = TraditionsSnapshot(showOptional, overrides.toMap(), entries.toList(), persons.toList(), plans.toMap())

    fun person(id: String): Person? = persons.firstOrNull { it.id == id }

    fun toggleOptional() {
        showOptional = !showOptional
        save()
    }

    fun setOverride(key: String, date: LocalDate?) {
        if (date == null) overrides.remove(key) else overrides[key] = date
        save()
    }

    // ── Entries (anniversaries) ──

    fun upsertEntry(id: String?, category: Category, name: String, date: LocalDate, yearKnown: Boolean) {
        if (category == Category.BIRTHDAY) {
            upsertPerson(null, name, date, yearKnown)
            return
        }
        val d = if (yearKnown) date else date.withYear(NO_YEAR)
        val i = entries.indexOfFirst { it.id == id }
        if (i >= 0) entries[i] = entries[i].copy(category = category, name = name.trim(), date = d, yearKnown = yearKnown)
        else entries += UserEntry(UUID.randomUUID().toString(), category, name.trim(), d, yearKnown)
        save()
    }

    fun import(rows: List<ImportRow>) {
        if (rows.isEmpty()) return
        for (r in rows) {
            if (r.category == Category.BIRTHDAY) persons += Person(UUID.randomUUID().toString(), r.name, r.date(), r.year != null)
            else entries += UserEntry(UUID.randomUUID().toString(), r.category, r.name, r.date(), r.year != null)
        }
        save()
    }

    fun deleteEntry(id: String) {
        entries.removeAll { it.id == id }
        dropSeries(id)
        save()
    }

    /** Legacy present ideas on anniversary entries (v1). */
    fun togglePresent(entryId: String, presentId: String) =
        updatePresents(entryId) { list -> list.map { if (it.id == presentId) it.copy(done = !it.done) else it } }

    fun deletePresent(entryId: String, presentId: String) =
        updatePresents(entryId) { list -> list.filterNot { it.id == presentId } }

    private fun updatePresents(entryId: String, f: (List<Present>) -> List<Present>) {
        val i = entries.indexOfFirst { it.id == entryId }
        if (i < 0) return
        entries[i] = entries[i].copy(presents = f(entries[i].presents))
        save()
    }

    // ── Persons ──

    /** Creates or updates a person; returns its id. A null [birthDate] removes the birthday. */
    fun upsertPerson(id: String?, name: String, birthDate: LocalDate?, yearKnown: Boolean): String {
        val d = birthDate?.let { if (yearKnown) it else it.withYear(NO_YEAR) }
        val i = persons.indexOfFirst { it.id == id }
        val outId: String
        if (i >= 0) {
            persons[i] = persons[i].copy(name = name.trim(), birthDate = d, yearKnown = yearKnown)
            outId = persons[i].id
            if (d == null) dropSeries(outId)
        } else {
            outId = UUID.randomUUID().toString()
            persons += Person(outId, name.trim(), d, yearKnown)
        }
        save()
        return outId
    }

    fun deletePerson(id: String) {
        persons.removeAll { it.id == id }
        for ((k, v) in plans.toMap()) if (id in v.people) plans[k] = EventPlan(v.people - id)
        dropSeries(id)
        save()
    }

    /** Drops plans of a deleted event series and moves its planned (not yet used) items back to the ideas. */
    private fun dropSeries(seriesId: String) {
        plans.keys.filter { splitKey(it)?.first == seriesId }.forEach { plans.remove(it) }
        for (i in persons.indices) {
            val p = persons[i]
            if (p.items.none { it.planned && it.eventKey?.let(::splitKey)?.first == seriesId }) continue
            persons[i] = p.copy(items = p.items.map {
                if (it.planned && it.eventKey?.let(::splitKey)?.first == seriesId) it.copy(eventKey = null, eventName = "", eventDate = null) else it
            })
        }
    }

    // ── Items (gifts / todos) ──

    /** Saves a new or edited item. With [event], the item is planned for that occurrence. */
    fun saveItem(personId: String, item: PersonItem) = updateItems(personId) { list ->
        if (list.any { it.id == item.id }) list.map { if (it.id == item.id) item else it } else list + item
    }

    fun newItem(kind: ItemKind, today: LocalDate) = PersonItem(UUID.randomUUID().toString(), kind, "", created = today)

    fun deleteItem(personId: String, itemId: String) = updateItems(personId) { list -> list.filterNot { it.id == itemId } }

    fun toggleDone(personId: String, itemId: String) =
        updateItems(personId) { list -> list.map { if (it.id == itemId) it.copy(done = !it.done) else it } }

    /** Plans an item for [occ], or (null) moves it back to the person's ideas. */
    fun assign(personId: String, itemId: String, occ: Occurrence?) = updateItems(personId) { list ->
        list.map {
            if (it.id != itemId) it
            else if (occ == null) it.copy(eventKey = null, eventName = "", eventDate = null, usedOn = null)
            else it.copy(eventKey = occ.key, eventName = occ.name, eventDate = occ.date, usedOn = null)
        }
    }

    /** Marks an item used by hand (null = not used; a planned one goes back to planned). */
    fun setUsed(personId: String, itemId: String, on: LocalDate?) = updateItems(personId) { list ->
        list.map { if (it.id == itemId) it.copy(usedOn = on) else it }
    }

    private fun updateItems(personId: String, f: (List<PersonItem>) -> List<PersonItem>) {
        val i = persons.indexOfFirst { it.id == personId }
        if (i < 0) return
        persons[i] = persons[i].copy(items = f(persons[i].items))
        save()
    }

    /** Flags items of past events as used; cheap no-op when nothing is due. */
    fun settle(today: LocalDate) {
        val planner = snapshot().planner()
        val next = settleItems(persons.toList(), today, planner::find) ?: return
        for (i in next.indices) if (persons[i] != next[i]) persons[i] = next[i]
        save()
    }

    // ── Event plans (people per occurrence) ──

    fun resolvedPlan(occ: Occurrence): ResolvedPlan {
        val default = listOfNotNull(occ.person?.id)
        val r = resolvePlan(plans, occ.key, default)
        val alive = persons.map { it.id }.toHashSet()
        return r.copy(people = r.people.filter { it in alive })
    }

    /** Turns a carried-over / default list into this occurrence's own list (before editing it). */
    fun ensurePlan(occ: Occurrence) {
        if (plans.containsKey(occ.key)) return
        plans[occ.key] = EventPlan(resolvedPlan(occ).people)
        save()
    }

    fun addPersonToEvent(occ: Occurrence, personId: String) {
        val people = resolvedPlan(occ).people
        if (personId in people && plans.containsKey(occ.key)) return
        plans[occ.key] = EventPlan((people + personId).distinct())
        save()
    }

    /** Removes the person from this occurrence; their planned items go back to the ideas. */
    fun removePersonFromEvent(occ: Occurrence, personId: String) {
        plans[occ.key] = EventPlan(resolvedPlan(occ).people - personId)
        val i = persons.indexOfFirst { it.id == personId }
        if (i >= 0) {
            persons[i] = persons[i].copy(items = persons[i].items.map {
                if (it.planned && it.eventKey == occ.key) it.copy(eventKey = null, eventName = "", eventDate = null) else it
            })
        }
        save()
    }

    /** Starts an empty list for this occurrence instead of the carried-over one. */
    fun newList(occ: Occurrence) {
        plans[occ.key] = EventPlan(emptyList())
        save()
    }

    /** Adds the people of the latest earlier year to this occurrence's own list. */
    fun copyFromPrevious(occ: Occurrence) {
        val r = resolvedPlan(occ)
        val (series, _) = splitKey(occ.key) ?: return
        val prev = r.previousYear ?: return
        val prevPeople = plans["$series@$prev"]?.people.orEmpty()
        plans[occ.key] = EventPlan((r.people + prevPeople).distinct())
        save()
    }

    // ── Reminders (anniversary entries and persons share the same ids space) ──

    fun addReminder(ownerId: String, days: Int, note: String) {
        val r = EntryReminder(days.coerceIn(0, MAX_REMINDER_DAYS), note.trim())
        updateReminders(ownerId) { if (r in it) it else it + r }
    }

    fun deleteReminder(ownerId: String, r: EntryReminder) = updateReminders(ownerId) { it - r }

    fun resetReminders(ownerId: String) {
        val e = entries.indexOfFirst { it.id == ownerId }
        if (e >= 0) entries[e] = entries[e].copy(reminders = null)
        val p = persons.indexOfFirst { it.id == ownerId }
        if (p >= 0) persons[p] = persons[p].copy(reminders = null)
        save()
    }

    private fun updateReminders(ownerId: String, f: (List<EntryReminder>) -> List<EntryReminder>) {
        val e = entries.indexOfFirst { it.id == ownerId }
        if (e >= 0) entries[e] = entries[e].copy(reminders = f(entries[e].effectiveReminders()))
        val p = persons.indexOfFirst { it.id == ownerId }
        if (p >= 0) persons[p] = persons[p].copy(reminders = f(persons[p].effectiveReminders()))
        save()
    }

    private fun save() = TraditionsData.save(store, snapshot())
}
