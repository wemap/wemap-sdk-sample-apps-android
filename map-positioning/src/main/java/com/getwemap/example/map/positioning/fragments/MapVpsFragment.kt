package com.getwemap.example.map.positioning.fragments

import android.Manifest.permission
import android.annotation.SuppressLint
import android.graphics.Color
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
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.getwemap.example.common.AlertFactory
import com.getwemap.example.common.HapticGenerator
import com.getwemap.example.common.PermissionHelper
import com.getwemap.example.common.map.GlobalOptions
import com.getwemap.example.common.map.SessionViewModel
import com.getwemap.example.common.multiline
import com.getwemap.example.common.onDismissed
import com.getwemap.example.common.setSurfaceVisible
import com.getwemap.example.map.positioning.AppConstants
import com.getwemap.example.map.positioning.Config
import com.getwemap.example.map.positioning.R
import com.getwemap.example.map.positioning.databinding.FragmentMapVpsBinding
import com.getwemap.sdk.core.awaitLoaded
import com.getwemap.sdk.core.model.entities.Coordinate
import com.getwemap.sdk.core.model.entities.Itinerary
import com.getwemap.sdk.core.model.entities.PointOfInterest
import com.getwemap.sdk.core.model.services.ItinerarySearchRules
import com.getwemap.sdk.core.navigation.info.NavigationInfo
import com.getwemap.sdk.core.navigation.manager.NavigationEvent
import com.getwemap.sdk.map.MapSession
import com.getwemap.sdk.map.WemapMapView
import com.getwemap.sdk.map.itineraries.ItineraryManager
import com.getwemap.sdk.map.itineraries.ItineraryOptions
import com.getwemap.sdk.map.itineraries.LineOptions
import com.getwemap.sdk.map.location.UserLocationManager
import com.getwemap.sdk.map.navigation.MapNavigationManager
import com.getwemap.sdk.map.poi.MapPointOfInterestManager
import com.getwemap.sdk.positioning.wemapvpsarcore.VpsARCoreLocationSource
import com.getwemap.sdk.positioning.wemapvpsarcore.VpsARCoreLocationSource.ScanStatus
import com.getwemap.sdk.positioning.wemapvpsarcore.VpsARCoreLocationSource.State
import com.getwemap.sdk.positioning.wemapvpsarcore.VpsARCoreLocationSourceError
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import org.maplibre.android.MapLibre
import org.maplibre.android.location.OnCameraTrackingChangedListener
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.location.modes.RenderMode
import org.maplibre.android.maps.MapLibreMap
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.time.Duration.Companion.seconds

@SuppressLint("MissingPermission")
class MapVpsFragment : Fragment() {

    enum class AppState { BROWSING, POI_SELECTED, ITINERARY, NAVIGATION, SCANNING }

    private var _binding: FragmentMapVpsBinding? = null
    private val binding get() = _binding!!

    private val applicationContext get() = requireContext().applicationContext
    private val mapView get() = binding.mapView
    private val pointOfInterestManager: MapPointOfInterestManager get() = mapView.pointOfInterestManager
    private val navigationManager: MapNavigationManager get() = mapView.navigationManager
    private val itineraryManager: ItineraryManager get() = mapView.itineraryManager
    private val locationManager: UserLocationManager get() = mapView.locationManager

    private val currentItinerary: Itinerary? get() = mapView.itineraryManager.drawnItineraries.firstOrNull()
    private val sessionViewModel: SessionViewModel by activityViewModels()
    private lateinit var permissionHelper: PermissionHelper
    private lateinit var session: MapSession
    private lateinit var vpsLocationSource: VpsARCoreLocationSource

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

        session = sessionViewModel.session!!
        mapView.configure(session, Config.makeMapViewConfig(requireContext()))

        // Create location source
        vpsLocationSource = VpsARCoreLocationSource(applicationContext, session, Config.makeVpsConfig(requireContext()))
        // Bind camera view to location source
        vpsLocationSource.bind(applicationContext, binding.surfaceView)
        makeCameraVisible(false)

        // to prevent interactions with MapView before it's loaded
        binding.locateMe.isEnabled = false

        lifecycleScope.launch {
            runCatching {
                mapView.awaitLoaded()
            }.onSuccess {
                onMapViewReady(it, it.map)
            }.onFailure { error ->
                val message = "Failed to load MapView with error - $error"
                Snackbar.make(mapView, message, Snackbar.LENGTH_LONG).show()
            }
        }

        binding.locateMe.setOnClickListener { locateMeButtonClicked() }
        binding.camera.setOnClickListener { cameraButtonClicked() }
        binding.stopScanButton.setOnClickListener { stopScan() }
        binding.itineraryCalculateButton.setOnClickListener { computeItinerariesToPoi() }
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

    fun onMapViewReady(mapView: WemapMapView, map: MapLibreMap) {
        // Observe VPS scan state and status changes
        observeVps()
        // Bind location source to the map to show the blue dot from VPS
        // This action can be done only when mapView is ready
        locationManager.locationSource = vpsLocationSource
        // It enables the blue dot orientation rendering
        locationManager.renderMode = RenderMode.COMPASS

        observeUserLocationManager()
        observePointOfInterestManager()
        observeNavigationManager()

        map.addOnMapClickListener {
            if (getAppState() == AppState.POI_SELECTED)
                pointOfInterestManager.unselectPoi()

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

        binding.levelsSwitcher.bind(mapView.buildingManager, viewLifecycleOwner.lifecycleScope)
        binding.locateMe.isEnabled = true

        // Moved clear of this screen's own bottom-trailing button stack, which would otherwise sit on top of
        // the attribution. The SDK's corner (bottom-trailing, matching iOS) is right for a screen without one.
        map.uiSettings.attributionGravity = Gravity.START or Gravity.BOTTOM
    }

    override fun onStop() {
        super.onStop()
        errorTimerJob?.cancel()
    }

    override fun onDestroyView() {
        super.onDestroyView()


        vpsLocationSource.unbind()
        vpsLocationSource.deinit()

        // Ensure screen wake lock is disabled when fragment is destroyed
        if (isScreenWakeLockEnabled) {
            requireActivity().window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            isScreenWakeLockEnabled = false
        }

        backgroundScanHint?.dismiss()

        _binding = null

        System.gc()
    }
    // endregion Lifecycle

    // region Location
    private fun locateMeButtonClicked() {
        if (!locationManager.isEnabled) {
            locateUser()
            return
        }

        when (vpsLocationSource.state) {
            is State.AccuratePositioning ->
                toggleNextUserTrackingMode()
            is State.DegradedPositioning ->
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
            is State.AccuratePositioning -> "Do you think your position is inaccurate? Scan again"
            is State.DegradedPositioning -> impreciseMessage
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

    private fun makeCameraVisible(visible: Boolean) {
        binding.cameraLayout.visibility = if (visible) View.VISIBLE else View.INVISIBLE
        binding.surfaceView.setSurfaceVisible(visible)
        // Hiding the map lets it release its render surface while the camera covers it: an INVISIBLE
        // SurfaceView releases its surface, which stops MapLibre's render loop. A visible one keeps drawing
        // at full rate behind an opaque feed, for nobody.
        binding.mapLayout.visibility = if (visible) View.INVISIBLE else View.VISIBLE
    }

    private fun updateLocateMeButtonIcon() {
        val iconId: Int = when (locationManager.cameraMode) {
            CameraMode.TRACKING -> R.drawable.baseline_my_location_24
            CameraMode.TRACKING_COMPASS -> R.drawable.explore_24px
            else /* NONE */ -> R.drawable.location_searching_24px
        }
        binding.locateMe.setImageDrawable(ContextCompat.getDrawable(requireContext(), iconId))
    }

    private suspend fun askForScan(message: String) {
        return AlertFactory.showSimpleAlert(
            requireContext(), message, "User refused to open camera", "Open camera"
        )
    }

    private fun positioningLost(reason: VpsARCoreLocationSource.NotPositioningReason) {
        // use this if you want to hide blue dot completely instead of having last known position visible.
        // blue dot becomes gray be default when tracking is lost
//        locationManager.isEnabled = false

        locationManager.cameraMode = CameraMode.NONE
        haptic?.error()
        if (locationManager.lastCoordinate != null)
            locateUser("We lost your position. In order to relocalize you we will use your camera. $reason")
    }

    private fun observeUserLocationManager() {
        viewLifecycleOwner.lifecycleScope.launch {
            locationManager.errors.collect { error ->
                setErrorMessageAndStartTimer(error)
            }
        }
    }

    private fun setErrorMessageAndStartTimer(error: Throwable) {

        if (error is VpsARCoreLocationSourceError.SlowConnectionDetected) {
            val text = "This is taking longer than expected. It looks like your internet connection is slow or unstable"
            return Snackbar.make(mapView, text, Snackbar.LENGTH_LONG).multiline().show()
        }

        binding.cameraDebugText.apply {
            isVisible = true
            text = error.message
        }
        // Cancel first: VPS reports TiltTooHigh once per frame while the phone points down, so without this a
        // fresh job stacks up at camera rate and each one races the next to clear the label.
        errorTimerJob?.cancel()
        errorTimerJob = lifecycleScope.launch {
            delay(1.seconds)
            binding.cameraDebugText.apply {
                isVisible = false
                text = ""
            }
        }
    }

    private fun observeVps() {
        viewLifecycleOwner.lifecycleScope.launch {
            launch {
                vpsLocationSource.scanStatuses.collect { status ->
                    Log.d("WEMAP", "onScanStatusChanged. Status: $status")
                    when (status) {
                        ScanStatus.STARTED -> {
                            makeCameraVisible(true)
                            createScanningTimer()
                            updateScreenWakeLock()
                        }
                        ScanStatus.STOPPED -> {
                            makeCameraVisible(false)
                            scanningTimerJob?.cancel()
                            updateScreenWakeLock()
                        }
                    }
                }
            }
            launch {
                vpsLocationSource.states.drop(1).collect { state ->
                    Log.d("WEMAP", "onStateChanged. State: $state")
                    showBackgroundScanHintIfNeeded()

                    binding.camera.isVisible = !state.isLost
                    binding.degradedIcon.isVisible = state.isDegraded

                    if (state is State.NotPositioning)
                        positioningLost(state.reason)
                }
            }
            launch {
                vpsLocationSource.backgroundScanStatuses.collect { status ->
                    Log.d("WEMAP", "onBackgroundScanStatusChanged. State: $status")
                    showBackgroundScanHintIfNeeded()
                }
            }
            launch {
                vpsLocationSource.userLocalizationUpdates.collect { update ->
                    if (update.backgroundScan && !vpsLocationSource.state.isDegraded)
                        return@collect
                    haptic?.success()
                }
            }
            launch {
                vpsLocationSource.cameraTrackingStates.collect { camera ->
                    println("Tracking state changed - ${camera.trackingState}")
                    println("Tracking failure reason changed - ${camera.trackingFailureReason}")
                }
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
    // endregion Location

    // region PoIs
    private fun renderPoI(poi: PointOfInterest) {
        hideAllStatesUI()
        binding.poiContainer.visibility = View.VISIBLE
        binding.poiInfo.text = poi.name
    }

    private fun observePointOfInterestManager() {
        viewLifecycleOwner.lifecycleScope.launch {
            pointOfInterestManager.selectionUpdates.collect { update ->
                if (update.unselected.isNotEmpty())
                    hideAllStatesUI()
                update.selected.forEach { renderPoI(it) }
            }
        }
    }
    // endregion PoIs

    // region Itinerary
    private fun computeItinerariesToPoi() {
        val selectedPoi = pointOfInterestManager.getSelectedPoi()
        if (selectedPoi == null) {
            Log.e("WEMAP", "Can't compute itineraries, there is no selected POI")
            return
        }

        val origin = locationManager.lastCoordinate
        if (origin != null) {
            calculateAndDrawItinerary(origin, selectedPoi.coordinate)
            return
        }

        lifecycleScope.launch {
            runCatching {
                val text = "We need to know your location to compute the best route. We will use your camera to localize you"
                checkLocationSource(text)

                startScan()

                withTimeout(20.seconds) {
                    locationManager.coordinates.first()
                }
            }.onSuccess {
                calculateAndDrawItinerary(it, selectedPoi.coordinate)
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
                itineraryManager.computeItineraries(origin, destination, searchRules = searchRules)
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

        val currentPoi = pointOfInterestManager.getSelectedPoi()!!
        binding.itineraryInfo.text = "Itinerary from user position to ${currentPoi.name}\n" +
                "Distance: ${itinerary.distance.toInt()}m\n" +
                "Duration: ${itinerary.duration.toInt()}s"
    }

    private fun onItineraryCloseClick() {
        if (itineraryManager.removeItinerary(currentItinerary!!) == null)
            return Snackbar.make(mapView, "Failed to remove itinerary", Snackbar.LENGTH_SHORT).show()

        pointOfInterestManager.isUserSelectionEnabled = true

        val selectedPoI = pointOfInterestManager.getSelectedPoi()
        if (selectedPoI == null)
            hideAllStatesUI()
        else
            renderPoI(selectedPoI)
    }
    // endregion Itinerary

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

    private fun observeNavigationManager() {
        viewLifecycleOwner.lifecycleScope.launch {
            launch {
                navigationManager.navigationInfoUpdates.collect { info ->
                    updateNavInfo(info)
                }
            }
            launch {
                navigationManager.navigationEvents.collect { event ->
                    when (event) {
                        is NavigationEvent.Stopped -> {
                            renderItinerary(event.navigation.itinerary)
                            Snackbar.make(mapView, "Navigation stopped", Snackbar.LENGTH_SHORT).show()
                            updateScreenWakeLock()
                        }
                        is NavigationEvent.Arrived -> {
                            Snackbar.make(mapView, "You arrived to destination", Snackbar.LENGTH_SHORT).show()
                            updateScreenWakeLock()
                        }
                        is NavigationEvent.Recalculated -> {
                            val text = "Navigation recalculated - ${event.navigation}"
                            Snackbar.make(mapView, text, Snackbar.LENGTH_SHORT).multiline().show()
                        }
                        is NavigationEvent.Started -> Unit
                    }
                }
            }
            launch {
                navigationManager.errors.collect { error ->
                    currentItinerary?.let { renderItinerary(it) }
                    val text = "Navigation failed with error - $error"
                    Snackbar.make(mapView, text, Snackbar.LENGTH_SHORT).multiline().show()
                    updateScreenWakeLock()
                }
            }
        }
    }
    // endregion Navigation

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
            pointOfInterestManager.getSelectedPoi() != null -> AppState.POI_SELECTED
            else -> AppState.BROWSING
        }
    }

    private fun handleBackPressed() {
        if (!mapView.loadPhase.isReady) {
            findNavController().navigateUp()
            return
        }

        when (getAppState()) {
            AppState.SCANNING -> stopScan()
            AppState.NAVIGATION -> onStopNavigationClick()
            AppState.ITINERARY -> onItineraryCloseClick()
            AppState.POI_SELECTED -> pointOfInterestManager.unselectPoi()
            // Navigate back to the previous fragment
            else -> findNavController().navigateUp()
        }
    }
    // endregion Misc

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
    // endregion Permissions
}