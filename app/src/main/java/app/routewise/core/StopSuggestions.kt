package app.routewise.core

import java.time.LocalTime
import kotlin.math.ceil
import kotlin.math.roundToInt

enum class TripPace(val multiplier: Double) { QUICK(0.75), BALANCED(1.0), RELAXED(1.35) }
enum class BreakKind { REST, MEAL }

data class VisitSuggestion(val minutes: Int, val minimum: Int, val maximum: Int, val reason: String, val category: String?)
data class SuggestionVisit(val id: String, val label: String, val minutes: Int)
data class SuggestionLeg(val fromId: String, val toId: String, val seconds: Double)
data class DayTiming(val stopId: String, val arrivalMinute: Int, val departureMinute: Int)
data class DriveTiming(val fromId: String, val toId: String, val departureMinute: Int, val arrivalMinute: Int)
enum class SimulationKind { VISIT, DRIVE, REST, MEAL, PAUSE, FINISHED }
data class SimulationStatus(val kind: SimulationKind, val title: String, val detail: String)
data class BreakSuggestion(
    val id: String,
    val kind: BreakKind,
    val atMinute: Int,
    val durationMinutes: Int,
    val fromId: String,
    val toId: String,
    val legFraction: Double,
    val duringDrive: Boolean,
)
data class DaySuggestions(
    val startMinute: Int,
    val timings: List<DayTiming>,
    val driving: List<DriveTiming>,
    val breaks: List<BreakSuggestion>,
    val endMinute: Int,
    val overBudgetMinutes: Int,
)

object StopSuggestions {
    private data class Profile(val name: String, val minimum: Int, val typical: Int, val maximum: Int)
    private val profiles = mapOf(
        "museum" to Profile("museum", 60, 90, 150),
        "gallery" to Profile("gallery", 30, 60, 90),
        "fort" to Profile("fort or palace", 60, 120, 180),
        "viewpoint" to Profile("viewpoint", 15, 30, 45),
        "park" to Profile("park or garden", 30, 60, 120),
        "beach" to Profile("beach", 45, 90, 180),
        "worship" to Profile("place of worship", 20, 45, 75),
        "restaurant" to Profile("meal", 30, 60, 90),
        "cafe" to Profile("cafe break", 15, 30, 45),
        "rest_area" to Profile("rest stop", 10, 20, 30),
        "fuel" to Profile("fuel stop", 10, 15, 25),
        "landmark" to Profile("historic or cultural landmark", 30, 60, 120),
        "observatory" to Profile("observatory", 45, 75, 120),
    )

    fun duration(label: String, osmCategory: String?, pace: TripPace): VisitSuggestion? {
        val source = when (osmCategory) {
            "tourism:museum" -> "museum"
            "tourism:gallery" -> "gallery"
            "historic:castle", "historic:fort", "historic:palace" -> "fort"
            "tourism:viewpoint" -> "viewpoint"
            "leisure:park", "leisure:garden", "leisure:nature_reserve" -> "park"
            "natural:beach" -> "beach"
            "amenity:place_of_worship" -> "worship"
            "amenity:restaurant", "amenity:fast_food", "amenity:food_court" -> "restaurant"
            "amenity:cafe" -> "cafe"
            "highway:rest_area", "highway:services" -> "rest_area"
            "amenity:fuel" -> "fuel"
            "railway:station" -> null
            "tourism:attraction" -> when {
                "jantar mantar" in label.lowercase() -> "observatory"
                else -> "landmark"
            }
            else -> null
        }
        val words = label.lowercase().split(Regex("[^\\p{L}]+"))
        val inferred = when {
            "museum" in words -> "museum"
            "gallery" in words -> "gallery"
            words.any { it in setOf("fort", "palace", "castle") } -> "fort"
            "viewpoint" in words -> "viewpoint"
            words.any { it in setOf("park", "garden", "gardens") } -> "park"
            "beach" in words -> "beach"
            words.any { it in setOf("temple", "mosque", "church", "gurudwara") } -> "worship"
            "restaurant" in words -> "restaurant"
            "cafe" in words -> "cafe"
            else -> null
        }
        val category = source ?: inferred ?: return null
        val profile = profiles.getValue(category)
        val minutes = ((profile.typical * pace.multiplier / 5).roundToInt() * 5).coerceIn(profile.minimum, profile.maximum)
        val evidence = if (source != null) "OpenStreetMap category" else "Name-based guess; confirm the place type"
        return VisitSuggestion(minutes, profile.minimum, profile.maximum,
            "$evidence: ${profile.name}. Planning range only; queues, access and opening hours are unverified.", category)
    }

    fun timeline(
        orderedNodeIds: List<String>,
        visits: List<SuggestionVisit>,
        legs: List<SuggestionLeg>,
        departure: LocalTime,
        dayLimitMinutes: Int = 480,
        restEveryMinutes: Int = 120,
        restMinutes: Int = 15,
        mealMinutes: Int = 45,
    ): DaySuggestions {
        require(orderedNodeIds.size in 2..100 && orderedNodeIds.distinct().size == orderedNodeIds.size)
        require(legs.size == orderedNodeIds.size - 1 && legs.map { it.fromId to it.toId } == orderedNodeIds.zipWithNext()) { "A complete, current route is required." }
        require(legs.all { it.seconds.isFinite() && it.seconds in 0.0..86_400.0 })
        require(visits.map { it.id }.distinct().size == visits.size && visits.all { it.id in orderedNodeIds && it.minutes in 0..1440 })
        require(dayLimitMinutes in 60..960 && restEveryMinutes in 60..240 && restMinutes in 5..60 && mealMinutes in 15..120)
        val visitsById = visits.associateBy { it.id }
        val timings = mutableListOf<DayTiming>()
        val driving = mutableListOf<DriveTiming>()
        val breaks = mutableListOf<BreakSuggestion>()
        val start = departure.hour * 60 + departure.minute
        var clock = start
        var drivingSinceRest = 0
        var mealAdded = start >= 14 * 60
        fun visit(id: String) {
            visitsById[id]?.let {
                timings += DayTiming(id, clock, clock + it.minutes)
                clock += it.minutes
                if (it.minutes >= restMinutes) drivingSinceRest = 0
            }
        }
        visit(orderedNodeIds.first())
        legs.forEachIndexed { index, leg ->
            val legMinutes = ceil(leg.seconds / 60).toInt()
            var traveled = 0
            while (traveled < legMinutes) {
                val untilRest = restEveryMinutes - drivingSinceRest
                val untilMeal = if (!mealAdded && clock < 12 * 60 + 30) 12 * 60 + 30 - clock else Int.MAX_VALUE
                val remaining = legMinutes - traveled
                val drive = minOf(remaining, untilRest, untilMeal)
                val driveStart = clock
                clock += drive; traveled += drive; drivingSinceRest += drive
                if (drive > 0) driving += DriveTiming(leg.fromId, leg.toId, driveStart, clock)
                if (traveled < legMinutes && (drivingSinceRest >= restEveryMinutes || !mealAdded && clock >= 12 * 60 + 30)) {
                    val kind = if (!mealAdded && clock >= 12 * 60 + 30) BreakKind.MEAL else BreakKind.REST
                    val duration = if (kind == BreakKind.MEAL) mealMinutes else restMinutes
                    breaks += BreakSuggestion("break-$index-$traveled", kind, clock, duration, leg.fromId, leg.toId,
                        if (legMinutes == 0) 0.0 else traveled.toDouble() / legMinutes, true)
                    clock += duration; drivingSinceRest = 0
                    if (kind == BreakKind.MEAL) mealAdded = true
                }
            }
            visit(leg.toId)
            if (!mealAdded && clock >= 12 * 60 + 30 && index < legs.lastIndex) {
                val nextLeg = legs[index + 1]
                breaks += BreakSuggestion("meal-$index", BreakKind.MEAL, clock, mealMinutes, nextLeg.fromId, nextLeg.toId, 0.0, false)
                clock += mealMinutes; drivingSinceRest = 0; mealAdded = true
            }
        }
        if (!mealAdded && clock >= 12 * 60 + 30) {
            val lastLeg = legs.last()
            breaks += BreakSuggestion("meal-after-route", BreakKind.MEAL, clock, mealMinutes, lastLeg.fromId, lastLeg.toId, 1.0, false)
            clock += mealMinutes
        }
        return DaySuggestions(start, timings, driving, breaks, clock, (clock - start - dayLimitMinutes).coerceAtLeast(0))
    }

    fun simulationStatus(schedule: DaySuggestions, stopLabels: Map<String, String>, minute: Int): SimulationStatus {
        if (minute >= schedule.endMinute) return SimulationStatus(SimulationKind.FINISHED, "Day preview finished", "Virtual playback only. No visit status was changed.")
        schedule.breaks.firstOrNull { minute >= it.atMinute && minute < it.atMinute + it.durationMinutes }?.let { pause ->
            val kind = if (pause.kind == BreakKind.MEAL) SimulationKind.MEAL else SimulationKind.REST
            val title = if (kind == SimulationKind.MEAL) "Meal break" else "Rest break"
            return SimulationStatus(kind, title, "${StopSuggestions.clockLabel(pause.atMinute)}-${StopSuggestions.clockLabel(pause.atMinute + pause.durationMinutes)} · suggested stop, not booked")
        }
        schedule.timings.firstOrNull { minute >= it.arrivalMinute && minute < it.departureMinute }?.let { visit ->
            val label = stopLabels[visit.stopId] ?: "Planned stop"
            return SimulationStatus(SimulationKind.VISIT, label, "Visiting · planned ${StopSuggestions.clockLabel(visit.arrivalMinute)}-${StopSuggestions.clockLabel(visit.departureMinute)}")
        }
        schedule.driving.firstOrNull { minute >= it.departureMinute && minute < it.arrivalMinute }?.let { leg ->
            val from = stopLabels[leg.fromId] ?: if (schedule.driving.firstOrNull() == leg) "Day start" else "Previous stop"
            val to = stopLabels[leg.toId] ?: "Next stop"
            return SimulationStatus(SimulationKind.DRIVE, "Driving · $from to $to", "Estimated ${StopSuggestions.clockLabel(leg.departureMinute)}-${StopSuggestions.clockLabel(leg.arrivalMinute)} · no live traffic")
        }
        val next = schedule.timings.firstOrNull { it.arrivalMinute > minute }
        return SimulationStatus(SimulationKind.PAUSE, "Flexible time", next?.let { "Next visit: ${StopSuggestions.clockLabel(it.arrivalMinute)}" } ?: "No scheduled activity")
    }

    fun nextSimulationBoundary(schedule: DaySuggestions, minute: Int): Int? = sequence {
        schedule.timings.forEach { yield(it.arrivalMinute); yield(it.departureMinute) }
        schedule.driving.forEach { yield(it.departureMinute); yield(it.arrivalMinute) }
        schedule.breaks.forEach { yield(it.atMinute); yield(it.atMinute + it.durationMinutes) }
        yield(schedule.endMinute)
    }.filter { it > minute }.minOrNull()

    fun clockLabel(minute: Int): String {
        val day = minute / 1440
        return "%02d:%02d".format((minute % 1440) / 60, minute % 60) + if (day > 0) " (+${day}d)" else ""
    }
}
