package com.getwemap.example.map.positioning.fragments

import android.Manifest.permission
import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.getwemap.example.common.AlertFactory
import com.getwemap.example.common.HapticGenerator
import com.getwemap.example.common.PermissionHelper
import com.getwemap.example.common.map.GlobalOptions
import com.getwemap.example.common.multiline
import com.getwemap.example.map.positioning.AppConstants
import com.getwemap.example.map.positioning.R
import com.getwemap.example.map.positioning.VpsLocalMapDownloader
import com.getwemap.example.map.positioning.databinding.FragmentMapVpsLocalBinding
import com.getwemap.sdk.core.model.entities.Attitude
import com.getwemap.sdk.core.model.entities.Coordinate
import com.getwemap.sdk.core.model.entities.Itinerary
import com.getwemap.sdk.core.model.entities.MapData
import com.getwemap.sdk.core.model.entities.PointOfInterest
import com.getwemap.sdk.core.model.services.parameters.ItinerarySearchRules
import com.getwemap.sdk.core.navigation.Navigation
import com.getwemap.sdk.core.navigation.info.NavigationInfo
import com.getwemap.sdk.core.navigation.manager.NavigationManagerListener
import com.getwemap.sdk.core.poi.PointOfInterestManagerListener
import com.getwemap.sdk.map.OnMapViewReadyCallback
import com.getwemap.sdk.map.WemapMapView
import com.getwemap.sdk.map.helpers.MapConstants
import com.getwemap.sdk.map.itineraries.ItineraryManager
import com.getwemap.sdk.map.itineraries.ItineraryOptions
import com.getwemap.sdk.map.itineraries.LineOptions
import com.getwemap.sdk.map.location.UserLocationManager
import com.getwemap.sdk.map.location.UserLocationManagerListener
import com.getwemap.sdk.map.navigation.IMapNavigationManager
import com.getwemap.sdk.map.poi.IMapPointOfInterestManager
import com.getwemap.sdk.positioning.wemapvpslocal.VpsLocalLocationSource
import com.getwemap.sdk.positioning.wemapvpslocal.VpsLocalLocationSource.ScanOutcome
import com.getwemap.sdk.positioning.wemapvpslocal.VpsLocalLocationSource.ScanStatus
import com.getwemap.sdk.positioning.wemapvpslocal.VpsLocalLocationSourceListener
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import org.maplibre.android.MapLibre
import org.maplibre.android.location.OnCameraTrackingChangedListener
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.location.modes.RenderMode
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.time.Duration.Companion.seconds

/**
 * Map + offline VPS positioning sample. Adapted from [MapVPSFragment], but backed by the offline,
 * ARCore-free [VpsLocalLocationSource]:
 * - the map database is loaded from a downloaded directory (see [VpsLocalMapDownloader]),
 * - the camera preview is rendered by the SDK into the [PreviewView] we pass in,
 * - there is no ARCore tracking / degraded-positioning state machine — positioning is discrete
 *   (a fix per successful scan) with live, VPS-anchored heading in between.
 */
@SuppressLint("MissingPermission")
class MapVPSLocalFragment : Fragment(), OnMapViewReadyCallback {

    enum class AppState { BROWSING, POI_SELECTED, ITINERARY, NAVIGATION, SCANNING }

    private var _binding: FragmentMapVpsLocalBinding? = null
    private val binding get() = _binding!!

    private val applicationContext get() = requireContext().applicationContext
    private val mapView get() = binding.mapView
    private val pointOfInterestManager: IMapPointOfInterestManager get() = mapView.pointOfInterestManager
    private val navigationManager: IMapNavigationManager get() = mapView.navigationManager
    private val itineraryManager: ItineraryManager get() = mapView.itineraryManager
    private val locationManager: UserLocationManager get() = mapView.locationManager

    private val currentItinerary: Itinerary? get() = mapView.itineraryManager.itineraries.firstOrNull()
    private lateinit var permissionHelper: PermissionHelper
    private lateinit var vpsLocationSource: VpsLocalLocationSource

    private var scanningTimerJob: Job? = null
    private var errorTimerJob: Job? = null
    private var hintTimerJob: Job? = null
    private var isScreenWakeLockEnabled = false

    private val haptic: HapticGenerator? by lazy {
        if (AppConstants.ENABLE_HAPTIC_FEEDBACK) HapticGenerator(requireContext()) else null
    }

    // region Lifecycle
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        MapConstants.STALE_TIMEOUT_MILLISECONDS = 30_000
        MapLibre.getInstance(applicationContext)
        _binding = FragmentMapVpsLocalBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        createPermissionsHelper()

        mapView.onCreate(savedInstanceState)

        val mapDataString = requireArguments().getString("mapData")!!
        val mapData = Json.decodeFromString<MapData>(mapDataString)
        mapView.mapData = mapData

        // Map database directory downloaded in advance (see VpsLocalMapDownloader).
        val mapDir = requireArguments().getString("mapDir")?.let { File(it) }
            ?: VpsLocalMapDownloader.mapDir(applicationContext)

        // Create the offline location source. The SDK owns the camera and renders the live preview
        // into the PreviewView we pass in.
        vpsLocationSource = VpsLocalLocationSource(applicationContext, mapDir, mapData, binding.previewView)

        // to prevent interactions with MapView before it's loaded
        binding.locateMe.isEnabled = false

        mapView.getMapViewAsync(this)

        binding.locateMe.setOnClickListener { locateMeButtonClicked() }
        binding.camera.setOnClickListener { cameraButtonClicked() }
        binding.stopScanButton.setOnClickListener { stopScan() }
        binding.itineraryCalculateButton.setOnClickListener { computeItinerariesToPOI() }
        binding.itineraryCloseButton.setOnClickListener { onItineraryCloseClick() }
        binding.navigationStartButton.setOnClickListener { onStartNavigationClick() }
        binding.navigationStopButton.setOnClickListener { onStopNavigationClick() }

        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : androidx.activity.OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    handleBackPressed()
                }
            })
    }

    override fun onMapViewReady(mapView: WemapMapView, map: MapLibreMap, style: Style, data: MapData) {
        // Register the offline VPS scan listener
        vpsLocationSource.listeners.add(vpsListener)
        // Bind location source to the map to show the blue dot from VPS
        // This action can be done only when mapView is ready
        locationManager.locationSource = vpsLocationSource
        // It enables the blue dot orientation rendering
        locationManager.renderMode = RenderMode.COMPASS

        locationManager.addListener(locationManagerListener)
        pointOfInterestManager.addListener(poiListener)
        navigationManager.addListener(navigationManagerListener)

        mapView.map.addOnMapClickListener {
            if (getAppState() == AppState.POI_SELECTED)
                pointOfInterestManager.unselectPOI()

            return@addOnMapClickListener true
        }

        locationManager.addOnCameraTrackingChangedListener(object : OnCameraTrackingChangedListener {
            override fun onCameraTrackingDismissed() {
                updateLocateMeButtonIcon()
            }
            override fun onCameraTrackingChanged(currentMode: Int) {
                updateLocateMeButtonIcon()
            }
        })

        binding.levelsSwitcher.bind(mapView.buildingManager)
        binding.locateMe.isEnabled = true

        mapView.map.uiSettings.attributionGravity = Gravity.START or Gravity.BOTTOM
    }

    override fun onStart() {
        super.onStart()
        mapView.onStart()
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
    }

    override fun onStop() {
        super.onStop()
        mapView.onStop()
        errorTimerJob?.cancel()
        hintTimerJob?.cancel()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        mapView.onSaveInstanceState(outState)
    }

    @Deprecated("Deprecated in Java")
    override fun onLowMemory() {
        super.onLowMemory()
        mapView.onLowMemory()
    }

    override fun onDestroyView() {
        super.onDestroyView()

        binding.levelsSwitcher.unbind()

        if (mapView.isLoaded) {
            navigationManager.removeListener(navigationManagerListener)
            pointOfInterestManager.removeListener(poiListener)
            locationManager.removeListener(locationManagerListener)
            locationManager.locationSource = null
        }
        vpsLocationSource.listeners.remove(vpsListener)
        vpsLocationSource.deinit()

        // Ensure screen wake lock is disabled when fragment is destroyed
        if (isScreenWakeLockEnabled) {
            requireActivity().window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            isScreenWakeLockEnabled = false
        }

        mapView.onDestroy()
        MapConstants.STALE_TIMEOUT_MILLISECONDS = 5_000

        _binding = null

        System.gc()
    }
    // endregion

    // region Location
    private fun locateMeButtonClicked() {
        if (!locationManager.isEnabled) {
            locateUser()
            return
        }
        // Already localized — cycle camera tracking modes.
        toggleNextUserTrackingMode()
    }

    private fun cameraButtonClicked() {
        locateUser("Do you think your position is inaccurate? Scan again")
    }

    private fun toggleNextUserTrackingMode() {
        locationManager.cameraMode = when (locationManager.cameraMode) {
            CameraMode.NONE -> CameraMode.TRACKING
            CameraMode.TRACKING -> CameraMode.TRACKING_COMPASS
            else /* TRACKING_COMPASS */ -> CameraMode.NONE
        }
    }

    private fun enableFollowIfNotAlreadyEnabled() {
        if (locationManager.cameraMode < CameraMode.TRACKING)
            locationManager.cameraMode = CameraMode.TRACKING
    }

    private fun locateUser(message: String? = null) {
        val message = message ?: "In order to be localized we will use your camera"

        lifecycleScope.launch {
            runCatching {
                checkLocationSource(message)
            }.onSuccess {
                startScan()
                enableFollowIfNotAlreadyEnabled()
            }.onFailure {
                Snackbar.make(mapView, it.message ?: "Failed to locate you", Snackbar.LENGTH_SHORT).show()
            }
        }
    }

    private suspend fun checkLocationSource(message: String) {
        checkPermissions()
        askForScan(message)
        startLocationSource()
    }

    private fun startLocationSource() {
        locationManager.isEnabled = true
    }

    private fun startScan() {
        vpsLocationSource.startScan()
        binding.camera.isVisible = true
        binding.cameraLayout.visibility = View.VISIBLE
        createScanningTimer()
        updateScreenWakeLock()
    }

    private fun stopScan() {
        vpsLocationSource.stopScan()
        _binding?.cameraLayout?.visibility = View.INVISIBLE
        scanningTimerJob?.cancel()
        updateScreenWakeLock()
    }

    /**
     * Continuous mode: dismiss the full-screen scan overlay to reveal the map, but keep the SDK
     * scanning in the background so each new fix re-anchors dead reckoning (drift auto-correction).
     */
    private fun revealMapKeepScanning() {
        _binding?.cameraLayout?.visibility = View.INVISIBLE
        scanningTimerJob?.cancel()
        updateScreenWakeLock()
    }

    private fun updateLocateMeButtonIcon() {
        val iconID: Int = when (locationManager.cameraMode) {
            CameraMode.TRACKING -> R.drawable.baseline_my_location_24
            CameraMode.TRACKING_COMPASS -> R.drawable.explore_24px
            else /* NONE */ -> R.drawable.location_searching_24px
        }
        binding.locateMe.setImageDrawable(ContextCompat.getDrawable(requireContext(), iconID))
    }

    private suspend fun askForScan(message: String) {
        return AlertFactory.showSimpleAlert(
            requireContext(), message, "User refused to open camera", "Open camera"
        )
    }

    private val locationManagerListener by lazy {
        UserLocationManagerListener { error ->
            setErrorMessageAndStartTimer(error)
        }
    }

    private fun setErrorMessageAndStartTimer(error: Throwable) {
        binding.cameraDebugText.apply {
            isVisible = true
            text = error.message
        }
        errorTimerJob = lifecycleScope.launch {
            delay(1.seconds)
            _binding?.cameraDebugText?.apply {
                isVisible = false
                text = ""
            }
        }
    }

    private val vpsListener by lazy {
        object : VpsLocalLocationSourceListener {
            override fun onLocalized(coordinate: Coordinate, attitude: Attitude) {
                Log.d("WEMAP", "onLocalized. Coordinate: $coordinate")
                runOnUi {
                    // Only act on the first fix that ends the scan-overlay session; later continuous
                    // fixes just update the dot underneath the (already-hidden) overlay.
                    if (_binding?.cameraLayout?.visibility != View.VISIBLE)
                        return@runOnUi
                    haptic?.success()
                    if (binding.continuousScanSwitch.isChecked) {
                        // Continuous: reveal the map but keep scanning so fixes keep re-anchoring PDR.
                        revealMapKeepScanning()
                    } else {
                        // One-shot: stop scanning; the dot then rides on dead reckoning until re-scan.
                        stopScan()
                    }
                }
            }

            override fun onScanFailed(outcome: ScanOutcome) {
                Log.d("WEMAP", "onScanFailed. Outcome: ${outcome.name}")
                val hint = when (outcome) {
                    ScanOutcome.INCLINATION_TOO_LOW -> "Hold your phone vertically to scan your surroundings"
                    ScanOutcome.PAUSED_FLAT -> "Raise your phone upright to resume scanning"
                    else -> null
                }
                hint?.let { runOnUi { showScanHint(it) } }
            }

            override fun onError(error: Throwable) {
                Log.e("WEMAP", "VPS local error", error)
                runOnUi { setErrorMessageAndStartTimer(error) }
            }
        }
    }

    /** Transient hint shown in the scan overlay (e.g. when the phone is tilted too low to scan). */
    private fun showScanHint(message: String) {
        binding.cameraDebugText.apply {
            isVisible = true
            text = message
        }
        hintTimerJob?.cancel()
        hintTimerJob = lifecycleScope.launch {
            delay(2.seconds)
            _binding?.cameraDebugText?.apply {
                isVisible = false
                text = ""
            }
        }
    }

    /** Listener callbacks arrive on a background scan thread; hop to the main thread for UI. */
    private fun runOnUi(block: () -> Unit) {
        val activity = activity ?: return
        activity.runOnUiThread {
            if (_binding == null) return@runOnUiThread
            block()
        }
    }

    private fun createScanningTimer() {
        scanningTimerJob = lifecycleScope.launch {
            delay(20.seconds)
            askToContinue()
        }
    }

    private fun updateScreenWakeLock() {
        // Keep the screen awake whenever the SDK is scanning (including continuous background scanning,
        // when the overlay is hidden) or navigating.
        val shouldEnable = vpsLocationSource.scanStatus == ScanStatus.STARTED ||
            navigationManager.hasActiveNavigation

        if (shouldEnable && !isScreenWakeLockEnabled) {
            requireActivity().window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            isScreenWakeLockEnabled = true
        } else if (!shouldEnable && isScreenWakeLockEnabled) {
            requireActivity().window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            isScreenWakeLockEnabled = false
        }
    }

    private fun askToContinue() {
        scanningTimerJob = lifecycleScope.launch {
            runCatching {
                AlertFactory.showSimpleAlert(
                    requireContext(), "We cannot localize you. Do you want to continue to try?",
                    "You decided to get back to the map", "Continue", "Back to map"
                )
            }.onSuccess {
                createScanningTimer()
            }.onFailure {
                stopScan()
                val text = "Failed to localize you in reasonable time. Try again later"
                Snackbar.make(mapView, text, Snackbar.LENGTH_LONG).multiline().show()
            }
        }
    }
    // endregion

    // region PoIs
    private fun renderPoI(poi: PointOfInterest) {
        hideAllStatesUI()
        binding.poiContainer.visibility = View.VISIBLE
        binding.poiInfo.text = poi.name
    }

    private val poiListener by lazy {
        PointOfInterestManagerListener(
            {
                renderPoI(it)
            },
            {
                hideAllStatesUI()
            }
        )
    }
    // endregion

    // region Itinerary
    private fun computeItinerariesToPOI() {
        val selectedPOI = pointOfInterestManager.getSelectedPOI()
        if (selectedPOI == null) {
            Log.e("WEMAP", "Can't compute itineraries, there is no selected POI")
            return
        }

        val origin = locationManager.lastCoordinate
        if (origin != null) {
            calculateAndDrawItinerary(origin, selectedPOI.coordinate)
            return
        }

        lifecycleScope.launch {
            runCatching {
                val text = "We need to know your location to compute the best route. We will use your camera to localize you"
                checkLocationSource(text)

                startScan()

                withTimeout(20.seconds) {
                    locationManager.coordinateFlow.first()
                }
            }.onSuccess {
                calculateAndDrawItinerary(it, selectedPOI.coordinate)
            }.onFailure {
                val text = "Failed to start itinerary with error - $it"
                Snackbar.make(mapView, text, Snackbar.LENGTH_SHORT).multiline().show()
            }
        }
    }

    private fun calculateAndDrawItinerary(origin: Coordinate, destination: Coordinate) {
        val searchRules = if (AppConstants.USE_WHEELCHAIR) ItinerarySearchRules.WHEELCHAIR else ItinerarySearchRules()

        lifecycleScope.launch {
            runCatching {
                itineraryManager.getItineraries(origin, destination, searchRules = searchRules)
            }.onSuccess {
                renderItinerary(it.first())
            }.onFailure {
                val text = "Failed to compute itineraries with error - $it"
                Snackbar.make(mapView, text, Snackbar.LENGTH_SHORT).multiline().show()
            }
        }
    }

    private fun renderItinerary(itinerary: Itinerary) {

        if (itineraryManager.addItinerary(itinerary) == null)
            return Snackbar.make(mapView, "Failed to add itinerary", Snackbar.LENGTH_SHORT).show()

        pointOfInterestManager.isUserSelectionEnabled = false
        hideAllStatesUI()
        binding.itineraryContainer.visibility = View.VISIBLE

        val currentPoi = pointOfInterestManager.getSelectedPOI()!!
        binding.itineraryInfo.text = "Itinerary from user position to ${currentPoi.name}\n" +
                "Distance: ${itinerary.distance.toInt()}m\n" +
                "Duration: ${itinerary.duration.toInt()}s"
    }

    private fun onItineraryCloseClick() {
        if (itineraryManager.removeItinerary(currentItinerary!!) == null)
            return Snackbar.make(mapView, "Failed to remove itinerary", Snackbar.LENGTH_SHORT).show()

        pointOfInterestManager.isUserSelectionEnabled = true

        val selectedPoI = pointOfInterestManager.getSelectedPOI()
        if (selectedPoI == null)
            hideAllStatesUI()
        else
            renderPoI(selectedPoI)
    }
    // endregion

    // region Navigation
    private fun onStartNavigationClick() {
        val navigationOptions = GlobalOptions.navigationOptions(requireContext())

        val itineraryOptions = ItineraryOptions(indoorLine = LineOptions(color = Color.GREEN))

        lifecycleScope.launch {
            runCatching {
                navigationManager.startNavigation(currentItinerary!!, navigationOptions, itineraryOptions = itineraryOptions)
            }.onSuccess {
                renderNavigation()
                updateScreenWakeLock()
            }.onFailure {
                val text = "Failed to start navigation with error - $it"
                Snackbar.make(mapView, text, Snackbar.LENGTH_SHORT).multiline().show()
            }
        }
    }

    private fun renderNavigation() {
        hideAllStatesUI()
        binding.navigationContainer.visibility = View.VISIBLE

        navigationManager.getNavigationInfo()?.let {
            updateNavInfo(it)
        }
    }

    private fun updateNavInfo(info: NavigationInfo) {
        binding.navigationInfo.text = "Remaining distance: ${info.remainingDistance.toInt()}m"
    }

    private fun onStopNavigationClick() {
        navigationManager.stopNavigation().onFailure {
            val text = "Failed to stop navigation with error - $it"
            Snackbar.make(mapView, text, Snackbar.LENGTH_SHORT).multiline().show()
        }
        updateScreenWakeLock()
    }

    private val navigationManagerListener by lazy {
        object : NavigationManagerListener {
            override fun onNavigationInfoChanged(info: NavigationInfo) {
                updateNavInfo(info)
            }

            override fun onNavigationStopped(navigation: Navigation) {
                renderItinerary(navigation.itinerary)
                Snackbar.make(mapView, "Navigation stopped", Snackbar.LENGTH_SHORT).show()
                updateScreenWakeLock()
            }

            override fun onArrivedAtDestination(navigation: Navigation) {
                Snackbar.make(mapView, "You arrived to destination", Snackbar.LENGTH_SHORT).show()
                updateScreenWakeLock()
            }

            override fun onNavigationFailed(error: Throwable) {
                currentItinerary?.let { renderItinerary(it) }
                val text = "Navigation failed with error - $error"
                Snackbar.make(mapView, text, Snackbar.LENGTH_SHORT).multiline().show()
                updateScreenWakeLock()
            }

            override fun onNavigationRecalculated(navigation: Navigation) {
                val text = "Navigation recalculated - $navigation"
                Snackbar.make(mapView, text, Snackbar.LENGTH_SHORT).multiline().show()
            }
        }
    }
    // endregion

    // region Misc
    private fun hideAllStatesUI() {
        binding.poiContainer.visibility = View.GONE
        binding.itineraryContainer.visibility = View.GONE
        binding.navigationContainer.visibility = View.GONE
    }

    private fun getAppState(): AppState {
        return when {
            // "Scanning" is the full-screen overlay session, not the SDK's scan status: in continuous
            // mode the SDK keeps scanning in the background while the app is browsing/navigating.
            _binding?.cameraLayout?.visibility == View.VISIBLE -> AppState.SCANNING
            navigationManager.hasActiveNavigation -> AppState.NAVIGATION
            currentItinerary != null -> AppState.ITINERARY
            pointOfInterestManager.getSelectedPOI() != null -> AppState.POI_SELECTED
            else -> AppState.BROWSING
        }
    }

    private fun handleBackPressed() {
        if (!mapView.isLoaded) {
            findNavController().navigateUp()
            return
        }

        when (getAppState()) {
            AppState.SCANNING -> stopScan()
            AppState.NAVIGATION -> onStopNavigationClick()
            AppState.ITINERARY -> onItineraryCloseClick()
            AppState.POI_SELECTED -> pointOfInterestManager.unselectPOI()
            // Navigate back to the previous fragment
            else -> findNavController().navigateUp()
        }
    }
    // endregion

    // region Permissions
    private fun createPermissionsHelper() {
        permissionHelper = PermissionHelper(this, listOf(permission.CAMERA))
    }

    private suspend fun checkPermissions() {
        if (permissionHelper.allGranted())
            return

        AlertFactory.showSimpleAlert(
            requireContext(),
            "In order to be localized, we will use your camera. Please accept following permissions",
            "User refused to review permissions"
        )
        requestPermissions()
    }

    private suspend fun requestPermissions() = suspendCancellableCoroutine { continuation ->
        permissionHelper.request { _, denied ->
            if (denied.isEmpty())
                continuation.resume(Unit)
            else
                continuation.resumeWithException(Throwable("User denied required permissions"))
        }
    }
    // endregion
}
