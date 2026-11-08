package dev.daniel.minihub.apps.todo

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.daniel.minihub.core.JsonStore
import org.json.JSONArray
import org.json.JSONObject
import java.text.BreakIterator
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import java.util.UUID

data class TodoList(val id: String, val name: String)

/** One todo. [due] and [remind] are optional; a reminder fires with the daily check on [remind]. */
data class TodoEntry(
    val id: String,
    val listId: String,
    val title: String,
    val due: LocalDate? = null,
    val remind: LocalDate? = null,
    val done: Boolean = false,
    val created: Long = 0L,
    val doneAt: Long = 0L,
)

data class TodoState(val lists: List<TodoList>, val entries: List<TodoEntry>, val currentId: String?)

/** Longest lead time offered by the due slider. */
const val MAX_DUE_DAYS = 60

/**
 * JSON file:
 * `{ "current": id, "lists": [{id, name}], "entries": [{id, list, title, due?, remind?, done, created, doneAt}] }`
 */
object TodoData {
    const val DEFAULT_LIST_ID = "todo"

    fun load(store: JsonStore): TodoState = parse(store.read())

    fun save(store: JsonStore, s: TodoState) = store.write(toJson(s))

    fun parse(o: JSONObject): TodoState {
        val lists = mutableListOf<TodoList>()
        o.optJSONArray("lists")?.let { a ->
            for (i in 0 until a.length()) {
                val l = a.optJSONObject(i) ?: continue
                val id = l.optString("id")
                if (id.isNotEmpty()) lists += TodoList(id, l.optString("name"))
            }
        }
        val entries = mutableListOf<TodoEntry>()
        o.optJSONArray("entries")?.let { a ->
            for (i in 0 until a.length()) {
                val e = a.optJSONObject(i) ?: continue
                val id = e.optString("id")
                if (id.isEmpty()) continue
                entries += TodoEntry(
                    id = id,
                    listId = e.optString("list"),
                    title = e.optString("title"),
                    due = e.date("due"),
                    remind = e.date("remind"),
                    done = e.optBoolean("done"),
                    created = e.optLong("created"),
                    doneAt = e.optLong("doneAt"),
                )
            }
        }
        return TodoState(lists, entries, o.optString("current").takeIf { it.isNotEmpty() })
    }

    fun toJson(s: TodoState): JSONObject = JSONObject().apply {
        s.currentId?.let { put("current", it) }
        put("lists", JSONArray().apply {
            for (l in s.lists) put(JSONObject().put("id", l.id).put("name", l.name))
        })
        put("entries", JSONArray().apply {
            for (e in s.entries) put(JSONObject().apply {
                put("id", e.id)
                put("list", e.listId)
                put("title", e.title)
                e.due?.let { put("due", it.toString()) }
                e.remind?.let { put("remind", it.toString()) }
                put("done", e.done)
                put("created", e.created)
                if (e.doneAt != 0L) put("doneAt", e.doneAt)
            })
        })
    }

    private fun JSONObject.date(key: String): LocalDate? =
        optString(key).takeIf { it.isNotEmpty() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
}

/** Open entries: soonest due first (no due date last), then oldest first. */
fun List<TodoEntry>.openSorted(): List<TodoEntry> =
    filter { !it.done }.sortedWith(compareBy<TodoEntry>({ it.due == null }, { it.due }, { it.created }))

/** Done entries: most recently completed first. */
fun List<TodoEntry>.doneSorted(): List<TodoEntry> = filter { it.done }.sortedByDescending { it.doneAt }

/** Open entries whose reminder fires on [day]. */
fun List<TodoEntry>.remindersOn(day: LocalDate): List<TodoEntry> = filter { !it.done && it.remind == day }

private val WeekdayFmt = DateTimeFormatter.ofPattern("EEEE", Locale.getDefault())
private val ShortFmt = DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault())
private val SliderFmt = DateTimeFormatter.ofPattern("EEE d", Locale.getDefault())

fun daysUntil(today: LocalDate, date: LocalDate): Long = ChronoUnit.DAYS.between(today, date)

/** "Due today", "Due tomorrow", "Due Friday" (within a week), "Due Fri 14 Nov", "Overdue · 3 d". */
fun dueText(due: LocalDate, today: LocalDate): String {
    val d = daysUntil(today, due)
    return when {
        d < 0 -> "Overdue · ${-d} d"
        d == 0L -> "Due today"
        d == 1L -> "Due tomorrow"
        d < 7 -> "Due ${due.format(WeekdayFmt)}"
        else -> "Due ${due.format(ShortFmt)}"
    }
}

/** "Today", "Tomorrow", "Friday" (within a week), "Fri 14 Nov"; past dates always with the date. */
fun dayText(date: LocalDate, today: LocalDate): String {
    val d = daysUntil(today, date)
    return when {
        d == 0L -> "Today"
        d == 1L -> "Tomorrow"
        d in 2..6 -> date.format(WeekdayFmt)
        else -> date.format(ShortFmt)
    }
}

/** Short label for the due slider's bubble (fits ~7 characters): "Today", "Tmrw", "Fri 14". */
fun sliderDayText(days: Int, today: LocalDate): String = when (days) {
    0 -> "Today"
    1 -> "Tmrw"
    else -> today.plusDays(days.toLong()).format(SliderFmt)
}

/** First character (emoji-safe) of a list name, for the dial. */
fun listIcon(name: String): String {
    val t = name.trim()
    if (t.isEmpty()) return "•"
    val it = BreakIterator.getCharacterInstance().apply { setText(t) }
    val end = it.next().takeIf { e -> e > 0 } ?: 1
    return t.substring(0, end).uppercase()
}

/** Compose-observable state on top of [TodoData]. Saves after every change. */
class TodoRepo(private val store: JsonStore) {
    val lists = mutableStateListOf<TodoList>()
    val entries = mutableStateListOf<TodoEntry>()
    var currentId by mutableStateOf(TodoData.DEFAULT_LIST_ID)
        private set

    init {
        val s = TodoData.load(store)
        lists.addAll(s.lists)
        entries.addAll(s.entries)
        if (lists.isEmpty()) lists += TodoList(TodoData.DEFAULT_LIST_ID, "Todo")
        currentId = s.currentId?.takeIf { id -> lists.any { it.id == id } } ?: lists.first().id
    }

    val current: TodoList get() = lists.firstOrNull { it.id == currentId } ?: lists.first()

    fun entry(id: String?): TodoEntry? = entries.firstOrNull { it.id == id }

    fun entriesOf(listId: String): List<TodoEntry> = entries.filter { it.listId == listId }

    fun select(listId: String) {
        if (listId == currentId || lists.none { it.id == listId }) return
        currentId = listId
        save()
    }

    fun addList(name: String) {
        if (name.isBlank()) return
        val l = TodoList(UUID.randomUUID().toString(), name.trim())
        lists += l
        currentId = l.id
        save()
    }

    fun renameList(id: String, name: String) {
        val i = lists.indexOfFirst { it.id == id }
        if (i < 0 || name.isBlank()) return
        lists[i] = lists[i].copy(name = name.trim())
        save()
    }

    /** Deletes the list and its entries. The last list can't be deleted. */
    fun deleteList(id: String) {
        if (lists.size <= 1) return
        lists.removeAll { it.id == id }
        entries.removeAll { it.listId == id }
        if (currentId == id) currentId = lists.first().id
        save()
    }

    /** Creates (id == null) or updates an entry in the current list. Returns the entry. */
    fun upsert(id: String?, title: String, due: LocalDate?, remind: LocalDate?): TodoEntry {
        val i = entries.indexOfFirst { it.id == id }
        val e = if (i >= 0) {
            entries[i].copy(title = title.trim(), due = due, remind = remind).also { entries[i] = it }
        } else {
            TodoEntry(UUID.randomUUID().toString(), currentId, title.trim(), due, remind, created = System.currentTimeMillis())
                .also { entries += it }
        }
        save()
        return e
    }

    fun toggleDone(id: String) {
        val i = entries.indexOfFirst { it.id == id }
        if (i < 0) return
        val e = entries[i]
        entries[i] = e.copy(done = !e.done, doneAt = if (e.done) 0L else System.currentTimeMillis())
        save()
    }

    fun delete(id: String) {
        entries.removeAll { it.id == id }
        save()
    }

    fun clearDone(listId: String) {
        entries.removeAll { it.listId == listId && it.done }
        save()
    }

    private fun save() = TodoData.save(store, TodoState(lists.toList(), entries.toList(), currentId))
}
