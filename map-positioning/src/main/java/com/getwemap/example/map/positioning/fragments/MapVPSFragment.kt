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
import androidx.navigation.fragment.findNavController
import com.getwemap.example.common.AlertFactory
import com.getwemap.example.common.PermissionHelper
import com.getwemap.example.map.positioning.R
import com.getwemap.example.map.positioning.databinding.FragmentMapVpsBinding
import com.getwemap.sdk.core.internal.extensions.disposedBy
import com.getwemap.sdk.core.model.entities.Coordinate
import com.getwemap.sdk.core.model.entities.Itinerary
import com.getwemap.sdk.core.model.entities.MapData
import com.getwemap.sdk.core.model.entities.PointOfInterest
import com.getwemap.sdk.core.navigation.info.NavigationInfo
import com.getwemap.sdk.core.navigation.manager.NavigationManagerListener
import com.getwemap.sdk.core.poi.PointOfInterestManagerListener
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
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.core.Observable
import io.reactivex.rxjava3.core.Single
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.disposables.SerialDisposable
import kotlinx.serialization.json.Json
import org.maplibre.android.MapLibre
import org.maplibre.android.location.OnCameraTrackingChangedListener
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.location.modes.RenderMode
import java.util.concurrent.TimeUnit

@SuppressLint("MissingPermission")
class MapVPSFragment : Fragment() {

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

    private val disposeBag = CompositeDisposable()
    private lateinit var permissionHelper: PermissionHelper
    private lateinit var vpsLocationSource: WemapVPSARCoreLocationSource
    private var scanningTimer = SerialDisposable()
    private var errorTimer = SerialDisposable()
    private var rescanSuggested = false
    private var isScreenWakeLockEnabled = false
    private val impreciseMessage = "Your location seems imprecise, you can scan again to refine your position if necessary"

    // region ------ Lifecycle ------
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
        // Register listeners for VPS scan state and status change
        vpsLocationSource.vpsListeners.add(vpsListener)

        // to prevent interactions with MapView before it's loaded
        binding.locateMe.isEnabled = false

        mapView.getMapViewAsync { _, _, _, _ ->
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
        disposeBag.clear()
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

        disposeBag.clear()
        disposeBag.dispose()
        mapView.onDestroy()

        _binding = null

        System.gc()
    }
    // endregion ------ Lifecycle ------

    // region ------ Location ------
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

                    AlertFactory
                        .showSimpleAlert(
                            requireContext(), message,
                            "You decided to scan later",
                            "Scan now", "Scan later"
                        ).subscribe(
                            {
                                startScan()
                                enableFollowIfNotAlreadyEnabled()
                            }, {
                                toggleNextUserTrackingMode()
                                val text = "When you'll be ready to scan again - click on camera button"
                                Snackbar.make(mapView, text, Snackbar.LENGTH_SHORT).show()
                            }
                        ).disposedBy(disposeBag)
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
        checkLocationSource(message)
            .subscribe(
                {
                    startScan()
                    enableFollowIfNotAlreadyEnabled()
                }, {
                    Snackbar.make(mapView, it.message ?: "Failed to locate you", Snackbar.LENGTH_SHORT).show()
                }
            ).disposedBy(disposeBag)
    }


    private fun checkLocationSource(message: String): Single<Unit> {
        return checkPermissions()
            .flatMap {
                askForScan(message)
            }.flatMap {
                startLocationSource()
            }
    }

    private fun startLocationSource(): Single<Unit> {
        locationManager.isEnabled = true
        return Single.just(Unit)
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

    private fun askForScan(message: String): Single<Unit> {
        return AlertFactory.showSimpleAlert(requireContext(), message, "User refused to open camera", "Open camera")
    }

    private fun positioningLost() {
        locationManager.cameraMode = CameraMode.NONE
        if (locationManager.lastCoordinate != null)
            locateUser("We lost your position. In order to relocalize you we will use your camera")
    }

    private val locationManagerListener by lazy {
        UserLocationManagerListener { error ->
            setErrorMessageAndStartTimer(error)
        }
    }

    private fun setErrorMessageAndStartTimer(error: Throwable) {

        if (error == WemapVPSARCoreLocationSourceError.slowConnectionDetected) {
            val text = "This is taking longer than expected. It looks like your internet connection is slow or unstable"
            return Snackbar.make(mapView, text, Snackbar.LENGTH_LONG).show()
        }

        binding.cameraDebugText.apply {
            isVisible = true
            text = error.message
        }
        val timer = Observable
            .timer(1, TimeUnit.SECONDS, AndroidSchedulers.mainThread())
            .subscribe {
                binding.cameraDebugText.apply {
                    isVisible = false
                    text = ""
                }
            }
        errorTimer.set(timer)
    }

    private val vpsListener by lazy {
        WemapVPSARCoreLocationSourceListener(
            { status ->
                Log.d("WEMAP", "onScanStatusChanged. Status: ${status.name}")
                when (status) {
                    ScanStatus.STARTED -> {
                        binding.cameraLayout.visibility = View.VISIBLE
                        createScanningTimer()
                        updateScreenWakeLock()
                    }
                    ScanStatus.STOPPED -> {
                        binding.cameraLayout.visibility = View.INVISIBLE
                        scanningTimer.set(null)
                        updateScreenWakeLock()
                    }
                }
            },
            { state ->
                Log.d("WEMAP", "onStateChanged. State: ${state.name}")

                locationManager.isEnabled = !state.isLost
                binding.camera.isVisible = !state.isLost
                binding.degradedIcon.isVisible = state.isDegraded

                if (state.isLost)
                    positioningLost()
            }
        )
    }

    private fun createScanningTimer() {
        val timer = Observable
            .timer(20, TimeUnit.SECONDS, AndroidSchedulers.mainThread())
            .subscribe {
                askToContinue()
            }
        scanningTimer.set(timer)
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
        val continueAlert = AlertFactory
            .showSimpleAlert(
                requireContext(), "We cannot localize you. Do you want to continue to try?",
                "You decided to get back to the map", "Continue", "Back to map"
            ).subscribe({
                createScanningTimer()
            }, {
                stopScan()
                Snackbar.make(mapView, "Failed to localize you in reasonable time. Try again later", Snackbar.LENGTH_LONG).show()
            })
        scanningTimer.set(continueAlert)

    }
    // endregion ------ Location ------

    // region ------ PoIs ------
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
    // endregion ------ PoIs ------

    // region ------ Itinerary ------
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

        checkLocationSource("We need to know your location to compute the best route. We will use your camera to localize you")
            .doOnSuccess {
                startScan()
            }
            .flatMap {
                locationManager.coordinate
                    .take(1).firstOrError()
                    .timeout(20, TimeUnit.SECONDS, AndroidSchedulers.mainThread())
            }
            .subscribe({
                calculateAndDrawItinerary(it, selectedPOI.coordinate)
            }, {
                Snackbar.make(mapView, "Failed to start itinerary with error - ${it.message}", Snackbar.LENGTH_SHORT).show()
            })
            .disposedBy(disposeBag)
    }

    private fun calculateAndDrawItinerary(origin: Coordinate, destination: Coordinate) {
        itineraryManager
            .getItineraries(origin, destination)
            .subscribe(
                { itineraries ->
                    renderItinerary(itineraries.first())
                },
                { error ->
                    Snackbar.make(mapView, "Failed to compute itineraries with error - $error", Snackbar.LENGTH_SHORT).show()
                }
            ).disposedBy(disposeBag)
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
    // endregion ------ Itinerary ------

    // region ------ Navigation ------
    private fun onStartNavigationClick() {
        navigationManager
            .startNavigation(currentItinerary!!)
            .subscribe(
                {
                    renderNavigation()
                    updateScreenWakeLock()
                }, {
                    Snackbar.make(mapView, "Failed to start navigation with error - $it", Snackbar.LENGTH_SHORT).show()
                }
            ).disposedBy(disposeBag)
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
            Snackbar.make(mapView, "Failed to stop navigation with error - $it", Snackbar.LENGTH_SHORT).show()
        }
        updateScreenWakeLock()
    }

    private val navigationManagerListener by lazy {
        NavigationManagerListener(
            onInfoChanged = {
                updateNavInfo(it)
            }, onStopped = {
                renderItinerary(it.itinerary)
                Snackbar.make(mapView, "Navigation stopped", Snackbar.LENGTH_SHORT).show()
                updateScreenWakeLock()
            }, onArrived = {
                Snackbar.make(mapView, "You arrived to destination", Snackbar.LENGTH_SHORT).show()
                updateScreenWakeLock()
            }, onFailed = { error ->
                currentItinerary?.let { renderItinerary(it) }
                Snackbar.make(mapView, "Navigation failed with error - $error", Snackbar.LENGTH_SHORT).show()
                updateScreenWakeLock()
            }, onRecalculated = {
                Snackbar.make(mapView, "Navigation recalculated - $it", Snackbar.LENGTH_SHORT).show()
            }
        )
    }
    // endregion ------ Navigation ------

    // region ------ Misc ------
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
    // endregion ------ Others ------

    // region ------ Permissions ------
    private fun createPermissionsHelper() {
        val requiredPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            listOf(permission.CAMERA, permission.ACTIVITY_RECOGNITION)
        else
            listOf(permission.CAMERA)

        permissionHelper = PermissionHelper(this, requiredPermissions)
    }

    private fun checkPermissions(): Single<Unit> {
        if (permissionHelper.allGranted()) {
            return Single.just(Unit)
        }

        return AlertFactory.showSimpleAlert(
            requireContext(),
            "In order to be localized, we will use your camera. Please accept following permissions",
            "User refused to review permissions"
        ).flatMap {
            requestPermissions()
        }
    }

    private fun requestPermissions(): Single<Unit> {
        return Single.create { emitter ->
            permissionHelper
                .request { granted, denied ->
                    if (denied.isEmpty())
                        emitter.onSuccess(Unit)
                    else
                        emitter.onError(Throwable("User denied required permissions"))
                }
        }
    }
    // endregion ------ Permissions ------
}