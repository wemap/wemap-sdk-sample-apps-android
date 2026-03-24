package com.getwemap.example.map.positioning.fragments

import android.Manifest.permission
import android.annotation.SuppressLint
import android.os.Build
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
import com.getwemap.example.common.onDismissed
import com.getwemap.example.map.positioning.AppConstants
import com.getwemap.example.map.positioning.R
import com.getwemap.example.map.positioning.databinding.FragmentMapVpsBinding
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
import com.getwemap.sdk.map.itineraries.ItineraryManager
import com.getwemap.sdk.map.location.UserLocationManager
import com.getwemap.sdk.map.location.UserLocationManagerListener
import com.getwemap.sdk.map.navigation.IMapNavigationManager
import com.getwemap.sdk.map.poi.IMapPointOfInterestManager
import com.getwemap.sdk.positioning.wemapvpsarcore.WemapVPSARCoreLocationSource
import com.getwemap.sdk.positioning.wemapvpsarcore.WemapVPSARCoreLocationSource.ScanStatus
import com.getwemap.sdk.positioning.wemapvpsarcore.WemapVPSARCoreLocationSource.State
import com.getwemap.sdk.positioning.wemapvpsarcore.WemapVPSARCoreLocationSourceError
import com.getwemap.sdk.positioning.wemapvpsarcore.WemapVPSARCoreLocationSourceListener
import com.google.android.material.snackbar.Snackbar
import com.google.ar.core.TrackingFailureReason
import com.google.ar.core.TrackingState
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
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.time.Duration.Companion.seconds

@SuppressLint("MissingPermission")
class MapVPSFragment : Fragment(), OnMapViewReadyCallback {

    enum class AppState { BROWSING, POI_SELECTED, ITINERARY, NAVIGATION, SCANNING }

    private var _binding: FragmentMapVpsBinding? = null
    private val binding get() = _binding!!

    private val applicationContext get() = requireContext().applicationContext
    private val mapView get() = binding.mapView
    private val pointOfInterestManager: IMapPointOfInterestManager get() = mapView.pointOfInterestManager
    private val navigationManager: IMapNavigationManager get() = mapView.navigationManager
    private val itineraryManager: ItineraryManager get() = mapView.itineraryManager
    private val locationManager: UserLocationManager get() = mapView.locationManager

    private val currentItinerary: Itinerary? get() = mapView.itineraryManager.itineraries.firstOrNull()
    private lateinit var permissionHelper: PermissionHelper
    private lateinit var vpsLocationSource: WemapVPSARCoreLocationSource

    private var scanningTimerJob: Job? = null
    private var errorTimerJob: Job? = null
    private var rescanSuggested = false
    private var isScreenWakeLockEnabled = false
    private val impreciseMessage = "Your location seems imprecise, you can scan again to refine your position if necessary"

    private val haptic: HapticGenerator? by lazy {
        if (AppConstants.ENABLE_HAPTIC_FEEDBACK) HapticGenerator(requireContext()) else null
    }

    private var backgroundScanHint: Snackbar? = null

    // region Lifecycle
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        MapLibre.getInstance(applicationContext)
        _binding = FragmentMapVpsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        createPermissionsHelper()

        mapView.onCreate(savedInstanceState)

        val mapDataString = requireArguments().getString("mapData")!!
        val mapData = Json.decodeFromString<MapData>(mapDataString)
        mapView.mapData = mapData

        // Create location source
        vpsLocationSource = WemapVPSARCoreLocationSource(applicationContext, mapData)
        // Bind camera view to location source
        vpsLocationSource.bind(applicationContext, binding.surfaceView)

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
        // Register listeners for VPS scan state and status change
        vpsLocationSource.vpsListeners.add(vpsListener)
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
        vpsLocationSource.vpsListeners.remove(vpsListener)
        vpsLocationSource.unbind()
        vpsLocationSource.deinit()

        // Ensure screen wake lock is disabled when fragment is destroyed
        if (isScreenWakeLockEnabled) {
            requireActivity().window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            isScreenWakeLockEnabled = false
        }

        mapView.onDestroy()
        backgroundScanHint?.dismiss()

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

        when (vpsLocationSource.state) {
            State.ACCURATE_POSITIONING ->
                toggleNextUserTrackingMode()
            State.DEGRADED_POSITIONING ->
                if (rescanSuggested) {
                    toggleNextUserTrackingMode()
                } else {
                    rescanSuggested = true

                    val message = "$impreciseMessage.\n\nThis alert will be shown only once. " +
                            "If you decide to scan later - click on camera button. " +
                            "We recommend you to scan again when you see warning icon on camera button"

                    lifecycleScope.launch {
                        runCatching {
                            AlertFactory.showSimpleAlert(
                                requireContext(), message, "You decided to scan later",
                                "Scan now", "Scan later"
                            )
                        }.onSuccess {
                            startScan()
                            enableFollowIfNotAlreadyEnabled()
                        }.onFailure {
                            toggleNextUserTrackingMode()
                            val text = "When you'll be ready to scan again - click on camera button"
                            Snackbar.make(mapView, text, Snackbar.LENGTH_SHORT).show()
                        }
                    }
                }
            else -> locateUser()
        }
    }

    private fun cameraButtonClicked() {
        val message = when (vpsLocationSource.state) {
            State.ACCURATE_POSITIONING -> "Do you think your position is inaccurate? Scan again"
            State.DEGRADED_POSITIONING -> impreciseMessage
            else -> null // should never happen by design
        }
        locateUser(message)
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
    }

    private fun stopScan() {
        vpsLocationSource.stopScan()
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

    private fun positioningLost(reason: WemapVPSARCoreLocationSource.NotPositioningReason) {
        locationManager.cameraMode = CameraMode.NONE
        haptic?.error()
        if (locationManager.lastCoordinate != null)
            locateUser("We lost your position. In order to relocalize you we will use your camera. $reason")
    }

    private val locationManagerListener by lazy {
        UserLocationManagerListener { error ->
            setErrorMessageAndStartTimer(error)
        }
    }

    private fun setErrorMessageAndStartTimer(error: Throwable) {

        if (error == WemapVPSARCoreLocationSourceError.slowConnectionDetected) {
            val text = "This is taking longer than expected. It looks like your internet connection is slow or unstable"
            return Snackbar.make(mapView, text, Snackbar.LENGTH_LONG).multiline().show()
        }

        binding.cameraDebugText.apply {
            isVisible = true
            text = error.message
        }
        errorTimerJob = lifecycleScope.launch {
            delay(1.seconds)
            binding.cameraDebugText.apply {
                isVisible = false
                text = ""
            }
        }
    }

    private val vpsListener by lazy {
        object: WemapVPSARCoreLocationSourceListener {
            override fun onScanStatusChanged(status: ScanStatus) {
                Log.d("WEMAP", "onScanStatusChanged. Status: ${status.name}")
                when (status) {
                    ScanStatus.STARTED -> {
                        binding.cameraLayout.visibility = View.VISIBLE
                        createScanningTimer()
                        updateScreenWakeLock()
                    }
                    ScanStatus.STOPPED -> {
                        binding.cameraLayout.visibility = View.INVISIBLE
                        scanningTimerJob?.cancel()
                        updateScreenWakeLock()
                    }
                }
            }

            override fun onStateChanged(state: State) {
                Log.d("WEMAP", "onStateChanged. State: ${state.name}")
                showBackgroundScanHintIfNeeded()

                binding.camera.isVisible = !state.isLost
                binding.degradedIcon.isVisible = state.isDegraded

                if (state.isLost)
                    positioningLost(vpsLocationSource.notPositioningReason)
            }

            override fun onBackgroundScanStatusChanged(status: ScanStatus) {
                Log.d("WEMAP", "onBackgroundScanStatusChanged. State: ${status.name}")
                showBackgroundScanHintIfNeeded()
            }

            override fun onLocalizedUser(coordinate: Coordinate, attitude: Attitude, backgroundScan: Boolean) {
                if (backgroundScan && !vpsLocationSource.state.isDegraded)
                    return
                haptic?.success()
            }

            override fun onTrackingStateChanged(reason: TrackingState) {
                println("Tracking state changed - $reason")
            }

            override fun onTrackingFailureReasonChanged(reason: TrackingFailureReason) {
                println("Tracking failure reason changed - $reason")
            }
        }
    }

    private fun showBackgroundScanHintIfNeeded() {
        if (vpsLocationSource.backgroundScanStatus.isStopped || !vpsLocationSource.state.isDegraded) {
            backgroundScanHint?.dismiss()
            return
        }

        if (backgroundScanHint != null)
            return

        val text = "Please hold your phone vertically in front of you to let system recognize your surroundings"
        backgroundScanHint = Snackbar.make(mapView, text, Snackbar.LENGTH_INDEFINITE).multiline()
            .onDismissed { backgroundScanHint = null }
            .apply { show() }
    }

    private fun createScanningTimer() {
        scanningTimerJob = lifecycleScope.launch {
            delay(20.seconds)
            askToContinue()
        }
    }

    private fun updateScreenWakeLock() {
        val shouldEnable = when (getAppState()) {
            AppState.NAVIGATION, AppState.SCANNING -> true
            else -> false
        }
        
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

        lifecycleScope.launch {
            runCatching {
                navigationManager.startNavigation(currentItinerary!!, navigationOptions)
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
        mapView.locationManager.cameraMode = CameraMode.TRACKING_COMPASS

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
            vpsLocationSource.scanStatus.isStarted -> AppState.SCANNING
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
        val requiredPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            listOf(permission.CAMERA, permission.ACTIVITY_RECOGNITION)
        else
            listOf(permission.CAMERA)

        permissionHelper = PermissionHelper(this, requiredPermissions)
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