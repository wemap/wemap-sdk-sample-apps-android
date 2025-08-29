package com.getwemap.example.positioning.ar.fragments

import android.Manifest.permission
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.getwemap.example.common.PermissionHelper
import com.getwemap.example.common.multiline
import com.getwemap.example.positioning.ar.ARGlobals
import com.getwemap.example.positioning.ar.databinding.FragmentSimulatorLsBinding
import com.getwemap.sdk.geoar.GeoARView
import com.getwemap.sdk.geoar.managers.IARPointOfInterestManager
import com.getwemap.sdk.core.internal.extensions.disposedBy
import com.getwemap.sdk.core.location.simulation.SimulationOptions
import com.getwemap.sdk.core.location.simulation.SimulatorLocationSource
import com.getwemap.sdk.core.model.entities.MapData
import com.getwemap.sdk.core.navigation.manager.NavigationManagerListener
import com.getwemap.sdk.core.poi.PointOfInterestManagerListener
import com.google.android.material.snackbar.Snackbar

class SimulatorLSFragment : ARFragment() {

    override val geoARView get() = binding.geoSceneView

    private var _binding: FragmentSimulatorLsBinding? = null
    private val binding get() = _binding!!

    private val startNavigationButton get() = binding.startNavigation
    private val stopNavigationButton get() = binding.stopNavigation

    private val simulator: SimulatorLocationSource
        get() = locationManager.locationSource as SimulatorLocationSource

    private lateinit var permissionHelper: PermissionHelper

    private val pointOfInterestManager: IARPointOfInterestManager
        get() = geoARView.pointOfInterestManager

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSimulatorLsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        createPermissionsHelper()

        startNavigationButton.setOnClickListener { startNavigation() }
        stopNavigationButton.setOnClickListener { stopNavigation() }
        binding.lookAtCamera.setOnClickListener {
            geoARView.pointOfInterestManager.lookAtCamera = !geoARView.pointOfInterestManager.lookAtCamera
        }
    }

    override fun onARViewLoaded(arView: GeoARView, mapData: MapData) {
        checkPermissionsAndSetupLocationSource()
        pointOfInterestManager.addListener(poiListener)
        navigationManager.addListener(navListener)
    }

    private fun setupLocationSource() {
        locationManager.locationSource = SimulatorLocationSource(mapData, SimulationOptions(altitude = 1.6))
        simulator.setCoordinates(listOf(ARGlobals.origin), sample = false)
    }

    private fun startNavigation() {
        val selectedPOI = pointOfInterestManager.getSelectedPOI()
        if (selectedPOI == null) {
            updateNavButtons()
            return println("Failed to start navigation because selected poi is null")
        }

        startNavigationButton.isEnabled = false

        navigationManager
            .startNavigation(destination = selectedPOI.coordinate)
            .subscribe({
                simulator.setItinerary(it.itinerary)
                updateNavButtons()
            }, {
                updateNavButtons()
            })
            .disposedBy(disposeBag)
    }

    private fun stopNavigation() {
        navigationManager.stopNavigation().fold({
            updateNavButtons()
            simulator.reset()
        }, {
            updateNavButtons()
        })
    }

    private fun updateNavButtons() {
        startNavigationButton.isEnabled = pointOfInterestManager.getSelectedPOI() != null && !navigationManager.hasActiveNavigation
        stopNavigationButton.isEnabled = navigationManager.hasActiveNavigation
    }

    private val poiListener by lazy {
        PointOfInterestManagerListener(
            onSelected = { updateNavButtons() },
            onUnselected = { updateNavButtons() }
        )
    }

    private val navListener by lazy {
        NavigationManagerListener(
           onStopped = {
               updateNavButtons()
               simulator.reset()
           }
        )
    }

    // region ------ Lifecycle ------
    override fun onStart() {
        super.onStart()
        if (geoARView.isLoaded) {
            pointOfInterestManager.addListener(poiListener)
            navigationManager.addListener(navListener)
        }
    }

    override fun onStop() {
        if (geoARView.isLoaded) {
            pointOfInterestManager.removeListener(poiListener)
            navigationManager.removeListener(navListener)
        }
        super.onStop()
    }
    // endregion ------ Lifecycle ------

    // region ------ Permissions ------
    private fun createPermissionsHelper() {
        val requiredPermissions = listOf(permission.CAMERA)
        permissionHelper = PermissionHelper(this, requiredPermissions)
    }

    private fun checkPermissionsAndSetupLocationSource() {
        permissionHelper
            .request { granted, denied ->
                if (denied.isEmpty()) {
                    setupLocationSource()
                } else {
                    val text = "In order to make sample app work properly you have to accept required permission"
                    Snackbar.make(requireView(), text, Snackbar.LENGTH_LONG).multiline().show()
                }
            }
    }
    // endregion ------ Permissions ------
}