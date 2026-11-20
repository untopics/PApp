package dev.daniel.minihub.apps.traditions

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class TraditionsPeopleTest {
    private val today = LocalDate.of(2026, 11, 20)

    private val v1 = """
        {
          "showOptional": false,
          "overrides": { "kg-nikolo@2026": "2026-12-07" },
          "entries": [
            { "id": "b1", "category": "birthday", "name": "Anna", "date": "2020-05-04", "yearKnown": true,
              "presents": [ { "id": "p1", "text": "Lego", "done": false }, { "id": "p2", "text": "Book", "done": true } ],
              "reminders": [ { "days": 7, "note": "" } ], "color": "red" },
            { "id": "b2", "category": "birthday", "name": "Opa", "date": "2000-02-29", "yearKnown": false },
            { "id": "a1", "category": "anniversary", "name": "Wedding", "date": "2015-06-20", "yearKnown": true }
          ],
          "futureKey": 42
        }
    """.trimIndent()

    @Test
    fun migratesBirthdayEntriesToPersons() {
        val s = TraditionsData.parse(JSONObject(v1), today)
        assertTrue(s.migrated)
        assertEquals(listOf("a1"), s.entries.map { it.id })
        assertEquals(listOf("b1", "b2"), s.persons.map { it.id })

        val anna = s.persons[0]
        assertEquals(LocalDate.of(2020, 5, 4), anna.birthDate)
        assertEquals(listOf(EntryReminder(7)), anna.reminders)
        assertEquals(listOf("Lego", "Book"), anna.items.map { it.title })
        assertTrue(anna.items[0].open)
        assertTrue(anna.items[1].used)
        assertTrue(JSONObject(anna.extraJson).has("color"))

        val opa = s.persons[1]
        assertFalse(opa.yearKnown)
        assertNull(opa.reminders)
    }

    @Test
    fun migrationKeepsOccurrenceKeys() {
        val s = TraditionsData.parse(JSONObject(v1), today)
        val occ = s.planner().find("b1@2027")
        assertNotNull(occ)
        assertEquals(LocalDate.of(2027, 5, 4), occ!!.date)
        assertEquals(Kind.BIRTHDAY, occ.kind)
        assertEquals("b1@2027#7", s.planner().remindersFor(occ).single().key)
        // 29 Feb, year unknown → 28 Feb in a non-leap year
        assertEquals(LocalDate.of(2027, 2, 28), s.planner().find("b2@2027")!!.date)
    }

    @Test
    fun roundTripAfterMigrationIsStable() {
        val first = TraditionsData.parse(JSONObject(v1), today)
        val json = TraditionsData.serialize(JSONObject(v1), first)
        assertEquals(42, json.getInt("futureKey"))
        val second = TraditionsData.parse(JSONObject(json.toString()), today)
        assertFalse(second.migrated)
        assertEquals(first.persons, second.persons)
        assertEquals(first.entries.map { it.id }, second.entries.map { it.id })
    }

    @Test
    fun personWithoutBirthdayHasNoOccurrence() {
        val p = Person("x", "Max")
        val planner = Planner(CATALOG, emptyList(), listOf(p), emptyMap(), true)
        assertNull(planner.find("x@2026"))
        assertTrue(planner.occurrences(today, today.plusYears(1)).none { it.person != null })
    }

    @Test
    fun planCarriesOverAndCanBeReplaced() {
        val plans = mapOf("christmas@2025" to EventPlan(listOf("a", "b")))
        val carried = resolvePlan(plans, "christmas@2026", emptyList())
        assertEquals(listOf("a", "b"), carried.people)
        assertEquals(2025, carried.carriedFrom)
        assertFalse(carried.own)

        val fresh = resolvePlan(plans + ("christmas@2026" to EventPlan(emptyList())), "christmas@2026", emptyList())
        assertTrue(fresh.own)
        assertTrue(fresh.people.isEmpty())
        assertEquals(2025, fresh.previousYear)

        // The latest earlier year wins; later years don't leak backwards.
        val more = plans + ("christmas@2024" to EventPlan(listOf("z")))
        assertEquals(listOf("a", "b"), resolvePlan(more, "christmas@2027", emptyList()).people)
        assertEquals(listOf("me"), resolvePlan(more, "christmas@2023", listOf("me")).people)
    }

    @Test
    fun itemsOfPastEventsGetFlaggedUsed() {
        val past = PersonItem("i1", ItemKind.GIFT, "Lego", created = today, eventKey = "b1@2026", eventName = "Anna", eventDate = LocalDate.of(2026, 5, 4))
        val future = PersonItem("i2", ItemKind.TODO, "Cake", created = today, eventKey = "b1@2027")
        val idea = PersonItem("i3", ItemKind.GIFT, "Bike", created = today)
        val p = Person("b1", "Anna", LocalDate.of(2020, 5, 4), items = listOf(past, future, idea))
        val planner = Planner(CATALOG, emptyList(), listOf(p), emptyMap(), true)

        val out = settleItems(listOf(p), today, planner::find)!!.single().items
        assertEquals(LocalDate.of(2026, 5, 4), out[0].usedOn)
        assertNull(out[1].usedOn)
        assertEquals(LocalDate.of(2027, 5, 4), out[1].eventDate)
        assertTrue(out[2].open)
        assertNull(settleItems(listOf(p.copy(items = out)), today, planner::find))
    }

    @Test
    fun importSkipsExistingPersons() {
        val rows = BirthdaysCsv.parse("Name,Datum,Typ\nAnna,04.05.2020,1\nBen,01.01.2019,1\n").rows
        val anna = Person("b1", "Anna", LocalDate.of(2020, 5, 4))
        assertEquals(listOf("Ben"), BirthdaysCsv.newRows(rows, emptyList(), listOf(anna)).map { it.name })
    }
}
