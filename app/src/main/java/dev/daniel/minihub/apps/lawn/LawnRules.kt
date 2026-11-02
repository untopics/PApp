package dev.daniel.minihub.apps.lawn

import java.time.Instant
import java.time.LocalDate
import java.time.Month
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.roundToInt

/** A notification to post. [key] is the dedupe key. */
data class Note(val key: String, val title: String, val text: String)

/**
 * Notification rules. Runs for *all* stages enabled in [LawnData.notify], independent of
 * the stage currently shown.
 *
 * Dedupe:
 *  - weather events ("wx:<kind>") and the daily germination reminder: at most once per day,
 *    even if the forecast changes later that day;
 *  - plan steps / seasonal tasks: once per occurrence.
 * Weather notifications only use forecast entries fetched within the last 24 h.
 */
object LawnRules {
    /** How far ahead weather events are announced. */
    private const val LOOKAHEAD_DAYS = 2L

    private fun inRange(today: LocalDate, sow: LocalDate, from: Int, to: Int): Boolean {
        val age = ChronoUnit.DAYS.between(sow, today)
        return age in from..to
    }

    /** Whether a weather event kind is worth notifying for a stage right now. */
    fun allowed(stage: Stage, kind: Wx, today: LocalDate, sow: LocalDate?): Boolean = when (stage) {
        // A new lawn only exists around a planned sowing date.
        Stage.BUILD -> sow != null && when (kind) {
            Wx.FROST -> inRange(today, sow, -7, 42)
            Wx.HEAT -> inRange(today, sow, -2, 42)
            Wx.WIND -> inRange(today, sow, -3, 21)
            Wx.STORM_RAIN -> inRange(today, sow, -1, 21)
            Wx.DRY -> inRange(today, sow, 22, 42) // days 0–21 have their own daily reminder
        }
        // Frost matters when it's unusual: early in autumn and late in spring, not every winter night.
        Stage.DEVELOP -> when (kind) {
            Wx.FROST -> today.month in Month.SEPTEMBER..Month.NOVEMBER || today.month in Month.MARCH..Month.MAY
            Wx.HEAT, Wx.DRY -> true
            else -> false
        }
        Stage.ESTABLISHED -> kind == Wx.HEAT || kind == Wx.DRY
    }

    fun advice(kind: Wx, stage: Stage): String = when (kind) {
        Wx.FROST -> when (stage) {
            Stage.BUILD -> "Don't sow or roll; frost damages seedlings. Sow once nights stay above ~5 °C."
            else -> "Keep off the frosted grass until it thaws. No mowing, no fertilizer."
        }
        Wx.HEAT -> when (stage) {
            Stage.BUILD -> "Seedbed dries fast – add a midday watering round."
            Stage.DEVELOP -> "Water early morning 10–15 L/m², mow high, no fertilizer."
            Stage.ESTABLISHED -> "Mow at 6 cm or skip, water deeply early morning, no fertilizer."
        }
        Wx.WIND -> "Don't sow in wind. Wind dries the seedbed and blows seed away – water more."
        Wx.STORM_RAIN -> "Heavy rain can wash out seed. Don't sow right before; reseed washed spots afterwards."
        Wx.DRY -> when (stage) {
            Stage.BUILD -> "Young seedlings: water every 1–2 days, 5–10 L/m²."
            Stage.DEVELOP -> "Water 10–15 L/m² early morning."
            Stage.ESTABLISHED -> "Deep watering 15–20 L/m² early morning."
        }
    }

    private fun dayLabel(d: LocalDate, today: LocalDate): String = when (ChronoUnit.DAYS.between(today, d)) {
        0L -> "today"
        1L -> "tomorrow"
        else -> d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())
    }

    private fun value(kind: Wx, d: ForecastDay): String = when (kind) {
        Wx.FROST -> "${d.tMin.roundToInt()}°"
        Wx.HEAT -> "${d.tMax.roundToInt()}°"
        Wx.WIND -> "${d.gust.roundToInt()} km/h gusts"
        Wx.STORM_RAIN -> "${d.rain.roundToInt()} mm"
        Wx.DRY -> "deficit ${d.deficit.roundToInt()} mm"
    }

    fun pending(data: LawnData, today: LocalDate, now: Instant): List<Note> {
        if (data.notify.isEmpty()) return emptyList()
        val notes = mutableListOf<Note>()
        val sow = data.sowDate
        val stages = Stage.entries.filter { it in data.notify } // fixed priority order
        fun notifiedToday(key: String) = data.notified[key] == today
        fun notifiedEver(key: String) = data.notified.containsKey(key)

        // ---- weather events
        val days = data.days
        for (kind in Wx.entries) {
            val key = "wx:${kind.name}"
            if (notifiedToday(key)) continue
            val horizon = if (kind == Wx.DRY) 0L else LOOKAHEAD_DAYS
            var hit: ForecastDay? = null
            val forStages = mutableListOf<Stage>()
            for ((i, d) in days.withIndex()) {
                val ahead = ChronoUnit.DAYS.between(today, d.date)
                if (ahead < 0 || ahead > horizon || !d.isFresh(now)) continue
                for (s in stages) {
                    if (allowed(s, kind, today, sow) && kind in eventsFor(days, i, s)) {
                        if (hit == null) hit = d
                        if (s !in forStages) forStages += s
                    }
                }
                if (hit != null) break
            }
            val d = hit ?: continue
            val advice = forStages.map { advice(kind, it) }.distinct().joinToString(" ")
            notes += Note(key, "${kind.symbol} ${kind.label} ${dayLabel(d.date, today)}: ${value(kind, d)}", advice)
        }

        // ---- new lawn plan
        if (Stage.BUILD in data.notify && sow != null) {
            for (step in BUILD_STEPS) {
                if (step.id in data.done || step.id == "germinate") continue
                val due = step.due(sow)
                val late = ChronoUnit.DAYS.between(due, today)
                val key = "build:${step.id}:$sow"
                if (late in 0..2 && !notifiedEver(key)) {
                    val prefix = if (step.prep) "Organise: " else ""
                    notes += Note(key, "🌱 $prefix${step.title}", step.detail)
                }
            }
            // Daily germination reminder, unless real rain is forecast for today.
            val age = ChronoUnit.DAYS.between(sow, today)
            val key = "build:water"
            if (age in 0..21 && !notifiedToday(key)) {
                val d0 = days.firstOrNull { it.date == today }?.takeIf { it.isFresh(now) }
                if (d0 == null || d0.rain < 5.0) {
                    notes += Note(key, "💧 Keep the seedbed moist", "Day $age after sowing: 2–3 short waterings today, surface must not dry out.")
                }
            }
        }

        // ---- seasonal tasks: once when the window opens (within its first week)
        for (t in SEASON_TASKS) {
            if (!t.notify || t.stages.none { it in data.notify }) continue
            val (start, _) = t.occurrence(today) ?: continue
            if (ChronoUnit.DAYS.between(start, today) > 6) continue
            if (t.tooYoung(sow, today)) continue
            val key = "task:${t.id}:$start"
            if (!notifiedEver(key)) notes += Note(key, "🌿 ${t.title}", t.detail)
        }
        return notes
    }
}
