package dev.daniel.minihub.apps.sports

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.daniel.minihub.core.JsonStore
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

enum class Kind { REPS, SESSION }

data class Exercise(val id: String, val name: String, val kind: Kind)

/** Edit this list to add/remove exercises. Ids are stored – don't rename them. */
val EXERCISES = listOf(
    Exercise("pushups", "Push-ups", Kind.REPS),
    Exercise("curls", "Curls", Kind.REPS),
    Exercise("rows", "Back rows", Kind.REPS),
    Exercise("situps", "Sit-ups", Kind.REPS),
    Exercise("jumps", "Jumps", Kind.REPS),
    Exercise("fullbody", "Full body session", Kind.SESSION),
    Exercise("calisthenics", "Calisthenics session", Kind.SESSION),
)

typealias Counts = Map<String, Int>

/**
 * A weekly plan (weekday 1=Mon..7=Sun → targets) valid from [from] onwards.
 * Editing creates/replaces the version for *today*; past days resolve to the
 * newest version that started on or before them, so they stay frozen.
 */
data class PlanVersion(val from: LocalDate, val week: Map<Int, Counts>)

class SportsRepo(private val store: JsonStore) {
    var versions by mutableStateOf(listOf<PlanVersion>())
        private set
    var done by mutableStateOf(mapOf<LocalDate, Counts>())
        private set

    init {
        runCatching { load() }
    }

    fun planFor(date: LocalDate): Counts {
        val v = versions.lastOrNull { !it.from.isAfter(date) } ?: return emptyMap()
        return v.week[date.dayOfWeek.value] ?: emptyMap()
    }

    fun doneFor(date: LocalDate): Counts = done[date] ?: emptyMap()

    /** Current (latest) weekly template for a weekday. */
    fun templateFor(dow: Int): Counts = versions.lastOrNull()?.week?.get(dow) ?: emptyMap()

    // ---- plan editing ----

    private fun editTemplate(today: LocalDate, transform: (MutableMap<Int, Counts>) -> Unit) {
        val week = (versions.lastOrNull()?.week ?: emptyMap()).toMutableMap()
        transform(week)
        val cleaned = week.mapValues { (_, c) -> c.filterValues { it > 0 } }.filterValues { it.isNotEmpty() }
        versions = versions.filter { it.from.isBefore(today) } + PlanVersion(today, cleaned)
        save()
    }

    fun addTarget(today: LocalDate, dow: Int, exId: String, delta: Int) = editTemplate(today) { w ->
        val c = (w[dow] ?: emptyMap()).toMutableMap()
        c[exId] = ((c[exId] ?: 0) + delta).coerceAtLeast(0)
        w[dow] = c
    }

    fun resetTarget(today: LocalDate, dow: Int, exId: String) = editTemplate(today) { w ->
        w[dow] = (w[dow] ?: emptyMap()) - exId
    }

    fun resetAllTargets(today: LocalDate, dow: Int) = editTemplate(today) { it.remove(dow) }

    fun copyToAllDays(today: LocalDate, dow: Int) = editTemplate(today) { w ->
        val c = w[dow] ?: emptyMap()
        for (d in 1..7) w[d] = c
    }

    // ---- done editing ----

    private fun editDone(date: LocalDate, transform: (MutableMap<String, Int>) -> Unit) {
        val m = doneFor(date).toMutableMap()
        transform(m)
        val c = m.filterValues { it > 0 }
        done = if (c.isEmpty()) done - date else done + (date to c)
        save()
    }

    fun addDone(date: LocalDate, exId: String, delta: Int) = editDone(date) {
        it[exId] = ((it[exId] ?: 0) + delta).coerceAtLeast(0)
    }

    fun resetDone(date: LocalDate, exId: String) = editDone(date) { it.remove(exId) }

    fun resetAllDone(date: LocalDate) = editDone(date) { it.clear() }

    // ---- persistence ----

    private fun countsFrom(o: JSONObject): Counts = o.keys().asSequence().associateWith { o.getInt(it) }

    private fun countsTo(c: Counts): JSONObject = JSONObject().apply { c.forEach { (k, v) -> put(k, v) } }

    private fun load() {
        val root = store.read()
        val vs = mutableListOf<PlanVersion>()
        root.optJSONArray("plans")?.let { arr ->
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val weekObj = o.getJSONObject("week")
                val week = weekObj.keys().asSequence().associate { k -> k.toInt() to countsFrom(weekObj.getJSONObject(k)) }
                vs += PlanVersion(LocalDate.parse(o.getString("from")), week)
            }
        }
        versions = vs.sortedBy { it.from }
        val d = root.optJSONObject("done")
        done = d?.keys()?.asSequence()?.associate { k -> LocalDate.parse(k) to countsFrom(d.getJSONObject(k)) } ?: emptyMap()
    }

    private fun save() {
        val plans = JSONArray()
        versions.forEach { v ->
            val week = JSONObject()
            v.week.forEach { (dow, c) -> week.put(dow.toString(), countsTo(c)) }
            plans.put(JSONObject().put("from", v.from.toString()).put("week", week))
        }
        val d = JSONObject()
        done.forEach { (date, c) -> d.put(date.toString(), countsTo(c)) }
        store.write(JSONObject().put("plans", plans).put("done", d))
    }
}
