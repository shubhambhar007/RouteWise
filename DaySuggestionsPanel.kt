package app.routewise.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import app.routewise.core.*
import app.routewise.data.*
import app.routewise.maps.*
import java.time.LocalTime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun DaySuggestionsPanel(
    day: DayEntity,
    revision: Long,
    progressRevision: Long,
    stops: List<StopEntity>,
    orderedNodeIds: List<String>,
    stopLabels: Map<String, String>,
    route: RoutePreview?,
    pace: TripPace,
    onPace: (TripPace) -> Unit,
    maps: OpenMaps,
    onApplyDurations: (Map<String, Int>, (Boolean, String?) -> Unit) -> Unit,
    onInsertBreak: (String?, Destination, Int, Int, (Boolean, String?) -> Unit) -> Unit,
) {
    val routeLegs = route?.segments.orEmpty().flatMap { segment -> segment.legs.map { SuggestionLeg(it.fromId, it.toId, it.seconds) } }
    val selectedDurations = remember(day.id, revision, progressRevision, route?.fingerprint, pace) { mutableStateMapOf<String, Int>() }
    val visits = stops.filter { it.visitState == "PLANNED" && it.id in orderedNodeIds }
        .map { SuggestionVisit(it.id, it.destination.label, selectedDurations[it.id] ?: it.durationMinutes) }
    var departure by rememberSaveable(day.id, route?.fingerprint) { mutableStateOf("09:00") }
    var dayBudget by rememberSaveable(day.id) { mutableStateOf("480") }
    var simulationMinute by rememberSaveable(day.id, route?.fingerprint) { mutableIntStateOf(0) }
    var simulationSpeed by rememberSaveable(day.id) { mutableIntStateOf(5) }
    var simulationPlaying by remember { mutableStateOf(false) }
    val online by maps.settings.collectAsState()
    val aiSettings by maps.aiSettings.collectAsState()
    var showDurationSuggestions by rememberSaveable(day.id) { mutableStateOf(false) }
    var savingDurations by remember { mutableStateOf(false) }
    var applyingBreak by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val nearbyResults = remember { mutableStateMapOf<String, List<PlaceResult>>() }
    val searching = remember { mutableStateMapOf<String, Boolean>() }
    val searchErrors = remember { mutableStateMapOf<String, String>() }
    val aiRecommendations = remember { mutableStateMapOf<String, List<AiPlaceRecommendation>>() }
    val aiRanking = remember { mutableStateMapOf<String, Boolean>() }
    var aiPreference by rememberSaveable(day.id) { mutableStateOf("") }
    var pendingAiRequest by remember { mutableStateOf<BreakSuggestion?>(null) }
    var aiError by remember { mutableStateOf<String?>(null) }
    var pendingBreak by remember { mutableStateOf<Pair<BreakSuggestion, PlaceResult>?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(aiPreference, pace, departure, route?.fingerprint) {
        aiRecommendations.clear()
    }

    val result = remember(route?.fingerprint, routeLegs, orderedNodeIds, visits, departure, dayBudget, pace) {
        runCatching {
            require(route?.complete == true) { "Confirm the stop locations and calculate a complete route for arrival times and break suggestions. Visit-time suggestions still work without a route." }
            StopSuggestions.timeline(
                orderedNodeIds, visits, routeLegs, LocalTime.parse(departure), dayBudget.toInt(),
                restEveryMinutes = if (pace == TripPace.RELAXED) 105 else 120,
                restMinutes = 15, mealMinutes = 45,
            )
        }
    }
    val schedule = result.getOrNull()
    LaunchedEffect(schedule?.startMinute, schedule?.endMinute) {
        simulationPlaying = false
        schedule?.let { simulationMinute = it.startMinute }
    }
    LaunchedEffect(simulationPlaying, simulationSpeed, schedule?.endMinute) {
        while (simulationPlaying && schedule != null && simulationMinute < schedule.endMinute) {
            kotlinx.coroutines.delay(250)
            val proposed = (simulationMinute + simulationSpeed).coerceAtMost(schedule.endMinute)
            val boundary = StopSuggestions.nextSimulationBoundary(schedule, simulationMinute)
            simulationMinute = minOf(proposed, boundary ?: proposed)
            if (simulationMinute >= schedule.endMinute) simulationPlaying = false
        }
    }
    val suggestions = remember(stops, pace) {
        stops.filter { it.visitState == "PLANNED" }.mapNotNull { stop ->
            StopSuggestions.duration(stop.destination.label, stop.destination.osmCategory, pace)?.let { stop to it }
        }
    }
    val existing = suggestions.associate { it.first.id to it.first.durationMinutes }
    val changes = selectedDurations.filter { (id, value) -> existing[id] != value }

    Column(Modifier.fillMaxWidth().heightIn(max = 720.dp).verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Draft only. Estimated times; nothing is booked or changed until you apply it.",
            style = MaterialTheme.typography.bodySmall, color = Muted)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            TripPace.entries.forEachIndexed { index, option ->
                SegmentedButton(selected = pace == option, onClick = { onPace(option) },
                    shape = SegmentedButtonDefaults.itemShape(index, TripPace.entries.size)) {
                    Text(when (option) {
                        TripPace.QUICK -> "Quick"
                        TripPace.BALANCED -> "Balanced"
                        TripPace.RELAXED -> "Relaxed"
                    })
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(departure, { departure = it.take(5) }, label = { Text("Leave at") },
                singleLine = true, modifier = Modifier.weight(1f).testTag("suggestion-departure"))
            OutlinedTextField(dayBudget, { dayBudget = it.filter(Char::isDigit).take(4) },
                label = { Text("Day limit (min)") }, singleLine = true, modifier = Modifier.weight(1f))
        }
        result.fold(onSuccess = { schedule ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text("ESTIMATED DAY", style = MaterialTheme.typography.labelSmall, color = Muted)
                Text("Ends ${StopSuggestions.clockLabel(schedule.endMinute)}", style = MaterialTheme.typography.titleSmall)
            }
            if (schedule.overBudgetMinutes > 0) Surface(color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.small) {
                Column(Modifier.padding(12.dp)) {
                    Text("Longer than your day limit", style = MaterialTheme.typography.titleSmall)
                    Text("About ${schedule.overBudgetMinutes} minutes over, including estimated driving, visits and breaks.", style = MaterialTheme.typography.bodySmall)
                }
            }
            if (schedule.timings.isNotEmpty()) {
                Text("Estimated arrival / departure", style = MaterialTheme.typography.titleSmall)
                schedule.timings.forEach { timing ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Rounded.AccessTime, null, Modifier.size(18.dp), tint = Teal)
                        Text(visits.firstOrNull { it.id == timing.stopId }?.label ?: "Stop", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                        Text("${StopSuggestions.clockLabel(timing.arrivalMinute)} / ${StopSuggestions.clockLabel(timing.departureMinute)}", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
            Text("Break suggestions", style = MaterialTheme.typography.titleSmall)
            if (aiSettings.enabled && schedule.breaks.isNotEmpty()) {
                OutlinedTextField(aiPreference, { aiPreference = it.take(160) },
                    label = { Text("What matters at breaks? (optional)") },
                    placeholder = { Text("e.g. local food, a quick stop, quiet cafe") },
                    supportingText = { Text("Sent to Groq only after you confirm an AI ranking. Details are not verified.") },
                    singleLine = true, modifier = Modifier.fillMaxWidth().testTag("ai-break-preference"))
            }
            aiError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            if (schedule.breaks.isEmpty()) Text("No long-driving break was triggered. Consider your own comfort and local conditions.", style = MaterialTheme.typography.bodySmall, color = Muted)
            schedule.breaks.forEach { suggestion ->
                BreakCandidateCard(suggestion, requireNotNull(route), online, nearbyResults, searching, searchErrors,
                    aiEnabled = aiSettings.enabled, aiRecommendations = aiRecommendations[suggestion.id].orEmpty(),
                    aiRanking = aiRanking[suggestion.id] == true,
                    search = {
                        val point = route?.let { OpenMapModels.pointAlong(it, suggestion.fromId, suggestion.toId, suggestion.legFraction) }
                        if (point == null) searchErrors[suggestion.id] = "No route geometry was returned; a nearby location cannot be estimated."
                        else scope.launch {
                            searching[suggestion.id] = true
                            searchErrors.remove(suggestion.id)
                            try { nearbyResults[suggestion.id] = maps.nearby(point, if (suggestion.kind == BreakKind.MEAL) BreakPlaceType.MEAL else BreakPlaceType.REST) }
                            catch (cancelled: CancellationException) { throw cancelled }
                            catch (failure: Exception) { searchErrors[suggestion.id] = failure.message ?: "Unable to search nearby places." }
                            finally { searching[suggestion.id] = false }
                        }
                    }, rankWithAi = { pendingAiRequest = suggestion; aiError = null },
                    choose = { pendingBreak = suggestion to it })
            }
            val status = StopSuggestions.simulationStatus(schedule, stopLabels, simulationMinute.coerceIn(schedule.startMinute, schedule.endMinute))
            Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth().testTag("virtual-day-simulation")) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Icon(Icons.Rounded.FastForward, null, tint = Teal)
                        Column(Modifier.weight(1f)) {
                            Text("Virtual day simulation", style = MaterialTheme.typography.titleSmall)
                            Text("No GPS. No live tracking. Does not mark stops visited.", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    Text(StopSuggestions.clockLabel(simulationMinute.coerceIn(schedule.startMinute, schedule.endMinute)),
                        style = MaterialTheme.typography.headlineSmall, color = Teal, modifier = Modifier.testTag("simulation-clock"))
                    Text(status.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.testTag("simulation-status"))
                    Text(status.detail, style = MaterialTheme.typography.bodySmall)
                    Slider(value = simulationMinute.coerceIn(schedule.startMinute, schedule.endMinute).toFloat(),
                        onValueChange = { simulationMinute = it.toInt(); simulationPlaying = false },
                        valueRange = schedule.startMinute.toFloat()..schedule.endMinute.coerceAtLeast(schedule.startMinute + 1).toFloat(),
                        enabled = schedule.endMinute > schedule.startMinute, modifier = Modifier.testTag("simulation-scrubber"))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilledTonalButton(onClick = {
                                if (simulationMinute >= schedule.endMinute) simulationMinute = schedule.startMinute
                                simulationPlaying = !simulationPlaying
                            }, enabled = schedule.endMinute > schedule.startMinute, modifier = Modifier.testTag("simulation-play")) {
                                Icon(if (simulationPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null)
                                Text(if (simulationPlaying) "Pause" else "Play")
                            }
                            IconButton(onClick = { simulationPlaying = false; simulationMinute = schedule.startMinute }, modifier = Modifier.testTag("simulation-reset")) {
                                Icon(Icons.Rounded.RestartAlt, "Reset simulation")
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            listOf(1, 5, 15).forEach { speed ->
                                FilterChip(selected = simulationSpeed == speed, onClick = { simulationSpeed = speed },
                                    label = { Text("${speed}x") }, modifier = Modifier.testTag("simulation-speed-$speed"))
                            }
                        }
                    }
                }
            }
        }, onFailure = { failure ->
            Surface(color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.small) {
                Column(Modifier.padding(12.dp)) {
                    Text("Review the route first", style = MaterialTheme.typography.titleSmall)
                    Text(failure.message ?: "A complete current route is required to estimate the day.", style = MaterialTheme.typography.bodySmall)
                }
            }
        })
        HorizontalDivider()
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Column(Modifier.weight(1f)) {
                Text("Suggested visit times", style = MaterialTheme.typography.titleSmall)
                Text("Category ranges only; not opening-hour data.", style = MaterialTheme.typography.bodySmall, color = Muted)
            }
            TextButton(onClick = {
                showDurationSuggestions = !showDurationSuggestions
                if (showDurationSuggestions) suggestions.forEach { (stop, suggestion) -> selectedDurations.putIfAbsent(stop.id, suggestion.minutes) }
            }) { Icon(Icons.Rounded.Tune, null); Text(if (showDurationSuggestions) "Hide" else "Review") }
        }
        if (showDurationSuggestions) {
            if (suggestions.isEmpty()) Text("No reliable place category is available for visit-time suggestions.", style = MaterialTheme.typography.bodySmall, color = Muted)
            suggestions.forEach { (stop, suggestion) ->
                Column(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stop.destination.label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        Switch(checked = stop.id in selectedDurations, onCheckedChange = { enabled ->
                            if (enabled) selectedDurations[stop.id] = suggestion.minutes else selectedDurations.remove(stop.id)
                        }, modifier = Modifier.testTag("duration-switch-${stop.id}"))
                    }
                    Text("${suggestion.minimum}-${suggestion.maximum} min / typical ${suggestion.minutes} min. ${suggestion.reason}",
                        style = MaterialTheme.typography.bodySmall, color = Muted)
                    if (stop.id in selectedDurations) Slider(value = selectedDurations[stop.id]!!.toFloat(),
                        onValueChange = { selectedDurations[stop.id] = (it / 5).toInt() * 5 },
                        valueRange = suggestion.minimum.toFloat()..suggestion.maximum.toFloat(),
                        steps = ((suggestion.maximum - suggestion.minimum) / 5 - 1).coerceAtLeast(0),
                        modifier = Modifier.testTag("duration-slider-${stop.id}"))
                }
            }
            TextButton(enabled = !savingDurations && changes.isNotEmpty(), onClick = {
                savingDurations = true; error = null
                onApplyDurations(changes) { success, failure ->
                    savingDurations = false
                    if (success) selectedDurations.clear() else error = failure
                }
            }, modifier = Modifier.testTag("apply-duration-suggestions")) {
                Icon(Icons.Rounded.Check, null); Text(if (savingDurations) "Applying..." else "Apply selected times (${changes.size})")
            }
        }
        error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
    }
    pendingBreak?.let { (suggestion, place) ->
        AlertDialog(onDismissRequest = { if (!applyingBreak) pendingBreak = null }, title = { Text("Add this break stop?") },
            text = { Text("${place.name}${place.address.takeIf(String::isNotBlank)?.let { ", $it" }.orEmpty()} will be added as an optional ${suggestion.durationMinutes}-minute stop before the next visit. Access, opening hours, safety and detour distance are unverified. The driving route becomes stale and must be recalculated.") },
            confirmButton = { TextButton(enabled = !applyingBreak, onClick = {
                applyingBreak = true
                error = null
                val beforeStop = suggestion.toId.takeIf { id -> stops.any { it.id == id } }
                onInsertBreak(beforeStop, Destination(place.name, latitude = place.point.latitude, longitude = place.point.longitude,
                    osmSourceId = place.sourceId, osmCategory = place.osmCategory), suggestion.durationMinutes, suggestion.atMinute) { success, message ->
                    applyingBreak = false
                    if (success) { nearbyResults.remove(suggestion.id); pendingBreak = null }
                    else error = message
                }
            }, modifier = Modifier.testTag("confirm-break-${suggestion.id}")) { Text(if (applyingBreak) "Adding..." else "Add optional stop") } },
            dismissButton = { TextButton(enabled = !applyingBreak, onClick = { pendingBreak = null }) { Text("Cancel") } })
    }
    pendingAiRequest?.let { suggestion ->
        val candidates = nearbyResults[suggestion.id].orEmpty()
        AlertDialog(onDismissRequest = { if (aiRanking[suggestion.id] != true) pendingAiRequest = null },
            title = { Text("Send this break request to Groq?") },
            text = { Text("Groq (${aiSettings.model}) will receive the break type, planned time and duration, your optional preference, pace, and ${candidates.size} nearby OpenStreetMap candidate IDs, names, addresses, categories and distances. It will not receive trip IDs, your planned stop names, exact route coordinates or visit progress. It may process the request under Groq's service terms. Continue only if you agree. AI ranking is unverified and will not change your trip.") },
            confirmButton = { TextButton(enabled = aiRanking[suggestion.id] != true, onClick = {
                aiRanking[suggestion.id] = true
                aiError = null
                scope.launch {
                    try {
                        val recommendations = maps.rankBreakPlaces(
                            kind = suggestion.kind.name,
                            time = StopSuggestions.clockLabel(suggestion.atMinute),
                            durationMinutes = suggestion.durationMinutes,
                            pace = pace.name,
                            preference = aiPreference,
                            candidates = candidates,
                        )
                        aiRecommendations[suggestion.id] = recommendations
                        pendingAiRequest = null
                    } catch (cancelled: CancellationException) { throw cancelled }
                    catch (failure: Exception) {
                        aiError = failure.message ?: "Unable to rank these places with AI."
                        pendingAiRequest = null
                    }
                    finally { aiRanking[suggestion.id] = false }
                }
            }) { Text(if (aiRanking[suggestion.id] == true) "Ranking..." else "Agree and rank") } },
            dismissButton = { TextButton(onClick = { pendingAiRequest = null }) { Text("Cancel") } })
    }
}

@Composable
private fun BreakCandidateCard(
    suggestion: BreakSuggestion,
    route: RoutePreview,
    online: MapSettings,
    results: SnapshotStateMap<String, List<PlaceResult>>,
    searching: SnapshotStateMap<String, Boolean>,
    errors: SnapshotStateMap<String, String>,
    aiEnabled: Boolean,
    aiRecommendations: List<AiPlaceRecommendation>,
    aiRanking: Boolean,
    search: () -> Unit,
    rankWithAi: () -> Unit,
    choose: (PlaceResult) -> Unit,
) {
    val point = remember(route?.fingerprint, suggestion) { route?.let { OpenMapModels.pointAlong(it, suggestion.fromId, suggestion.toId, suggestion.legFraction) } }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("${if (suggestion.kind == BreakKind.MEAL) "Meal" else "Rest"} near ${StopSuggestions.clockLabel(suggestion.atMinute)} · ${suggestion.durationMinutes} min",
            style = MaterialTheme.typography.bodyMedium)
        Text(if (suggestion.duringDrive) "Approximate point along the mapped road; search is distance-limited, not a detour route." else "Between route legs; access and extra travel time are unknown.",
            style = MaterialTheme.typography.bodySmall, color = Muted)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = search, enabled = online.enabled && point != null && searching[suggestion.id] != true,
                modifier = Modifier.testTag("search-break-${suggestion.id}")) {
                Icon(Icons.Rounded.Search, null); Spacer(Modifier.width(6.dp)); Text(if (searching[suggestion.id] == true) "Searching..." else "Find nearby places")
            }
            if (results.containsKey(suggestion.id)) Text("${results[suggestion.id]!!.size} candidates", Modifier.align(Alignment.CenterVertically), style = MaterialTheme.typography.bodySmall)
        }
        if (aiEnabled) {
            OutlinedButton(onClick = rankWithAi, enabled = !aiRanking && results[suggestion.id].orEmpty().isNotEmpty(),
                modifier = Modifier.testTag("rank-break-${suggestion.id}")) {
                Icon(Icons.Rounded.AutoAwesome, null); Spacer(Modifier.width(6.dp)); Text(if (aiRanking) "Ranking..." else "Rank with AI")
            }
        }
        if (!online.enabled) Text("Enable online maps to search. Your timing draft stays on this device.", style = MaterialTheme.typography.bodySmall, color = Muted)
        errors[suggestion.id]?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        if (aiRecommendations.isNotEmpty()) Text("AI-ranked from real map results. Venue details are unverified.", style = MaterialTheme.typography.bodySmall, color = Muted)
        val places = results[suggestion.id].orEmpty()
        val reasons = aiRecommendations.associate { it.sourceId to it.reason }
        val ranked = aiRecommendations.mapNotNull { recommendation -> places.firstOrNull { it.sourceId == recommendation.sourceId } }
        (ranked + places.filterNot { it.sourceId in reasons }).take(5).forEach { place ->
            OutlinedButton(onClick = { choose(place) }, modifier = Modifier.fillMaxWidth().testTag("break-place-${suggestion.id}-${place.sourceId}")) {
                Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.Start) {
                    Text(place.name, style = MaterialTheme.typography.titleSmall)
                    Text(listOfNotNull(place.distanceFromSearchKm?.let { "~${"%.1f".format(it)} km from route" }, place.address, place.osmCategory).joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = Muted)
                    reasons[place.sourceId]?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
    }
}
