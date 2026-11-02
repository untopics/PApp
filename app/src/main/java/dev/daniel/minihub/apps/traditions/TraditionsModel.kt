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

/** User-added entry. [category] decides the fields; extra JSON is kept for later fields. */
data class UserEntry(
    val id: String,
    val category: Category,
    val name: String,
    val date: LocalDate,
    val extraJson: String = "{}",
)

enum class Category(val id: String, val label: String, val kind: Kind, val reminderDays: List<Int>) {
    BIRTHDAY("birthday", "Birthday", Kind.BIRTHDAY, listOf(28, 14, 3, 0));

    companion object {
        fun byId(id: String): Category? = entries.firstOrNull { it.id == id }
    }
}

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
    val overridden: Boolean = false,
)

data class Reminder(val key: String, val occurrence: Occurrence, val daysBefore: Int, val text: String)

class Planner(
    private val catalog: List<Tradition>,
    private val entries: List<UserEntry>,
    private val overrides: Map<String, LocalDate>,
    private val showOptional: Boolean,
) {
    fun occurrences(from: LocalDate, to: LocalDate): List<Occurrence> {
        val out = ArrayList<Occurrence>()
        // One year margin on each side: an override may move a date across the year boundary.
        for (year in from.year - 1..to.year + 1) {
            for (t in catalog) {
                if (!showOptional && t.tier == Tier.OPTIONAL) continue
                val key = overrideKey(t.id, year)
                val o = overrides[key]
                out += Occurrence(key, o ?: t.rule.dateIn(year), t.name, t.kind, t.tier, tradition = t, overridden = o != null)
            }
            for (e in entries) {
                if (year < e.date.year) continue
                out += Occurrence("${e.id}@$year", anniversaryIn(e.date, year), e.name, e.category.kind, Tier.CORE, entry = e)
            }
        }
        return out.filter { !it.date.isBefore(from) && !it.date.isAfter(to) }
            .sortedWith(compareBy<Occurrence> { it.date }.thenBy { it.tier.ordinal }.thenBy { it.name })
    }

    fun remindersFor(o: Occurrence): List<Reminder> {
        val t = o.tradition
        if (t != null) return t.prep.map { Reminder("${o.key}#${it.daysBefore}", o, it.daysBefore, it.text) }
        val e = o.entry ?: return emptyList()
        return e.category.reminderDays.map { d ->
            val text = when {
                d == 0 -> "${e.name}'s birthday is today"
                d >= 7 && d % 7 == 0 -> "${e.name}'s birthday in ${d / 7} weeks"
                else -> "${e.name}'s birthday in $d days"
            }
            Reminder("${o.key}#$d", o, d, text)
        }
    }

    /** Reminders whose fire date is [day]. */
    fun remindersOn(day: LocalDate): List<Reminder> =
        occurrences(day, day.plusDays(60)).flatMap { remindersFor(it) }
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
