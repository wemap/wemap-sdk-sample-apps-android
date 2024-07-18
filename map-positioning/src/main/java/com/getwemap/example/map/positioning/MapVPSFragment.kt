package com.getwemap.example.map.positioning

import android.Manifest.permission
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import com.getwemap.example.map.positioning.databinding.FragmentMapVpsBinding
import com.getwemap.sdk.core.model.entities.Coordinate
import com.getwemap.sdk.positioning.wemapvpsarcore.WemapVPSARCoreLocationSource
import com.getwemap.sdk.positioning.wemapvpsarcore.WemapVPSARCoreLocationSource.ScanStatus
import com.getwemap.sdk.positioning.wemapvpsarcore.WemapVPSARCoreLocationSource.State
import com.getwemap.sdk.positioning.wemapvpsarcore.WemapVPSARCoreLocationSourceError
import com.getwemap.sdk.positioning.wemapvpsarcore.WemapVPSARCoreLocationSourceListener
import com.google.android.material.snackbar.Snackbar
import com.google.ar.core.TrackingFailureReason
import com.google.gson.JsonArray
import com.mapbox.mapboxsdk.Mapbox
import com.mapbox.mapboxsdk.location.modes.CameraMode
import com.mapbox.mapboxsdk.location.modes.RenderMode
import com.mapbox.mapboxsdk.plugins.annotation.Circle
import com.mapbox.mapboxsdk.plugins.annotation.CircleManager
import com.mapbox.mapboxsdk.plugins.annotation.CircleOptions

class MapVPSFragment : BaseFragment() {

    override val mapView get() = binding.mapView
    override val levelToggle get() = binding.levelToggle

    private val surfaceView get() = binding.surfaceView

    private val buttonStartNavigation get() = binding.startNavigation
    private val buttonStopNavigation get() = binding.stopNavigation
    private val buttonStartNavigationFromUserCreatedAnnotations get() = binding.startNavigationFromUserCreatedAnnotations
    private val buttonRemoveUserCreatedAnnotations get() = binding.removeUserCreatedAnnotations

    private val navigationManager get() = mapView.navigationManager

    private val userCreatedAnnotations: MutableList<Circle> = mutableListOf()

    private var rescanRequested = false

    private lateinit var binding: FragmentMapVpsBinding
    private lateinit var circleManager: CircleManager

    private val vpsLocationSource by lazy {
        WemapVPSARCoreLocationSource(requireContext(), Constants.vpsEndpoint)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        Mapbox.getInstance(requireContext())
        binding = FragmentMapVpsBinding.inflate(inflater, container, false)
        vpsLocationSource.bind(surfaceView) // you can't create GLSurfaceView and set renderer later
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        mapView.getWemapMapAsync { _, map, _ ->

            circleManager = CircleManager(mapView, map, map.style!!)

            map.addOnMapLongClickListener {
                if (userCreatedAnnotations.size >= 2) {
                    Snackbar.make(mapView,
                        "You already created 2 annotations. Remove old ones to be able to add new",
                        Snackbar.LENGTH_LONG).show()
                    return@addOnMapLongClickListener false
                }

                val array = JsonArray()
                if (focusedBuilding != null && focusedBuilding!!.boundingBox.contains(it))
                    array.add(focusedBuilding!!.activeLevel.id)

                val options = CircleOptions()
                    .withLatLng(it)
                    .withData(array)

                val point = circleManager.create(options)
                userCreatedAnnotations.add(point)
                updateUI()

                return@addOnMapLongClickListener true
            }
        }

        binding.startScanButton.setOnClickListener { vpsLocationSource.startScan() }
        binding.stopScanButton.setOnClickListener { vpsLocationSource.stopScan() }

        binding.rescanButton.setOnClickListener {
            rescanRequested = true
            hideMapShowCamera()
        }

        buttonStartNavigation.setOnClickListener {
            startNavigation()
        }

        buttonStopNavigation.setOnClickListener {
            stopNavigation()
        }

        buttonStartNavigationFromUserCreatedAnnotations.setOnClickListener {
            startNavigationFromUserCreatedAnnotations()
        }

        buttonRemoveUserCreatedAnnotations.setOnClickListener {
            removeUserCreatedAnnotations()
        }
    }

    private fun startNavigation() {
        startNavigation(null, getDestinationCoordinate())
    }

    private fun stopNavigation() {
        navigationManager
            .stopNavigation()
            .fold(
                {
                    buttonStopNavigation.isEnabled = false
                    updateUI()
                }, {
                    Snackbar.make(mapView, "Failed to stop navigation with error - $it", Snackbar.LENGTH_LONG)
                        .show()
                }
            )
    }

    private fun startNavigationFromUserCreatedAnnotations() {
        val origin = getOriginCoordinate()
        val destination = getDestinationCoordinate()

        startNavigation(origin, destination)
    }

    private fun startNavigation(origin: Coordinate?, destination: Coordinate) {
        disableStartButtons()

        val disposable = navigationManager
            .startNavigation(origin, destination)
            .subscribe({
                buttonStopNavigation.isEnabled = true
            }, {
                Snackbar.make(mapView, "Failed to start navigation with error - $it", Snackbar.LENGTH_LONG).show()
                updateUI()
            })

        disposeBag.add(disposable)
    }

    private fun removeUserCreatedAnnotations() {
        circleManager.delete(userCreatedAnnotations)
        userCreatedAnnotations.clear()
        updateUI()
    }

    private fun updateUI() {
        buttonStartNavigation.isEnabled = userCreatedAnnotations.size == 1 && !buttonStopNavigation.isEnabled
        buttonStartNavigationFromUserCreatedAnnotations.isEnabled = userCreatedAnnotations.size == 2 && !buttonStopNavigation.isEnabled
        buttonRemoveUserCreatedAnnotations.isEnabled = userCreatedAnnotations.isNotEmpty()
    }

    private fun disableStartButtons() {
        buttonStartNavigation.isEnabled = false
        buttonStartNavigationFromUserCreatedAnnotations.isEnabled = false
    }

    private fun getLevelFromAnnotation(annotation: Circle): List<Float> {
        return annotation.data!!.asJsonArray.map { it.asFloat }
    }

    private fun getDestinationCoordinate(): Coordinate {
        return getCoordinateFrom(userCreatedAnnotations.first())
    }

    private fun getOriginCoordinate(): Coordinate {
        return getCoordinateFrom(userCreatedAnnotations[1])
    }

    private fun getCoordinateFrom(annotation: Circle): Coordinate {
        val to = annotation.latLng
        return Coordinate(to.latitude, to.longitude, getLevelFromAnnotation(annotation))
    }

    override fun checkPermissionsAndSetupLocationSource() {
        val permissionsAccepted = checkGPSPermission() && checkCameraPermission()
        if (!permissionsAccepted) return
        setupLocationSource()
    }

    private fun checkCameraPermission(): Boolean {
        return if (ContextCompat.checkSelfPermission(
                requireContext(),
                permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            activityResultLauncher.launch(permission.CAMERA)
            false
        } else {
            true
        }
    }

    @SuppressLint("MissingPermission")
    override fun setupLocationSource() {
        vpsLocationSource.listeners.add(vpsListener)

        mapView.locationManager.apply {
            source = vpsLocationSource
            isEnabled = true
        }
        mapView.map.locationComponent.apply {
            cameraMode = CameraMode.TRACKING_COMPASS
            renderMode = RenderMode.COMPASS
        }
    }

    private fun hideMapShowCamera() {
        binding.mapLayout.visibility = View.INVISIBLE
        binding.cameraLayout.visibility = View.VISIBLE
        binding.scanButtons.isEnabled = true
    }

    override fun onStop() {
        super.onStop()
        vpsLocationSource.stop()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        vpsLocationSource.unbind()
    }

    private fun updateScanButtonsState(status: ScanStatus) {
        val isScanning = status == ScanStatus.STARTED
        binding.startScanButton.isEnabled = !isScanning
        binding.stopScanButton.isEnabled = isScanning
    }

    private fun showMapHideCamera() {
        binding.mapLayout.visibility = View.VISIBLE
        binding.cameraLayout.visibility = View.INVISIBLE
        binding.scanButtons.isEnabled = true

        mapView.map.locationComponent.apply {
            cameraMode = CameraMode.TRACKING_COMPASS
            renderMode = RenderMode.COMPASS
        }
    }

    private val vpsListener by lazy {
        object : WemapVPSARCoreLocationSourceListener {

            override fun onScanStatusChanged(status: ScanStatus) {
                binding.debugTextTitle.text = "Scan status - $status"
                updateScanButtonsState(status)
                if (status == ScanStatus.STOPPED && vpsLocationSource.state == State.NORMAL) {
                    showMapHideCamera()
                }
            }

            override fun onStateChanged(state: State) {
                println("state -> $state")
                when(state) {
                    State.NORMAL -> {
                        if (rescanRequested) return
                        showMapHideCamera()
                    }
                    State.SCAN_REQUIRED -> {
                        rescanRequested = false
                        hideMapShowCamera()
                    }
                    State.NO_TRACKING -> {
                        binding.scanButtons.isEnabled = false
                    }
                }
            }

            override fun onTrackingFailureReasonChanged(reason: TrackingFailureReason) {
                binding.debugTextMessage.text = "Tracking failure reason - $reason"
            }

            override fun onError(error: WemapVPSARCoreLocationSourceError) {
                binding.debugTextMessage.text = "Error occurred - $error"
            }
        }
    }
}