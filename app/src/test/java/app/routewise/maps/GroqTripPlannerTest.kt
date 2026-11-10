package app.routewise.maps

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class GroqTripPlannerTest {
    private val candidate = PlaceResult(
        sourceId = "N/123",
        name = "Route Cafe",
        address = "Old City, Jaipur",
        point = MapPoint(26.9, 75.8),
        osmCategory = "amenity:cafe",
        distanceFromSearchKm = 2.4,
    )

    @Test fun requestContainsOnlyBreakContextAndNearbyCandidates() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(response("""{"recommendations":[{"id":"N/123","reason":"Nearby cafe matching the requested quiet break."}]}"""))
            val planner = planner(server)
            val result = planner.rank("gsk-test-secret", "test-model", "MEAL", "12:30", 45, "BALANCED", "quiet", listOf(candidate))
            val request = server.takeRequest()
            val body = request.body.readUtf8()

            assertEquals("Bearer gsk-test-secret", request.getHeader("Authorization"))
            assertEquals(listOf(AiPlaceRecommendation("N/123", "Nearby cafe matching the requested quiet break.")), result)
            assertTrue(body.contains("Route Cafe"))
            assertTrue(body.contains("MEAL"))
            assertFalse(body.contains("gsk-test-secret"))
            assertFalse(body.contains("26.9"))
            assertFalse(body.contains("75.8"))
            assertFalse(body.contains("trip-private-id"))
            assertFalse(body.contains("Hawa Mahal"))
        }
    }

    @Test fun modelInventedCandidateIdsAreDiscarded() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(response("""{"recommendations":[{"id":"W/999","reason":"Invented venue."},{"id":"N/123","reason":"A real mapped candidate."}]}"""))
            val ranked = planner(server).rank("key", "test-model", "REST", "10:15", 15, "RELAXED", "", listOf(candidate))
            assertEquals(listOf(AiPlaceRecommendation("N/123", "A real mapped candidate.")), ranked)
        }
    }

    @Test fun inventedOnlyResponseFailsWithoutReturningPlaces() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(response("""{"recommendations":[{"id":"W/999","reason":"Invented venue."}]}"""))
            val failure = runCatching {
                planner(server).rank("key", "test-model", "MEAL", "13:00", 45, "QUICK", "", listOf(candidate))
            }.exceptionOrNull()
            assertTrue(failure is IllegalArgumentException)
            assertTrue(failure?.message.orEmpty().contains("Nothing was changed"))
        }
    }

    @Test fun emptyCandidateListDoesNotCallProvider() = runBlocking {
        MockWebServer().use { server ->
            val failure = runCatching {
                planner(server).rank("key", "test-model", "MEAL", "13:00", 45, "QUICK", "", emptyList())
            }.exceptionOrNull()
            assertTrue(failure is IllegalArgumentException)
            assertEquals(0, server.requestCount)
        }
    }

    private fun planner(server: MockWebServer) = GroqTripPlanner(OkHttpClient(), server.url("/openai/v1/chat/completions"))
    private fun response(content: String) = MockResponse().addHeader("Content-Type", "application/json")
        .setBody("""{"choices":[{"message":{"content":${kotlinx.serialization.json.JsonPrimitive(content)}}}]}""")
}