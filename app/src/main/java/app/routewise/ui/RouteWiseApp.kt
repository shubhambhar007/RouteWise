package app.routewise.ui

import androidx.compose.animation.animateContentSize
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.*
import app.routewise.core.TripRules
import app.routewise.core.TripPace
import app.routewise.data.*
import app.routewise.maps.*
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RouteWiseApp(model: TripViewModel) {
    val nav = rememberNavController()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val trips by model.trips.collectAsStateWithLifecycle()
    val detail by model.detail.collectAsStateWithLifecycle()
    val incoming by model.incomingShare.collectAsStateWithLifecycle()
    val pending by model.pending.collectAsStateWithLifecycle()
    var createTrip by rememberSaveable { mutableStateOf(false) }
    var mapSettings by rememberSaveable { mutableStateOf(false) }
    val importFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            try { model.importTrip(AndroidActions.readTrip(context, uri)) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { model.message("Unable to read this trip file.") }
        }
    }
    LaunchedEffect(model) {
        model.events.collect { event ->
            when (event) {
                is TripEvent.Open -> nav.navigate("trip/${event.id}") { popUpTo("trips"); launchSingleTop = true }
                is TripEvent.Message -> snackbar.showSnackbar(event.text)
                is TripEvent.Share -> try { AndroidActions.share(context, event.title, event.json) }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { snackbar.showSnackbar("Unable to share this trip.") }
            }
        }
    }
    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { outerPadding ->
        NavHost(nav, startDestination = "trips", modifier = Modifier.padding(outerPadding)) {
            composable("trips") {
                TripLibrary(trips, incoming != null,
                    create = { createTrip = true }, open = { nav.navigate("trip/$it") },
                    import = { importFile.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
                    sample = model::sample, simulate = model::jaipurSimulation,
                    clearShare = model::clearShare, settings = { mapSettings = true },
                    pending = pending, selectShare = model::selectShare, discardShare = model::discardShare)
            }
            composable("trip/{id}") { entry ->
                val id = requireNotNull(entry.arguments?.getString("id"))
                LaunchedEffect(id) { model.select(id) }
                val current = detail?.takeIf { it.trip.id == id }
                if (current == null) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                else TripEditor(current, incoming, model, back = { nav.popBackStack() }, settings = { mapSettings = true })
            }
        }
    }
    if (createTrip) TripNameDialog(dismiss = { createTrip = false }, save = model::create)
    if (mapSettings) MapSettingsDialog(model.maps) { mapSettings = false; model.clearRoute() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TripLibrary(trips: List<TripSummary>?, hasSharedPlace: Boolean, create: () -> Unit, open: (String) -> Unit, import: () -> Unit, sample: () -> Unit, simulate: () -> Unit, clearShare: () -> Unit, settings: () -> Unit, pending: List<PendingShare>, selectShare: (String) -> Unit, discardShare: (String) -> Unit) {
    Scaffold(containerColor = MaterialTheme.colorScheme.background, contentWindowInsets = WindowInsets(0), topBar = {
        TopAppBar(windowInsets = WindowInsets(0), colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background), title = { Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(Modifier.size(34.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Route, null, tint = Color.White, modifier = Modifier.size(19.dp))
            }
            Text("RouteWise", style = MaterialTheme.typography.titleLarge)
        } }, actions = {
            ToolButton(Icons.Rounded.FileOpen, "Import trip", import)
            ToolButton(Icons.Rounded.Settings, "Map settings", settings)
        })
    }, floatingActionButton = {
        ExtendedFloatingActionButton(onClick = create, containerColor = Teal, contentColor = Color.White) {
            Icon(Icons.Rounded.Add, null)
            Spacer(Modifier.width(8.dp))
            Text("New trip")
        }
    }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 112.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            item {
                Surface(shape = RoundedCornerShape(8.dp), color = Color.Transparent) {
                    Box(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(Color(0xFF153D32), Color(0xFF28705A))))) {
                        Icon(Icons.Rounded.Route, null, Modifier.align(Alignment.TopEnd).padding(top = 16.dp, end = 16.dp).size(132.dp), tint = Color.White.copy(alpha = 0.10f))
                        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("MAKE THE DAY YOURS", style = MaterialTheme.typography.labelMedium, color = Color(0xFFC7E4B0))
                            Text("Good plans leave\nroom to wander.", style = MaterialTheme.typography.headlineMedium, color = Color.White)
                            Text("Build a route around the places you care about.", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.82f))
                            FilledTonalButton(onClick = simulate, modifier = Modifier.testTag("jaipur-simulation"),
                                colors = ButtonDefaults.filledTonalButtonColors(containerColor = Color(0xFFE2F0B7), contentColor = Ink),
                                shape = RoundedCornerShape(8.dp)) {
                                Icon(Icons.Rounded.PlayArrow, null); Spacer(Modifier.width(8.dp)); Text("Try Jaipur 3-stop simulation")
                            }
                        }
                    }
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Your trips", style = MaterialTheme.typography.headlineSmall)
                        Text("${trips?.size ?: 0} saved on this device", color = Muted, style = MaterialTheme.typography.bodySmall)
                    }
                    Icon(Icons.Rounded.Luggage, null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(24.dp))
                }
            }
            if (hasSharedPlace) item {
                Row(Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(8.dp)).padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Shared place ready. Select a trip.", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                    ToolButton(Icons.Rounded.Close, "Dismiss shared place", clearShare)
                }
            }
            if (trips == null) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            else if (trips.isEmpty()) item {
                Column(Modifier.fillMaxWidth().padding(vertical = 56.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Icon(Icons.Rounded.Explore, null, Modifier.size(56.dp), tint = Teal)
                    Text("No trips yet", style = MaterialTheme.typography.titleLarge)
                    OutlinedButton(onClick = sample) { Icon(Icons.Rounded.AddRoad, null); Spacer(Modifier.width(8.dp)); Text("Open sample trip") }
                }
            }
            if (pending.isNotEmpty()) item { Text("Pending shares (${pending.size})", style = MaterialTheme.typography.titleMedium) }
            items(pending, key = { "pending-${it.id}" }) { item ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { selectShare(item.id) }, modifier = Modifier.weight(1f)) {
                        Text(item.text.take(160), maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    ToolButton(Icons.Rounded.DeleteOutline, "Discard pending share", { discardShare(item.id) })
                }
            }
            items(trips.orEmpty(), key = { it.trip.id }) { summary ->
                Card(onClick = { open(summary.trip.id) }, shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth().animateContentSize(),
                    colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)) {
                    Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                                Icon(Icons.Rounded.Luggage, null, tint = Teal, modifier = Modifier.size(23.dp))
                            }
                            Spacer(Modifier.width(12.dp))
                            Text(summary.trip.title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, tint = Muted, modifier = Modifier.size(20.dp))
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                            SmallMetric(Icons.Rounded.CalendarMonth, "${summary.dayCount} days")
                            SmallMetric(Icons.Rounded.Place, "${summary.stopCount} stops")
                        }
                        if (summary.visitedCount > 0) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                LinearProgressIndicator(progress = { summary.visitedCount.toFloat() / summary.stopCount.coerceAtLeast(1) }, modifier = Modifier.fillMaxWidth())
                                Text("${summary.visitedCount} of ${summary.stopCount} stops visited", style = MaterialTheme.typography.labelMedium, color = Teal)
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TripEditor(detail: TripDetail, incoming: String?, model: TripViewModel, back: () -> Unit, settings: () -> Unit) {
    val context = LocalContext.current
    val days = detail.days.sortedBy { it.ordinal }
    var selectedDay by rememberSaveable(detail.trip.id) { mutableStateOf(days.firstOrNull()?.id) }
    val dayId = selectedDay?.takeIf { selected -> days.any { it.id == selected } }
    val dayIndex = days.indexOfFirst { it.id == dayId }
    val selectedDayDetails = days.getOrNull(dayIndex)
    val sourceStops = detail.stops.filter { it.dayId == dayId }.sortedBy { it.position }
    var displayed by remember(dayId) { mutableStateOf(sourceStops) }
    var dragging by remember { mutableStateOf(false) }
    LaunchedEffect(sourceStops, dragging) { if (!dragging) displayed = sourceStops }
    var adding by rememberSaveable { mutableStateOf(false) }
    var editing by remember { mutableStateOf<StopEntity?>(null) }
    var moving by remember { mutableStateOf<StopEntity?>(null) }
    var deleting by remember { mutableStateOf<StopEntity?>(null) }
    var skipping by remember { mutableStateOf<StopEntity?>(null) }
    var rename by rememberSaveable { mutableStateOf(false) }
    var deleteTrip by rememberSaveable { mutableStateOf(false) }
    var deleteDay by rememberSaveable { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var mapMode by rememberSaveable { mutableStateOf(false) }
    var confirmInAppNavigation by remember { mutableStateOf(false) }
    var pin by remember { mutableStateOf<MapPoint?>(null) }
    var editingDay by remember { mutableStateOf(false) }
    var anchorChoice by remember { mutableStateOf<Boolean?>(null) }
    var newAnchor by remember { mutableStateOf<Boolean?>(null) }
    val mapOptions by model.maps.settings.collectAsStateWithLifecycle()
    val roadRoute by model.route.collectAsStateWithLifecycle()
    val routeStops = sourceStops.filter { it.visitState == "PLANNED" }
    val destinations = buildList {
        selectedDayDetails?.start?.let { add("start-$dayId" to it) }
        addAll(routeStops.map { it.id to it.destination })
        selectedDayDetails?.finish?.let { add("finish-$dayId" to it) }
    }
    val stopLabels = destinations.associate { (id, destination) -> id to destination.label }
    val nodes = destinations.mapNotNull { (id, destination) -> destination.latitude?.let { latitude ->
        destination.longitude?.let { longitude -> RouteNode(id, MapPoint(latitude, longitude)) }
    } }
    val fingerprint = OpenMapModels.fingerprint(nodes)
    val routePreview = roadRoute.preview?.takeIf { it.fingerprint == fingerprint && nodes.size == destinations.size }
    LaunchedEffect(dayId, detail.trip.id) { model.clearRoute() }
    var showSuggestions by rememberSaveable(detail.trip.id) { mutableStateOf(false) }
    var pace by rememberSaveable(detail.trip.id) { mutableStateOf(TripPace.BALANCED) }
    val listState = rememberLazyListState()
    val reorder = rememberReorderableLazyListState(listState) { from, to ->
        val origin = displayed.indexOfFirst { it.id == from.key }
        val destination = displayed.indexOfFirst { it.id == to.key }
        if (origin >= 0 && destination >= 0) displayed = TripRules.move(displayed, origin, destination)
    }
    fun moveBy(stop: StopEntity, delta: Int) {
        val from = displayed.indexOfFirst { it.id == stop.id }
        if (from + delta in displayed.indices) {
            displayed = TripRules.move(displayed, from, from + delta)
            model.reorder(detail.trip.id, dayId, displayed.map { it.id })
        }
    }
    val nextStop = sourceStops.firstOrNull { it.visitState == "PLANNED" }
    val inAppDestinations = buildList {
        addAll(routeStops.map { it.destination })
        selectedDayDetails?.finish?.let(::add)
    }
    val inAppNavigationReady = inAppDestinations.isNotEmpty() && inAppDestinations.size <= 25 &&
        inAppDestinations.all { it.latitude != null && it.longitude != null }
    Scaffold(contentWindowInsets = WindowInsets(0), topBar = {
        TopAppBar(windowInsets = WindowInsets(0), title = { Text(detail.trip.title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium) },
            navigationIcon = { ToolButton(Icons.AutoMirrored.Rounded.ArrowBack, "Back to trips", back) },
            actions = {
                ToolButton(Icons.Rounded.Share, "Share trip", { model.share(detail.trip.id) })
                Box {
                    ToolButton(Icons.Rounded.MoreVert, "Trip options", { menu = true })
                    DropdownMenu(menu, { menu = false }) {
                        MenuAction("Rename trip", Icons.Rounded.Edit) { menu = false; rename = true }
                        MenuAction("Duplicate trip", Icons.Rounded.ContentCopy) { menu = false; model.duplicate(detail.trip.id) }
                        MenuAction("Add day", Icons.Rounded.CalendarMonth) { menu = false; model.addDay(detail.trip.id) }
                        if (dayId != null) MenuAction("Day details", Icons.Rounded.EditCalendar) { menu = false; editingDay = true }
                        MenuAction("Map settings", Icons.Rounded.Settings) { menu = false; settings() }
                        if (dayId != null) MenuAction("Delete selected day", Icons.Rounded.EventBusy) { menu = false; deleteDay = true }
                        MenuAction("Delete trip", Icons.Rounded.DeleteOutline) { menu = false; deleteTrip = true }
                    }
                }
            })
    }, bottomBar = {
        val nextDestination = nextStop?.destination ?: selectedDayDetails?.finish
        if (nextDestination != null) Surface(shadowElevation = 6.dp) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(if (nextStop != null) "NEXT STOP" else "DAY FINISH", style = MaterialTheme.typography.labelSmall, color = Muted)
                    Text(nextDestination.label, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Button(onClick = { confirmInAppNavigation = true }, enabled = inAppNavigationReady, shape = RoundedCornerShape(8.dp), modifier = Modifier.testTag("navigate-in-app")) {
                        Icon(Icons.Rounded.Navigation, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("In app")
                    }
                    TextButton(onClick = { if (!AndroidActions.navigate(context, nextDestination)) model.message("No Maps app or browser is available.") }, modifier = Modifier.heightIn(min = 40.dp)) {
                        Icon(Icons.Rounded.NearMe, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp)); Text("Google Maps", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(days, key = { it.id }) { day ->
                    FilterChip(selected = dayId == day.id, onClick = { selectedDay = day.id }, label = { Text("Day ${days.indexOf(day) + 1}") })
                }
                item { FilterChip(selected = dayId == null, onClick = { selectedDay = null }, label = { Text("Unassigned") }) }
                item { ToolButton(Icons.Rounded.Add, "Add day", { model.addDay(detail.trip.id) }) }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(if (dayIndex < 0) "Unassigned" else days[dayIndex].title.ifBlank { "Day ${dayIndex + 1}" }, style = MaterialTheme.typography.headlineSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text("${sourceStops.size} stops  /  ${sourceStops.sumOf { it.durationMinutes }} min visits", style = MaterialTheme.typography.bodySmall, color = Muted)
                    selectedDayDetails?.localDate?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = Muted) }
                }
                FilledTonalButton(onClick = { adding = true }, shape = RoundedCornerShape(8.dp)) {
                    Icon(Icons.Rounded.Add, null, Modifier.size(18.dp)); Spacer(Modifier.width(4.dp)); Text("Add stop")
                }
            }
            if (selectedDayDetails != null) Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                TextButton(onClick = { anchorChoice = true }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Rounded.TripOrigin, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp))
                    Text(selectedDayDetails.start?.label ?: "Set start", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                TextButton(onClick = { anchorChoice = false }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Rounded.Flag, null, Modifier.size(16.dp)); Spacer(Modifier.width(6.dp))
                    Text(selectedDayDetails.finish?.label ?: "Set finish", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
                SegmentedButton(selected = !mapMode, onClick = { mapMode = false }, shape = SegmentedButtonDefaults.itemShape(0, 2), icon = { Icon(Icons.Rounded.ViewList, null) }) { Text("List") }
                SegmentedButton(selected = mapMode, onClick = { mapMode = true }, shape = SegmentedButtonDefaults.itemShape(1, 2), icon = { Icon(Icons.Rounded.Map, null) }) { Text("Map") }
            }
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(when {
                        !mapOptions.enabled -> "Online maps are off"
                        roadRoute.loading -> "Calculating driving route..."
                        roadRoute.error != null -> roadRoute.error!!
                        routePreview?.complete == true -> String.format(Locale.getDefault(), "%.1f km / %d min driving", routePreview.meters!! / 1000, kotlin.math.ceil(routePreview.seconds!! / 60).toInt())
                        routePreview != null -> "Incomplete route: ${routePreview.failedBatches} segment(s) unavailable${routePreview.failureReasons.firstOrNull()?.let { " · $it" }.orEmpty()}"
                        roadRoute.preview != null -> "Route out of date"
                        nodes.size != destinations.size -> "${destinations.size - nodes.size} locations need confirmation"
                        nodes.size < 2 -> "Set a day start/finish or add another located stop"
                        nodes.size > 100 -> "Free preview supports up to 100 located stops at a time"
                        else -> "Ready to calculate this day's route"
                    }, color = Muted, style = MaterialTheme.typography.labelMedium)
                    if (routePreview != null) Text("OSRM / OpenStreetMap. No live traffic.", color = Muted, style = MaterialTheme.typography.labelSmall)
                }
                FilledTonalButton(onClick = { model.calculateRoute(nodes) },
                    enabled = mapOptions.enabled && !roadRoute.loading && nodes.size in 2..100 && nodes.size == destinations.size,
                    modifier = Modifier.heightIn(min = 48.dp).semantics { contentDescription = "Calculate driving route" },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)) {
                    Icon(Icons.Rounded.Route, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Calculate", maxLines = 1)
                }
                IconButton(onClick = { showSuggestions = true }, enabled = dayId != null && routeStops.isNotEmpty()) {
                    Icon(Icons.Rounded.AutoAwesome, "Suggest visit times and breaks", tint = if (routeStops.isNotEmpty()) Teal else Muted)
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            if (mapMode && mapOptions.enabled) TripMap(sourceStops, routePreview, model.maps, { pin = it; adding = true }, Modifier.weight(1f).fillMaxWidth(), selectedDayDetails?.start, selectedDayDetails?.finish)
            else if (mapMode) Column(Modifier.fillMaxWidth().weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                OutlinedButton(onClick = settings) { Icon(Icons.Rounded.Map, null); Spacer(Modifier.width(8.dp)); Text("Enable online maps") }
            }
            else if (sourceStops.isEmpty()) Column(Modifier.fillMaxWidth().weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Icon(Icons.Rounded.AddLocationAlt, null, Modifier.size(40.dp), tint = Teal)
                Spacer(Modifier.height(16.dp)); Text("No stops yet", style = MaterialTheme.typography.titleMedium)
            } else LazyColumn(state = listState, modifier = Modifier.weight(1f), contentPadding = PaddingValues(bottom = 12.dp)) {
                items(displayed, key = { it.id }) { stop ->
                    ReorderableItem(reorder, key = stop.id) { isDragging ->
                        StopRow(stop, displayed.indexOf(stop) + 1, isDragging,
                            handle = { ToolButton(Icons.Rounded.DragHandle, "Reorder ${stop.destination.label}", {},
                                Modifier.draggableHandle(onDragStarted = { dragging = true }, onDragStopped = {
                                    model.reorder(detail.trip.id, dayId, displayed.map { it.id }); dragging = false
                                })) },
                            edit = { editing = stop }, up = { moveBy(stop, -1) }, down = { moveBy(stop, 1) },
                            canUp = displayed.first().id != stop.id, canDown = displayed.last().id != stop.id,
                            move = { moving = stop }, delete = { deleting = stop },
                            visit = { model.visit(stop.id, if (stop.visitState == "VISITED") "PLANNED" else "VISITED") },
                            skip = { if (stop.visitState == "SKIPPED") model.visit(stop.id, "PLANNED") else if (stop.mustVisit) skipping = stop else model.visit(stop.id, "SKIPPED") })
                    }
                }
            }
        }
    }
    if (adding || editing != null || incoming != null) StopEditor(editing, incoming, model.maps, pin,
        dismiss = { adding = false; editing = null; pin = null; model.clearShare() },
        save = { destination, minutes, required, notes, done -> model.saveStop(detail.trip.id, dayId, destination, minutes, required, notes, editing?.id, done) })
    moving?.let { stop -> MoveDayDialog(days, { moving = null }) { model.moveToDay(stop.id, it) } }
    deleting?.let { stop -> ConfirmDialog("Delete stop?", stop.destination.label, dismiss = { deleting = null }) { model.removeStop(stop.id) } }
    skipping?.let { stop -> ConfirmDialog("Skip must-visit stop?", stop.destination.label, "Skip", { skipping = null }) { model.visit(stop.id, "SKIPPED") } }
    if (rename) TripNameDialog(detail.trip.title, false, { rename = false }) { title, _ -> model.rename(detail.trip.id, title) }
    if (deleteTrip) ConfirmDialog("Delete trip?", "This permanently removes the trip and all its stops from this device.", dismiss = { deleteTrip = false }) { model.delete(detail.trip.id, back) }
    if (deleteDay) ConfirmDialog("Delete day?", "Its stops will move to Unassigned.", dismiss = { deleteDay = false }) { dayId?.let(model::removeDay); selectedDay = null }
    if (editingDay && selectedDayDetails != null) DayDetailsDialog(selectedDayDetails, { editingDay = false }) { title, date, done -> model.updateDay(selectedDayDetails.id, title, date, done) }
    anchorChoice?.let { start -> if (dayId != null) AnchorDialog(start, detail.stops.map { it.destination }, { anchorChoice = null },
        { destination -> model.setAnchor(dayId, start, destination) { success -> if (success) anchorChoice = null } },
        { anchorChoice = null; newAnchor = start }) }
    newAnchor?.let { start -> if (dayId != null) StopEditor(null, null, model.maps, dismiss = { newAnchor = null },
        save = { destination, _, _, _, done -> model.setAnchor(dayId, start, destination) { success -> if (success) newAnchor = null; done(success) } }) }
    if (showSuggestions && dayId != null && selectedDayDetails != null && routeStops.isNotEmpty()) {
        ModalBottomSheet(onDismissRequest = { showSuggestions = false }, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
            DaySuggestionsPanel(
                day = selectedDayDetails, revision = detail.trip.revision,
                progressRevision = detail.trip.progressRevision, stops = routeStops, stopLabels = stopLabels,
                orderedNodeIds = nodes.map { it.id }, route = routePreview?.takeIf { it.complete }, pace = pace, onPace = { pace = it },
                maps = model.maps,
                onApplyDurations = { changes, done -> model.applySuggestedDurations(
                    detail.trip.id, selectedDayDetails.id, detail.trip.revision, detail.trip.progressRevision, changes, done) },
                onInsertBreak = { beforeStop, destination, minutes, suggestedMinute, done -> model.insertSuggestedBreak(
                    detail.trip.id, selectedDayDetails.id, detail.trip.revision, detail.trip.progressRevision,
                    beforeStop, destination, minutes, suggestedMinute, done) },
            )
        }
    }
    if (confirmInAppNavigation) AlertDialog(
        onDismissRequest = { confirmInAppNavigation = false },
        title = { Text("Start Google navigation in RouteWise?") },
        text = { Text("Google will receive your current device location and ${inAppDestinations.size} confirmed day destinations to calculate a fresh route. This can differ from the free OpenStreetMap preview and may be billed per destination after Google's monthly free allowance. Precise location is requested only after you continue.") },
        confirmButton = { TextButton(onClick = {
            confirmInAppNavigation = false
            AndroidActions.navigateInApp(context, inAppDestinations)
        }, modifier = Modifier.testTag("confirm-in-app-navigation")) { Text("Continue") } },
        dismissButton = { TextButton(onClick = { confirmInAppNavigation = false }) { Text("Cancel") } },
    )
}

@Composable
private fun StopRow(stop: StopEntity, number: Int, dragging: Boolean, handle: @Composable () -> Unit, edit: () -> Unit,
    up: () -> Unit, down: () -> Unit, canUp: Boolean, canDown: Boolean, move: () -> Unit, delete: () -> Unit, visit: () -> Unit, skip: () -> Unit) {
    var menu by remember { mutableStateOf(false) }
    Surface(color = if (dragging) MaterialTheme.colorScheme.primaryContainer else Color.White, shadowElevation = if (dragging) 8.dp else 0.dp) {
        Row(Modifier.fillMaxWidth().clickable(onClick = edit).semantics {
            customActions = buildList {
                if (canUp) add(CustomAccessibilityAction("Move up") { up(); true })
                if (canDown) add(CustomAccessibilityAction("Move down") { down(); true })
            }
        }.padding(start = 20.dp, end = 4.dp, top = 16.dp, bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(34.dp).clip(CircleShape).background(if (stop.visitState == "VISITED") Teal else MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                if (stop.visitState == "VISITED") Icon(Icons.Rounded.Check, "Visited", tint = Color.White, modifier = Modifier.size(18.dp))
                else Text(number.toString(), style = MaterialTheme.typography.labelMedium, color = Teal)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(stop.destination.label, style = MaterialTheme.typography.titleMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
                Text("${stop.durationMinutes} min  /  " + when {
                    stop.visitState == "SKIPPED" -> "Skipped"
                    stop.visitState == "VISITED" -> "Visited"
                    stop.mustVisit -> "Must visit"
                    else -> "Optional"
                }, style = MaterialTheme.typography.bodySmall, color = if (stop.visitState == "SKIPPED") Coral else Muted)
            }
            Box {
                ToolButton(Icons.Rounded.MoreVert, "Options for ${stop.destination.label}", { menu = true })
                DropdownMenu(menu, { menu = false }) {
                    MenuAction("Edit stop", Icons.Rounded.Edit) { menu = false; edit() }
                    MenuAction(if (stop.visitState == "VISITED") "Mark unvisited" else "Mark visited", Icons.Rounded.CheckCircleOutline) { menu = false; visit() }
                    MenuAction(if (stop.visitState == "SKIPPED") "Restore stop" else "Skip stop", Icons.Rounded.SkipNext) { menu = false; skip() }
                    if (canUp) MenuAction("Move up", Icons.Rounded.ArrowUpward) { menu = false; up() }
                    if (canDown) MenuAction("Move down", Icons.Rounded.ArrowDownward) { menu = false; down() }
                    MenuAction("Move to day", Icons.Rounded.CalendarMonth) { menu = false; move() }
                    MenuAction("Delete stop", Icons.Rounded.DeleteOutline) { menu = false; delete() }
                }
            }
            handle()
        }
    }
    HorizontalDivider(Modifier.padding(start = 66.dp, end = 20.dp), color = MaterialTheme.colorScheme.surfaceVariant)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ToolButton(icon: ImageVector, label: String, action: () -> Unit, modifier: Modifier = Modifier) {
    TooltipBox(positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(), tooltip = { PlainTooltip { Text(label) } }, state = rememberTooltipState()) {
        IconButton(onClick = action, modifier = modifier.size(48.dp)) { Icon(icon, contentDescription = label, modifier = Modifier.size(22.dp)) }
    }
}

@Composable
private fun MenuAction(label: String, icon: ImageVector, action: () -> Unit) {
    DropdownMenuItem(text = { Text(label) }, onClick = action, leadingIcon = { Icon(icon, null) })
}

@Composable
private fun SmallMetric(icon: ImageVector, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(icon, null, Modifier.size(16.dp), tint = Muted)
        Text(label, color = Muted, style = MaterialTheme.typography.bodySmall)
    }
}
