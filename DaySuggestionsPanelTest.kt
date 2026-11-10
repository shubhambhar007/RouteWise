package app.routewise.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.routewise.data.DayEntity
import app.routewise.data.StopEntity
import app.routewise.data.Destination
import app.routewise.maps.*
import app.routewise.core.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DaySuggestionsPanelTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val stops = listOf(StopEntity(
        id = "fort", tripId = "trip", dayId = "day", position = 0,
        destination = Destination("Amber Fort", latitude = 26.9855, longitude = 75.8513, osmSourceId = "W/123", osmCategory = "historic:fort"),
        durationMinutes = 90,
    ))
    private val route = RoutePreview(
        fingerprint = "current", totalBatches = 1, failedBatches = 0,
        segments = listOf(RoadSegment(
            nodes = listOf(RouteNode("start", MapPoint(26.9, 75.8)), RouteNode("fort", MapPoint(26.98, 75.85)), RouteNode("finish", MapPoint(27.0, 75.9))),
            geometry = listOf(MapPoint(26.9, 75.8), MapPoint(26.95, 75.82), MapPoint(26.98, 75.85), MapPoint(27.0, 75.9)),
            legs = listOf(SuggestionLeg("start", "fort", 10_800.0).let { RoadLeg(it.fromId, it.toId, 20_000.0, it.seconds,
                listOf(MapPoint(26.9, 75.8), MapPoint(26.95, 75.82), MapPoint(26.98, 75.85))) },
                RoadLeg("fort", "finish", 20_000.0, 1800.0)),
        )),
    )

    @Test fun timingAndDurationChangeWaitForExplicitApply() {
        var applied: Map<String, Int>? = null
        val maps = OpenMaps(InstrumentationRegistry.getInstrumentation().targetContext)
        compose.setContent {
            DaySuggestionsPanel(
                day = DayEntity(id = "day", tripId = "trip", ordinal = 0), revision = 4, progressRevision = 0,
                stops = stops, orderedNodeIds = listOf("start", "fort", "finish"), stopLabels = mapOf("start" to "Hotel", "fort" to "Amber Fort", "finish" to "Home"), route = route,
                pace = TripPace.BALANCED, onPace = {}, maps = maps,
                onApplyDurations = { changes, done -> applied = changes; done(true, null) },
                onInsertBreak = { _, _, _, _, done -> done(true, null) },
            )
        }
        compose.onNodeWithText("12:15 / 13:45").assertExists()
        compose.onNodeWithText("Break suggestions").assertExists()
        compose.onAllNodesWithText("11:00", substring = true).assertCountEquals(2)
        compose.onNodeWithText("Category ranges only; not opening-hour data.").performScrollTo().assertExists()
        compose.onNodeWithText("Review").performScrollTo().performClick()
        compose.onNodeWithText("12:15 / 14:15").assertExists()
        assertNull(applied)
        compose.onNodeWithTag("apply-duration-suggestions").performScrollTo().performClick()
        assertEquals(120, applied?.get("fort"))
    }

    @Test fun unverifiedNearbyVenueSearchIsClearlyUnavailableWhileOffline() {
        compose.setContent {
            DaySuggestionsPanel(
                day = DayEntity(id = "day", tripId = "trip", ordinal = 0), revision = 0, progressRevision = 0,
                stops = stops, orderedNodeIds = listOf("start", "fort", "finish"), stopLabels = mapOf("start" to "Hotel", "fort" to "Amber Fort", "finish" to "Home"), route = route,
                pace = TripPace.BALANCED, onPace = {}, maps = OpenMaps(InstrumentationRegistry.getInstrumentation().targetContext),
                onApplyDurations = { _, done -> done(true, null) }, onInsertBreak = { _, _, _, _, done -> done(true, null) },
            )
        }
        compose.onNodeWithTag("search-break-break-0-120").performScrollTo().assertIsNotEnabled()
        compose.onAllNodesWithText("Enable online maps to search. Your timing draft stays on this device.")
            .assertCountEquals(2)
    }

    @Test fun staleRouteCannotProduceAReviewableTimeline() {
        compose.setContent {
            DaySuggestionsPanel(
                day = DayEntity(id = "day", tripId = "trip", ordinal = 0), revision = 0, progressRevision = 0,
                stops = stops, orderedNodeIds = listOf("start", "fort", "finish"), stopLabels = mapOf("start" to "Hotel", "fort" to "Amber Fort", "finish" to "Home"), route = route.copy(failedBatches = 1),
                pace = TripPace.BALANCED, onPace = {}, maps = OpenMaps(InstrumentationRegistry.getInstrumentation().targetContext),
                onApplyDurations = { _, done -> fail("Incomplete route must not be applied") }, onInsertBreak = { _, _, _, _, done -> fail("Incomplete route must not add breaks") },
            )
        }
        compose.onNodeWithText("Review the route first").performScrollTo().assertExists()
        compose.onNodeWithText("Confirm the stop locations and calculate a complete route", substring = true).performScrollTo().assertExists()
    }

    @Test fun missingRouteStillAllowsOfflineVisitDurationSuggestions() {
        var applied: Map<String, Int>? = null
        compose.setContent {
            DaySuggestionsPanel(
                day = DayEntity(id = "day", tripId = "trip", ordinal = 0), revision = 2, progressRevision = 0,
                stops = stops, orderedNodeIds = listOf("fort"), stopLabels = mapOf("fort" to "Amber Fort"), route = null,
                pace = TripPace.BALANCED, onPace = {}, maps = OpenMaps(InstrumentationRegistry.getInstrumentation().targetContext),
                onApplyDurations = { changes, done -> applied = changes; done(true, null) },
                onInsertBreak = { _, _, _, _, done -> fail("A break needs a complete route") },
            )
        }
        compose.onNodeWithText("Visit-time suggestions still work without a route.", substring = true).assertExists()
        compose.onNodeWithText("Review").performScrollTo().performClick()
        assertNull(applied)
        compose.onNodeWithTag("apply-duration-suggestions").performScrollTo().performClick()
        assertEquals(120, applied?.get("fort"))
    }

    @Test fun virtualSimulationAdvancesIntoARestWithoutGpsOrTripProgress() {
        compose.setContent {
            DaySuggestionsPanel(
                day = DayEntity(id = "day", tripId = "trip", ordinal = 0), revision = 2, progressRevision = 0,
                stops = stops, orderedNodeIds = listOf("start", "fort", "finish"),
                stopLabels = mapOf("start" to "Hotel", "fort" to "Amber Fort", "finish" to "Home"), route = route,
                pace = TripPace.BALANCED, onPace = {}, maps = OpenMaps(InstrumentationRegistry.getInstrumentation().targetContext),
                onApplyDurations = { _, done -> done(true, null) }, onInsertBreak = { _, _, _, _, done -> done(true, null) },
            )
        }
        compose.onNodeWithText("No GPS. No live tracking. Does not mark stops visited.").performScrollTo().assertExists()
        compose.onNodeWithText("15x").performScrollTo().performClick()
        compose.onNodeWithTag("simulation-play").performScrollTo().performClick()
        compose.waitUntil(5000) { compose.onAllNodesWithText("Rest break", substring = false).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Rest break", substring = false).assertExists()
        compose.onNodeWithText("Pause").performScrollTo().performClick()
    }

    @Test fun groqKeyIsEncryptedLocallyAndCanBeRemovedWithoutNetwork() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val maps = OpenMaps(context)
        val originalMaps = maps.settings.value
        val originalAi = maps.aiSettings.value
        try {
            context.getSharedPreferences("routewise-ai", android.content.Context.MODE_PRIVATE).edit().clear().commit()
            val store = GroqSettingsStore(context)
            assertTrue(store.reserveDailyRequestSlot(limit = 2))
            assertTrue(store.reserveDailyRequestSlot(limit = 2))
            assertFalse(store.reserveDailyRequestSlot(limit = 2))

            compose.setContent { RouteWiseTheme { MapSettingsDialog(maps) {} } }
            compose.onNodeWithTag("groq-api-key").performTextInput("gsk-routewise-test-key")
            compose.onNodeWithTag("groq-enabled-switch").performClick()
            compose.onNodeWithText("Save settings").performClick()

            val stored = context.getSharedPreferences("routewise-ai", android.content.Context.MODE_PRIVATE)
                .getString("key-ciphertext", null)
            assertNotNull(stored)
            assertFalse(stored!!.contains("gsk-routewise-test-key"))
            assertEquals("gsk-routewise-test-key", store.apiKey())
            assertTrue(maps.aiSettings.value.enabled)

            maps.updateAiSettings(false, originalAi.model, "", true)
            assertNull(store.apiKey())
            assertFalse(maps.aiSettings.value.enabled)
        } finally {
            maps.updateAiSettings(false, originalAi.model, "", true)
            maps.update(originalMaps)
        }
    }
}
