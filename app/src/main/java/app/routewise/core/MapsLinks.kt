package app.routewise.core

import app.routewise.data.Destination
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder

data class SharedPlace(val query: String? = null, val placeId: String? = null, val needsSearch: Boolean = false)

object MapsLinks {
    private val hosts = setOf("www.google.com", "google.com", "maps.google.com", "maps.google.co.in", "www.google.co.in", "maps.app.goo.gl", "goo.gl")

    fun parseShared(text: String): SharedPlace {
        require(text.length <= 16_384) { "Shared text is too large." }
        val raw = Regex("https://[^\\s<>]+").find(text)?.value?.trimEnd('.', ',', ')')
            ?: return SharedPlace(needsSearch = true)
        val uri = URI(raw)
        require(uri.scheme == "https" && uri.host?.lowercase() in hosts && uri.userInfo == null && uri.port in listOf(-1, 443)) {
            "Only supported Google Maps links can be imported."
        }
        if (uri.host in setOf("maps.app.goo.gl", "goo.gl")) return SharedPlace(needsSearch = true)
        require(uri.path == "/maps" || uri.path.startsWith("/maps/")) { "This is not a Maps link." }
        val parameters = uri.rawQuery.orEmpty().split('&').filter { it.isNotBlank() }.associate { part ->
            val pair = part.split('=', limit = 2)
            URLDecoder.decode(pair[0], "UTF-8") to URLDecoder.decode(pair.getOrElse(1) { "" }, "UTF-8")
        }
        val placeId = parameters["destination_place_id"] ?: parameters["query_place_id"]
        val query = parameters["destination"] ?: parameters["query"] ?: parameters["q"]
        return if (!placeId.isNullOrBlank() || !query.isNullOrBlank()) {
            SharedPlace(query?.takeIf { it.isNotBlank() }?.take(300), placeId?.takeIf { it.isNotBlank() }?.take(512))
        } else SharedPlace(needsSearch = true)
    }

    fun navigation(destination: Destination): String {
        TripTransfer.validateDestination(destination)
        val target = destination.query ?: destination.latitude?.let { "$it,${destination.longitude}" } ?: destination.label
        val query = linkedMapOf("api" to "1", "destination" to target, "travelmode" to "driving", "dir_action" to "navigate")
        destination.placeId?.let { query["destination_place_id"] = it }
        val url = "https://www.google.com/maps/dir/?" + query.entries.joinToString("&") {
            "${it.key}=${URLEncoder.encode(it.value, "UTF-8") }"
        }
        require(url.length <= 2048) { "Destination link is too long." }
        return url
    }
}
