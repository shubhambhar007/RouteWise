package app.routewise.data

import androidx.room.withTransaction
import app.routewise.core.TransferStop
import app.routewise.core.TransferTrip
import app.routewise.core.TransferDayDetails
import app.routewise.core.TripTransfer
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TripRepository @Inject constructor(private val database: TripDatabase) {
    private val dao = database.trips()
    val trips = dao.observeTrips()
    fun observeTrip(id: String) = dao.observeTrip(id)

    suspend fun create(title: String, dayCount: Int): String {
        require(dayCount in 1..30) { "Choose 1-30 initial days." }
        return importTrip(TransferTrip(title = title.trim(), days = List(dayCount) { "" }, stops = emptyList()))
    }

    suspend fun rename(id: String, title: String) {
        require(title.trim().isNotBlank() && title.trim().length <= 120) { "Enter a title of 1-120 characters." }
        dao.rename(id, title.trim())
    }

    suspend fun delete(id: String) = dao.deleteTrip(id)
    suspend fun duplicate(id: String): String = importTrip(export(id).let { it.copy(title = (it.title.take(113) + " (copy)")) })

    suspend fun addDay(tripId: String): String = database.withTransaction {
        val trip = requireNotNull(dao.detail(tripId)) { "Trip no longer exists." }
        val day = DayEntity(tripId = tripId, ordinal = (trip.days.maxOfOrNull { it.ordinal } ?: -1) + 1)
        dao.insertDays(listOf(day))
        dao.touch(tripId)
        day.id
    }

    suspend fun updateDay(id: String, title: String, localDate: String?) = database.withTransaction {
        val day = requireNotNull(dao.day(id)) { "Day no longer exists." }
        require(title.length <= 120) { "Day title is too long." }
        localDate?.let { java.time.LocalDate.parse(it) }
        val allDays = requireNotNull(dao.detail(day.tripId)).days.sortedBy { it.ordinal }
        val dates = allDays.mapNotNull { if (it.id == id) localDate else it.localDate }
        require(dates.zipWithNext().all { (first, second) -> first < second }) { "Day dates must be unique and in increasing order." }
        dao.updateDay(day.copy(title = title.trim(), localDate = localDate))
        dao.touch(day.tripId)
    }

    suspend fun setAnchor(dayId: String, start: Boolean, destination: Destination?) = database.withTransaction {
        val day = requireNotNull(dao.day(dayId)) { "Day no longer exists." }
        destination?.let(TripTransfer::validateDestination)
        dao.updateDay(if (start) day.copy(start = destination) else day.copy(finish = destination))
        dao.touch(day.tripId)
    }

    suspend fun saveStop(tripId: String, dayId: String?, destination: Destination, minutes: Int, mustVisit: Boolean, notes: String, stopId: String? = null) = database.withTransaction {
        TripTransfer.validate(TransferTrip(title = "Validation", days = emptyList(), stops = listOf(TransferStop(destination, durationMinutes = minutes, mustVisit = mustVisit, notes = notes))))
        requireNotNull(dao.detail(tripId)) { "Trip no longer exists." }
        require(dayId == null || dao.day(dayId)?.tripId == tripId) { "Day belongs to another trip." }
        val previous = stopId?.let { requireNotNull(dao.stop(it)) { "Stop no longer exists." } }
        require(previous == null || previous.tripId == tripId) { "Stop belongs to another trip." }
        val targetPosition = if (previous != null && previous.dayId == dayId) previous.position
            else (dao.ordered(tripId, dayId).maxOfOrNull { it.position } ?: -1) + 1
        val stop = StopEntity(previous?.id ?: newId(), tripId, dayId, targetPosition, destination, minutes, mustVisit, notes, previous?.visitState ?: "PLANNED")
        if (previous == null) dao.insertStops(listOf(stop)) else dao.updateStop(stop)
        if (previous != null && previous.dayId != dayId) normalize(tripId, previous.dayId)
        dao.touch(tripId)
    }

    suspend fun reorder(tripId: String, dayId: String?, ids: List<String>) = database.withTransaction {
        val current = dao.ordered(tripId, dayId)
        require(ids.size == current.size && ids.toSet() == current.map { it.id }.toSet()) { "Stops changed; please retry." }
        writeOrder(tripId, dayId, ids.map { id -> current.first { it.id == id } })
        dao.touch(tripId)
    }

    suspend fun moveToDay(stopId: String, dayId: String?) = database.withTransaction {
        val stop = requireNotNull(dao.stop(stopId)) { "Stop no longer exists." }
        require(dayId == null || dao.day(dayId)?.tripId == stop.tripId) { "Day belongs to another trip." }
        if (stop.dayId != dayId) {
            val position = (dao.ordered(stop.tripId, dayId).maxOfOrNull { it.position } ?: -1) + 1
            dao.updateStop(stop.copy(dayId = dayId, position = position))
            normalize(stop.tripId, stop.dayId)
            dao.touch(stop.tripId)
        }
    }

    suspend fun removeStop(id: String) = database.withTransaction {
        val stop = requireNotNull(dao.stop(id)) { "Stop no longer exists." }
        dao.deleteStop(id)
        normalize(stop.tripId, stop.dayId)
        dao.touch(stop.tripId)
    }

    suspend fun applySuggestedDurations(tripId: String, dayId: String, revision: Long, progressRevision: Long, changes: Map<String, Int>) = database.withTransaction {
        val detail = requireNotNull(dao.detail(tripId)) { "Trip no longer exists." }
        require(detail.trip.revision == revision && detail.trip.progressRevision == progressRevision) { "The trip changed. Review new suggestions first." }
        require(changes.isNotEmpty() && changes.values.all { it in 0..1440 }) { "Choose valid visit durations." }
        val stops = detail.stops.filter { it.dayId == dayId && it.visitState == "PLANNED" }.associateBy { it.id }
        require(changes.keys.all { it in stops }) { "A selected stop changed or belongs to another day." }
        changes.forEach { (id, minutes) -> dao.updateStop(stops.getValue(id).copy(durationMinutes = minutes)) }
        dao.touch(tripId)
    }

    suspend fun insertSuggestedBreak(tripId: String, dayId: String, revision: Long, progressRevision: Long,
        beforeStopId: String?, destination: Destination, minutes: Int, estimatedMinute: Int) = database.withTransaction {
        val detail = requireNotNull(dao.detail(tripId)) { "Trip no longer exists." }
        require(detail.trip.revision == revision && detail.trip.progressRevision == progressRevision) { "The trip changed. Review new suggestions first." }
        require(detail.days.any { it.id == dayId }) { "Day no longer exists." }
        TripTransfer.validateDestination(destination)
        require(destination.osmSourceId != null && destination.osmCategory in setOf("amenity:cafe", "amenity:restaurant", "amenity:fast_food", "amenity:food_court", "highway:rest_area", "highway:services")) { "Choose a confirmed break venue." }
        require(minutes in 5..120) { "Break duration must be 5-120 minutes." }
        require(estimatedMinute in 0..2879) { "Suggested break time is invalid." }
        val ordered = dao.ordered(tripId, dayId)
        val insertion = beforeStopId?.let { id -> ordered.indexOfFirst { it.id == id }.also { require(it >= 0) { "The next stop changed." } } } ?: ordered.size
        val stop = StopEntity(tripId = tripId, dayId = dayId, position = (ordered.maxOfOrNull { it.position } ?: -1) + 1,
            destination = destination, durationMinutes = minutes, mustVisit = false,
            notes = "Suggested around ${app.routewise.core.StopSuggestions.clockLabel(estimatedMinute)}. Advisory only; verify access and opening hours, then recalculate and review the route.")
        dao.insertStops(listOf(stop))
        writeOrder(tripId, dayId, ordered.toMutableList().apply { add(insertion, stop) })
        dao.touch(tripId)
    }

    suspend fun setVisitState(id: String, state: String) = database.withTransaction {
        require(state in setOf("PLANNED", "VISITED", "SKIPPED"))
        val stop = requireNotNull(dao.stop(id)) { "Stop no longer exists." }
        dao.updateStop(stop.copy(visitState = state))
        dao.touchProgress(stop.tripId)
    }

    suspend fun removeDay(id: String) = database.withTransaction {
        val day = requireNotNull(dao.day(id)) { "Day no longer exists." }
        var position = (dao.ordered(day.tripId, null).maxOfOrNull { it.position } ?: -1) + 1
        dao.ordered(day.tripId, id).forEach { dao.updateStop(it.copy(dayId = null, position = position++)) }
        dao.deleteDay(id)
        dao.touch(day.tripId)
    }

    private suspend fun normalize(tripId: String, dayId: String?) = writeOrder(tripId, dayId, dao.ordered(tripId, dayId))

    private suspend fun writeOrder(tripId: String, dayId: String?, stops: List<StopEntity>) {
        val offset = (stops.maxOfOrNull { it.position } ?: 0) + stops.size + 1
        dao.offsetPositions(tripId, dayId, offset)
        stops.forEachIndexed { index, stop -> dao.updateStop(stop.copy(position = index)) }
    }

    suspend fun export(id: String): TransferTrip = database.withTransaction {
        val detail = requireNotNull(dao.detail(id)) { "Trip no longer exists." }
        val days = detail.days.sortedBy { it.ordinal }
        TransferTrip(
            title = detail.trip.title,
            timezoneId = detail.trip.timezoneId,
            days = days.map { it.title },
            dayDetails = days.map { TransferDayDetails(it.localDate, it.start, it.finish) },
            stops = detail.stops.sortedWith(compareBy<StopEntity> { days.indexOfFirst { day -> day.id == it.dayId } }.thenBy { it.position }).map { stop ->
                TransferStop(stop.destination, days.indexOfFirst { it.id == stop.dayId }.takeIf { it >= 0 }, stop.durationMinutes, stop.mustVisit, stop.notes)
            },
        )
    }

    suspend fun importTrip(transfer: TransferTrip): String = database.withTransaction {
        TripTransfer.validate(transfer)
        val trip = TripEntity(title = transfer.title, timezoneId = transfer.timezoneId)
        val days = transfer.days.mapIndexed { index, title ->
            val details = transfer.dayDetails.getOrNull(index)
            DayEntity(tripId = trip.id, ordinal = index, title = title, localDate = details?.localDate, start = details?.start, finish = details?.finish)
        }
        val positions = mutableMapOf<Int?, Int>()
        val stops = transfer.stops.map { item ->
            val position = positions.getOrDefault(item.day, 0)
            positions[item.day] = position + 1
            StopEntity(tripId = trip.id, dayId = item.day?.let { days[it].id }, position = position,
                destination = item.destination, durationMinutes = item.durationMinutes, mustVisit = item.mustVisit, notes = item.notes)
        }
        dao.insertTrip(trip)
        dao.insertDays(days)
        dao.insertStops(stops)
        trip.id
    }
}
