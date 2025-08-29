package com.getwemap.example.positioning.ar.fragments

import android.Manifest.permission
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.getwemap.example.common.PermissionHelper
import com.getwemap.example.common.multiline
import com.getwemap.example.positioning.ar.databinding.FragmentGenericLsBinding
import com.getwemap.sdk.core.internal.extensions.disposedBy
import com.getwemap.sdk.core.model.entities.MapData
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

    private lateinit var permissionHelper: PermissionHelper
    private var snackbar: Snackbar? = null

    private val pointOfInterestManager: IARPointOfInterestManager
        get() = geoARView.pointOfInterestManager

    private var locationSourceId: Int = -1

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
    }

    override fun onARViewLoaded(arView: GeoARView, mapData: MapData) {
        checkPermissionsAndSetupLocationSource()
        pointOfInterestManager.addListener(poiListener)
    }

    private fun setupLocationSource() {
        locationManager.locationSource = when (locationSourceId) {
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
            return println("Failed to start navigation because selected poi is null")
        }

        startNavigationButton.isEnabled = false

        navigationManager
            .startNavigation(destination = selectedPOI.coordinate)
            .subscribe({
                updateNavButtons()
            }, {
                println("failed to start navigation with error - $it")
                updateNavButtons()
            })
            .disposedBy(disposeBag)
    }

    private fun stopNavigation() {
        navigationManager.stopNavigation().fold({
            updateNavButtons()
        }, {
            println("failed to stop navigation with error - $it")
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

    // region ------ Lifecycle ------
    override fun onStart() {
        super.onStart()
        if (geoARView.isLoaded)
            pointOfInterestManager.addListener(poiListener)
    }

    override fun onStop() {
        if (geoARView.isLoaded)
            pointOfInterestManager.removeListener(poiListener)
        super.onStop()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
    // endregion ------ Lifecycle ------

    // region ------ Permissions ------
    private fun createPermissionsHelper() {
        val requiredPermissions = listOf(
            permission.ACCESS_FINE_LOCATION,
            permission.ACCESS_COARSE_LOCATION,
            permission.CAMERA
        )
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