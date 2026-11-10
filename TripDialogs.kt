package app.routewise.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Search
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import app.routewise.core.MapsLinks
import app.routewise.core.SharedPlace
import app.routewise.core.TripTransfer
import app.routewise.data.*
import app.routewise.maps.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@Composable
fun TripNameDialog(initial: String = "", creating: Boolean = true, dismiss: () -> Unit, save: (String, Int) -> Unit) {
    var title by rememberSaveable { mutableStateOf(initial) }
    var days by rememberSaveable { mutableStateOf("2") }
    AlertDialog(onDismissRequest = dismiss,
        title = { Text(if (creating) "New trip" else "Rename trip") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(title, { title = it.take(120) }, label = { Text("Trip name") }, singleLine = true)
                if (creating) OutlinedTextField(days, { days = it.filter(Char::isDigit).take(2) }, label = { Text("Days") },
                    supportingText = { Text("1-30 days") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
            }
        },
        confirmButton = { TextButton(enabled = title.isNotBlank() && (!creating || days.toIntOrNull() in 1..30), onClick = { save(title.trim(), days.toIntOrNull() ?: 1); dismiss() }) { Text(if (creating) "Create trip" else "Save") } },
        dismissButton = { TextButton(onClick = dismiss) { Text("Cancel") } },
    )
}

@Composable
fun StopEditor(stop: StopEntity?, sharedText: String?, maps: OpenMaps, initialPoint: MapPoint? = null, dismiss: () -> Unit, save: (Destination, Int, Boolean, String, (Boolean) -> Unit) -> Unit) {
    val shared = remember(sharedText) { runCatching { sharedText?.let(MapsLinks::parseShared) }.getOrNull() ?: SharedPlace() }
    var label by rememberSaveable { mutableStateOf(stop?.destination?.label.orEmpty()) }
    var query by rememberSaveable { mutableStateOf(stop?.destination?.query ?: shared.query.orEmpty()) }
    var latitude by rememberSaveable { mutableStateOf((stop?.destination?.latitude ?: initialPoint?.latitude)?.toString().orEmpty()) }
    var longitude by rememberSaveable { mutableStateOf((stop?.destination?.longitude ?: initialPoint?.longitude)?.toString().orEmpty()) }
    var coordinates by rememberSaveable { mutableStateOf(stop?.destination?.latitude != null || initialPoint != null) }
    var placeId by rememberSaveable { mutableStateOf(stop?.destination?.placeId ?: shared.placeId) }
    var osmSourceId by rememberSaveable { mutableStateOf(stop?.destination?.osmSourceId) }
    var osmCategory by rememberSaveable { mutableStateOf(stop?.destination?.osmCategory) }
    val settings by maps.settings.collectAsState()
    val scope = rememberCoroutineScope()
    var searchJob by remember { mutableStateOf<Job?>(null) }
    var searching by remember { mutableStateOf(false) }
    var results by remember { mutableStateOf<List<PlaceResult>?>(null) }
    var searchGeneration by remember { mutableIntStateOf(0) }
    var minutes by rememberSaveable { mutableStateOf((stop?.durationMinutes ?: 60).toString()) }
    var notes by rememberSaveable { mutableStateOf(stop?.notes.orEmpty()) }
    var mustVisit by rememberSaveable { mutableStateOf(stop?.mustVisit ?: true) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    AlertDialog(onDismissRequest = { if (!saving) dismiss() },
        title = { Text(if (stop == null) "Add stop" else "Edit stop") },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (sharedText != null && shared.needsSearch) Text("This link needs a destination. Confirm the place and city below.", color = Coral)
                OutlinedTextField(label, { label = it.take(120) }, label = { Text("Stop name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                if (placeId != null) {
                    Text("Google Place ID selected", color = Teal)
                    TextButton(onClick = { placeId = null; coordinates = false }) { Text("Change location") }
                }
                else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Use coordinates", Modifier.weight(1f))
                        Switch(checked = coordinates, onCheckedChange = { coordinates = it; osmSourceId = null })
                    }
                    if (coordinates) {
                        OutlinedTextField(latitude, { latitude = it; osmSourceId = null }, label = { Text("Latitude") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                        OutlinedTextField(longitude, { longitude = it; osmSourceId = null }, label = { Text("Longitude") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                        if (osmSourceId != null) Text("OpenStreetMap contributors / ODbL", style = MaterialTheme.typography.bodySmall)
                    } else {
                        OutlinedTextField(query, {
                            query = it.take(300); searchGeneration++; searchJob?.cancel(); searching = false; results = null
                        }, label = { Text("Place and city") }, singleLine = false, maxLines = 3, modifier = Modifier.fillMaxWidth())
                        if (settings.enabled) {
                            OutlinedButton(enabled = query.trim().length >= 3 && !searching, onClick = {
                                val generation = ++searchGeneration
                                searchJob = scope.launch {
                                    searching = true; error = null
                                    try { val found = maps.search(query); if (generation == searchGeneration) results = found }
                                    catch (cancelled: CancellationException) { throw cancelled }
                                    catch (failure: Exception) { if (generation == searchGeneration) error = failure.message }
                                    finally { if (generation == searchGeneration) searching = false }
                                }
                            }) { Icon(Icons.Rounded.Search, null); Spacer(Modifier.width(6.dp)); Text(if (searching) "Searching..." else "Search places") }
                            results?.let { places ->
                                if (places.isEmpty()) Text("No matching places found.", style = MaterialTheme.typography.bodySmall)
                                places.forEachIndexed { index, place ->
                                    TextButton(onClick = {
                                        if (label.isBlank()) label = place.name
                                        latitude = place.point.latitude.toString(); longitude = place.point.longitude.toString()
                                        osmSourceId = place.sourceId; osmCategory = place.osmCategory; coordinates = true; results = null
                                    }, modifier = Modifier.fillMaxWidth().testTag("place-result-$index")) {
                                        Column(Modifier.fillMaxWidth()) {
                                            Text(place.name, style = MaterialTheme.typography.titleSmall)
                                            Text(place.address, style = MaterialTheme.typography.bodySmall)
                                        }
                                    }
                                }
                                Text("Search: Photon / OpenStreetMap contributors", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
                OutlinedTextField(minutes, { minutes = it.filter(Char::isDigit).take(4) }, label = { Text("Visit time (minutes)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Must visit", Modifier.weight(1f))
                    Switch(mustVisit, { mustVisit = it })
                }
                OutlinedTextField(notes, { notes = it.take(4000) }, label = { Text("Notes") }, minLines = 2, maxLines = 4, modifier = Modifier.fillMaxWidth())
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            TextButton(enabled = !saving && label.isNotBlank(), onClick = {
                val destination = when {
                    placeId != null -> Destination(label.trim(), placeId = placeId)
                    coordinates -> Destination(label.trim(), latitude = latitude.toDoubleOrNull(), longitude = longitude.toDoubleOrNull(), osmSourceId = osmSourceId, osmCategory = osmCategory)
                    else -> Destination(label.trim(), query = query.trim().ifBlank { label.trim() })
                }
                val validation = runCatching {
                    TripTransfer.validateDestination(destination)
                    require(minutes.toIntOrNull() in 0..1440) { "Visit time must be 0-1440 minutes." }
                }
                if (validation.isFailure) error = validation.exceptionOrNull()?.message
                else {
                    saving = true
                    save(destination, minutes.toInt(), mustVisit, notes) { success ->
                        saving = false
                        if (success) dismiss() else error = "Unable to save. Please retry."
                    }
                }
            }) { Text(if (saving) "Saving..." else "Save stop") }
        },
        dismissButton = { TextButton(enabled = !saving, onClick = dismiss) { Text("Cancel") } },
    )
}

@Composable
fun MapSettingsDialog(maps: OpenMaps, dismiss: () -> Unit) {
    val current by maps.settings.collectAsState()
    val aiCurrent by maps.aiSettings.collectAsState()
    var enabled by rememberSaveable { mutableStateOf(current.enabled) }
    var advanced by rememberSaveable { mutableStateOf(false) }
    var search by rememberSaveable { mutableStateOf(current.searchUrl) }
    var routing by rememberSaveable { mutableStateOf(current.routingUrl) }
    var tiles by rememberSaveable { mutableStateOf(current.tilesUrl) }
    var aiEnabled by rememberSaveable { mutableStateOf(aiCurrent.enabled) }
    var aiModel by rememberSaveable { mutableStateOf(aiCurrent.model) }
    var aiApiKey by remember { mutableStateOf("") }
    var clearAiApiKey by rememberSaveable { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest = dismiss, title = { Text("Online maps") }, text = {
        Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Searches go to Photon, routes to FOSSGIS, and viewed map areas to OpenStreetMap. These free community services may log requests, throttle access, or be unavailable. No account or payment is required.", style = MaterialTheme.typography.bodyMedium)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Allow online maps", Modifier.weight(1f)); Switch(enabled, { enabled = it })
            }
            Text("No background tracking or bulk downloads. Manual planning stays available offline.", style = MaterialTheme.typography.bodySmall)
            HorizontalDivider()
            Text("AI break planner", style = MaterialTheme.typography.titleMedium)
            Text("Optional. Groq receives break type/time, your short preference, and nearby OSM candidate IDs, names, addresses, categories and distances only after you confirm a ranking request. It does not receive trip IDs, planned stop names, exact route coordinates, notes or visit progress. AI rankings are unverified.", style = MaterialTheme.typography.bodySmall)
            Text("Groq quotas and pricing may change. The 12-request daily app limit is not a billing cap; check your Groq plan before enabling.", style = MaterialTheme.typography.bodySmall, color = Coral)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Enable Groq AI", Modifier.weight(1f)); Switch(aiEnabled, { aiEnabled = it }, modifier = Modifier.testTag("groq-enabled-switch"))
            }
            OutlinedTextField(aiApiKey, { aiApiKey = it.take(512); clearAiApiKey = false },
                label = { Text("Groq API key") }, placeholder = { Text(if (aiCurrent.hasApiKey) "Saved securely on this device" else "Enter key on this device") },
                visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                singleLine = true, modifier = Modifier.fillMaxWidth().testTag("groq-api-key"))
            Text("The key is encrypted with Android Keystore and never included in trip exports. Leave blank to keep the saved key.", style = MaterialTheme.typography.bodySmall)
            OutlinedTextField(aiModel, { aiModel = it.take(100) }, label = { Text("Groq model ID") }, singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("groq-model"))
            if (aiCurrent.hasApiKey && !clearAiApiKey) TextButton(onClick = { clearAiApiKey = true; aiApiKey = ""; aiEnabled = false }) { Text("Remove saved API key") }
            if (clearAiApiKey) Text("Saved API key will be removed.", style = MaterialTheme.typography.bodySmall, color = Coral)
            TextButton(onClick = { advanced = !advanced }) { Text("Provider settings") }
            if (advanced) {
                OutlinedTextField(search, { search = it }, label = { Text("Photon base URL") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(routing, { routing = it }, label = { Text("OSRM driving URL") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(tiles, { tiles = it }, label = { Text("OSM tile template") }, modifier = Modifier.fillMaxWidth())
                Text("Custom services receive the same data and must provide compatible APIs and licensed OSM tiles.", style = MaterialTheme.typography.bodySmall)
            }
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }, confirmButton = { TextButton(onClick = {
        runCatching {
            maps.updateAiSettings(aiEnabled, aiModel, aiApiKey, clearAiApiKey)
            maps.update(MapSettings(enabled, search.trim(), routing.trim(), tiles.trim()))
        }
            .onSuccess { dismiss() }.onFailure { error = it.message }
    }) { Text("Save settings") } }, dismissButton = { TextButton(onClick = dismiss) { Text("Cancel") } })
}

@Composable
fun ConfirmDialog(title: String, message: String, action: String = "Delete", dismiss: () -> Unit, confirm: () -> Unit) {
    AlertDialog(onDismissRequest = dismiss, title = { Text(title) }, text = { Text(message) },
        confirmButton = { TextButton(onClick = { confirm(); dismiss() }) { Text(action, color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = dismiss) { Text("Cancel") } })
}

@Composable
fun MoveDayDialog(days: List<DayEntity>, dismiss: () -> Unit, move: (String?) -> Unit) {
    AlertDialog(onDismissRequest = dismiss, title = { Text("Move to day") },
        text = { Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
            TextButton(onClick = { move(null); dismiss() }) { Text("Unassigned") }
            days.forEachIndexed { index, day -> TextButton(onClick = { move(day.id); dismiss() }) {
                Text("Day ${index + 1}" + day.title.takeIf(String::isNotBlank)?.let { " - $it" }.orEmpty())
            } }
        } }, confirmButton = {}, dismissButton = { TextButton(onClick = dismiss) { Text("Cancel") } })
}

@Composable
fun DayDetailsDialog(day: DayEntity, dismiss: () -> Unit, save: (String, String?, () -> Unit) -> Unit) {
    var title by rememberSaveable { mutableStateOf(day.title) }
    var date by rememberSaveable { mutableStateOf(day.localDate.orEmpty()) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    AlertDialog(onDismissRequest = dismiss, title = { Text("Day details") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(title, { title = it.take(120) }, label = { Text("Day name") }, singleLine = true)
            OutlinedTextField(date, { date = it.take(10) }, label = { Text("Date (YYYY-MM-DD)") }, singleLine = true)
            error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }, confirmButton = { TextButton(onClick = {
        val value = date.trim().takeIf(String::isNotEmpty)
        if (value != null && runCatching { java.time.LocalDate.parse(value) }.isFailure) error = "Enter a valid date, or leave it empty."
        else save(title, value, dismiss)
    }) { Text("Save day") } }, dismissButton = { TextButton(onClick = dismiss) { Text("Cancel") } })
}

@Composable
fun AnchorDialog(start: Boolean, destinations: List<Destination>, dismiss: () -> Unit, choose: (Destination?) -> Unit, create: () -> Unit) {
    AlertDialog(onDismissRequest = dismiss, title = { Text(if (start) "Start location" else "Finish location") }, text = {
        Column(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
            TextButton(onClick = { choose(null) }) { Text("None") }
            destinations.distinct().forEach { destination -> TextButton(onClick = { choose(destination) }) { Text(destination.label) } }
            OutlinedButton(onClick = create) { Text("Add another location") }
        }
    }, confirmButton = {}, dismissButton = { TextButton(onClick = dismiss) { Text("Cancel") } })
}
