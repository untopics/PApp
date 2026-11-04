package dev.daniel.minihub.apps.traditions

import java.nio.charset.Charset
import java.time.LocalDate

// Pure Kotlin (no Android imports), like TraditionsModel.

/** One importable line. [year] is null when the export marks it unknown. */
data class ImportRow(val line: Int, val name: String, val category: Category, val month: Int, val day: Int, val year: Int?) {
    fun date(): LocalDate = LocalDate.of(year ?: NO_YEAR, month, day)
}

data class ImportResult(val rows: List<ImportRow>, val unreadableLines: List<Int>)

/**
 * CSV export of the "Birthdays" app: `Name,Datum,Typ`, date as dd.MM.yyyy with 9999 for an unknown
 * year, Typ 1 = birthday, 2 = anniversary. Also accepts ';' or tab separators, quoted cells,
 * ISO dates and files without a header row.
 */
object BirthdaysCsv {
    private const val UNKNOWN_YEAR = 9999
    private val DOTTED = Regex("""^(\d{1,2})\.(\d{1,2})\.(\d{4})$""")
    private val ISO = Regex("""^(\d{4})-(\d{1,2})-(\d{1,2})""")

    /** UTF-8, falling back to Windows-1252 for exports with umlauts in the old Excel encoding. */
    fun decode(bytes: ByteArray): String {
        val utf = String(bytes, Charsets.UTF_8)
        val text = if ('�' in utf) String(bytes, Charset.forName("windows-1252")) else utf
        return text.removePrefix("﻿")
    }

    fun parse(text: String): ImportResult {
        val lines = text.removePrefix("﻿").split(Regex("\r\n|\n|\r"))
        val first = lines.firstOrNull { it.isNotBlank() } ?: return ImportResult(emptyList(), emptyList())
        val sep = listOf(';', ',', '\t').maxBy { c -> first.count { it == c } }

        val headerCells = split(first, sep).map { it.lowercase() }
        val hasHeader = "name" in headerCells
        val nameCol = if (hasHeader) headerCells.indexOf("name") else 0
        val dateCol = if (hasHeader) headerCells.indexOfFirst { it in setOf("datum", "date", "geburtstag", "birthday") } else 1
        val typeCol = if (hasHeader) headerCells.indexOfFirst { it in setOf("typ", "type") } else 2

        val rows = ArrayList<ImportRow>()
        val bad = ArrayList<Int>()
        var headerSkipped = !hasHeader
        lines.forEachIndexed { i, line ->
            if (line.isBlank()) return@forEachIndexed
            if (!headerSkipped) { headerSkipped = true; return@forEachIndexed }
            val cells = split(line, sep)
            val name = cells.getOrNull(nameCol).orEmpty()
            val date = parseDate(cells.getOrNull(dateCol).orEmpty())
            if (name.isEmpty() || date == null) { bad += i + 1; return@forEachIndexed }
            val cat = if (cells.getOrNull(typeCol) == "2") Category.ANNIVERSARY else Category.BIRTHDAY
            rows += ImportRow(i + 1, name, cat, date.first, date.second, date.third)
        }
        return ImportResult(rows, bad)
    }

    /** Rows not yet in [existing] (same kind, name and day), without repeats inside the file. */
    fun newRows(rows: List<ImportRow>, existing: List<UserEntry>): List<ImportRow> {
        val seen = existing.map { sameKey(it.category, it.name, it.date.monthValue, it.date.dayOfMonth) }.toHashSet()
        return rows.filter { seen.add(sameKey(it.category, it.name, it.month, it.day)) }
    }

    private fun sameKey(c: Category, name: String, month: Int, day: Int) = "${c.id}|${name.trim().lowercase()}|$month-$day"

    /** (month, day, year or null) */
    private fun parseDate(s: String): Triple<Int, Int, Int?>? {
        val (d, m, y) = DOTTED.find(s)?.destructured?.let { (d, m, y) -> Triple(d, m, y) }
            ?: ISO.find(s)?.destructured?.let { (y, m, d) -> Triple(d, m, y) }
            ?: return null
        val day = d.toInt()
        val month = m.toInt()
        val year = y.toInt().takeIf { it != UNKNOWN_YEAR }
        // Validate month/day in a leap year; a known year must also hold the date (29 Feb).
        if (runCatching { LocalDate.of(NO_YEAR, month, day) }.isFailure) return null
        if (year != null && runCatching { LocalDate.of(year, month, day) }.isFailure) return null
        return Triple(month, day, year)
    }

    /** Splits one line, honouring "quoted; cells" and "" escapes. */
    private fun split(line: String, sep: Char): List<String> {
        val out = ArrayList<String>()
        val cur = StringBuilder()
        var quoted = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            when {
                quoted && c == '"' && line.getOrNull(i + 1) == '"' -> { cur.append('"'); i++ }
                c == '"' -> quoted = !quoted
                !quoted && c == sep -> { out += cur.toString().trim(); cur.clear() }
                else -> cur.append(c)
            }
            i++
        }
        out += cur.toString().trim()
        return out
    }
}
