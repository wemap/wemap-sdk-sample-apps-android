package com.getwemap.example.positioning.ar.fragments

import android.Manifest.permission
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.getwemap.example.common.PermissionHelper
import com.getwemap.example.common.multiline
import com.getwemap.example.positioning.ar.databinding.FragmentGenericLsBinding
import com.getwemap.sdk.core.extensions.toLocation
import com.getwemap.sdk.core.internal.extensions.disposedBy
import com.getwemap.sdk.core.location.simulation.SimulationOptions
import com.getwemap.sdk.core.location.simulation.SimulatorLocationSource
import com.getwemap.sdk.core.model.entities.Coordinate
import com.getwemap.sdk.core.model.entities.MapData
import com.getwemap.sdk.core.model.entities.PointOfInterest
import com.getwemap.sdk.core.navigation.manager.NavigationManagerListener
import com.getwemap.sdk.core.poi.PointOfInterestManagerListener
import com.getwemap.sdk.geoar.GeoARView
import com.getwemap.sdk.geoar.managers.IARPointOfInterestManager
import com.getwemap.sdk.positioning.androidfusedadaptive.AndroidFusedAdaptiveLocationSource
import com.getwemap.sdk.positioning.fusedgms.GmsFusedLocationSource
import com.getwemap.sdk.positioning.gps.GPSLocationSource
import com.google.android.material.snackbar.Snackbar

class GenericLSFragment: ARFragment() {

    override val geoARView get() = binding.geoSceneView

    private var _binding: FragmentGenericLsBinding? = null
    private val binding get() = _binding!!

    private val startNavigationButton get() = binding.startNavigation
    private val stopNavigationButton get() = binding.stopNavigation

    private val pointOfInterestManager: IARPointOfInterestManager get() = geoARView.pointOfInterestManager

    private val simulator: SimulatorLocationSource?
        get() = locationManager.locationSource as? SimulatorLocationSource

    private lateinit var permissionHelper: PermissionHelper
    private var snackbar: Snackbar? = null

    private var locationSourceId: Int = -1
    private var direction: Float = -90f
    private var customPOIs: MutableSet<PointOfInterest> = mutableSetOf()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentGenericLsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        locationSourceId = requireArguments().getInt("locationSourceId")

        createPermissionsHelper()

        startNavigationButton.setOnClickListener { startNavigation() }
        stopNavigationButton.setOnClickListener { stopNavigation() }

        binding.addPOI.setOnClickListener { addPOI() }
        binding.removePOI.setOnClickListener { removePOI() }
        binding.addPOIs.setOnClickListener { addPOIs() }
        binding.removePOIs.setOnClickListener { removePOIs() }
    }

    override fun onARViewLoaded(arView: GeoARView, mapData: MapData) {
        checkPermissionsAndSetupLocationSource()
        pointOfInterestManager.addListener(poiListener)
        navigationManager.addListener(navListener)
    }

    private fun setupLocationSource() {
        locationManager.locationSource = when (locationSourceId) {
            0 -> // Simulator
                SimulatorLocationSource(mapData, SimulationOptions(altitude = 1.6)).apply {
                    setCoordinates(listOf(Coordinate(mapData.center.toLocation())), sample = false)
                }
            2 -> // Adaptive
                AndroidFusedAdaptiveLocationSource(requireContext(), mapData)
            3 -> // Fused GMS
                GmsFusedLocationSource(requireContext(), mapData)
            4 -> // GPS
                GPSLocationSource(requireContext(), mapData)
            else ->
                throw IllegalArgumentException("Unsupported location source id - $locationSourceId")
        }

        snackbar = Snackbar.make(geoARView, "Searching for you location...", Snackbar.LENGTH_INDEFINITE)
            .also { it.show() }

        locationManager.coordinate
            .take(1)
            .doFinally { snackbar?.dismiss() }
            .subscribe {
                snackbar?.dismiss()
            }
            .disposedBy(disposeBag)
    }

    private fun startNavigation() {
        val selectedPOI = pointOfInterestManager.getSelectedPOI()
        if (selectedPOI == null) {
            updateNavButtons()
            val text = "Failed to start navigation because selected poi is null"
            Snackbar.make(requireView(), text, Snackbar.LENGTH_LONG).multiline().show()
            return
        }

        startNavigationButton.isEnabled = false

        navigationManager
            .startNavigation(destination = selectedPOI.coordinate)
            .subscribe({
                simulator?.setItinerary(it.itinerary)
                updateNavButtons()
            }, {
                updateNavButtons()
            })
            .disposedBy(disposeBag)
    }

    private fun stopNavigation() {
        navigationManager.stopNavigation().fold({
            updateNavButtons()
            simulator?.reset()
        }, {
            updateNavButtons()
        })
    }

    private fun addPOI() {
        val poi = generatePOI()
        if (poi == null) {
            val text = "Failed to generate POI"
            Snackbar.make(requireView(), text, Snackbar.LENGTH_LONG).multiline().show()
            return
        }
        if (!pointOfInterestManager.addPOI(poi)) {
            val text = "Failed to add POI - $poi"
            Snackbar.make(requireView(), text, Snackbar.LENGTH_LONG).multiline().show()
        } else {
            customPOIs.add(poi)
        }
    }

    private fun removePOI() {
        val poi = customPOIs.randomOrNull()
        if (poi == null) {
            val text = "There is no POI to remove"
            Snackbar.make(requireView(), text, Snackbar.LENGTH_LONG).multiline().show()
            return
        }
        if (!pointOfInterestManager.removePOI(poi)) {
            val text = "Failed to remove POI - $poi"
            Snackbar.make(requireView(), text, Snackbar.LENGTH_LONG).multiline().show()
        } else {
            customPOIs.remove(poi)
        }
    }

    private fun addPOIs() {
        val pois = (0 until 3).mapNotNull {
            generatePOI()
        }
        if (pois.isEmpty()) {
            val text = "Failed to generate POIs"
            Snackbar.make(requireView(), text, Snackbar.LENGTH_LONG).multiline().show()
            return
        }

        if (!pointOfInterestManager.addPOIs(pois.toSet())) {
            val text = "Failed to add POIs"
            Snackbar.make(requireView(), text, Snackbar.LENGTH_LONG).multiline().show()
        } else {
            customPOIs.addAll(pois)
        }
    }

    private fun removePOIs() {
        if (customPOIs.isEmpty()) {
            val text = "There are no POIs to remove"
            Snackbar.make(requireView(), text, Snackbar.LENGTH_LONG).multiline().show()
            return
        }
        if (!pointOfInterestManager.removePOIs(customPOIs)) {
            val text = "Failed to remove POIs"
            Snackbar.make(requireView(), text, Snackbar.LENGTH_LONG).multiline().show()
        } else {
            customPOIs.clear()
        }
    }

    private fun generatePOI(): PointOfInterest? {

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
            imageURL = "https://api.getwemap.com/images/pps-categories/icon_circle_maaap.png"
        )
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
                simulator?.reset()
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

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
    // endregion ------ Lifecycle ------

    // region ------ Permissions ------
    private fun createPermissionsHelper() {
        val requiredPermissions = when (locationSourceId) {
            0 -> listOf(permission.CAMERA)
            else -> listOf(permission.CAMERA, permission.ACCESS_FINE_LOCATION, permission.ACCESS_COARSE_LOCATION)
        }
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