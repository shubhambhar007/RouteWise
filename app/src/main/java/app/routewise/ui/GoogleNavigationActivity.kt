package app.routewise.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import app.routewise.BuildConfig
import com.google.android.gms.maps.GoogleMap
import com.google.android.libraries.navigation.ArrivalEvent
import com.google.android.libraries.navigation.AudioGuidanceSettings
import com.google.android.libraries.navigation.ListenableResultFuture
import com.google.android.libraries.navigation.NavigationApi
import com.google.android.libraries.navigation.NavigationFragment
import com.google.android.libraries.navigation.Navigator
import com.google.android.libraries.navigation.RoutingOptions
import com.google.android.libraries.navigation.Waypoint

class GoogleNavigationActivity : FragmentActivity() {
    private lateinit var status: TextView
    private lateinit var navigationFragment: NavigationFragment
    private lateinit var mapContainer: FrameLayout
    private var navigator: Navigator? = null
    private var arrivalListener: Navigator.ArrivalListener? = null
    private var currentDestinationIndex = 0
    private var destinationTitles: List<String> = emptyList()
    private var destinationLatitudes = doubleArrayOf()
    private var destinationLongitudes = doubleArrayOf()

    private val requestLocation = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        if (grants[Manifest.permission.ACCESS_FINE_LOCATION] == true) initializeNavigation()
        else status.text = "Precise location is needed for turn-by-turn navigation. Google Maps handoff remains available."
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        destinationTitles = intent.getStringArrayListExtra(EXTRA_TITLES).orEmpty()
        destinationLatitudes = intent.getDoubleArrayExtra(EXTRA_LATITUDES) ?: doubleArrayOf()
        destinationLongitudes = intent.getDoubleArrayExtra(EXTRA_LONGITUDES) ?: doubleArrayOf()
        if (destinationTitles.isEmpty() || destinationTitles.size > 25 || destinationLatitudes.size != destinationTitles.size || destinationLongitudes.size != destinationTitles.size ||
            destinationLatitudes.any { !it.isFinite() || it !in -90.0..90.0 } || destinationLongitudes.any { !it.isFinite() || it !in -180.0..180.0 }) {
            finish()
            return
        }
        setContentView(createContent())
        if (BuildConfig.GOOGLE_NAVIGATION_API_KEY.isBlank()) {
            status.text = "Google Navigation isn't configured. Add the key from NEXT_STEPS.md and rebuild."
            return
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            initializeNavigation()
        } else {
            requestLocation.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
        }
    }

    private fun createContent(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(android.graphics.Color.WHITE)
        }
        status = TextView(this).apply {
            text = "Preparing in-app navigation…"
            textSize = 15f
            setTextColor(android.graphics.Color.rgb(23, 45, 39))
            setPadding(20, 14, 20, 14)
        }
        mapContainer = FrameLayout(this).apply { id = View.generateViewId() }
        val stopButton = Button(this).apply {
            text = "End navigation"
            setOnClickListener { stopNavigation(); finish() }
        }
        root.addView(status, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        root.addView(mapContainer, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        root.addView(stopButton, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            setMargins(16, 8, 16, 16)
        })
        return root
    }

    private fun initializeNavigation() {
        if (!::navigationFragment.isInitialized) {
            navigationFragment = NavigationFragment.newInstance()
            supportFragmentManager.beginTransaction().replace(mapContainer.id, navigationFragment).commitNow()
        }
        status.text = "Connecting to Google Navigation…"
        NavigationApi.getNavigator(this, object : NavigationApi.NavigatorListener {
            override fun onNavigatorReady(ready: Navigator) {
                if (isFinishing || isDestroyed) {
                    ready.cleanup()
                    return
                }
                navigator = ready
                ready.setTaskRemovedBehavior(Navigator.TaskRemovedBehavior.QUIT_SERVICE)
                navigationFragment.getMapAsync { map ->
                    map.followMyLocation(GoogleMap.CameraPerspective.TILTED)
                }
                val waypoints = try {
                    destinationTitles.indices.map { index ->
                        Waypoint.builder().setLatLng(destinationLatitudes[index], destinationLongitudes[index])
                            .setTitle(destinationTitles[index]).setVehicleStopover(true).build()
                    }
                } catch (_: RuntimeException) {
                    status.text = "A day destination can't be used for vehicle navigation."
                    return
                }
                status.text = "Finding a driving route to ${destinationTitles.first()}…"
                val routing = RoutingOptions().apply { travelMode(RoutingOptions.TravelMode.DRIVING) }
                arrivalListener = Navigator.ArrivalListener { event ->
                    if (event.isFinalDestination()) {
                        status.text = "Arrived at ${destinationTitles.last()}"
                        stopNavigation()
                        finish()
                    } else {
                        currentDestinationIndex = (currentDestinationIndex + 1).coerceAtMost(destinationTitles.lastIndex)
                        ready.continueToNextDestination()
                        status.text = "Navigating to ${destinationTitles[currentDestinationIndex]}"
                        ready.startGuidance()
                    }
                }
                ready.addArrivalListener(requireNotNull(arrivalListener))
                ready.setDestinations(waypoints, routing).setOnResultListener(object : ListenableResultFuture.OnResultListener<Navigator.RouteStatus> {
                    override fun onResult(result: Navigator.RouteStatus) {
                        if (isFinishing || isDestroyed) return
                        if (result == Navigator.RouteStatus.OK) {
                            ready.setAudioGuidanceSettings(AudioGuidanceSettings.builder()
                                .setGuidanceMode(AudioGuidanceSettings.GuidanceMode.VOICE_ALERTS_AND_GUIDANCE).build())
                            status.text = "Navigating to ${destinationTitles.first()} · ${destinationTitles.size} destinations"
                            ready.startGuidance()
                        } else {
                            status.text = "Couldn't start navigation ($result). You can use Google Maps instead."
                        }
                    }
                })
            }

            override fun onError(errorCode: Int) {
                if (!isFinishing && !isDestroyed) status.text = "Google Navigation unavailable ($errorCode). Check the API key, billing, connection and location permission."
            }
        })
    }

    private fun stopNavigation() {
        navigator?.let { active ->
            arrivalListener?.let { listener -> runCatching { active.removeArrivalListener(listener) } }
            arrivalListener = null
            runCatching { active.stopGuidance() }
            runCatching { active.clearDestinations() }
            runCatching { active.cleanup() }
        }
        navigator = null
    }

    override fun onDestroy() {
        stopNavigation()
        super.onDestroy()
    }

    companion object {
        private const val EXTRA_TITLES = "destination_titles"
        private const val EXTRA_LATITUDES = "destination_latitudes"
        private const val EXTRA_LONGITUDES = "destination_longitudes"

        fun intent(context: Context, destinations: List<app.routewise.data.Destination>) =
            Intent(context, GoogleNavigationActivity::class.java).apply {
                putStringArrayListExtra(EXTRA_TITLES, ArrayList(destinations.map { it.label }))
                putExtra(EXTRA_LATITUDES, destinations.map { requireNotNull(it.latitude) }.toDoubleArray())
                putExtra(EXTRA_LONGITUDES, destinations.map { requireNotNull(it.longitude) }.toDoubleArray())
            }
    }
}