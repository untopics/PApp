package dev.daniel.minihub.apps.lawn

import java.time.LocalDate
import java.time.MonthDay
import java.time.temporal.ChronoUnit

// Timings are tuned for the Pannonian climate around the Neusiedler See: hot, dry summers,
// little summer rain, mild autumns. Edit freely – ids are stored, so don't rename them.

// ------------------------------------------------------------------ new lawn (build)

/** A step relative to the sowing day. [prep] = something to organise/order in advance. */
data class BuildStep(
    val id: String,
    val offset: Int,
    val title: String,
    val detail: String,
    val prep: Boolean = false,
)

val BUILD_STEPS = listOf(
    BuildStep("measure", -42, "Measure area & test soil",
        "Measure m² to calculate quantities. pH test kit (target 6–7), check drainage: does water pool after rain?", prep = true),
    BuildStep("order_soil", -35, "Organise topsoil / compost / sand",
        "Only if levelling or poor soil: ~1–2 m³ per 100 m² for a 1–2 cm layer. Delivery often takes 1–2 weeks.", prep = true),
    BuildStep("order_seed", -28, "Order seed",
        "Drought-tolerant mix (high share of tall fescue, plus ryegrass / smooth meadow-grass). 25–30 g/m² + 10 % reserve for patching.", prep = true),
    BuildStep("order_fert", -28, "Order starter fertilizer",
        "Phosphorus-rich lawn starter fertilizer, amount per pack for your area.", prep = true),
    BuildStep("book_tools", -21, "Book tiller, roller, spreader",
        "Rent a rotary tiller for the dig day, a lawn roller and a spreader for sowing day.", prep = true),
    BuildStep("clear", -21, "Clear the area",
        "Remove old turf, weeds with roots, stones. Weeds with runners (couch grass) come back if roots stay."),
    BuildStep("till", -18, "Loosen the soil",
        "Till / dig 15–20 cm deep, work in compost or sand. Not when the soil is wet."),
    BuildStep("level", -14, "Level, roll, water – let it settle",
        "Rake level, roll once, water. Leave ~10–14 days: soil settles and weed seeds sprout (stale seedbed)."),
    BuildStep("weed", -4, "Remove sprouted weeds, fine rake",
        "Hoe off sprouted weeds shallowly, fill dips, rake the top 1–2 cm fine and crumbly."),
    BuildStep("starter", -1, "Spread starter fertilizer",
        "Spread evenly with the spreader, rake in lightly."),
    BuildStep("sow", 0, "Sow",
        "No wind. 25–30 g/m²: half lengthwise, half crosswise. Rake in 0.5–1 cm, roll, water gently with a fine spray."),
    BuildStep("germinate", 1, "Germination: keep the surface moist",
        "2–3 short waterings per day (~2–3 L/m² each) for ~3 weeks. The top 1–2 cm must never dry out. Germination takes 7–21 days."),
    BuildStep("taper", 21, "Water less often, longer",
        "Every 1–2 days, 5–10 L/m². Drives roots deeper."),
    BuildStep("first_mow", 35, "First mow",
        "When grass is 8–10 cm: cut to 5–6 cm with a sharp blade, on dry grass. Then the lawn is 'Developing'."),
    BuildStep("first_feed", 49, "First regular fertilizer",
        "Normal lawn fertilizer at half dose. If it's already late autumn, wait for spring."),
)

data class Window(val start: LocalDate, val end: LocalDate, val label: String)

/** Sowing windows: early autumn is the best one here, mid spring is the second choice. */
private fun windowsOf(year: Int) = listOf(
    Window(LocalDate.of(year, 4, 15), LocalDate.of(year, 5, 20), "Spring"),
    Window(LocalDate.of(year, 8, 20), LocalDate.of(year, 9, 20), "Early autumn (best)"),
)

/** Minimum days from today for the preparation steps (clear, till, settle). */
const val MIN_PREP_DAYS = 21

/** Earliest sowing date that leaves enough prep time and falls into a sowing window. */
fun suggestSowing(today: LocalDate): Pair<LocalDate, Window> {
    val earliest = today.plusDays(MIN_PREP_DAYS.toLong())
    val windows = (today.year..today.year + 1).flatMap { windowsOf(it) }
    for (w in windows) {
        if (!earliest.isAfter(w.end)) return (if (earliest.isAfter(w.start)) earliest else w.start) to w
    }
    val w = windowsOf(today.year + 2).first()
    return w.start to w
}

fun windowContaining(date: LocalDate): Window? =
    windowsOf(date.year).firstOrNull { !date.isBefore(it.start) && !date.isAfter(it.end) }

fun BuildStep.due(sow: LocalDate): LocalDate = sow.plusDays(offset.toLong())

// ------------------------------------------------------------------ seasonal tasks

/** A yearly recurring task for [stages], active between [from] and [to] (may wrap the new year). */
data class SeasonTask(
    val id: String,
    val stages: Set<Stage>,
    val from: MonthDay,
    val to: MonthDay,
    val title: String,
    val detail: String,
    /** Only meaningful once the lawn is at least this old (needs a known sowing date). */
    val minAgeDays: Int = 0,
    /** Worth a notification when its window opens. */
    val notify: Boolean = true,
)

private fun md(m: Int, d: Int) = MonthDay.of(m, d)
private val DEV = setOf(Stage.DEVELOP)
private val EST = setOf(Stage.ESTABLISHED)

val SEASON_TASKS = listOf(
    // ---- developing (first year after sowing)
    SeasonTask("dev_mow", DEV, md(3, 15), md(10, 31), "Mow regularly",
        "Never remove more than 1/3 of the blade; keep 5–6 cm. Sharp blade, dry grass.", notify = false),
    SeasonTask("dev_feed_spring", DEV, md(4, 1), md(4, 30), "Fertilize (spring)",
        "Slow-release lawn fertilizer, normal dose, water in."),
    SeasonTask("dev_overseed_spring", DEV, md(4, 15), md(5, 20), "Close bare patches",
        "Scratch the surface open, 15–20 g/m² seed, press in, keep moist for 2–3 weeks (soil ≥ 10 °C)."),
    SeasonTask("dev_feed_summer", DEV, md(6, 1), md(6, 20), "Fertilize before the heat",
        "Second feed before July. Never fertilize during a heat wave or on dry soil."),
    SeasonTask("dev_summer", DEV, md(6, 21), md(8, 19), "Summer: water deep, mow high",
        "Mowing height 6 cm. Water early morning, 10–15 L/m² every 2–3 days in dry spells – young roots dry out faster.", notify = false),
    SeasonTask("dev_overseed_autumn", DEV, md(8, 20), md(9, 20), "Overseed thin areas (best window)",
        "Warm soil + autumn rain = best establishment. 15–20 g/m² on thin spots, keep moist."),
    SeasonTask("dev_aerate", DEV, md(9, 1), md(9, 30), "First aeration",
        "Hollow-tine aerate compacted areas, sweep sand into the holes. Not in the first year; no scarifying yet either.",
        minAgeDays = 330),
    SeasonTask("dev_feed_autumn", DEV, md(9, 20), md(10, 20), "Autumn fertilizer",
        "Potassium-rich autumn fertilizer for winter hardiness."),
    SeasonTask("dev_winter", DEV, md(11, 1), md(2, 28), "Winter rest",
        "Last cut at 5–6 cm. Rake leaves off. Don't walk on frozen or frosty grass – young blades break.", notify = false),

    // ---- established
    SeasonTask("est_spring_clean", EST, md(3, 1), md(3, 31), "Spring clean-up",
        "Rake off debris. First mow once it grows (soil > 5 °C), not lower than 5 cm."),
    SeasonTask("est_feed_spring", EST, md(3, 20), md(4, 20), "Fertilize (spring)",
        "Slow-release lawn fertilizer when growth is active."),
    SeasonTask("est_scarify", EST, md(4, 10), md(5, 10), "Scarify + overseed",
        "If thatch > 1 cm: scarify 2–3 weeks after feeding, then overseed thin spots and keep moist."),
    SeasonTask("est_aerate_spring", EST, md(4, 15), md(5, 20), "Aerate (spring)",
        "Hollow-tine where compacted (paths, play areas), top-dress with sand."),
    SeasonTask("est_feed_summer", EST, md(6, 1), md(6, 20), "Fertilize before the heat",
        "Second feed, water in. No fertilizer during heat waves."),
    SeasonTask("est_summer", EST, md(6, 21), md(8, 19), "Summer: deep, rare watering",
        "Mow at 6 cm. When dry: 15–20 L/m² early morning, 1–2× per week. A brown dormant lawn recovers; daily sprinkling breeds shallow roots.",
        notify = false),
    SeasonTask("est_overseed", EST, md(8, 20), md(9, 20), "Overseed (best window)",
        "Overseed thin areas while soil is warm and autumn rain starts."),
    SeasonTask("est_aerate_autumn", EST, md(9, 1), md(9, 30), "Aerate (autumn)",
        "Best time for aeration: roots regrow before winter."),
    SeasonTask("est_feed_autumn", EST, md(9, 15), md(10, 15), "Autumn fertilizer",
        "Potassium-rich autumn fertilizer."),
    SeasonTask("est_leaves", EST, md(10, 15), md(11, 30), "Leaves & last mow",
        "Remove leaves weekly. Last mow at ~5 cm when growth stops."),
    SeasonTask("est_winter", EST, md(12, 1), md(2, 28), "Winter rest",
        "Keep off frozen or waterlogged grass. Service the mower, sharpen the blade.", notify = false),
)

private fun MonthDay.on(year: Int): LocalDate = atYear(year)

/** The concrete occurrence of this task that contains [date], or null. */
fun SeasonTask.occurrence(date: LocalDate): Pair<LocalDate, LocalDate>? {
    for (y in listOf(date.year - 1, date.year)) {
        val s = from.on(y)
        val e = if (to < from) to.on(y + 1) else to.on(y)
        if (!date.isBefore(s) && !date.isAfter(e)) return s to e
    }
    return null
}

/** Next occurrence start strictly after [date]. */
fun SeasonTask.nextStart(date: LocalDate): LocalDate {
    val s = from.on(date.year)
    return if (s.isAfter(date)) s else from.on(date.year + 1)
}

/** Age gate: true if this task is too early for the lawn's age. Unknown sowing date = no gate. */
fun SeasonTask.tooYoung(sow: LocalDate?, date: LocalDate): Boolean =
    minAgeDays > 0 && sow != null && ChronoUnit.DAYS.between(sow, date) < minAgeDays

// ------------------------------------------------------------------ watering

/**
 * Today's watering advice. There is no weather history, so this uses today + the coming days:
 * reference evapotranspiration (ET0) minus forecast rain.
 */
fun waterAdvice(stage: Stage, days: List<ForecastDay>, today: LocalDate, sow: LocalDate?): String {
    val ahead = days.filter { !it.date.isBefore(today) }
    val d0 = ahead.firstOrNull() ?: return "No forecast yet."
    fun deficit(n: Int) = ahead.take(n).sumOf { it.deficit }
    fun rain(n: Int) = ahead.take(n).sumOf { it.rain }
    return when (stage) {
        Stage.BUILD -> {
            val age = sow?.let { ChronoUnit.DAYS.between(it, today) }
            when {
                age == null || age < 0 -> "Before sowing: water the prepared bed only to let it settle and sprout weeds."
                age <= 21 -> if (d0.rain >= 5)
                    "Germination: ${"%.0f".format(d0.rain)} mm rain forecast today – check the surface, skip rounds while it stays moist."
                else "Germination: 2–3 short waterings today (~2–3 L/m² each), surface must stay moist." +
                    if (d0.tMax >= Thresholds.HEAT_YOUNG_C) " Hot day – add a midday round." else ""
                age <= 42 -> if (rain(2) >= 8) "Rain covers it – skip watering." else "Every 1–2 days, 5–10 L/m²."
                else -> "Lawn is past establishment – see 'Developing'."
            }
        }
        Stage.DEVELOP -> when {
            d0.tMax < Thresholds.GROWTH_MIN_TMAX -> "No watering needed – too cool for growth."
            deficit(3) >= 8 -> "Water today: 10–15 L/m², early morning (3-day deficit ${"%.0f".format(deficit(3))} mm)."
            else -> "No watering needed (rain or low evaporation in the next days)."
        }
        Stage.ESTABLISHED -> when {
            d0.tMax < Thresholds.GROWTH_MIN_TMAX -> "No watering needed – dormant / slow growth."
            deficit(4) >= 14 && rain(2) < 5 -> "Deep watering: 15–20 L/m² early morning (4-day deficit ${"%.0f".format(deficit(4))} mm)."
            else -> "No watering needed."
        }
    }
}
