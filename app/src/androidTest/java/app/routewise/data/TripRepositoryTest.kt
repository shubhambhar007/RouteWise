package app.routewise.data

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.routewise.core.TransferStop
import app.routewise.core.TransferTrip
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TripRepositoryTest {
    private lateinit var database: TripDatabase
    private lateinit var repository: TripRepository

    @Before fun setup() {
        database = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), TripDatabase::class.java).build()
        repository = TripRepository(database)
    }
    @After fun close() = database.close()

    @Test fun reorderAndDayDeletionPreserveThousandStops() = runBlocking {
        val id = repository.importTrip(TransferTrip(title = "Large trip", days = listOf("City"), stops = List(1000) {
            TransferStop(Destination("Visit $it", query = "Place $it"), day = 0)
        }))
        val original = database.trips().detail(id)!!
        val day = original.days.single()
        val ids = original.stops.sortedBy { it.position }.map { it.id }.reversed()
        repository.reorder(id, day.id, ids)
        assertEquals(ids, database.trips().ordered(id, day.id).map { it.id })
        repository.removeDay(day.id)
        assertEquals(1000, database.trips().ordered(id, null).size)
        assertEquals((0 until 1000).toList(), database.trips().ordered(id, null).map { it.position })
        val duplicate = repository.duplicate(id)
        assertEquals(1000, database.trips().detail(duplicate)!!.stops.size)
        assertTrue(database.trips().detail(duplicate)!!.stops.none { it.id in ids })
        repository.delete(id)
        assertNull(database.trips().detail(id))
        assertEquals(1000, database.trips().detail(duplicate)!!.stops.size)
    }

    @Test fun crossTripDayMoveIsRejectedAndProgressDoesNotChangePlan() = runBlocking {
        val first = repository.create("First", 1)
        val second = repository.create("Second", 1)
        val day = database.trips().detail(first)!!.days.single().id
        repository.saveStop(first, day, Destination("Museum", query = "Museum, Jaipur"), 60, true, "")
        val detail = database.trips().detail(first)!!
        val stop = detail.stops.single()
        repository.setVisitState(stop.id, "VISITED")
        assertEquals(detail.trip.revision, database.trips().detail(first)!!.trip.revision)
        assertEquals(1L, database.trips().detail(first)!!.trip.progressRevision)
        try {
            repository.moveToDay(stop.id, database.trips().detail(second)!!.days.single().id)
            fail("Cross-trip move should fail")
        } catch (_: IllegalArgumentException) {
            assertEquals(day, database.trips().stop(stop.id)!!.dayId)
        }
    }

    @Test fun dayDatesAnchorsAndAttributionSurviveTripCopies() = runBlocking {
        val id = repository.create("Anchored journey", 2)
        val days = database.trips().detail(id)!!.days.sortedBy { it.ordinal }
        val hotel = Destination("My hotel", latitude = 26.9, longitude = 75.8, osmSourceId = "N/123")
        repository.updateDay(days[0].id, "Old city", "2026-12-10")
        repository.updateDay(days[1].id, "Forts", "2026-12-11")
        repository.setAnchor(days[0].id, true, hotel)
        repository.setAnchor(days[0].id, false, hotel)
        val copy = repository.duplicate(id)
        val copied = database.trips().detail(copy)!!.days.sortedBy { it.ordinal }
        assertEquals("2026-12-10", copied[0].localDate)
        assertEquals(hotel, copied[0].start)
        assertEquals(hotel, copied[0].finish)
        try { repository.updateDay(days[1].id, "Invalid", "2026-12-09"); fail("Unordered dates must fail") }
        catch (_: IllegalArgumentException) { assertEquals("2026-12-11", database.trips().day(days[1].id)!!.localDate) }
    }

    @Test fun pendingShareSurvivesRecreationAndDeduplicatesDelivery() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val first = PendingShares(context)
        val text = "https://maps.app.goo.gl/pending-test-${System.currentTimeMillis()}"
        val item = first.add(text)
        first.add(text)
        val reopened = PendingShares(context)
        assertEquals(1, reopened.items.value.count { it.id == item.id })
        assertEquals(text, reopened.items.value.first { it.id == item.id }.text)
        reopened.remove(item.id)
        assertFalse(PendingShares(context).items.value.any { it.id == item.id })
    }

    @Test fun suggestionsApplyAtomicallyAndRejectStaleOrCrossDayChanges() = runBlocking {
        val id = repository.create("Suggestions", 2)
        val days = database.trips().detail(id)!!.days.sortedBy { it.ordinal }
        repository.saveStop(id, days[0].id, Destination("Museum", query = "Museum"), 60, true, "Keep notes")
        repository.saveStop(id, days[1].id, Destination("Park", query = "Park"), 60, true, "")
        val before = database.trips().detail(id)!!
        val first = before.stops.first { it.dayId == days[0].id }
        val other = before.stops.first { it.dayId == days[1].id }
        try {
            repository.applySuggestedDurations(id, days[0].id, before.trip.revision, before.trip.progressRevision, mapOf(first.id to 90, other.id to 30))
            fail("Cross-day change must fail")
        } catch (_: IllegalArgumentException) { assertEquals(60, database.trips().stop(first.id)!!.durationMinutes) }
        repository.applySuggestedDurations(id, days[0].id, before.trip.revision, before.trip.progressRevision, mapOf(first.id to 90))
        assertEquals(90, database.trips().stop(first.id)!!.durationMinutes)
        assertEquals("Keep notes", database.trips().stop(first.id)!!.notes)
        assertEquals(before.trip.revision + 1, database.trips().detail(id)!!.trip.revision)
        try {
            repository.applySuggestedDurations(id, days[0].id, before.trip.revision, before.trip.progressRevision, mapOf(first.id to 30))
            fail("Stale change must fail")
        } catch (_: IllegalArgumentException) { assertEquals(90, database.trips().stop(first.id)!!.durationMinutes) }
        val fresh = database.trips().detail(id)!!
        repository.setVisitState(first.id, "VISITED")
        try {
            repository.applySuggestedDurations(id, days[0].id, fresh.trip.revision, fresh.trip.progressRevision, mapOf(first.id to 30))
            fail("Progress changes must invalidate suggestions")
        } catch (_: IllegalArgumentException) { assertEquals(90, database.trips().stop(first.id)!!.durationMinutes) }
    }

    @Test fun chosenBreakIsInsertedBeforeTheIntendedStopWithProvenance() = runBlocking {
        val id = repository.create("Break insertion", 1)
        val day = database.trips().detail(id)!!.days.single().id
        repository.saveStop(id, day, Destination("Fort", query = "Fort"), 90, true, "")
        val before = database.trips().detail(id)!!
        val cafe = Destination("Roadside cafe", latitude = 26.9, longitude = 75.8, osmSourceId = "N/567", osmCategory = "amenity:cafe")
        repository.insertSuggestedBreak(id, day, before.trip.revision, before.trip.progressRevision, before.stops.single().id, cafe, 20, 630)
        val stops = database.trips().ordered(id, day)
        assertEquals(listOf("Roadside cafe", "Fort"), stops.map { it.destination.label })
        assertEquals(listOf(0, 1), stops.map { it.position })
        assertEquals(20, stops.first().durationMinutes)
        assertFalse(stops.first().mustVisit)
        assertEquals("amenity:cafe", repository.export(id).stops.first().destination.osmCategory)
        assertTrue(repository.export(id).stops.first().notes.contains("10:30"))
    }
}
