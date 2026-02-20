package com.getwemap.example.map.fragments

import android.Manifest.permission.ACCESS_FINE_LOCATION
import android.Manifest.permission.BLUETOOTH_SCAN
import android.annotation.SuppressLint
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import com.getwemap.example.common.Constants
import com.getwemap.example.common.PermissionHelper
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

abstract class MapFragment : Fragment() {

    protected abstract val mapView: WemapMapView
    protected abstract val levelsSwitcher: MapLevelsSwitcher

    protected lateinit var mapData: MapData

    protected val disposeBag = CompositeDisposable()

    // also you can use simulator to generate locations along the itinerary
    protected val simulator: SimulatorLocationSource?
        get() = mapView.locationManager.locationSource as? SimulatorLocationSource

    protected var locationSourceId: Int = -1

    protected val pointOfInterestManager get() = mapView.pointOfInterestManager
    protected val focusedBuilding get() = buildingManager.focusedBuilding

    private val buildingManager get() = mapView.buildingManager

    private lateinit var permissionHelper: PermissionHelper

//    private val maxBounds = LatLngBounds
//        .from(48.84811619854466, 2.377353558713054,
//            48.84045277048898, 2.371600716985739)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val args = requireArguments()
        locationSourceId = args.getInt("locationSourceId")

        createPermissionsHelper()

        val mapDataString = args.getString("mapData")!!
        mapData = Json.decodeFromString(mapDataString)

        mapView.mapData = mapData
        // camera bounds can be specified even if they don't exist in MapData
//        mapView.cameraBounds = maxBounds
        mapView.onCreate(savedInstanceState)

        mapView.getMapViewAsync { _, _, _, _ ->
            checkPermissionsAndSetupLocationSource()
            levelsSwitcher.bind(buildingManager)
        }
    }

    @SuppressLint("MissingPermission")
    private fun setupLocationSource() {
        val locationSource: LocationSource? = when (locationSourceId) {
            0 -> SimulatorLocationSource(mapData, SimulationOptions(deviationRange = -20.0..20.0, horizontalAccuracy = 3f))
            1 -> PolestarLocationSource(requireContext(), mapData, Constants.polestarApiKey)
            2 -> null
            3 -> PolestarLocationSource(requireContext(), mapData, "emulator")
            4 -> GmsFusedLocationSource(requireContext(), mapData)
            5 -> GareDeLyonSimulatorsLocationSource.fromIndoorToOutdoor(mapData)
            else -> throw Exception("Location source id should be passed in Bundle")
        }
        mapView.locationManager.apply {
            this.locationSource = locationSource
            isEnabled = true
        }

        locationManagerReady()
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

    // region ------ Permissions ------
    private fun createPermissionsHelper() {
        val requiredPermissions = when (locationSourceId) {
            0, 5 -> listOf() // Simulator
            1, 3 -> { // Polestar
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
                    listOf(ACCESS_FINE_LOCATION, BLUETOOTH_SCAN)
                else
                    listOf(ACCESS_FINE_LOCATION)
            }
            2, 4 -> listOf(ACCESS_FINE_LOCATION) // GMS and default
            else -> throw Exception("Location source id should be passed in Bundle")
        }

        permissionHelper = PermissionHelper(this, requiredPermissions)
    }

    private fun checkPermissionsAndSetupLocationSource() {
        permissionHelper
            .request { _, denied ->
                if (denied.isEmpty()) {
                    setupLocationSource()
                } else {
                    val text = "In order to make sample app work properly you have to accept required permission"
                    Snackbar.make(mapView, text, Snackbar.LENGTH_LONG).multiline().show()
                }
            }
    }
    // endregion ------ Permissions ------
}