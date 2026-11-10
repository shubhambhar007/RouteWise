package app.routewise.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import app.routewise.MainActivity
import app.routewise.maps.OpenMaps
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TripFlowTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    private fun await(text: String) {
        try {
            compose.waitUntil(10_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
        } catch (failure: Throwable) {
            compose.onRoot(useUnmergedTree = true).printToLog("RouteWiseTest")
            throw failure
        }
    }

    private fun addStop(name: String) {
        compose.onNodeWithText("Add stop").performClick()
        compose.onNodeWithText("Stop name").performTextInput(name)
        compose.onNodeWithText("Place and city").performTextInput("$name, Jaipur")
        compose.onNodeWithText("Save stop").performClick()
        await(name)
    }

    @Test fun createEditReorderMoveAndRestoreTrip() {
        val tripName = "UI journey ${System.currentTimeMillis()}"
        await("New trip")
        compose.onNodeWithText("New trip").performClick()
        compose.onNodeWithText("Trip name").performTextInput(tripName)
        compose.onNodeWithText("Create trip").performClick()
        await("Add stop")
        addStop("First museum")
        addStop("Second museum")
        compose.onNodeWithContentDescription("Options for Second museum").performClick()
        compose.onNodeWithText("Move up").performClick()
        compose.waitForIdle()
        val firstPosition = compose.onAllNodesWithText("First museum")[0].getUnclippedBoundsInRoot().top
        val secondPosition = compose.onAllNodesWithText("Second museum")[0].getUnclippedBoundsInRoot().top
        assertTrue(secondPosition < firstPosition)
        compose.onNodeWithContentDescription("Options for Second museum").performClick()
        compose.onNodeWithText("Mark visited").performClick()
        await("60 min  /  Visited")
        compose.onNodeWithContentDescription("Options for First museum").performClick()
        compose.onNodeWithText("Edit stop").performClick()
        compose.onNodeWithText("Visit time (minutes)").performTextReplacement("90")
        compose.onNodeWithText("Save stop").performClick()
        await("90 min  /  Must visit")
        compose.onNodeWithContentDescription("Options for First museum").performClick()
        compose.onNodeWithText("Move to day").performClick()
        compose.onAllNodesWithText("Day 2").onLast().performClick()
        compose.onNodeWithText("Day 2").performClick()
        await("First museum")
        compose.activityRule.scenario.recreate()
        await("First museum")
        compose.onNodeWithText("90 min  /  Must visit").assertExists()
        compose.onNodeWithContentDescription("Trip options").performClick()
        compose.onNodeWithText("Delete trip").performClick()
        compose.onNodeWithText("Delete").performClick()
        await("Your trips")
    }

    @Test fun dragOrderSurvivesActivityRecreation() {
        await("New trip")
        compose.onNodeWithText("New trip").performClick()
        compose.onNodeWithText("Trip name").performTextInput("Drag journey ${System.currentTimeMillis()}")
        compose.onNodeWithText("Create trip").performClick()
        await("Add stop")
        addStop("Alpha stop")
        addStop("Bravo stop")
        addStop("Charlie stop")
        val source = compose.onNodeWithContentDescription("Reorder Alpha stop").fetchSemanticsNode().boundsInRoot
        val target = compose.onNodeWithContentDescription("Reorder Charlie stop").fetchSemanticsNode().boundsInRoot
        compose.onNodeWithContentDescription("Reorder Alpha stop").performTouchInput {
            swipe(center, target.center - source.topLeft, durationMillis = 800)
        }
        compose.waitUntil(5000) {
            compose.onNodeWithContentDescription("Options for Charlie stop").fetchSemanticsNode().boundsInRoot.top <
                compose.onNodeWithContentDescription("Options for Alpha stop").fetchSemanticsNode().boundsInRoot.top
        }
        compose.activityRule.scenario.recreate()
        await("Charlie stop")
        assertTrue(compose.onNodeWithContentDescription("Options for Charlie stop").fetchSemanticsNode().boundsInRoot.top <
            compose.onNodeWithContentDescription("Options for Alpha stop").fetchSemanticsNode().boundsInRoot.top)
        compose.onNodeWithContentDescription("Trip options").performClick()
        compose.onNodeWithText("Delete trip").performClick()
        compose.onNodeWithText("Delete").performClick()
        await("Your trips")
    }

    @Test fun liveSearchRouteAndNativeMap() {
        assumeTrue(InstrumentationRegistry.getArguments().getString("runLiveMaps") == "true")
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val originalSettings = OpenMaps(context).settings.value
        try {
            await("New trip")
            compose.onNodeWithContentDescription("Map settings").performClick()
            if (!originalSettings.enabled) compose.onNode(isToggleable()).performClick()
            compose.onNodeWithText("Save settings").performClick()
            runBlocking { assertTrue(OpenMaps(context).search("Hawa Mahal Jaipur").isNotEmpty()) }
            compose.onNodeWithText("New trip").performClick()
            compose.onNodeWithText("Trip name").performTextInput("Map verification ${System.currentTimeMillis()}")
            compose.onNodeWithText("Create trip").performClick()
            await("Add stop")
            compose.onNodeWithText("Add stop").performClick()
            compose.onNodeWithText("Stop name").performTextInput("Map test museum")
            compose.onNodeWithText("Place and city").performTextInput("Hawa Mahal Jaipur")
            compose.onNodeWithText("Search places").performScrollTo().performClick()
            compose.waitUntil(30_000) { compose.onAllNodesWithTag("place-result-0").fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithTag("place-result-0").performScrollTo().performClick()
            compose.onNodeWithText("Save stop").performClick()
            await("Map test museum")
            compose.onNodeWithText("Add stop").performClick()
            compose.onNodeWithText("Stop name").performTextInput("Map test palace")
            compose.onNodeWithText("Use coordinates").performScrollTo()
            compose.onAllNodes(isToggleable())[0].performClick()
            compose.onNodeWithText("Latitude").performTextInput("26.9258")
            compose.onNodeWithText("Longitude").performScrollTo().performTextInput("75.8237")
            compose.onNodeWithText("Save stop").performClick()
            await("Map test palace")
            compose.onNodeWithContentDescription("Calculate driving route").performClick()
            compose.waitUntil(40_000) { compose.onAllNodesWithText("km /", substring = true).fetchSemanticsNodes().isNotEmpty() }
            compose.onNodeWithText("Map", substring = false).performClick()
            await("Add pin")
            compose.onNodeWithText("Add pin").assertIsEnabled()
            compose.onNodeWithText("(c) OpenStreetMap").assertIsDisplayed()
            compose.onNodeWithContentDescription("Fit all stops").performClick()
            compose.waitForIdle()
            val capture = InstrumentationRegistry.getInstrumentation().uiAutomation
                .executeShellCommand("screencap -p /sdcard/Download/routewise-live-map.png")
            ParcelFileDescriptor.AutoCloseInputStream(capture).use { it.readBytes() }
            compose.onNodeWithContentDescription("Trip options").performClick()
            compose.onNodeWithText("Delete trip").performClick()
            compose.onNodeWithText("Delete").performClick()
            await("Your trips")
        } finally { OpenMaps(context).update(originalSettings) }
    }

    @Test fun dayDetailsAndAnchorsStayEditableOffline() {
        await("New trip")
        compose.onNodeWithText("New trip").performClick()
        compose.onNodeWithText("Trip name").performTextInput("Day settings ${System.currentTimeMillis()}")
        compose.onNodeWithText("Create trip").performClick()
        await("Add stop")
        addStop("My hotel")
        compose.onNodeWithText("Set start").performClick()
        compose.onAllNodesWithText("My hotel").onLast().performClick()
        compose.onNodeWithText("Set finish").performClick()
        compose.onAllNodesWithText("My hotel").onLast().performClick()
        compose.onNodeWithContentDescription("Trip options").performClick()
        compose.onNodeWithText("Day details").performClick()
        compose.onNodeWithText("Day name").performTextInput("Old city day")
        compose.onNodeWithText("Date (YYYY-MM-DD)").performTextInput("2026-12-10")
        compose.onNodeWithText("Save day").performClick()
        await("Old city day")
        compose.activityRule.scenario.recreate()
        await("Old city day")
        compose.onNodeWithText("2026-12-10").assertExists()
        compose.onNodeWithText("Set start").assertDoesNotExist()
        compose.onNodeWithText("Set finish").assertDoesNotExist()
        compose.onNodeWithContentDescription("Trip options").performClick()
        compose.onNodeWithText("Delete trip").performClick()
        compose.onNodeWithText("Delete").performClick()
        await("Your trips")
    }

    @Test fun oneTapJaipurSimulationOpensLocatedThreeStopTripWithoutEditingExistingTrips() {
        await("Try Jaipur 3-stop simulation")
        val preservedTrip = "Preserved trip ${System.currentTimeMillis()}"
        compose.onNodeWithText("New trip").performClick()
        compose.onNodeWithText("Trip name").performTextInput(preservedTrip)
        compose.onNodeWithText("Create trip").performClick()
        await("Add stop")
        addStop("Preserved stop")
        compose.onNodeWithContentDescription("Back to trips").performClick()
        await("Try Jaipur 3-stop simulation")
        compose.onNodeWithTag("jaipur-simulation").performClick()
        await("Jaipur 3-stop simulation")
        compose.onAllNodesWithText("Hawa Mahal").assertCountEquals(2)
        await("City Palace")
        await("Jantar Mantar")
        compose.onNodeWithTag("navigate-in-app").assertIsEnabled()
        compose.onNodeWithText("Google Maps").assertExists()
        compose.onNodeWithTag("navigate-in-app").performClick()
        await("Start Google navigation in RouteWise?")
        compose.onNodeWithText("Cancel").performClick()
        compose.onAllNodesWithText("Jaipur Junction").assertCountEquals(2)
        compose.onNodeWithContentDescription("Suggest visit times and breaks").assertIsEnabled()
        compose.onNodeWithContentDescription("Calculate driving route").assertExists()
        compose.onNodeWithContentDescription("Trip options").performClick()
        compose.onNodeWithText("Delete trip").performClick()
        compose.onNodeWithText("Delete").performClick()
        await("Your trips")
        compose.onNodeWithText(preservedTrip).assertExists()
        compose.onNodeWithText("Jaipur 3-stop simulation").assertDoesNotExist()
        compose.onNodeWithText(preservedTrip).performClick()
        await("Preserved stop")
        compose.onNodeWithContentDescription("Trip options").performClick()
        compose.onNodeWithText("Delete trip").performClick()
        compose.onNodeWithText("Delete").performClick()
        await("Your trips")
    }
}
