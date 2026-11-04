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
 *   "entries": [ { "id": "...", "category": "birthday", "name": "Anna", "date": "2020-05-04", "yearKnown": true,
 *                  "presents": [ { "id": "...", "text": "Lego", "done": false } ], ... } ]
 * }
 * Unknown keys on an entry are kept, so later per-category fields survive edits.
 */
data class TraditionsSnapshot(
    val showOptional: Boolean,
    val overrides: Map<String, LocalDate>,
    val entries: List<UserEntry>,
) {
    fun planner() = Planner(CATALOG, entries, overrides, showOptional)
}

object TraditionsData {
    private val KNOWN = setOf("id", "category", "name", "date", "yearKnown", "presents", "reminders")

    fun load(store: JsonStore): TraditionsSnapshot {
        val root = store.read()
        val overrides = HashMap<String, LocalDate>()
        root.optJSONObject("overrides")?.let { o ->
            o.keys().forEach { k -> runCatching { overrides[k] = LocalDate.parse(o.getString(k)) } }
        }
        val entries = ArrayList<UserEntry>()
        root.optJSONArray("entries")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val cat = Category.byId(o.optString("category")) ?: continue
                val date = runCatching { LocalDate.parse(o.getString("date")) }.getOrNull() ?: continue
                val extra = JSONObject()
                o.keys().forEach { k -> if (k !in KNOWN) extra.put(k, o.get(k)) }
                val presents = ArrayList<Present>()
                o.optJSONArray("presents")?.let { pa ->
                    for (j in 0 until pa.length()) {
                        val p = pa.optJSONObject(j) ?: continue
                        val text = p.optString("text")
                        if (text.isBlank()) continue
                        presents += Present(p.optString("id").ifBlank { UUID.randomUUID().toString() }, text, p.optBoolean("done"))
                    }
                }
                val reminders = o.optJSONArray("reminders")?.let { ra ->
                    (0 until ra.length()).mapNotNull { j ->
                        ra.optJSONObject(j)?.let { r -> EntryReminder(r.optInt("days").coerceIn(0, MAX_REMINDER_DAYS), r.optString("note")) }
                    }
                }
                entries += UserEntry(
                    o.optString("id"), cat, o.optString("name"), date, o.optBoolean("yearKnown", true),
                    presents, reminders, extra.toString(),
                )
            }
        }
        return TraditionsSnapshot(root.optBoolean("showOptional", true), overrides, entries)
    }

    fun save(store: JsonStore, s: TraditionsSnapshot) {
        val root = store.read() // keep keys this version doesn't know about
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
                val rs = e.reminders
                if (rs == null) o.remove("reminders")
                else o.put("reminders", JSONArray().apply {
                    for (r in rs) put(JSONObject().put("days", r.daysBefore).put("note", r.note))
                })
                put(o)
            }
        })
        store.write(root)
    }
}

/** Compose-observable state on top of [TraditionsData]. */
class TraditionsRepo(private val store: JsonStore) {
    var showOptional by mutableStateOf(true)
        private set
    val overrides = mutableStateMapOf<String, LocalDate>()
    val entries = mutableStateListOf<UserEntry>()

    init {
        val s = TraditionsData.load(store)
        showOptional = s.showOptional
        overrides.putAll(s.overrides)
        entries.addAll(s.entries)
    }

    fun snapshot() = TraditionsSnapshot(showOptional, overrides.toMap(), entries.toList())

    fun toggleOptional() {
        showOptional = !showOptional
        save()
    }

    fun setOverride(key: String, date: LocalDate?) {
        if (date == null) overrides.remove(key) else overrides[key] = date
        save()
    }

    fun upsert(id: String?, category: Category, name: String, date: LocalDate, yearKnown: Boolean) {
        val d = if (yearKnown) date else date.withYear(NO_YEAR)
        val i = entries.indexOfFirst { it.id == id }
        if (i >= 0) entries[i] = entries[i].copy(category = category, name = name.trim(), date = d, yearKnown = yearKnown)
        else entries += UserEntry(UUID.randomUUID().toString(), category, name.trim(), d, yearKnown)
        save()
    }

    fun import(rows: List<ImportRow>) {
        if (rows.isEmpty()) return
        entries += rows.map { UserEntry(UUID.randomUUID().toString(), it.category, it.name, it.date(), it.year != null) }
        save()
    }

    fun addPresent(entryId: String, text: String) {
        if (text.isBlank()) return
        updatePresents(entryId) { it + Present(UUID.randomUUID().toString(), text.trim()) }
    }

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

    fun addReminder(entryId: String, days: Int, note: String) {
        val r = EntryReminder(days.coerceIn(0, MAX_REMINDER_DAYS), note.trim())
        updateReminders(entryId) { if (r in it) it else it + r }
    }

    fun deleteReminder(entryId: String, r: EntryReminder) = updateReminders(entryId) { it - r }

    fun resetReminders(entryId: String) {
        val i = entries.indexOfFirst { it.id == entryId }
        if (i < 0) return
        entries[i] = entries[i].copy(reminders = null)
        save()
    }

    private fun updateReminders(entryId: String, f: (List<EntryReminder>) -> List<EntryReminder>) {
        val i = entries.indexOfFirst { it.id == entryId }
        if (i < 0) return
        entries[i] = entries[i].copy(reminders = f(entries[i].effectiveReminders()))
        save()
    }

    fun delete(id: String) {
        entries.removeAll { it.id == id }
        save()
    }

    private fun save() = TraditionsData.save(store, snapshot())
}
