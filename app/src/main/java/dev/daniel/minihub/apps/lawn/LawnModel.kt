package dev.daniel.minihub.apps.lawn

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.Month

/** The three lawn stages. The id is stored – don't rename. */
enum class Stage(val id: String, val label: String) {
    BUILD("build", "New lawn"),
    DEVELOP("develop", "Developing"),
    ESTABLISHED("established", "Established");

    companion object {
        fun of(id: String?): Stage? = entries.firstOrNull { it.id == id }
    }
}

/** Forecast location. Fixed to Oggau for now; stored so a picker can be added later. */
data class Place(val name: String, val lat: Double, val lon: Double) {
    companion object {
        val OGGAU = Place("Oggau am Neusiedler See", 47.834, 16.663)
    }
}

/**
 * One forecast day. [fetchedAt] is when *this* entry was last fetched – entries are replaced
 * on every fetch and dropped once the date is past, so there is never any weather history.
 */
data class ForecastDay(
    val date: LocalDate,
    val code: Int,
    val tMin: Double,
    val tMax: Double,
    val rain: Double,      // mm
    val wind: Double,      // km/h, max mean wind
    val gust: Double,      // km/h, max gust
    val et0: Double,       // mm, FAO reference evapotranspiration
    val soil: Double?,     // °C, mean soil temperature at 6 cm
    val fetchedAt: Instant,
) {
    fun ageHours(now: Instant = Instant.now()): Long = Duration.between(fetchedAt, now).toHours()

    /** Fresh enough to base a notification on. */
    fun isFresh(now: Instant = Instant.now()): Boolean = ageHours(now) < MAX_NOTIFY_AGE_HOURS

    val deficit: Double get() = et0 - rain

    companion object {
        const val MAX_NOTIFY_AGE_HOURS = 24
    }
}

/**
 * Everything the lawn app stores. [view] only selects what's shown; it never affects
 * fetching or notifications. [notify] is the set of stages whose events are notified.
 */
data class LawnData(
    val place: Place = Place.OGGAU,
    val days: List<ForecastDay> = emptyList(),
    val sowDate: LocalDate? = null,
    val done: Set<String> = emptySet(),
    val notify: Set<Stage> = emptySet(),
    val view: Stage = Stage.BUILD,
    /** Dedupe: notification key → day it was last posted. */
    val notified: Map<String, LocalDate> = emptyMap(),
)

/** Critical weather events. Name is stored as dedupe key – don't rename. */
enum class Wx(val symbol: String, val label: String) {
    FROST("❄️", "Frost"),
    HEAT("🔥", "Heat"),
    WIND("💨", "Strong wind"),
    STORM_RAIN("🌧️", "Heavy rain"),
    DRY("💧", "Water"),
}

/**
 * Thresholds per stage. A fresh seedbed cares about wind (seed and topsoil blow away / dry out)
 * and heavy rain (seed washes out); a young lawn about frost and drought; an established lawn
 * mostly about long dry, hot spells.
 */
object Thresholds {
    const val FROST_C = 1.0               // ground frost possible at ≤1 °C air minimum
    const val HEAT_YOUNG_C = 30.0
    const val HEAT_ESTABLISHED_C = 32.0
    const val GUST_SEEDBED_KMH = 50.0
    const val HEAVY_RAIN_MM = 20.0
    const val DRY_SEEDBED_MM = 2.0        // daily ET0 - rain
    const val DRY_YOUNG_MM = 3.0          // daily ET0 - rain
    const val DRY_ESTABLISHED_3D_MM = 12.0 // 3-day ET0 - rain starting that day
    const val GROWTH_MIN_TMAX = 12.0      // below this, grass barely grows / uses water
}

/** Events for [days][i] that matter for [stage]. Needs the list for multi-day rules. */
fun eventsFor(days: List<ForecastDay>, i: Int, stage: Stage): Set<Wx> {
    val d = days[i]
    val out = mutableSetOf<Wx>()
    val growing = d.tMax >= Thresholds.GROWTH_MIN_TMAX
    when (stage) {
        Stage.BUILD -> {
            if (d.tMin <= Thresholds.FROST_C) out += Wx.FROST
            if (d.tMax >= Thresholds.HEAT_YOUNG_C) out += Wx.HEAT
            if (d.gust >= Thresholds.GUST_SEEDBED_KMH) out += Wx.WIND
            if (d.rain >= Thresholds.HEAVY_RAIN_MM) out += Wx.STORM_RAIN
            if (growing && d.deficit >= Thresholds.DRY_SEEDBED_MM) out += Wx.DRY
        }
        Stage.DEVELOP -> {
            if (d.tMin <= Thresholds.FROST_C) out += Wx.FROST
            if (d.tMax >= Thresholds.HEAT_YOUNG_C) out += Wx.HEAT
            if (growing && d.deficit >= Thresholds.DRY_YOUNG_MM) out += Wx.DRY
        }
        Stage.ESTABLISHED -> {
            if (d.tMax >= Thresholds.HEAT_ESTABLISHED_C) out += Wx.HEAT
            // Looks ahead (there is no history): this day and the next two.
            val window = days.subList(i, (i + 3).coerceAtMost(days.size))
            if (growing && window.size == 3 &&
                window.sumOf { it.deficit } >= Thresholds.DRY_ESTABLISHED_3D_MM &&
                window.none { it.rain >= 5.0 }
            ) out += Wx.DRY
        }
    }
    return out
}

/** How many forecast days are worth showing for a stage in a given season. */
fun forecastRange(stage: Stage, today: LocalDate, sowDate: LocalDate?): Int {
    val m = today.month
    val growingSeason = m in Month.APRIL..Month.OCTOBER
    val sowingSeason = m in Month.MARCH..Month.MAY || m in Month.AUGUST..Month.SEPTEMBER
    return when (stage) {
        Stage.BUILD -> {
            val untilSow = sowDate?.let { java.time.temporal.ChronoUnit.DAYS.between(today, it) }
            when {
                untilSow != null && untilSow in -21L..16L -> 16
                sowingSeason -> 14
                else -> 7
            }
        }
        Stage.DEVELOP -> if (growingSeason) 10 else 7
        Stage.ESTABLISHED -> if (growingSeason) 7 else 5
    }
}

fun weatherEmoji(code: Int): String = when (code) {
    0 -> "☀️"
    1, 2 -> "🌤️"
    3 -> "☁️"
    45, 48 -> "🌫️"
    in 51..57 -> "🌦️"
    in 61..67 -> "🌧️"
    in 71..77 -> "🌨️"
    80, 81, 82 -> "🌦️"
    85, 86 -> "🌨️"
    in 95..99 -> "⛈️"
    else -> "·"
}
