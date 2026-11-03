package dev.daniel.minihub.apps.calories

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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.daniel.minihub.controls.SliderMode
import dev.daniel.minihub.controls.ValueScale
import dev.daniel.minihub.controls.ValueSlider
import dev.daniel.minihub.core.AppContext
import dev.daniel.minihub.core.ChoiceRow
import dev.daniel.minihub.core.JsonStore
import dev.daniel.minihub.core.MiniApp
import dev.daniel.minihub.core.MiniTopBar
import dev.daniel.minihub.core.rememberToday
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.IsoFields
import java.time.temporal.TemporalAdjusters
import java.util.Locale
import kotlin.math.roundToInt
import org.json.JSONObject

object CalorieApp : MiniApp {
    override val id = "calories"
    override val name = "Calories"
    override val icon = "🔥"
    override val accent = Color(0xFFFF7043)

    /** Exposed metric: "kcal" for a given date. */
    override fun readMetric(store: JsonStore, metric: String, date: LocalDate): Int? =
        if (metric == "kcal") CalorieRepo(store).get(date) else null

    @Composable
    override fun Content(ctx: AppContext) {
        val repo = remember { CalorieRepo(ctx.store) }
        CalorieScreen(repo, ctx.onBack)
    }
}

class CalorieRepo(private val store: JsonStore) {
    private val days = mutableStateMapOf<LocalDate, Int>()
    private val weights = mutableStateMapOf<LocalDate, Float>()

    /** Daily limit (chart line + "left/over"); null = none. */
    var limit by mutableStateOf<Int?>(null)
        private set

    init {
        val root = store.read()
        val obj = root.optJSONObject("days")
        obj?.keys()?.forEach { k -> runCatching { days[LocalDate.parse(k)] = obj.getInt(k) } }
        limit = root.optInt("limit", 0).takeIf { it > 0 }
        val w = root.optJSONObject("weights")
        w?.keys()?.forEach { k -> runCatching { weights[LocalDate.parse(k)] = w.getDouble(k).toFloat() } }
    }

    /** Latest logged weight on or before [date] and the day it was logged; null = never logged. */
    fun currentWeight(date: LocalDate): Pair<LocalDate, Float>? =
        weights.entries.filter { !it.key.isAfter(date) }.maxByOrNull { it.key }?.let { it.key to it.value }

    /** Logs [kg] for [date] (one value per day, a new one replaces it). */
    fun setWeight(date: LocalDate, kg: Float) {
        weights[date] = (kg * 10).roundToInt() / 10f
        save()
    }

    fun get(date: LocalDate): Int = days[date] ?: 0

    fun add(date: LocalDate, delta: Int) {
        val v = (get(date) + delta).coerceAtLeast(0)
        if (v == 0) days.remove(date) else days[date] = v
        save()
    }

    fun updateLimit(value: Int?) {
        val v = value?.takeIf { it > 0 }
        if (v == limit) return
        limit = v
        save()
    }

    private fun save() {
        val d = JSONObject()
        days.forEach { (k, value) -> d.put(k.toString(), value) }
        val root = JSONObject().put("days", d)
        limit?.let { root.put("limit", it) }
        if (weights.isNotEmpty()) {
            val w = JSONObject()
            weights.forEach { (k, v) -> w.put(k.toString(), v.toDouble()) }
            root.put("weights", w)
        }
        store.write(root)
    }
}

/** Calendar periods: Mon–Sun weeks, 1st–last months. [offset] 0 = the one containing today, -1 = previous, … */
private enum class Range(val label: String) {
    WEEK("Week") {
        override fun start(today: LocalDate, offset: Int): LocalDate =
            today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).plusWeeks(offset.toLong())
        override fun end(start: LocalDate): LocalDate = start.plusDays(6)
        override fun title(start: LocalDate): String {
            val end = end(start)
            val kw = start.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR)
            val from = if (start.month == end.month) start.dayOfMonth.toString() else start.format(DayMonth)
            return "W$kw · $from – ${end.format(DayMonth)}"
        }
    },
    MONTH("Month") {
        override fun start(today: LocalDate, offset: Int): LocalDate =
            today.withDayOfMonth(1).plusMonths(offset.toLong())
        override fun end(start: LocalDate): LocalDate = start.withDayOfMonth(start.lengthOfMonth())
        override fun title(start: LocalDate): String = start.format(MonthYear)
    };

    abstract fun start(today: LocalDate, offset: Int): LocalDate
    abstract fun end(start: LocalDate): LocalDate
    abstract fun title(start: LocalDate): String
}

private val DayMonth = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())
private val MonthYear = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())

/** +/− slider scale: 25 → 400 over the first quarter, 400 → 2000 over the remaining three quarters. */
private val KcalScale = ValueScale.piecewise(0f to 25f, 0.25f to 400f, 1f to 2000f)

/** Weight slider: ±1 kg linear each way from the current weight, committed in 0.1 kg steps. */
private val WeightScale = ValueScale.linear(0f, 1f)

@Composable
private fun CalorieScreen(repo: CalorieRepo, onBack: () -> Unit) {
    val today = rememberToday()
    var range by remember { mutableStateOf(Range.WEEK) }
    var offset by remember { mutableIntStateOf(0) } // 0 = current week/month
    var limitText by remember { mutableStateOf(repo.limit?.toString() ?: "") }
    val limit = repo.limit
    val value = repo.get(today)
    val fmt = remember { DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault()) }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        MiniTopBar("Calories", onBack)

        // --- Chart (top) ---
        Row(verticalAlignment = Alignment.CenterVertically) {
            ChoiceRow(Range.entries, range, { it.label }, { range = it; offset = 0 }, Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            OutlinedTextField(
                value = limitText,
                onValueChange = { s ->
                    limitText = s.filter { it.isDigit() }.take(5)
                    repo.updateLimit(limitText.toIntOrNull())
                },
                label = { Text("Limit") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.width(110.dp),
            )
        }
        val start = range.start(today, offset)
        val end = range.end(start)
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = { offset-- }) { Text("‹", fontSize = 26.sp) }
            Text(
                range.title(start),
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                // Tap the title to jump back to the current week/month
                modifier = Modifier.weight(1f).clickable(enabled = offset != 0) { offset = 0 },
                color = if (offset == 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.primary,
            )
            TextButton(onClick = { offset++ }, enabled = offset < 0) { Text("›", fontSize = 26.sp) }
        }
        Spacer(Modifier.height(4.dp))
        val dates = generateSequence(start) { it.plusDays(1) }.takeWhile { !it.isAfter(end) }.toList()
        val values = dates.map { repo.get(it) }
        CalorieChart(dates, values, limit, CalorieApp.accent, Modifier.fillMaxWidth().weight(1f))
        val logged = values.filter { it > 0 }
        Text(
            if (logged.isNotEmpty()) "Ø ${logged.sum() / logged.size} kcal over ${logged.size} logged days" else " ",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )

        // --- Controls (bottom) ---
        Spacer(Modifier.height(16.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    "$value",
                    fontSize = 56.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                    color = if (limit != null && value > limit) Color(0xFFE53935) else MaterialTheme.colorScheme.onSurface,
                )
                val sub = buildString {
                    append("kcal · ").append(today.format(fmt))
                    if (limit != null) {
                        val left = limit - value
                        append(" · ").append(if (left >= 0) "$left left" else "${-left} over")
                    }
                }
                Text(
                    sub,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(12.dp))
            WeightControl(repo, today, Modifier.width(120.dp))
        }
        Spacer(Modifier.height(16.dp))
        Row(
            Modifier.fillMaxWidth().padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ValueSlider(
                text = "− kcal",
                scale = KcalScale,
                step = 25f,
                format = { "−${it.roundToInt()}" },
                onRelease = { repo.add(today, -it.roundToInt()) },
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.weight(1f).height(72.dp),
            )
            ValueSlider(
                text = "+ kcal",
                scale = KcalScale,
                step = 25f,
                format = { "+${it.roundToInt()}" },
                onRelease = { repo.add(today, it.roundToInt()) },
                modifier = Modifier.weight(1f).height(72.dp),
            )
        }
    }
}

/**
 * Weight for today: a bidirectional slider that always starts at the latest logged weight
 * (±1 kg, 0.1 kg steps). Before the first entry it's a number field instead.
 */
@Composable
private fun WeightControl(repo: CalorieRepo, today: LocalDate, modifier: Modifier = Modifier) {
    val current = repo.currentWeight(today)
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        if (current == null) {
            var text by remember { mutableStateOf("") }
            fun commit() {
                text.replace(',', '.').toFloatOrNull()?.takeIf { it in 20f..400f }?.let { repo.setWeight(today, it) }
            }
            OutlinedTextField(
                value = text,
                onValueChange = { s -> text = s.filter { it.isDigit() || it == '.' || it == ',' }.take(5) },
                label = { Text("Weight kg") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { commit() }),
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            WeightSlider(repo, today, current.first, current.second)
        }
    }
}

@Composable
private fun WeightSlider(repo: CalorieRepo, today: LocalDate, date: LocalDate, kg: Float) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            if (date == today) "kg · today" else "kg · ${date.format(ShortDate)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        ValueSlider(
            text = "%.1f".format(kg),
            scale = WeightScale,
            step = 0.1f,
            mode = SliderMode.Bidirectional(start = kg),
            onRelease = { repo.setWeight(today, it) },
            color = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            modifier = Modifier.fillMaxWidth().height(64.dp),
        )
    }
}

private val ShortDate = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())
