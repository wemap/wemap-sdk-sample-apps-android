package com.getwemap.example.map.fragments

import android.Manifest.permission.ACCESS_FINE_LOCATION
import android.Manifest.permission.BLUETOOTH_SCAN
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.getwemap.example.common.Constants
import com.getwemap.example.common.map.MapLevelsSwitcher
import com.getwemap.example.common.multiline
import com.getwemap.example.map.GareDeLyonSimulatorsLocationSource
import com.getwemap.sdk.core.location.LocationSource
import com.getwemap.sdk.core.location.simulation.SimulationOptions
import com.getwemap.sdk.core.location.simulation.SimulatorLocationSource
import com.getwemap.sdk.core.model.entities.MapData
import com.getwemap.sdk.map.WemapMapView
import com.getwemap.sdk.positioning.fusedgms.GmsFusedLocationSource
import com.getwemap.sdk.positioning.polestar.PolestarLocationSource
import com.google.android.material.snackbar.Snackbar
import io.reactivex.rxjava3.disposables.CompositeDisposable
import kotlinx.serialization.json.Json
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.location.modes.RenderMode

abstract class MapFragment : Fragment() {

    protected abstract val mapView: WemapMapView
    protected abstract val levelsSwitcher: MapLevelsSwitcher

    protected val disposeBag = CompositeDisposable()

    // also you can use simulator to generate locations along the itinerary
    protected val simulator by lazy { SimulatorLocationSource(SimulationOptions(deviationRange = -20.0..20.0)) }

    protected var locationSourceId: Int = -1

    protected val pointOfInterestManager get() = mapView.pointOfInterestManager
    protected val focusedBuilding get() = buildingManager.focusedBuilding

    private val buildingManager get() = mapView.buildingManager

    private val activityResultLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
        if (isGranted) {
            checkPermissionsAndSetupLocationSource()
        } else {
            val text = "In order to make sample app work properly you have to accept required permission"
            Snackbar.make(mapView, text, Snackbar.LENGTH_LONG).multiline().show()
        }
    }

//    private val maxBounds = LatLngBounds
//        .from(48.84811619854466, 2.377353558713054,
//            48.84045277048898, 2.371600716985739)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val args = requireArguments()
        locationSourceId = args.getInt("locationSourceId")

        val mapDataString = args.getString("mapData")!!
        val mapData: MapData = Json.decodeFromString(mapDataString)

        mapView.mapData = mapData
        // camera bounds can be specified even if they don't exist in MapData
//        mapView.cameraBounds = maxBounds
        mapView.onCreate(savedInstanceState)
        levelsSwitcher.bind(mapView)

        mapView.getMapViewAsync { _, _, _, _ ->
            checkPermissionsAndSetupLocationSource()
        }
    }

    private fun checkPermissionsAndSetupLocationSource() {
        val permissionsAccepted = when (locationSourceId) {
            0 -> true
            1, 3 -> checkGPSPermission() && checkBluetoothPermission()
            2, 4, 5 -> checkGPSPermission()
            else -> throw Exception("Location source id should be passed in Bundle")
        }
        if (!permissionsAccepted) return

        setupLocationSource()
    }

    private fun checkGPSPermission(): Boolean {
        return if (ContextCompat.checkSelfPermission(requireContext(), ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            activityResultLauncher.launch(ACCESS_FINE_LOCATION)
            false
        } else {
            true
        }
    }

    private fun checkBluetoothPermission(): Boolean {
        return if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            ContextCompat.checkSelfPermission(requireContext(), BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
            activityResultLauncher.launch(BLUETOOTH_SCAN)
            false
        } else {
            true
        }
    }

    @SuppressLint("MissingPermission")
    private fun setupLocationSource() {
        val locationSource: LocationSource? = when (locationSourceId) {
            0 -> simulator
            1 -> PolestarLocationSource(requireContext(), Constants.polestarApiKey)
            2 -> null
            3 -> PolestarLocationSource(requireContext(), "emulator")
            4 -> GmsFusedLocationSource(requireContext())
            5 -> GareDeLyonSimulatorsLocationSource.FromIndoorToOutdoor
            else -> throw Exception("Location source id should be passed in Bundle")
        }
        mapView.locationManager.apply {
            this.locationSource = locationSource
            isEnabled = true
            cameraMode = CameraMode.TRACKING_COMPASS
            renderMode = RenderMode.COMPASS
        }

        locationManagerReady()
        // this way you can specify user location indicator appearance
//        mapView.locationManager.userLocationViewStyle = UserLocationViewStyle(
//            foregroundDrawable = R.drawable.custom_user_puck_icon,
//            backgroundTintColor = Color.TRANSPARENT,
//            bearingDrawable = R.drawable.custom_user_arrow,
//            outOfActiveLevelStyle = UserLocationViewStyle.OutOfActiveLevelStyle(
//                foregroundDrawable = R.drawable.ic_layers_clear
//            )
//        )
    }

    open fun locationManagerReady() {
        // no-op
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
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        mapView.onSaveInstanceState(outState)
    }

    override fun onLowMemory() {
        super.onLowMemory()
        mapView.onLowMemory()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        disposeBag.clear()
        mapView.onDestroy()
    }

    override fun onDestroy() {
        super.onDestroy()
        disposeBag.dispose()
    }
}