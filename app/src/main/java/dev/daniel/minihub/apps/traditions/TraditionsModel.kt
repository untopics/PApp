package dev.daniel.minihub.apps.traditions

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.MonthDay
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

// Pure Kotlin (no Android imports) so the date logic can be tested on a plain JVM.

/** How a built-in entry finds its date in a given year. */
sealed interface DateRule {
    fun dateIn(year: Int): LocalDate

    data class Fixed(val month: Int, val day: Int) : DateRule {
        override fun dateIn(year: Int): LocalDate = LocalDate.of(year, month, day)
    }

    /** Offset in days from Easter Sunday (Palm Sunday = -7, Pentecost = +49). */
    data class Easter(val offset: Int) : DateRule {
        override fun dateIn(year: Int): LocalDate = easterSunday(year).plusDays(offset.toLong())
    }

    /** n-th weekday of a month; n = -1 means the last one. */
    data class NthWeekday(val month: Int, val dow: DayOfWeek, val n: Int) : DateRule {
        override fun dateIn(year: Int): LocalDate {
            val first = LocalDate.of(year, month, 1)
            return if (n < 0) first.with(TemporalAdjusters.lastInMonth(dow))
            else first.with(TemporalAdjusters.dayOfWeekInMonth(n, dow))
        }
    }

    /** Last [dow] on or before month/day (e.g. the Friday before Christmas). */
    data class WeekdayOnOrBefore(val month: Int, val day: Int, val dow: DayOfWeek) : DateRule {
        override fun dateIn(year: Int): LocalDate =
            LocalDate.of(year, month, day).with(TemporalAdjusters.previousOrSame(dow))
    }

    /** Advent Sunday n (1..4). The 4th is the last Sunday before 25 Dec. */
    data class Advent(val n: Int) : DateRule {
        override fun dateIn(year: Int): LocalDate {
            val fourth = LocalDate.of(year, 12, 24).with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))
            return fourth.minusWeeks((4 - n).toLong())
        }
    }

    /** Wien/NÖ/Burgenland: summer holidays start on the Saturday between 28 Jun and 4 Jul; Zeugnis the day before. */
    data object SchoolEndEast : DateRule {
        override fun dateIn(year: Int): LocalDate =
            LocalDate.of(year, 6, 28).with(TemporalAdjusters.nextOrSame(DayOfWeek.SATURDAY)).minusDays(1)
    }
}

/** Anonymous Gregorian algorithm (Meeus/Jones/Butcher). */
fun easterSunday(year: Int): LocalDate {
    val a = year % 19
    val b = year / 100
    val c = year % 100
    val d = b / 4
    val e = b % 4
    val f = (b + 8) / 25
    val g = (b - f + 1) / 3
    val h = (19 * a + b - d - g + 15) % 30
    val i = c / 4
    val k = c % 4
    val l = (32 + 2 * e + 2 * i - h - k) % 7
    val m = (a + 11 * h + 22 * l) / 451
    val month = (h + l - 7 * m + 114) / 31
    val day = (h + l - 7 * m + 114) % 31 + 1
    return LocalDate.of(year, month, day)
}

enum class Tier(val label: String) { CORE("Core"), NICE("Nice"), OPTIONAL("Optional") }

enum class Kind(val label: String, val icon: String) {
    TRADITION("Tradition", "🎄"),
    KINDERGARTEN("Kindergarten", "🧸"),
    SCHOOL("School calendar", "🎒"),
    REGIONAL("Regional holiday", "📍"),
    BIRTHDAY("Birthday", "🎂"),
    ANNIVERSARY("Anniversary", "💍"),
}

/** A prep task; [daysBefore] is also when its reminder fires (0 = on the day). */
data class Prep(val daysBefore: Int, val text: String)

data class Tradition(
    val id: String,
    val name: String,
    val kind: Kind,
    val tier: Tier,
    val rule: DateRule,
    val ruleText: String,
    val what: String,
    val prep: List<Prep> = emptyList(),
    /** Entries whose date differs per institution; the detail view offers "change date". */
    val editable: Boolean = false,
)

/** Legacy present idea of a birthday entry; migrated to [PersonItem]s on load. */
data class Present(val id: String, val text: String, val done: Boolean = false)

/** A reminder on an entry; a blank [note] gets the standard "… in N days" text. */
data class EntryReminder(val daysBefore: Int, val note: String = "")

/** User-added entry (anniversaries; legacy birthdays are migrated to [Person]s). Extra JSON is kept for later fields. */
data class UserEntry(
    val id: String,
    val category: Category,
    val name: String,
    /** With an unknown year, [date] carries [NO_YEAR] and only month and day count. */
    val date: LocalDate,
    val yearKnown: Boolean = true,
    val presents: List<Present> = emptyList(),
    /** null = the category's default reminders; a list (possibly empty) once edited for this entry. */
    val reminders: List<EntryReminder>? = null,
    val extraJson: String = "{}",
)

/** Placeholder year for entries without one; a leap year, so 29 Feb survives. */
const val NO_YEAR = 2000

val DEFAULT_REMINDER_DAYS = listOf(28, 14, 3, 0)

enum class Category(val id: String, val label: String, val kind: Kind, val dateLabel: String, val reminderDays: List<Int>) {
    /** Only a form / import choice: a birthday is stored as a [Person] with a birth date. */
    BIRTHDAY("birthday", "Birthday", Kind.BIRTHDAY, "Date of birth", DEFAULT_REMINDER_DAYS),
    ANNIVERSARY("anniversary", "Anniversary", Kind.ANNIVERSARY, "Date", DEFAULT_REMINDER_DAYS);

    /** Subject of a reminder sentence: "Anna's birthday", or the anniversary's own name. */
    fun subject(name: String) = if (this == BIRTHDAY) "$name's birthday" else name

    companion object {
        fun byId(id: String): Category? = entries.firstOrNull { it.id == id }
    }
}

// ── People, their gift ideas and todos ───────────────────────────────

enum class ItemKind(val id: String, val label: String, val icon: String) {
    GIFT("gift", "Gift", "🎁"),
    TODO("todo", "Todo", "☑️");

    companion object {
        fun byId(id: String): ItemKind = entries.firstOrNull { it.id == id } ?: GIFT
    }
}

/**
 * A gift idea or todo, always owned by a [Person]. It is planned for at most one event ([eventKey] =
 * occurrence key, e.g. "christmas@2026") and flagged used ([usedOn]) once that event is over.
 * [eventName] / [eventDate] are a snapshot so the history survives deleted or moved events.
 */
data class PersonItem(
    val id: String,
    val kind: ItemKind,
    val title: String,
    val description: String = "",
    val links: List<String> = emptyList(),
    val done: Boolean = false,
    val created: LocalDate,
    val eventKey: String? = null,
    val eventName: String = "",
    val eventDate: LocalDate? = null,
    val usedOn: LocalDate? = null,
) {
    val used: Boolean get() = usedOn != null
    /** Collected on the person, not planned for an event yet, not used. */
    val open: Boolean get() = usedOn == null && eventKey == null
    val planned: Boolean get() = usedOn == null && eventKey != null
}

/** A person; with a [birthDate] they show up in the calendar as a birthday. */
data class Person(
    val id: String,
    val name: String,
    /** With an unknown year, carries [NO_YEAR] and only month and day count. */
    val birthDate: LocalDate? = null,
    val yearKnown: Boolean = true,
    /** null = default birthday reminders; a list (possibly empty) once edited. */
    val reminders: List<EntryReminder>? = null,
    val items: List<PersonItem> = emptyList(),
    val extraJson: String = "{}",
) {
    fun birthdaySubject() = "$name's birthday"
}

/** People attached to one occurrence. Their items reference the occurrence via [PersonItem.eventKey]. */
data class EventPlan(val people: List<String>)

/**
 * People shown for an occurrence: its own list, else carried over from the latest earlier year of the
 * same event, else [defaultPeople] (the birthday person). [carriedFrom] is the year it was carried from.
 */
data class ResolvedPlan(val people: List<String>, val own: Boolean, val carriedFrom: Int?, val previousYear: Int?)

fun splitKey(key: String): Pair<String, Int>? {
    val i = key.lastIndexOf('@')
    if (i <= 0) return null
    val year = key.substring(i + 1).toIntOrNull() ?: return null
    return key.substring(0, i) to year
}

fun resolvePlan(plans: Map<String, EventPlan>, key: String, defaultPeople: List<String>): ResolvedPlan {
    val split = splitKey(key)
    val previousYear = split?.let { (series, year) ->
        plans.keys.mapNotNull { k -> splitKey(k)?.takeIf { it.first == series && it.second < year }?.second }.maxOrNull()
    }
    plans[key]?.let { return ResolvedPlan(it.people, own = true, carriedFrom = null, previousYear = previousYear) }
    if (split != null && previousYear != null) {
        return ResolvedPlan(plans.getValue("${split.first}@$previousYear").people, own = false, carriedFrom = previousYear, previousYear = previousYear)
    }
    return ResolvedPlan(defaultPeople, own = false, carriedFrom = null, previousYear = null)
}

/**
 * Flags items whose event is over as used, and refreshes the event snapshot of planned ones.
 * [find] resolves an occurrence key (null if the event no longer exists). Returns null if nothing changed.
 */
fun settleItems(persons: List<Person>, today: LocalDate, find: (String) -> Occurrence?): List<Person>? {
    var changed = false
    val out = persons.map { p ->
        var pChanged = false
        val items = p.items.map { item ->
            val key = item.eventKey
            if (key == null || item.used) return@map item
            val occ = find(key)
            val date = occ?.date ?: item.eventDate
            val name = occ?.name ?: item.eventName
            val next = when {
                date != null && date.isBefore(today) -> item.copy(eventDate = date, eventName = name, usedOn = date)
                else -> item.copy(eventDate = date, eventName = name)
            }
            if (next != item) pChanged = true
            next
        }
        if (pChanged) { changed = true; p.copy(items = items) } else p
    }
    return if (changed) out else null
}

fun UserEntry.effectiveReminders(): List<EntryReminder> =
    reminders ?: category.reminderDays.map { EntryReminder(it) }

fun Person.effectiveReminders(): List<EntryReminder> =
    if (birthDate == null) emptyList() else reminders ?: DEFAULT_REMINDER_DAYS.map { EntryReminder(it) }

fun UserEntry.reminderText(r: EntryReminder): String = reminderText(category.subject(name), r)

fun Person.reminderText(r: EntryReminder): String = reminderText(birthdaySubject(), r)

fun reminderText(subject: String, r: EntryReminder): String {
    if (r.note.isNotBlank()) return r.note
    val d = r.daysBefore
    return when {
        d == 0 -> "$subject is today"
        d >= 7 && d % 7 == 0 -> "$subject in ${d / 7} weeks"
        else -> "$subject in $d days"
    }
}

/** Longest lead time a reminder can have. */
const val MAX_REMINDER_DAYS = 365

/** Birthday on [year]; 29 Feb falls on 28 Feb in non-leap years. */
fun anniversaryIn(date: LocalDate, year: Int): LocalDate = MonthDay.from(date).atYear(year)

/** One concrete calendar item. [key] is stable per item and year. */
data class Occurrence(
    val key: String,
    val date: LocalDate,
    val name: String,
    val kind: Kind,
    val tier: Tier,
    val tradition: Tradition? = null,
    val entry: UserEntry? = null,
    val person: Person? = null,
    val overridden: Boolean = false,
)

data class Reminder(val key: String, val occurrence: Occurrence, val daysBefore: Int, val text: String)

class Planner(
    private val catalog: List<Tradition>,
    private val entries: List<UserEntry>,
    private val persons: List<Person>,
    private val overrides: Map<String, LocalDate>,
    private val showOptional: Boolean,
) {
    private fun traditionIn(t: Tradition, year: Int): Occurrence {
        val key = overrideKey(t.id, year)
        val o = overrides[key]
        return Occurrence(key, o ?: t.rule.dateIn(year), t.name, t.kind, t.tier, tradition = t, overridden = o != null)
    }

    private fun entryIn(e: UserEntry, year: Int): Occurrence? {
        if (e.yearKnown && year < e.date.year) return null
        return Occurrence("${e.id}@$year", anniversaryIn(e.date, year), e.name, e.category.kind, Tier.CORE, entry = e)
    }

    private fun birthdayIn(p: Person, year: Int): Occurrence? {
        val b = p.birthDate ?: return null
        if (p.yearKnown && year < b.year) return null
        return Occurrence("${p.id}@$year", anniversaryIn(b, year), p.name, Kind.BIRTHDAY, Tier.CORE, person = p)
    }

    fun occurrences(from: LocalDate, to: LocalDate): List<Occurrence> {
        val out = ArrayList<Occurrence>()
        // One year margin on each side: an override may move a date across the year boundary.
        for (year in from.year - 1..to.year + 1) {
            for (t in catalog) {
                if (!showOptional && t.tier == Tier.OPTIONAL) continue
                out += traditionIn(t, year)
            }
            for (e in entries) entryIn(e, year)?.let { out += it }
            for (p in persons) birthdayIn(p, year)?.let { out += it }
        }
        return out.filter { !it.date.isBefore(from) && !it.date.isAfter(to) }
            .sortedWith(compareBy<Occurrence> { it.date }.thenBy { it.tier.ordinal }.thenBy { it.name })
    }

    /** The occurrence with this key, also for optional entries while they are hidden. */
    fun find(key: String): Occurrence? {
        val (id, year) = splitKey(key) ?: return null
        catalog.firstOrNull { it.id == id }?.let { return traditionIn(it, year) }
        entries.firstOrNull { it.id == id }?.let { return entryIn(it, year) }
        persons.firstOrNull { it.id == id }?.let { return birthdayIn(it, year) }
        return null
    }

    fun remindersFor(o: Occurrence): List<Reminder> {
        val t = o.tradition
        if (t != null) return t.prep.map { Reminder("${o.key}#${it.daysBefore}", o, it.daysBefore, it.text) }
        val e = o.entry
        val p = o.person
        val list = e?.effectiveReminders() ?: p?.effectiveReminders() ?: return emptyList()
        return list.map { r ->
            val key = if (r.note.isBlank()) "${o.key}#${r.daysBefore}" else "${o.key}#${r.daysBefore}#${r.note.hashCode()}"
            Reminder(key, o, r.daysBefore, e?.reminderText(r) ?: p!!.reminderText(r))
        }
    }

    /** Reminders whose fire date is [day]. */
    fun remindersOn(day: LocalDate): List<Reminder> =
        occurrences(day, day.plusDays(MAX_REMINDER_DAYS.toLong() + 1)).flatMap { remindersFor(it) }
            .filter { it.occurrence.date.minusDays(it.daysBefore.toLong()) == day }

    companion object {
        fun overrideKey(id: String, year: Int) = "$id@$year"
    }
}

fun daysUntil(today: LocalDate, date: LocalDate): Long = ChronoUnit.DAYS.between(today, date)

fun relativeLabel(today: LocalDate, date: LocalDate): String {
    val d = daysUntil(today, date)
    return when {
        d == 0L -> "today"
        d == 1L -> "tomorrow"
        d == -1L -> "yesterday"
        d > 0 -> "in $d days"
        else -> "${-d} days ago"
    }
}
