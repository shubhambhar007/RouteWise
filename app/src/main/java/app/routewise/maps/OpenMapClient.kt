package app.routewise.maps

import app.routewise.core.TripRules
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.*
import okhttp3.HttpUrl.Companion.toHttpUrl
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLHandshakeException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class OpenMapClient(
    private val http: OkHttpClient,
    private val searchBase: HttpUrl = "https://photon.komoot.io/".toHttpUrl(),
    private val routeBase: HttpUrl = "https://routing.openstreetmap.de/routed-car/route/v1/driving/".toHttpUrl(),
    private val spacingMillis: Long = 1100,
) {
    private val gate = Mutex()
    private var nextRequestNanos = 0L
    private val searchCache = LinkedHashMap<String, List<PlaceResult>>()

    suspend fun search(query: String, near: MapPoint? = null, category: BreakPlaceType? = null): List<PlaceResult> = gate.withLock {
        val normalized = query.trim().replace(Regex("\\s+"), " ")
        require(normalized.length in 3..300) { "Enter at least 3 characters and a city." }
        require(category == null || near != null)
        val cacheKey = "${normalized.lowercase()}|$near|$category"
        searchCache[cacheKey]?.let { return@withLock it }
        throttle()
        val builder = searchBase.newBuilder().addPathSegment("api").addPathSegment("")
            .addQueryParameter("q", normalized).addQueryParameter("limit", "8").addQueryParameter("lang", "en")
        near?.let {
            builder.addQueryParameter("lat", it.latitude.toString()).addQueryParameter("lon", it.longitude.toString())
                .addQueryParameter("location_bias_scale", "0.1")
        }
        category?.tags?.forEach { builder.addQueryParameter("osm_tag", it) }
        val places = OpenMapModels.places(request(builder.build())).mapNotNull { place ->
            val distance = near?.let { OpenMapModels.distanceKm(it, place.point) }
            if (distance != null && distance > 5.0 || category != null && place.osmCategory !in category.tags) null
            else place.copy(distanceFromSearchKm = distance)
        }
        if (searchCache.size >= 40) searchCache.remove(searchCache.keys.first())
        searchCache[cacheKey] = places
        places
    }

    suspend fun route(nodes: List<RouteNode>): RoutePreview = gate.withLock {
        require(nodes.size in 2..100) { "Free previews support 2-100 located stops at a time; saved trips have no stop limit." }
        require(nodes.map { it.id }.distinct().size == nodes.size)
        val batches = TripRules.routeBatches(nodes)
        val segments = mutableListOf<RoadSegment>()
        val reasons = mutableListOf<String>()
        var failed = 0
        for (batch in batches) {
            throttle()
            val coordinates = batch.joinToString(";") { "${it.point.longitude},${it.point.latitude}" }
            val url = routeBase.newBuilder().addPathSegment(coordinates)
                .addQueryParameter("overview", "simplified").addQueryParameter("geometries", "geojson")
                .addQueryParameter("steps", "true").addQueryParameter("alternatives", "false").build()
            try { segments += OpenMapModels.route(request(url), batch) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: ProviderException) {
                if (failure.code == 403 || failure.code == 429) throw failure
                failed++
                reasons += failure.message ?: "The public routing service rejected this segment."
            }
            catch (cancelled: Exception) {
                failed++
                reasons += when {
                    cancelled.message?.contains("response", ignoreCase = true) == true -> "The route response could not be validated."
                    cancelled.message?.contains("trust", ignoreCase = true) == true -> cancelled.message!!
                    cancelled.message?.contains("proxy", ignoreCase = true) == true -> cancelled.message!!
                    cancelled.message?.contains("timed out", ignoreCase = true) == true -> cancelled.message!!
                    else -> "The free routing service could not provide this segment. Check connectivity or provider availability."
                }
            }
        }
        return RoutePreview(OpenMapModels.fingerprint(nodes), segments, failed, batches.size, reasons)
    }

    private suspend fun throttle() {
        val remaining = (nextRequestNanos - System.nanoTime()) / 1_000_000
        if (remaining > 0) delay(remaining)
        nextRequestNanos = System.nanoTime() + spacingMillis * 1_000_000
    }

    private suspend fun request(url: HttpUrl): String = suspendCancellableCoroutine { continuation ->
        val call = http.newCall(Request.Builder().url(url)
            .header("User-Agent", "RouteWise/0.2 (Android; app.routewise; personal trip planner)")
            .header("Accept", "application/json").build())
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, exception: IOException) {
                val message = when (exception) {
                    is SSLHandshakeException -> "Secure map connection failed. Check this device's trusted network certificates."
                    is UnknownHostException -> "The map service or configured proxy could not be found."
                    is SocketTimeoutException -> "The map service timed out. Check your connection."
                    else -> "Unable to reach the free map service. Check your connection."
                }
                if (continuation.isActive) continuation.resumeWithException(IOException(message, exception))
            }
            override fun onResponse(call: Call, response: Response) {
                response.use {
                    try {
                        if (!it.isSuccessful) throw ProviderException(it.code)
                        val body = requireNotNull(it.body)
                        require(body.contentType()?.subtype?.contains("json", ignoreCase = true) == true) { "Map service returned an unexpected response." }
                        val source = body.source()
                        require(!source.request(4L * 1024 * 1024 + 1)) { "Map response exceeds the size limit." }
                        val text = source.readUtf8()
                        if (continuation.isActive) continuation.resume(text)
                    } catch (failure: Exception) {
                        if (continuation.isActive) continuation.resumeWithException(failure)
                    }
                }
            }
        })
    }
}

enum class BreakPlaceType(val query: String, val tags: Set<String>) {
    CAFE("cafe", setOf("amenity:cafe")),
    MEAL("restaurant", setOf("amenity:restaurant", "amenity:fast_food", "amenity:food_court")),
    REST("rest area", setOf("highway:rest_area", "highway:services")),
}

class ProviderException(val code: Int) : IOException(when (code) {
    403 -> "The network or map provider blocked this request. No alternate connection was attempted."
    429 -> "The free service is busy. Please try again later."
    else -> "The free map service is unavailable ($code)."
})
