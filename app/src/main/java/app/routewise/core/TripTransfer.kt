package app.routewise.core

import app.routewise.data.Destination
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.time.ZoneId
import java.time.LocalDate

@Serializable
data class TransferDayDetails(val localDate: String? = null, val start: Destination? = null, val finish: Destination? = null)

@Serializable
data class TransferStop(
    val destination: Destination,
    val day: Int? = null,
    val durationMinutes: Int = 60,
    val mustVisit: Boolean = true,
    val notes: String = "",
)

@Serializable
data class TransferTrip(
    val format: String = "routewise.local-trip",
    val version: Int = 4,
    val title: String,
    val timezoneId: String = "Asia/Kolkata",
    val days: List<String>,
    val stops: List<TransferStop>,
    val dayDetails: List<TransferDayDetails> = emptyList(),
    val attribution: String = "User-authored trip data; OpenStreetMap-derived locations, where present, are (c) OpenStreetMap contributors, ODbL 1.0: https://www.openstreetmap.org/copyright",
)

object TripTransfer {
    const val MAX_BYTES = 8 * 1024 * 1024
    private val json = Json { prettyPrint = true; encodeDefaults = true }

    fun encode(trip: TransferTrip): String {
        validate(trip)
        return json.encodeToString(trip)
    }

    fun decode(text: String): TransferTrip {
        require(text.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "Trip file exceeds 8 MB." }
        return json.decodeFromString<TransferTrip>(text).also(::validate)
    }

    fun validate(trip: TransferTrip) {
        require(trip.format == "routewise.local-trip" && trip.version in 1..4) { "Unsupported trip format." }
        require(trip.title.isNotBlank() && trip.title.length <= 120) { "Trip title must be 1-120 characters." }
        require(trip.attribution.length <= 1000) { "Attribution is too long." }
        ZoneId.of(trip.timezoneId)
        require(trip.days.all { it.length <= 120 }) { "Day title is too long." }
        require(trip.dayDetails.isEmpty() || trip.dayDetails.size == trip.days.size) { "Day details do not match the trip days." }
        trip.dayDetails.forEach { day ->
            day.localDate?.let { LocalDate.parse(it) }
            day.start?.let(::validateDestination)
            day.finish?.let(::validateDestination)
        }
        val dates = trip.dayDetails.mapNotNull { it.localDate }
        require(dates.zipWithNext().all { (first, second) -> first < second }) { "Day dates must be unique and in increasing order." }
        trip.stops.forEach { stop ->
            validateDestination(stop.destination)
            require(stop.day == null || stop.day in trip.days.indices) { "Stop references a missing day." }
            require(stop.durationMinutes in 0..1440) { "Visit time must be 0-1440 minutes." }
            require(stop.notes.length <= 4000) { "Stop notes exceed 4000 characters." }
        }
    }

    fun validateDestination(destination: Destination) {
        require(destination.label.isNotBlank() && destination.label.length <= 120) { "Add a name of up to 120 characters." }
        val hasCoordinates = destination.latitude != null || destination.longitude != null
        destination.osmSourceId?.let {
            require(hasCoordinates && Regex("[NWR]/[0-9]{1,20}").matches(it)) { "Invalid OpenStreetMap reference." }
        }
        destination.osmCategory?.let {
            require(destination.osmSourceId != null && Regex("[a-z_]{1,40}:[a-z_]{1,80}").matches(it)) { "Place category requires a valid OpenStreetMap reference." }
        }
        require(listOf(destination.query != null, destination.placeId != null, hasCoordinates).count { it } == 1) {
            "Choose one destination: search text, Place ID, or coordinates."
        }
        destination.query?.let {
            require(it.isNotBlank() && it.length <= 300 && !it.contains("://")) { "Enter a place and city, not a URL." }
        }
        destination.placeId?.let { require(it.isNotBlank() && it.length <= 512) { "Invalid Place ID." } }
        if (hasCoordinates) {
            require(destination.latitude != null && destination.longitude != null &&
                TripRules.validCoordinates(destination.latitude, destination.longitude)) { "Invalid latitude or longitude." }
        }
    }
}
