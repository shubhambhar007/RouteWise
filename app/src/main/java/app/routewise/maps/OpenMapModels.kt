package app.routewise.maps

import app.routewise.core.TripRules
import kotlinx.serialization.json.*
import java.security.MessageDigest
import kotlin.math.abs
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import org.maplibre.turf.TurfConstants
import org.maplibre.turf.TurfMeasurement

data class MapPoint(val latitude: Double, val longitude: Double) {
    init { require(TripRules.validCoordinates(latitude, longitude)) { "Invalid map coordinates." } }
}

data class PlaceResult(val sourceId: String, val name: String, val address: String, val point: MapPoint, val osmCategory: String? = null, val distanceFromSearchKm: Double? = null)
data class RouteNode(val id: String, val point: MapPoint)
data class RoadLeg(val fromId: String, val toId: String, val meters: Double, val seconds: Double, val geometry: List<MapPoint> = emptyList())
data class RoadSegment(val nodes: List<RouteNode>, val geometry: List<MapPoint>, val legs: List<RoadLeg>)
data class RoutePreview(
    val fingerprint: String,
    val segments: List<RoadSegment>,
    val failedBatches: Int,
    val totalBatches: Int,
    val failureReasons: List<String> = emptyList(),
) {
    val complete: Boolean get() = failedBatches == 0 && segments.size == totalBatches && totalBatches > 0
    val meters: Double? get() = if (complete) segments.sumOf { segment -> segment.legs.sumOf { it.meters } } else null
    val seconds: Double? get() = if (complete) segments.sumOf { segment -> segment.legs.sumOf { it.seconds } } else null
}

object OpenMapModels {
    fun distanceKm(first: MapPoint, second: MapPoint): Double = TurfMeasurement.distance(
        Point.fromLngLat(first.longitude, first.latitude), Point.fromLngLat(second.longitude, second.latitude), TurfConstants.UNIT_KILOMETERS)

    fun pointAlong(leg: RoadLeg, fraction: Double): MapPoint? {
        require(fraction.isFinite() && fraction in 0.0..1.0)
        if (leg.geometry.size < 2) return null
        val line = LineString.fromLngLats(leg.geometry.map { Point.fromLngLat(it.longitude, it.latitude) })
        val point = TurfMeasurement.along(line, TurfMeasurement.length(line, TurfConstants.UNIT_KILOMETERS) * fraction, TurfConstants.UNIT_KILOMETERS)
        return MapPoint(point.latitude(), point.longitude())
    }

    fun pointAlong(preview: RoutePreview, fromId: String, toId: String, fraction: Double): MapPoint? =
        preview.segments.asSequence().flatMap { it.legs.asSequence() }
            .firstOrNull { it.fromId == fromId && it.toId == toId }?.let { pointAlong(it, fraction) }
    fun fingerprint(nodes: List<RouteNode>): String = MessageDigest.getInstance("SHA-256")
        .digest(nodes.joinToString("|") { "${it.id}:${it.point.latitude}:${it.point.longitude}" }.toByteArray())
        .joinToString("") { "%02x".format(it) }

    fun places(body: String): List<PlaceResult> {
        val root = Json.parseToJsonElement(body).jsonObject
        require(root["type"]?.jsonPrimitive?.content == "FeatureCollection") { "Search returned an invalid response." }
        return root.getValue("features").jsonArray.take(8).mapNotNull { feature ->
            runCatching {
                val value = feature.jsonObject
                val geometry = value.getValue("geometry").jsonObject
                require(geometry["type"]?.jsonPrimitive?.content == "Point")
                val point = point(geometry.getValue("coordinates"))
                val properties = value.getValue("properties").jsonObject
                fun text(name: String) = properties[name]?.jsonPrimitive?.contentOrNull.orEmpty().take(300)
                val name = text("name").ifBlank { text("street") }.ifBlank { text("city") }
                require(name.isNotBlank())
                val sourceId = "${text("osm_type")}/${text("osm_id")}".take(80)
                require(text("osm_type") in setOf("N", "W", "R") && text("osm_id").toLongOrNull() != null)
                val address = listOf(text("street"), text("city"), text("state"), text("country")).filter(String::isNotBlank).distinct().joinToString(", ")
                val category = "${text("osm_key")}:${text("osm_value")}".takeIf { Regex("[a-z_]{1,40}:[a-z_]{1,80}").matches(it) }
                PlaceResult(sourceId, name.take(120), address, point, category)
            }.getOrNull()
        }.distinctBy { it.sourceId }
    }

    fun route(body: String, nodes: List<RouteNode>): RoadSegment {
        require(nodes.size in 2..27 && nodes.map { it.id }.distinct().size == nodes.size)
        val root = Json.parseToJsonElement(body).jsonObject
        require(root["code"]?.jsonPrimitive?.content == "Ok") { "No driving route was found for these stops." }
        require(root.getValue("waypoints").jsonArray.size == nodes.size) { "Route is missing a stop." }
        val route = root.getValue("routes").jsonArray.first().jsonObject
        val legs = route.getValue("legs").jsonArray
        require(legs.size == nodes.size - 1) { "Route is missing a leg." }
        fun quantity(value: JsonElement): Double = value.jsonPrimitive.double.also { require(it.isFinite() && it >= 0) }
        val mappedLegs = legs.mapIndexed { index, leg ->
            val stepGeometry = leg.jsonObject["steps"]?.jsonArray.orEmpty().flatMap { step ->
                val geometry = step.jsonObject.getValue("geometry").jsonObject
                require(geometry["type"]?.jsonPrimitive?.content == "LineString")
                geometry.getValue("coordinates").jsonArray.map(::point)
            }
            require(stepGeometry.size <= 50_000) { "Route detail is too large." }
            RoadLeg(nodes[index].id, nodes[index + 1].id, quantity(leg.jsonObject.getValue("distance")), quantity(leg.jsonObject.getValue("duration")), stepGeometry)
        }
        require(abs(quantity(route.getValue("distance")) - mappedLegs.sumOf { it.meters }) <= 2.0) { "Route distance does not reconcile." }
        require(abs(quantity(route.getValue("duration")) - mappedLegs.sumOf { it.seconds }) <= 2.0) { "Route duration does not reconcile." }
        val geometry = route.getValue("geometry").jsonObject
        require(geometry["type"]?.jsonPrimitive?.content == "LineString")
        val coordinates = geometry.getValue("coordinates").jsonArray
        require(coordinates.size in 2..50_000) { "Route geometry is invalid or too large." }
        return RoadSegment(nodes, coordinates.map(::point), mappedLegs)
    }

    private fun point(value: JsonElement): MapPoint {
        val coordinates = value.jsonArray
        require(coordinates.size >= 2)
        return MapPoint(coordinates[1].jsonPrimitive.double, coordinates[0].jsonPrimitive.double)
    }
}
