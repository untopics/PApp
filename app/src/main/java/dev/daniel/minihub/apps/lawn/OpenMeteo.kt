package dev.daniel.minihub.apps.lawn

import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.time.Instant
import java.time.LocalDate
import java.util.Locale

/** Open-Meteo forecast API: free, no API key. Blocking – call off the main thread. */
object OpenMeteo {
    private const val DAILY =
        "weather_code,temperature_2m_max,temperature_2m_min,precipitation_sum," +
            "wind_speed_10m_max,wind_gusts_10m_max,et0_fao_evapotranspiration"

    fun fetch(place: Place, now: Instant = Instant.now()): List<ForecastDay> {
        val url = String.format(
            Locale.US,
            "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f&daily=%s" +
                "&hourly=soil_temperature_6cm&timezone=auto&forecast_days=16" +
                "&wind_speed_unit=kmh&temperature_unit=celsius&precipitation_unit=mm",
            place.lat, place.lon, DAILY,
        )
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 15_000
        try {
            val code = conn.responseCode
            if (code != 200) throw IOException("HTTP $code")
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            return parse(JSONObject(body), now)
        } finally {
            conn.disconnect()
        }
    }

    private fun JSONArray.num(i: Int): Double? = if (i >= length() || isNull(i)) null else getDouble(i)

    fun parse(o: JSONObject, now: Instant): List<ForecastDay> {
        // Mean soil temperature per date from the hourly series.
        val soilByDate = mutableMapOf<String, MutableList<Double>>()
        o.optJSONObject("hourly")?.let { h ->
            val t = h.getJSONArray("time")
            val s = h.optJSONArray("soil_temperature_6cm")
            if (s != null) for (i in 0 until t.length()) {
                s.num(i)?.let { soilByDate.getOrPut(t.getString(i).take(10)) { mutableListOf() } += it }
            }
        }
        val d = o.getJSONObject("daily")
        val time = d.getJSONArray("time")
        val code = d.getJSONArray("weather_code")
        val tMax = d.getJSONArray("temperature_2m_max")
        val tMin = d.getJSONArray("temperature_2m_min")
        val rain = d.getJSONArray("precipitation_sum")
        val wind = d.getJSONArray("wind_speed_10m_max")
        val gust = d.getJSONArray("wind_gusts_10m_max")
        val et0 = d.getJSONArray("et0_fao_evapotranspiration")
        val out = mutableListOf<ForecastDay>()
        for (i in 0 until time.length()) {
            val date = time.getString(i)
            val hi = tMax.num(i) ?: continue
            val lo = tMin.num(i) ?: continue
            out += ForecastDay(
                date = LocalDate.parse(date),
                code = code.num(i)?.toInt() ?: -1,
                tMin = lo,
                tMax = hi,
                rain = rain.num(i) ?: 0.0,
                wind = wind.num(i) ?: 0.0,
                gust = gust.num(i) ?: 0.0,
                et0 = et0.num(i) ?: 0.0,
                soil = soilByDate[date]?.takeIf { it.isNotEmpty() }?.average(),
                fetchedAt = now,
            )
        }
        return out
    }
}
