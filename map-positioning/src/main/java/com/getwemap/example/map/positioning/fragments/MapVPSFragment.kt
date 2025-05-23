package com.getwemap.example.map.positioning.fragments

import android.Manifest.permission
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import com.getwemap.example.map.positioning.databinding.FragmentMapVpsBinding
import com.getwemap.sdk.core.internal.extensions.disposedBy
import com.getwemap.sdk.core.model.entities.Coordinate
import com.getwemap.sdk.map.WemapMapView
import com.getwemap.sdk.map.location.UserLocationManagerListener
import com.getwemap.sdk.positioning.wemapvpsarcore.WemapVPSARCoreLocationSource
import com.getwemap.sdk.positioning.wemapvpsarcore.WemapVPSARCoreLocationSource.ScanStatus
import com.getwemap.sdk.positioning.wemapvpsarcore.WemapVPSARCoreLocationSource.State
import com.getwemap.sdk.positioning.wemapvpsarcore.WemapVPSARCoreLocationSourceListener
import com.getwemap.sdk.positioning.wemapvpsarcore.WemapVPSARCoreLocationSourceObserver
import com.google.android.material.snackbar.Snackbar
import com.google.ar.core.TrackingFailureReason
import com.google.gson.JsonArray
import kotlinx.serialization.json.Json
import org.maplibre.android.MapLibre
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.location.modes.RenderMode
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.plugins.annotation.Circle
import org.maplibre.android.plugins.annotation.CircleManager
import org.maplibre.android.plugins.annotation.CircleOptions

class MapVPSFragment : BaseFragment() {

    override val mapView get() = binding.mapView
    override val levelsSwitcher get() = binding.levelsSwitcher

    private val surfaceView get() = binding.surfaceView

    private val buttonStartNavigation get() = binding.startNavigation
    private val buttonStopNavigation get() = binding.stopNavigation
    private val buttonStartNavigationFromUserCreatedAnnotations get() = binding.startNavigationFromUserCreatedAnnotations
    private val buttonRemoveUserCreatedAnnotations get() = binding.removeUserCreatedAnnotations

    private val navigationManager get() = mapView.navigationManager

    private val userCreatedAnnotations: MutableList<Circle> = mutableListOf()

    private var rescanRequested = false

    private var _binding: FragmentMapVpsBinding? = null
    private val binding get() = _binding!!

    private var _circleManager: CircleManager? = null
    private val circleManager get() = _circleManager!!

    private lateinit var vpsLocationSource: WemapVPSARCoreLocationSource

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        MapLibre.getInstance(requireContext())
        _binding = FragmentMapVpsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val mapDataString = requireArguments().getString("mapData")!!
        mapData = Json.decodeFromString(mapDataString)
        vpsLocationSource = WemapVPSARCoreLocationSource(requireContext(), mapData)
        vpsLocationSource.bind(requireContext(), surfaceView)

        mapView.getMapViewAsync { mapView, map, style, _ ->
            createCircleManager(mapView, map, style)
        }

        binding.startScanButton.setOnClickListener { vpsLocationSource.startScan() }
        binding.stopScanButton.setOnClickListener { vpsLocationSource.stopScan() }

        binding.rescanButton.setOnClickListener {
            rescanRequested = true
            makeCameraVisible(true)
        }

        buttonStartNavigation.setOnClickListener { startNavigation() }
        buttonStopNavigation.setOnClickListener { stopNavigation() }

        buttonStartNavigationFromUserCreatedAnnotations.setOnClickListener {
            startNavigationFromUserCreatedAnnotations()
        }

        buttonRemoveUserCreatedAnnotations.setOnClickListener {
            removeUserCreatedAnnotations()
        }

        makeCameraVisible(true)
    }

    private fun createCircleManager(mapView: WemapMapView, map: MapLibreMap, style: Style) {
        _circleManager = CircleManager(mapView, map, style)

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

        navigationManager
            .startNavigation(origin, destination)
            .subscribe({
                buttonStopNavigation.isEnabled = true
            }, {
                Snackbar.make(mapView, "Failed to start navigation with error - $it", Snackbar.LENGTH_LONG).show()
                updateUI()
            })
            .disposedBy(disposeBag)
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
                && if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) checkActivityPermission() else true
        if (!permissionsAccepted) return
        setupLocationSource()
    }

    private fun checkCameraPermission(): Boolean {
        return if (ContextCompat.checkSelfPermission(
                requireContext(),
                permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            activityResultLauncher.launch(permission.CAMERA)
            false
        } else
            true
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun checkActivityPermission(): Boolean {
        return if (ContextCompat.checkSelfPermission(
                requireContext(),
                permission.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED) {
            activityResultLauncher.launch(permission.ACTIVITY_RECOGNITION)
            false
        } else
            true
    }

    @SuppressLint("MissingPermission")
    override fun setupLocationSource() {
        vpsLocationSource.apply {
            vpsListeners.add(vpsListener)
            observers.add(vpsObserver)
        }
        mapView.locationManager.addListener(locationManagerListener)
        mapView.locationManager.apply {
            locationSource = vpsLocationSource
            isEnabled = true
            cameraMode = CameraMode.TRACKING_COMPASS
            renderMode = RenderMode.COMPASS
        }
    }

    private fun makeCameraVisible(cameraVisible: Boolean) {
        if (cameraVisible) {
            binding.apply {
                mapLayout.visibility = View.INVISIBLE
                // to avoid map view overlapping by surface view. required for old devices like Android 9
                surfaceView.translationX = 0f
                surfaceView.translationY = 0f
                cameraLayout.visibility = View.VISIBLE
                scanButtons.isEnabled = true
            }
        } else {
            binding.apply {
                mapLayout.visibility = View.VISIBLE
                cameraLayout.visibility = View.INVISIBLE
                // to avoid map view overlapping by surface view. required for old devices like Android 9
                surfaceView.translationX = 5000f
                surfaceView.translationY = 5000f
                scanButtons.isEnabled = true
            }
            mapView.locationManager.apply {
                cameraMode = CameraMode.TRACKING_COMPASS
                renderMode = RenderMode.COMPASS
            }
        }
    }

    override fun onStop() {
        super.onStop()
        vpsLocationSource.stop()
    }

    override fun onDestroyView() {
        mapView.locationManager.removeListener(locationManagerListener)
        _circleManager?.onDestroy()
        super.onDestroyView()
        vpsLocationSource.apply {
            vpsListeners.remove(vpsListener)
            observers.remove(vpsObserver)
            unbind()
        }
        _binding = null
    }

    private fun updateScanButtonsState(status: ScanStatus) {
        val isScanning = status == ScanStatus.STARTED
        binding.apply {
            startScanButton.isEnabled = !isScanning
            stopScanButton.isEnabled = isScanning
        }
    }

    private val vpsListener by lazy {
        object : WemapVPSARCoreLocationSourceListener {

            override fun onScanStatusChanged(status: ScanStatus) {
                binding.debugTextTitle.text = "Scan status - $status"
                updateScanButtonsState(status)
                if (status == ScanStatus.STOPPED && vpsLocationSource.state == State.ACCURATE_POSITIONING) {
                    makeCameraVisible(false)
                }
            }

            override fun onStateChanged(state: State) {
                println("state -> $state")
                when(state) {
                    State.ACCURATE_POSITIONING, State.DEGRADED_POSITIONING -> {
                        if (rescanRequested) return
                        makeCameraVisible(false)
                    }
                    State.NOT_POSITIONING -> {
                        rescanRequested = false
                        makeCameraVisible(true)
                    }
                }
            }

            override fun onNotPositioningReasonChanged(reason: WemapVPSARCoreLocationSource.NotPositioningReason) {
                binding.debugTextMessage.text = "Not positioning reason - $reason"
            }

            override fun onTrackingFailureReasonChanged(reason: TrackingFailureReason) {
                binding.debugTextMessage.text = "Tracking failure reason - $reason"
            }
        }
    }

    private val vpsObserver by lazy {
        object : WemapVPSARCoreLocationSourceObserver {
            override fun onImageSend(bitmap: Bitmap) {
                binding.imageview.setImageBitmap(bitmap)
            }

            override fun onMovementStateChanged(state: String) {
                binding.debugTextTitle.text = "Movement state changed: $state"
            }

            override fun onConveyingStateChanged(state: String) {
                binding.debugTextMessage2.text = "Conveying state changed: $state"
            }
        }
    }

    private val locationManagerListener by lazy {
        object : UserLocationManagerListener {
            override fun onError(error: Throwable) {
                binding.debugTextMessage2.text = "Error: ${error.message}"
            }
        }
    }
}