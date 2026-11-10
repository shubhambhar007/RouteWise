package app.routewise.maps

import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test

class OpenMapClientTest {
    private val places = """{"type":"FeatureCollection","features":[{"geometry":{"type":"Point","coordinates":[75.8,26.9]},"properties":{"osm_type":"N","osm_id":123,"name":"Museum","city":"Jaipur","country":"India"}},{"geometry":{"type":"Point","coordinates":[999,26.9]},"properties":{"osm_type":"N","osm_id":456,"name":"Invalid"}}]}"""

    @Test fun searchValidatesResultsCachesQueriesAndIdentifiesApp() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody(places))
            val client = OpenMapClient(OkHttpClient(), server.url("/"), spacingMillis = 0)
            val results = client.search("Museum Jaipur")
            assertEquals(1, results.size)
            assertEquals(MapPoint(26.9, 75.8), results.single().point)
            assertEquals(results, client.search("  museum   jaipur  "))
            assertEquals(1, server.requestCount)
            val request = server.takeRequest()
            assertTrue(request.getHeader("User-Agent")!!.startsWith("RouteWise/"))
            assertEquals("Museum Jaipur", request.requestUrl!!.queryParameter("q"))
        }
    }

    private fun routeBody(count: Int) = """{"code":"Ok","waypoints":[${List(count) { "{}" }.joinToString()}],"routes":[{"distance":${(count - 1) * 100},"duration":${(count - 1) * 10},"legs":[${List(count - 1) { "{\"distance\":100,\"duration\":10}" }.joinToString()}],"geometry":{"type":"LineString","coordinates":[[75.8,26.9],[75.9,27.0]]}}]}"""

    @Test fun fiftyStopsPreserveEveryLegAndPartialResultsHaveNoCompleteTotal() = runBlocking {
        MockWebServer().use { server ->
            val nodes = List(52) { RouteNode("visit-$it", MapPoint(26.9 + it / 1000.0, 75.8)) }
            val client = OpenMapClient(OkHttpClient(), routeBase = server.url("/route/"), spacingMillis = 0)
            server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody(routeBody(27)))
            server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody(routeBody(26)))
            val complete = client.route(nodes)
            assertTrue(complete.complete)
            assertEquals(5100.0, complete.meters!!, 0.01)
            assertEquals(nodes.zipWithNext().map { it.first.id to it.second.id }, complete.segments.flatMap { it.legs }.map { it.fromId to it.toId })
            server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody(routeBody(27)))
            server.enqueue(MockResponse().setResponseCode(503))
            val partial = client.route(nodes)
            assertFalse(partial.complete)
            assertNull(partial.meters)
            assertEquals(1, partial.failedBatches)
            assertTrue(partial.failureReasons.single().contains("503"))
        }
    }

    @Test fun invalidRoutesAndStaleFingerprintsAreDetectable() {
        val nodes = listOf(RouteNode("one", MapPoint(26.9, 75.8)), RouteNode("two", MapPoint(27.0, 75.9)))
        assertThrows(IllegalArgumentException::class.java) { OpenMapModels.route(routeBody(3), nodes) }
        assertNotEquals(OpenMapModels.fingerprint(nodes), OpenMapModels.fingerprint(nodes.reversed()))
    }

    @Test fun routeRequestKeepsCoordinateDelimitersInTheExpectedOsrmPath() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody(routeBody(2)))
            val nodes = listOf(RouteNode("one", MapPoint(26.9, 75.8)), RouteNode("two", MapPoint(27.0, 75.9)))
            val client = OpenMapClient(OkHttpClient(), routeBase = server.url("/routed-car/route/v1/driving/"), spacingMillis = 0)
            assertTrue(client.route(nodes).complete)
            val path = server.takeRequest().requestUrl!!.encodedPath
            assertEquals("/routed-car/route/v1/driving/75.8,26.9;75.9,27.0", path)
        }
    }

    @Test fun deniedProviderRequestFailsWithoutFallback() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setResponseCode(403).setBody("Denied"))
            try {
                OpenMapClient(OkHttpClient(), server.url("/"), spacingMillis = 0).search("Museum Jaipur")
                fail("Expected denied request")
            } catch (failure: ProviderException) { assertEquals(403, failure.code) }
            assertEquals(1, server.requestCount)
        }
    }

    @Test fun nearbySearchFiltersFarAwayAndWrongCategoryResultsAndCachesPerArea() = runBlocking {
        MockWebServer().use { server ->
            val response = """{"type":"FeatureCollection","features":[
                {"geometry":{"type":"Point","coordinates":[75.8,26.9]},"properties":{"osm_type":"N","osm_id":1,"name":"Nearby cafe","osm_key":"amenity","osm_value":"cafe"}},
                {"geometry":{"type":"Point","coordinates":[10,50]},"properties":{"osm_type":"N","osm_id":2,"name":"Distant cafe","osm_key":"amenity","osm_value":"cafe"}},
                {"geometry":{"type":"Point","coordinates":[75.8,26.9]},"properties":{"osm_type":"N","osm_id":3,"name":"Cafe street","osm_key":"highway","osm_value":"residential"}}]}"""
            server.enqueue(MockResponse().setHeader("Content-Type", "application/json").setBody(response))
            val client = OpenMapClient(OkHttpClient(), server.url("/"), spacingMillis = 0)
            val result = client.search("cafe", MapPoint(26.9, 75.8), BreakPlaceType.CAFE)
            assertEquals(listOf("Nearby cafe"), result.map { it.name })
            assertEquals("amenity:cafe", result.single().osmCategory)
            assertEquals(0.0, result.single().distanceFromSearchKm!!, 0.001)
            assertEquals(result, client.search("cafe", MapPoint(26.9, 75.8), BreakPlaceType.CAFE))
            val request = server.takeRequest()
            assertEquals("26.9", request.requestUrl!!.queryParameter("lat"))
            assertEquals("amenity:cafe", request.requestUrl!!.queryParameter("osm_tag"))
            assertEquals(1, server.requestCount)
        }
    }

    @Test fun breakSearchPointUsesRoadGeometryAndNeverInventsMissingGeometry() {
        val leg = RoadLeg("a", "b", 1000.0, 120.0, listOf(MapPoint(0.0, 0.0), MapPoint(0.0, 1.0), MapPoint(1.0, 1.0)))
        val halfway = OpenMapModels.pointAlong(leg, .5)!!
        assertEquals(0.0, halfway.latitude, .01)
        assertEquals(1.0, halfway.longitude, .01)
        assertNull(OpenMapModels.pointAlong(leg.copy(geometry = emptyList()), .5))
    }
}
