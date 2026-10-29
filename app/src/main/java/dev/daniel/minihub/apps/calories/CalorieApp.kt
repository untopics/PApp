package dev.daniel.minihub.apps.calories

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.daniel.minihub.core.AppContext
import dev.daniel.minihub.core.ChoiceRow
import dev.daniel.minihub.core.JsonStore
import dev.daniel.minihub.core.MiniApp
import dev.daniel.minihub.core.MiniTopBar
import dev.daniel.minihub.core.StepRow
import dev.daniel.minihub.core.rememberToday
import org.json.JSONObject
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

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

    init {
        val obj = store.read().optJSONObject("days")
        obj?.keys()?.forEach { k -> runCatching { days[LocalDate.parse(k)] = obj.getInt(k) } }
    }

    fun get(date: LocalDate): Int = days[date] ?: 0

    fun add(date: LocalDate, delta: Int) {
        val v = (get(date) + delta).coerceAtLeast(0)
        if (v == 0) days.remove(date) else days[date] = v
        val d = JSONObject()
        days.forEach { (k, value) -> d.put(k.toString(), value) }
        store.write(JSONObject().put("days", d))
    }
}

private enum class Range(val label: String, val days: Int) { WEEK("Week", 7), MONTH("Month", 30) }

@Composable
private fun CalorieScreen(repo: CalorieRepo, onBack: () -> Unit) {
    val today = rememberToday()
    var range by remember { mutableStateOf(Range.WEEK) }
    var limitText by remember { mutableStateOf("") } // intentionally not persisted
    val limit = limitText.toIntOrNull()?.takeIf { it > 0 }
    val value = repo.get(today)
    val fmt = remember { DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault()) }

    Column(
        Modifier.fillMaxSize().padding(horizontal = 16.dp).verticalScroll(rememberScrollState()),
    ) {
        MiniTopBar("Calories", onBack)
        Spacer(Modifier.height(12.dp))
        Text(
            "$value",
            fontSize = 72.sp,
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
        Spacer(Modifier.height(20.dp))
        StepRow(listOf(10, 50, 100), { repo.add(today, it) }, height = 64.dp)
        Spacer(Modifier.height(8.dp))
        StepRow(listOf(-10, -50, -100), { repo.add(today, it) }, height = 64.dp)
        Spacer(Modifier.height(24.dp))
        ChoiceRow(Range.entries, range, { it.label }, { range = it })
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = limitText,
            onValueChange = { s -> limitText = s.filter { it.isDigit() }.take(5) },
            label = { Text("Limit (chart only)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(12.dp))
        val dates = (range.days - 1 downTo 0).map { today.minusDays(it.toLong()) }
        val values = dates.map { repo.get(it) }
        CalorieChart(dates, values, limit, CalorieApp.accent, Modifier.fillMaxWidth().height(240.dp))
        val logged = values.filter { it > 0 }
        if (logged.isNotEmpty()) {
            Text(
                "Ø ${logged.sum() / logged.size} kcal over ${logged.size} logged days",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp, bottom = 16.dp),
            )
        }
    }
}
