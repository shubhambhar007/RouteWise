package app.routewise.maps

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.Cache
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

data class MapSettings(
    val enabled: Boolean = false,
    val searchUrl: String = "https://photon.komoot.io/",
    val routingUrl: String = "https://routing.openstreetmap.de/routed-car/route/v1/driving/",
    val tilesUrl: String = "https://tile.openstreetmap.org/{z}/{x}/{y}.png",
)

@Singleton
class OpenMaps @Inject constructor(@ApplicationContext context: Context) {
    private val preferences = context.getSharedPreferences("open-maps", Context.MODE_PRIVATE)
    private val groqSettingsStore = GroqSettingsStore(context)
    private val groqPlanner = GroqTripPlanner()
    private val defaultSettings = MapSettings()
    private val mutableSettings = MutableStateFlow(MapSettings(
        preferences.getBoolean("enabled", false),
        preferences.getString("search", null) ?: defaultSettings.searchUrl,
        preferences.getString("routing", null) ?: defaultSettings.routingUrl,
        preferences.getString("tiles", null) ?: defaultSettings.tilesUrl,
    ))
    val settings = mutableSettings.asStateFlow()
    val aiSettings = groqSettingsStore.settings
    val tileError = MutableStateFlow<String?>(null)
    val http = OkHttpClient.Builder()
        .cache(Cache(File(context.cacheDir, "open-map-http"), 64L * 1024 * 1024))
        .connectTimeout(10, TimeUnit.SECONDS).readTimeout(20, TimeUnit.SECONDS).callTimeout(25, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false)
        .addInterceptor { chain ->
            if (!settings.value.enabled) throw IOException("Online maps are off.")
            val request = chain.request().newBuilder().header("User-Agent", "RouteWise/0.2 (Android; app.routewise; personal trip planner)").build()
            val tiles = settings.value.tilesUrl.substringBefore("/{z}").toHttpUrl().host == request.url.host
            try {
                val response = chain.proceed(request)
                if (tiles && !response.isSuccessful) tileError.value = "Map tiles unavailable (${response.code})."
                response
            } catch (failure: IOException) {
                if (tiles) tileError.value = "Map tiles unavailable. Check your connection."
                throw failure
            }
        }.build()
    private var client = newClient()

    private fun newClient() = OpenMapClient(http, settings.value.searchUrl.toHttpUrl(), settings.value.routingUrl.toHttpUrl())
    suspend fun search(query: String): List<PlaceResult> { check(settings.value.enabled) { "Enable online maps first." }; return client.search(query) }
    suspend fun nearby(point: MapPoint, type: BreakPlaceType): List<PlaceResult> {
        check(settings.value.enabled) { "Enable online maps first." }
        return client.search(type.query, point, type)
    }
    suspend fun route(nodes: List<RouteNode>): RoutePreview { check(settings.value.enabled) { "Enable online maps first." }; return client.route(nodes) }
    fun updateAiSettings(enabled: Boolean, model: String, apiKey: String, clearApiKey: Boolean) =
        groqSettingsStore.update(enabled, model, apiKey, clearApiKey)

    suspend fun rankBreakPlaces(
        kind: String,
        time: String,
        durationMinutes: Int,
        pace: String,
        preference: String,
        candidates: List<PlaceResult>,
    ): List<AiPlaceRecommendation> {
        check(groqSettingsStore.settings.value.enabled) { "Enable Groq AI in settings first." }
        val apiKey = requireNotNull(groqSettingsStore.apiKey()) { "Add a Groq API key in AI settings first." }
        require(candidates.size in 1..8) { "Find nearby map candidates before asking AI to rank them." }
        check(groqSettingsStore.reserveDailyRequestSlot()) { "Today's 12 AI requests are used up. Try again tomorrow." }
        return groqPlanner.rank(apiKey, groqSettingsStore.settings.value.model, kind, time, durationMinutes, pace, preference, candidates)
    }

    fun update(next: MapSettings) {
        listOf(next.searchUrl, next.routingUrl, next.tilesUrl).forEach { value ->
            val url = value.toHttpUrl()
            require(url.isHttps && url.username.isEmpty() && url.password.isEmpty() && url.fragment == null && url.query == null) { "Use an HTTPS provider URL without credentials or query parameters." }
        }
        require(next.searchUrl.endsWith('/') && next.routingUrl.endsWith('/')) { "Search and routing base URLs must end with /." }
        require(listOf("{z}", "{x}", "{y}").all(next.tilesUrl::contains)) { "Tile URL needs {z}, {x}, and {y}." }
        http.dispatcher.cancelAll()
        preferences.edit().putBoolean("enabled", next.enabled).putString("search", next.searchUrl)
            .putString("routing", next.routingUrl).putString("tiles", next.tilesUrl).apply()
        mutableSettings.value = next
        tileError.value = null
        client = newClient()
    }
}
