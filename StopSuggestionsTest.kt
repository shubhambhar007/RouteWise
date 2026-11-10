package app.routewise.core

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalTime

class StopSuggestionsTest {
    @Test fun durationUsesKnownCategoryAndLabelsGuesses() {
        assertEquals(90, StopSuggestions.duration("Unknown name", "tourism:museum", TripPace.BALANCED)!!.minutes)
        assertTrue(StopSuggestions.duration("Amber Fort", null, TripPace.RELAXED)!!.reason.contains("Name-based guess"))
        assertNull(StopSuggestions.duration("My favourite place", null, TripPace.BALANCED))
        assertNull(StopSuggestions.duration("Parkinson House", null, TripPace.BALANCED))
        assertEquals(10, StopSuggestions.duration("Fuel", "amenity:fuel", TripPace.RELAXED)!!.minimum)
    }

    @Test fun longDrivingLegGetsBreakBeforeArrivalWithoutInventedVenue() {
        val result = StopSuggestions.timeline(listOf("hotel", "fort"), listOf(SuggestionVisit("fort", "Fort", 60)),
            listOf(SuggestionLeg("hotel", "fort", 180.0 * 60)), LocalTime.of(9, 0), mealMinutes = 45)
        assertEquals(2, result.breaks.size)
        val rest = result.breaks.single { it.kind == BreakKind.REST }
        assertEquals(11 * 60, rest.atMinute)
        assertEquals(2.0 / 3, rest.legFraction, 0.001)
        assertEquals(BreakKind.MEAL, result.breaks.last().kind)
        assertEquals(12 * 60 + 15, result.timings.single().arrivalMinute)
        assertEquals(14 * 60, result.endMinute)
    }

    @Test fun missingTravelNeverBecomesZeroTime() {
        assertThrows(IllegalArgumentException::class.java) {
            StopSuggestions.timeline(listOf("one", "two"), emptyList(), emptyList(), LocalTime.NOON)
        }
    }

    @Test fun mealAndDayBudgetAreExplicitAndCrossingMidnightIsNotWrappedAway() {
        val result = StopSuggestions.timeline(listOf("start", "visit", "end"), listOf(SuggestionVisit("visit", "Museum", 180)),
            listOf(SuggestionLeg("start", "visit", 3600.0), SuggestionLeg("visit", "end", 3600.0)), LocalTime.of(9, 0), dayLimitMinutes = 180)
        assertEquals(1, result.breaks.count { it.kind == BreakKind.MEAL })
        assertEquals(165, result.overBudgetMinutes)
        assertEquals("01:10 (+1d)", StopSuggestions.clockLabel(1510))
    }

    @Test fun lateFinalVisitStillGetsAnExplicitMealPrompt() {
        val result = StopSuggestions.timeline(listOf("start", "last"), listOf(SuggestionVisit("last", "Last stop", 180)),
            listOf(SuggestionLeg("start", "last", 1800.0)), LocalTime.of(12, 0), mealMinutes = 45)
        assertEquals(1, result.breaks.count { it.kind == BreakKind.MEAL })
        assertEquals("meal-after-route", result.breaks.single { it.kind == BreakKind.MEAL }.id)
        assertEquals(12 * 60 + 30, result.timings.single().arrivalMinute)
        assertEquals(16 * 60 + 15, result.endMinute)
    }

    @Test fun virtualPlaybackIdentifiesDrivingVisitsBreaksAndCompletion() {
        val schedule = StopSuggestions.timeline(listOf("start", "museum", "end"),
            listOf(SuggestionVisit("museum", "Museum", 60)),
            listOf(SuggestionLeg("start", "museum", 90.0 * 60), SuggestionLeg("museum", "end", 30.0 * 60)),
            LocalTime.of(9, 0), restEveryMinutes = 60)
        val labels = mapOf("museum" to "Museum")
        assertEquals(SimulationKind.DRIVE, StopSuggestions.simulationStatus(schedule, labels, 9 * 60 + 30).kind)
        assertEquals(SimulationKind.REST, StopSuggestions.simulationStatus(schedule, labels, 10 * 60).kind)
        assertEquals(SimulationKind.VISIT, StopSuggestions.simulationStatus(schedule, labels, 11 * 60 + 30).kind)
        assertEquals(SimulationKind.FINISHED, StopSuggestions.simulationStatus(schedule, labels, schedule.endMinute).kind)
        assertTrue(StopSuggestions.simulationStatus(schedule, labels, schedule.endMinute).detail.contains("No visit status was changed"))
        assertEquals(10 * 60, StopSuggestions.nextSimulationBoundary(schedule, 9 * 60 + 30))
    }
}
