package dev.daniel.minihub.apps.lawn

import dev.daniel.minihub.core.JsonStore
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.LocalDate

/**
 * Load/save for the lawn JSON file. The UI and the background job both write it, so every
 * change is a read-modify-write under one lock.
 */
object LawnStore {
    const val APP_ID = "lawn"
    private val LOCK = Any()

    fun load(store: JsonStore): LawnData = synchronized(LOCK) { runCatching { decode(store.read()) }.getOrDefault(LawnData()) }

    fun update(store: JsonStore, f: (LawnData) -> LawnData): LawnData = synchronized(LOCK) {
        val next = f(runCatching { decode(store.read()) }.getOrDefault(LawnData()))
        store.write(encode(next))
        next
    }

    /**
     * Replace forecast entries with freshly fetched ones and drop past days.
     * Entries the new fetch doesn't contain keep their old [ForecastDay.fetchedAt].
     */
    fun mergeForecast(old: List<ForecastDay>, fresh: List<ForecastDay>, today: LocalDate): List<ForecastDay> {
        val byDate = old.associateBy { it.date }.toMutableMap()
        fresh.forEach { byDate[it.date] = it }
        return byDate.values.filter { !it.date.isBefore(today) }.sortedBy { it.date }
    }

    // ---- JSON

    private fun decode(o: JSONObject): LawnData {
        val place = o.optJSONObject("place")?.let {
            Place(it.getString("name"), it.getDouble("lat"), it.getDouble("lon"))
        } ?: Place.OGGAU
        val days = mutableListOf<ForecastDay>()
        o.optJSONArray("days")?.let { arr ->
            for (i in 0 until arr.length()) {
                val d = arr.getJSONObject(i)
                days += ForecastDay(
                    date = LocalDate.parse(d.getString("date")),
                    code = d.optInt("code"),
                    tMin = d.getDouble("tmin"),
                    tMax = d.getDouble("tmax"),
                    rain = d.optDouble("rain", 0.0),
                    wind = d.optDouble("wind", 0.0),
                    gust = d.optDouble("gust", 0.0),
                    et0 = d.optDouble("et0", 0.0),
                    soil = if (d.has("soil")) d.getDouble("soil") else null,
                    fetchedAt = Instant.ofEpochMilli(d.getLong("fetchedAt")),
                )
            }
        }
        val done = o.optJSONArray("done")?.let { a -> (0 until a.length()).map { a.getString(it) }.toSet() } ?: emptySet()
        val notify = o.optJSONArray("notify")?.let { a -> (0 until a.length()).mapNotNull { Stage.of(a.getString(it)) }.toSet() } ?: emptySet()
        val notified = o.optJSONObject("notified")?.let { n ->
            n.keys().asSequence().associateWith { LocalDate.parse(n.getString(it)) }
        } ?: emptyMap()
        return LawnData(
            place = place,
            days = days.sortedBy { it.date },
            sowDate = o.optString("sowDate").takeIf { it.isNotEmpty() }?.let(LocalDate::parse),
            done = done,
            notify = notify,
            view = Stage.of(o.optString("view")) ?: Stage.BUILD,
            notified = notified,
        )
    }

    private fun encode(d: LawnData): JSONObject {
        val days = JSONArray()
        d.days.forEach { f ->
            days.put(JSONObject().apply {
                put("date", f.date.toString())
                put("code", f.code)
                put("tmin", f.tMin)
                put("tmax", f.tMax)
                put("rain", f.rain)
                put("wind", f.wind)
                put("gust", f.gust)
                put("et0", f.et0)
                f.soil?.let { put("soil", it) }
                put("fetchedAt", f.fetchedAt.toEpochMilli())
            })
        }
        // Dedupe keys only need to live a bit longer than a year (yearly tasks).
        val cutoff = LocalDate.now().minusDays(400)
        val notified = JSONObject()
        d.notified.filterValues { it.isAfter(cutoff) }.forEach { (k, v) -> notified.put(k, v.toString()) }
        return JSONObject().apply {
            put("place", JSONObject().put("name", d.place.name).put("lat", d.place.lat).put("lon", d.place.lon))
            put("days", days)
            d.sowDate?.let { put("sowDate", it.toString()) }
            put("done", JSONArray(d.done.toList()))
            put("notify", JSONArray(d.notify.map { it.id }))
            put("view", d.view.id)
            put("notified", notified)
        }
    }
}
