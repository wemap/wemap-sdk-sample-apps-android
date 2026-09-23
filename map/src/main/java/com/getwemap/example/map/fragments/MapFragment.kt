package com.getwemap.example.map.fragments

import android.annotation.SuppressLint
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.getwemap.example.common.CommonAppConstants
import com.getwemap.example.common.PermissionHelper
import com.getwemap.example.common.map.MapLevelsSwitcher
import com.getwemap.example.common.map.SessionViewModel
import com.getwemap.example.common.multiline
import com.getwemap.example.map.Config
import com.getwemap.example.map.LocationSourceType
import com.getwemap.example.map.insetCompassBelowTransparentAppBar
import com.getwemap.sdk.core.awaitLoaded
import com.getwemap.sdk.core.location.LocationSource
import com.getwemap.sdk.core.location.simulation.SimulationOptions
import com.getwemap.sdk.core.location.simulation.SimulatorLocationSource
import com.getwemap.sdk.map.MapSession
import com.getwemap.sdk.map.WemapMapView
import com.getwemap.sdk.positioning.fusedgms.GmsFusedLocationSource
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

abstract class MapFragment : Fragment() {

    protected abstract val mapView: WemapMapView
    protected abstract val levelsSwitcher: MapLevelsSwitcher

    private val sessionViewModel: SessionViewModel by activityViewModels()

    protected lateinit var session: MapSession

    // also you can use simulator to generate locations along the itinerary
    protected val simulator: SimulatorLocationSource?
        get() = mapView.locationManager.locationSource as? SimulatorLocationSource

    protected lateinit var locationSource: LocationSourceType

    protected val pointOfInterestManager get() = mapView.pointOfInterestManager
    protected val focusedBuilding get() = buildingManager.focusedBuilding

    private val buildingManager get() = mapView.buildingManager

    private lateinit var permissionHelper: PermissionHelper

//    private val maxBounds = LatLngBounds.from(
//        48.84811619854466, 2.377353558713054,
//        48.84045277048898, 2.371600716985739
//    )

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        locationSource = LocationSourceType.from(requireArguments())

        createPermissionsHelper()

        // The session was created in InitialFragment and shared via the activity-scoped ViewModel.
        session = sessionViewModel.session!!

        mapView.configure(session, Config.makeMapViewConfig(requireContext()))

        // camera bounds can be specified even if they don't exist in the map data
//        mapView.cameraBounds = maxBounds

        lifecycleScope.launch {
            runCatching {
                mapView.awaitLoaded()
            }.onSuccess {
                // The compass is inside the map, so it needs the inset applied to its own margins rather than
                // to a layout param — and only once the map is loaded.
                mapView.insetCompassBelowTransparentAppBar()
                checkPermissionsAndSetupLocationSource()
                levelsSwitcher.bind(buildingManager, viewLifecycleOwner.lifecycleScope)
            }.onFailure { error ->
                println("Failed to load mapView with error - $error")
            }
        }

    }

    @SuppressLint("MissingPermission")
    private fun setupLocationSource() {

        val rangeBound = CommonAppConstants.SIMULATOR_DEVIATION_RANGE
        val simulationOptions = if (rangeBound == .0) {
            SimulationOptions()
        } else {
            SimulationOptions(deviationRange = -rangeBound/2 .. rangeBound/2)
        }

        val source: LocationSource? = when (locationSource) {
            LocationSourceType.SIMULATOR -> SimulatorLocationSource(session, simulationOptions)
            LocationSourceType.SYSTEM_DEFAULT -> null
            LocationSourceType.FUSED_GMS -> GmsFusedLocationSource(requireContext(), session)
        }
        mapView.locationManager.apply {
            this.locationSource = source
            isEnabled = true
        }

        locationManagerReady()
    }

    open fun locationManagerReady() {
        // no-op
    }

    // region Permissions
    private fun createPermissionsHelper() {
        permissionHelper = PermissionHelper(this, locationSource.requiredPermissions)
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
    // endregion Permissions
}
