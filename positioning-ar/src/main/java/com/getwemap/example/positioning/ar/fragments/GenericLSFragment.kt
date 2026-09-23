package com.getwemap.example.positioning.ar.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.getwemap.example.common.PermissionHelper
import com.getwemap.example.common.multiline
import com.getwemap.example.positioning.ar.LocationSourceType
import com.getwemap.example.positioning.ar.databinding.FragmentGenericLsBinding
import com.getwemap.sdk.core.extensions.toLocation
import com.getwemap.sdk.core.location.simulation.SimulationOptions
import com.getwemap.sdk.core.location.simulation.SimulatorLocationSource
import com.getwemap.sdk.core.model.entities.Coordinate
import com.getwemap.sdk.core.model.entities.PointOfInterest
import com.getwemap.sdk.core.navigation.manager.NavigationEvent
import com.getwemap.sdk.geoar.GeoARView
import com.getwemap.sdk.geoar.managers.ARPointOfInterestManager
import com.getwemap.sdk.positioning.androidfusedadaptive.AndroidFusedAdaptiveLocationSource
import com.getwemap.sdk.positioning.fusedgms.GmsFusedLocationSource
import com.getwemap.sdk.positioning.gps.GpsLocationSource
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class GenericLSFragment: ARFragment() {

    override val geoARView get() = binding.geoSceneView

    private var _binding: FragmentGenericLsBinding? = null
    private val binding get() = _binding!!

    private val startNavigationButton get() = binding.startNavigation
    private val stopNavigationButton get() = binding.stopNavigation

    private val pointOfInterestManager: ARPointOfInterestManager get() = geoARView.pointOfInterestManager

    private val simulator: SimulatorLocationSource?
        get() = locationManager.locationSource as? SimulatorLocationSource

    private lateinit var permissionHelper: PermissionHelper
    private var snackbar: Snackbar? = null

    private lateinit var locationSource: LocationSourceType
    private var direction: Double = -90.0
    private var customPois: MutableSet<PointOfInterest> = mutableSetOf()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentGenericLsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        // Read the source BEFORE building the permission helper, which asks it what to request. The two were
        // the other way round until v1 and the bug was invisible: `locationSourceId` defaulted to -1, so the
        // helper fell through to the `else` branch and the simulator sample asked for location permissions it
        // never uses — the one thing `requiredPermissions` exists to avoid.
        locationSource = LocationSourceType.from(requireArguments())

        createPermissionsHelper()

        super.onViewCreated(view, savedInstanceState)

        startNavigationButton.setOnClickListener { startNavigation() }
        stopNavigationButton.setOnClickListener { stopNavigation() }

        binding.addPoi.setOnClickListener { addPoi() }
        binding.removePoi.setOnClickListener { removePoi() }
        binding.addPois.setOnClickListener { addPois() }
        binding.removePois.setOnClickListener { removePois() }
    }

    override fun onARViewLoaded(arView: GeoARView) {
        checkPermissionsAndSetupLocationSource()
        observePointOfInterestManager()
        observeNavigationManager()
    }

    private fun setupLocationSource() {
        locationManager.locationSource = when (locationSource) {
            LocationSourceType.SIMULATOR ->
                SimulatorLocationSource(session, SimulationOptions(altitude = 1.6)).apply {
                    setCoordinates(listOf(Coordinate(session.mapCenter.toLocation())), sample = false)
                }
            LocationSourceType.ANDROID_FUSED_ADAPTIVE ->
                AndroidFusedAdaptiveLocationSource(requireContext(), session)
            LocationSourceType.FUSED_GMS ->
                GmsFusedLocationSource(requireContext(), session)
            LocationSourceType.GPS ->
                GpsLocationSource(requireContext(), session)
            // VPS has a screen of its own (`VpsLSFragment`) and never reaches here.
            LocationSourceType.VPS ->
                throw IllegalArgumentException("$locationSource has its own screen")
        }

        snackbar = Snackbar.make(geoARView, "Searching for your location...", Snackbar.LENGTH_INDEFINITE)
            .also { it.show() }

        lifecycleScope.launch {
            try {
                locationManager.coordinates.first()
            } finally {
                snackbar?.dismiss()
            }
        }
    }

    private fun startNavigation() {
        val selectedPoi = pointOfInterestManager.getSelectedPoi()
        if (selectedPoi == null) {
            updateNavButtons()
            val text = "Failed to start navigation because selected poi is null"
            Snackbar.make(requireView(), text, Snackbar.LENGTH_LONG).multiline().show()
            return
        }

        startNavigationButton.isEnabled = false

        lifecycleScope.launch {
            runCatching {
                navigationManager.startNavigation(destination = selectedPoi.coordinate)
            }.onSuccess {
                simulator?.setItinerary(it.itinerary)
                updateNavButtons()
            }.onFailure {
                updateNavButtons()
            }
        }
    }

    private fun stopNavigation() {
        navigationManager.stopNavigation()
            .onSuccess {
                updateNavButtons()
                simulator?.reset()
            }.onFailure {
                updateNavButtons()
            }
    }

    private fun addPoi() {
        val poi = generatePoi()
        if (poi == null) {
            val text = "Failed to generate POI"
            Snackbar.make(requireView(), text, Snackbar.LENGTH_LONG).multiline().show()
            return
        }
        if (!pointOfInterestManager.addPoi(poi)) {
            val text = "Failed to add POI - $poi"
            Snackbar.make(requireView(), text, Snackbar.LENGTH_LONG).multiline().show()
        } else {
            customPois.add(poi)
        }
    }

    private fun removePoi() {
        val poi = customPois.randomOrNull()
        if (poi == null) {
            val text = "There is no POI to remove"
            Snackbar.make(requireView(), text, Snackbar.LENGTH_LONG).multiline().show()
            return
        }
        if (!pointOfInterestManager.removePoi(poi)) {
            val text = "Failed to remove POI - $poi"
            Snackbar.make(requireView(), text, Snackbar.LENGTH_LONG).multiline().show()
        } else {
            customPois.remove(poi)
        }
    }

    private fun addPois() {
        val pois = (0 until 3).mapNotNull {
            generatePoi()
        }
        if (pois.isEmpty()) {
            val text = "Failed to generate POIs"
            Snackbar.make(requireView(), text, Snackbar.LENGTH_LONG).multiline().show()
            return
        }

        if (!pointOfInterestManager.addPois(pois.toSet())) {
            val text = "Failed to add POIs"
            Snackbar.make(requireView(), text, Snackbar.LENGTH_LONG).multiline().show()
        } else {
            customPois.addAll(pois)
        }
    }

    private fun removePois() {
        if (customPois.isEmpty()) {
            val text = "There are no POIs to remove"
            Snackbar.make(requireView(), text, Snackbar.LENGTH_LONG).multiline().show()
            return
        }
        if (!pointOfInterestManager.removePois(customPois)) {
            val text = "Failed to remove POIs"
            Snackbar.make(requireView(), text, Snackbar.LENGTH_LONG).multiline().show()
        } else {
            customPois.clear()
        }
    }

    private fun generatePoi(): PointOfInterest? {

        val userCoordinate = geoARView.locationManager.lastCoordinate
        if (userCoordinate == null) {
            val text = "Failed to get user location"
            Snackbar.make(requireView(), text, Snackbar.LENGTH_LONG).multiline().show()
            return null
        }

        val target = userCoordinate.destination(50.0, direction)
        direction += 15

        return PointOfInterest(
            "Custom POI",
            target,
            imageUrl = "https://api.getwemap.com/images/pps-categories/icon_circle_maaap.png"
        )
    }

    private fun updateNavButtons() {
        startNavigationButton.isEnabled = pointOfInterestManager.getSelectedPoi() != null && !navigationManager.hasActiveNavigation
        stopNavigationButton.isEnabled = navigationManager.hasActiveNavigation
    }

    private fun observePointOfInterestManager() {
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                pointOfInterestManager.selectionUpdates.collect {
                    updateNavButtons()
                }
            }
        }
    }

    private fun observeNavigationManager() {
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                navigationManager.navigationEvents.collect { event ->
                    if (event is NavigationEvent.Stopped) {
                        updateNavButtons()
                        simulator?.reset()
                    }
                }
            }
        }
    }

    // region Lifecycle
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
    // endregion Lifecycle

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
                    Snackbar.make(requireView(), text, Snackbar.LENGTH_LONG).multiline().show()
                }
            }
    }
    // endregion Permissions
}