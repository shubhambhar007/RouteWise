package app.routewise.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.routewise.core.*
import app.routewise.data.*
import app.routewise.maps.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface TripEvent {
    data class Open(val id: String) : TripEvent
    data class Message(val text: String) : TripEvent
    data class Share(val title: String, val json: String) : TripEvent
}

data class RoadRouteState(val loading: Boolean = false, val preview: RoutePreview? = null, val error: String? = null)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TripViewModel @Inject constructor(
    private val repository: TripRepository,
    private val savedState: SavedStateHandle,
    val maps: OpenMaps,
    private val pendingShares: PendingShares,
) : ViewModel() {
    val trips = repository.trips.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    private val selected = savedState.getStateFlow<String?>("trip", null)
    val detail = selected.flatMapLatest { id -> id?.let(repository::observeTrip) ?: flowOf(null) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val pending = pendingShares.items
    private val activeShareId = savedState.getStateFlow<String?>("activeShare", null)
    val incomingShare = combine(activeShareId, pending) { id, items -> items.find { it.id == id }?.text }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    private val eventChannel = Channel<TripEvent>(Channel.BUFFERED)
    val events = eventChannel.receiveAsFlow()
    private val mutableRoute = MutableStateFlow(RoadRouteState())
    val route = mutableRoute.asStateFlow()
    private var routeJob: Job? = null

    fun clearRoute() {
        routeJob?.cancel()
        mutableRoute.value = RoadRouteState()
    }

    fun calculateRoute(nodes: List<RouteNode>) {
        clearRoute()
        routeJob = viewModelScope.launch {
            mutableRoute.value = RoadRouteState(loading = true)
            try { mutableRoute.value = RoadRouteState(preview = maps.route(nodes)) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { mutableRoute.value = RoadRouteState(error = failure.message ?: "Unable to calculate a driving route.") }
        }
    }

    fun applySuggestedDurations(tripId: String, dayId: String, revision: Long, progressRevision: Long, changes: Map<String, Int>, done: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            try { repository.applySuggestedDurations(tripId, dayId, revision, progressRevision, changes); done(true, null) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { done(false, failure.message ?: "Trip changed; review suggestions again.") }
        }
    }

    fun insertSuggestedBreak(tripId: String, dayId: String, revision: Long, progressRevision: Long,
        beforeStopId: String?, destination: Destination, minutes: Int, estimatedMinute: Int, done: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            try { repository.insertSuggestedBreak(tripId, dayId, revision, progressRevision, beforeStopId, destination, minutes, estimatedMinute); done(true, null) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { done(false, failure.message ?: "Trip changed; search again.") }
        }
    }

    fun select(id: String) { savedState["trip"] = id }
    fun receiveShare(text: String) = perform {
        val item = withContext(Dispatchers.IO) { pendingShares.add(text) }
        savedState["activeShare"] = item.id
    }
    fun selectShare(id: String) { savedState["activeShare"] = id }
    fun clearShare() { savedState["activeShare"] = null }
    fun discardShare(id: String) = perform {
        withContext(Dispatchers.IO) { pendingShares.remove(id) }
        if (activeShareId.value == id) clearShare()
    }
    fun message(text: String) { eventChannel.trySend(TripEvent.Message(text)) }

    private fun perform(action: suspend () -> Unit) {
        viewModelScope.launch {
            try { action() }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (failure: Exception) { eventChannel.send(TripEvent.Message(failure.message ?: "Unable to save. Please retry.")) }
        }
    }

    fun create(title: String, days: Int) = perform { eventChannel.send(TripEvent.Open(repository.create(title, days))) }
    fun rename(id: String, title: String) = perform { repository.rename(id, title) }
    fun duplicate(id: String) = perform { eventChannel.send(TripEvent.Open(repository.duplicate(id))) }
    fun delete(id: String, done: () -> Unit) = perform { repository.delete(id); done() }
    fun addDay(id: String) = perform { repository.addDay(id) }
    fun removeDay(id: String) = perform { repository.removeDay(id) }
    fun updateDay(id: String, title: String, date: String?, done: () -> Unit) = perform { repository.updateDay(id, title, date); done() }
    fun setAnchor(dayId: String, start: Boolean, destination: Destination?, done: (Boolean) -> Unit) = perform {
        try { repository.setAnchor(dayId, start, destination); done(true) }
        catch (failure: Exception) { done(false); throw failure }
    }
    fun removeStop(id: String) = perform { repository.removeStop(id) }
    fun moveToDay(id: String, dayId: String?) = perform { repository.moveToDay(id, dayId) }
    fun visit(id: String, state: String) = perform { repository.setVisitState(id, state) }
    fun reorder(tripId: String, dayId: String?, ids: List<String>) = perform { repository.reorder(tripId, dayId, ids) }
    fun saveStop(tripId: String, dayId: String?, destination: Destination, minutes: Int, mustVisit: Boolean, notes: String, stopId: String?, done: (Boolean) -> Unit) = perform {
        try {
            repository.saveStop(tripId, dayId, destination, minutes, mustVisit, notes, stopId)
            activeShareId.value?.let { id -> withContext(Dispatchers.IO) { pendingShares.remove(id) } }
            clearShare()
            done(true)
        } catch (failure: Exception) {
            done(false)
            throw failure
        }
    }
    fun share(id: String) = perform {
        val trip = repository.export(id)
        eventChannel.send(TripEvent.Share(trip.title, TripTransfer.encode(trip)))
    }
    fun importTrip(text: String) = perform {
        eventChannel.send(TripEvent.Open(repository.importTrip(TripTransfer.decode(text))))
    }
    fun sample() = perform {
        val places = listOf("Hawa Mahal", "City Palace", "Jantar Mantar", "Amber Fort", "Panna Meena ka Kund", "Nahargarh Fort")
        val sample = TransferTrip(title = "Jaipur weekend (sample)", days = listOf("The old city", "Forts & hills"), stops = places.mapIndexed { index, name ->
            TransferStop(Destination(name, query = "$name, Jaipur, India"), day = index / 3,
                durationMinutes = listOf(45, 90, 60, 120, 30, 90)[index], mustVisit = index != 4)
        })
        eventChannel.send(TripEvent.Open(repository.importTrip(sample)))
    }

    fun jaipurSimulation() = perform {
        eventChannel.send(TripEvent.Open(repository.importTrip(JaipurSimulation.trip())))
    }
}
