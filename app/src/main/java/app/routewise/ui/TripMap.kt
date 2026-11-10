package app.routewise.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddLocationAlt
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.routewise.data.StopEntity
import app.routewise.data.Destination
import app.routewise.maps.*
import kotlinx.serialization.json.*
import org.maplibre.android.MapLibre
import org.maplibre.android.annotations.IconFactory
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.annotations.PolylineOptions
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.module.http.HttpRequestUtil
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style

@Composable
fun TripMap(stops: List<StopEntity>, preview: RoutePreview?, maps: OpenMaps, addPin: (MapPoint) -> Unit, modifier: Modifier = Modifier, start: Destination? = null, finish: Destination? = null) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val uriHandler = LocalUriHandler.current
    val settings by maps.settings.collectAsState()
    val tileError by maps.tileError.collectAsState()
    var selected by remember { mutableStateOf<String?>(null) }
    var readyMap by remember { mutableStateOf<MapLibreMap?>(null) }
    val located = stops.filter { it.destination.latitude != null && it.destination.longitude != null }
    val markers = buildList {
        start?.let { add("S" to it) }
        addAll(stops.mapIndexed { index, stop -> (index + 1).toString() to stop.destination })
        finish?.let { add("E" to it) }
    }.filter { it.second.latitude != null && it.second.longitude != null }
    val mapView = remember(context, settings.tilesUrl) {
        MapLibre.getInstance(context)
        HttpRequestUtil.setOkHttpClient(maps.http)
        HttpRequestUtil.setLogEnabled(false)
        MapView(context).also { it.onCreate(null) }
    }
    fun fit(map: MapLibreMap) {
        val points = markers.map { LatLng(it.second.latitude!!, it.second.longitude!!) }.distinct()
        if (points.size == 1) map.moveCamera(CameraUpdateFactory.newLatLngZoom(points.single(), 13.0))
        else if (points.size > 1) map.moveCamera(CameraUpdateFactory.newLatLngBounds(LatLngBounds.Builder().includes(points).build(), 72))
        else map.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(23.5, 80.0), 3.5))
    }
    DisposableEffect(mapView, owner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView.onStart()
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                Lifecycle.Event.ON_STOP -> mapView.onStop()
                else -> Unit
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose {
            owner.lifecycle.removeObserver(observer)
            mapView.onPause(); mapView.onStop(); mapView.onDestroy()
            readyMap = null
        }
    }
    LaunchedEffect(mapView) {
        mapView.getMapAsync { map ->
            map.setPrefetchZoomDelta(0)
            val style = buildJsonObject {
                put("version", 8)
                putJsonObject("sources") {
                    putJsonObject("osm") {
                        put("type", "raster"); put("tileSize", 256)
                        putJsonArray("tiles") { add(settings.tilesUrl) }
                        put("attribution", "OpenStreetMap contributors")
                        put("maxzoom", 19)
                    }
                }
                putJsonArray("layers") {
                    add(buildJsonObject { put("id", "base"); put("type", "raster"); put("source", "osm") })
                }
            }.toString()
            map.setStyle(Style.Builder().fromJson(style)) { readyMap = map; fit(map) }
            map.setOnMarkerClickListener { marker -> selected = marker.title; false }
        }
    }
    LaunchedEffect(readyMap, stops, preview, start, finish) {
        readyMap?.let { map ->
            map.clear()
            preview?.segments?.forEach { segment ->
                map.addPolyline(PolylineOptions().addAll(segment.geometry.map { LatLng(it.latitude, it.longitude) })
                    .color(android.graphics.Color.rgb(18, 106, 92)).width(4f))
            }
            markers.forEach { (number, destination) ->
                val pixels = (40 * context.resources.displayMetrics.density).toInt()
                val bitmap = Bitmap.createBitmap(pixels, pixels, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.rgb(18, 106, 92) }
                canvas.drawCircle(pixels / 2f, pixels / 2f, pixels / 2f - 3, paint)
                paint.apply { color = android.graphics.Color.WHITE; textSize = pixels * .42f; textAlign = Paint.Align.CENTER; isFakeBoldText = true }
                canvas.drawText(number.toString(), pixels / 2f, pixels / 2f - (paint.descent() + paint.ascent()) / 2, paint)
                map.addMarker(MarkerOptions().position(LatLng(destination.latitude!!, destination.longitude!!))
                    .title(destination.label).icon(IconFactory.getInstance(context).fromBitmap(bitmap)))
            }
        }
    }
    Column(modifier) {
        Box(Modifier.fillMaxWidth().weight(1f)) {
            AndroidView(factory = { mapView }, modifier = Modifier.fillMaxSize())
            Column(Modifier.align(Alignment.TopStart).padding(10.dp)) {
                if (tileError != null) Surface { Text(tileError!!, Modifier.padding(8.dp), style = MaterialTheme.typography.bodySmall) }
                if (located.size != stops.size) Surface { Text("${stops.size - located.size} stops need a location", Modifier.padding(8.dp), style = MaterialTheme.typography.bodySmall) }
                selected?.let { Surface { Text(it, Modifier.padding(8.dp), style = MaterialTheme.typography.labelLarge) } }
            }
            Column(Modifier.align(Alignment.BottomEnd).padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalIconButton(onClick = { readyMap?.let(::fit) }, enabled = readyMap != null) { Icon(Icons.Rounded.CenterFocusStrong, "Fit all stops") }
                FilledTonalButton(onClick = { readyMap?.cameraPosition?.target?.let { addPin(MapPoint(it.latitude, it.longitude)) } }, enabled = readyMap != null) {
                    Icon(Icons.Rounded.AddLocationAlt, null); Spacer(Modifier.width(6.dp)); Text("Add pin")
                }
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = { uriHandler.openUri("https://www.openstreetmap.org/copyright") }) { Text("(c) OpenStreetMap", style = MaterialTheme.typography.labelSmall) }
            TextButton(onClick = { uriHandler.openUri("https://www.openstreetmap.org/fixthemap") }) { Text("Fix the map", style = MaterialTheme.typography.labelSmall) }
        }
    }
}
