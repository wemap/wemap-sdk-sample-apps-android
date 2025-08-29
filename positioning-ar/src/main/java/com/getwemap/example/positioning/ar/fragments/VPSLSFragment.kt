package com.getwemap.example.positioning.ar.fragments

import android.Manifest.permission
import android.annotation.SuppressLint
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.getwemap.example.common.PermissionHelper
import com.getwemap.example.common.multiline
import com.getwemap.example.positioning.ar.databinding.FragmentVpsLsBinding
import com.getwemap.sdk.geoar.GeoARView
import com.getwemap.sdk.geoar.managers.ARLocationManagerListener
import com.getwemap.sdk.geoar.managers.IARPointOfInterestManager
import com.getwemap.sdk.core.internal.extensions.disposedBy
import com.getwemap.sdk.core.model.entities.MapData
import com.getwemap.sdk.core.navigation.manager.NavigationManagerListener
import com.getwemap.sdk.core.poi.PointOfInterestManagerListener
import com.getwemap.sdk.positioning.wemapvpsarcore.WemapVPSARCoreLocationSource
import com.getwemap.sdk.positioning.wemapvpsarcore.WemapVPSARCoreLocationSource.ScanStatus
import com.getwemap.sdk.positioning.wemapvpsarcore.WemapVPSARCoreLocationSourceListener
import com.google.android.material.snackbar.Snackbar

class VPSLSFragment : ARFragment() {

    override val geoARView get() = binding.geoSceneView

    private var _binding: FragmentVpsLsBinding? = null
    private val binding get() = _binding!!

    private val startScanningButton get() = binding.startScanning
    private val stopScanningButton get() = binding.stopScanning

    private val startNavigationButton get() = binding.startNavigation
    private val stopNavigationButton get() = binding.stopNavigation

    private val vpsLocationSource: WemapVPSARCoreLocationSource
        get() = locationManager.locationSource as WemapVPSARCoreLocationSource

    private val pointOfInterestManager: IARPointOfInterestManager
        get() = geoARView.pointOfInterestManager

    private lateinit var permissionHelper: PermissionHelper

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentVpsLsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        createPermissionsHelper()

        startNavigationButton.setOnClickListener { startNavigation() }
        stopNavigationButton.setOnClickListener { stopNavigation() }

        startScanningButton.setOnClickListener { startScanning() }
        stopScanningButton.setOnClickListener { stopScanning() }
    }

    override fun onARViewLoaded(arView: GeoARView, mapData: MapData) {
        checkPermissionsAndSetupLocationSource()
        pointOfInterestManager.addListener(poiListener)
        navigationManager.addListener(navListener)
    }

    @SuppressLint("MissingPermission")
    private fun setupLocationSource() {
        locationManager.locationSource = WemapVPSARCoreLocationSource(requireContext(), mapData)
        vpsLocationSource.vpsListeners.add(vpsListener)
        startScanningButton.isEnabled = true
    }

    private fun startNavigation() {
        val selectedPOI = pointOfInterestManager.getSelectedPOI()
            ?: return println("Failed to start navigation because selected POI is nil")

        startNavigationButton.isEnabled = false

        navigationManager
            .startNavigation(destination = selectedPOI.coordinate)
            .subscribe({
                stopNavigationButton.isEnabled = true
            }, {
                println("failed to start navigation with error - $it")
                startNavigationButton.isEnabled = true
            })
            .disposedBy(disposeBag)
    }

    private fun stopNavigation() {
        navigationManager.stopNavigation().fold({
            startNavigationButton.isEnabled = true
            stopNavigationButton.isEnabled = false
        }, {
            println("failed to stop navigation with error - $it")
        })
    }

    private fun startScanning() {
        vpsLocationSource.startScan()
        stopScanningButton.isEnabled = true
    }

    private fun stopScanning() {
        vpsLocationSource.stopScan()
        startScanningButton.isEnabled = true
    }

    private val vpsListener by lazy {
        WemapVPSARCoreLocationSourceListener(
            onScanStatusChanged = { scanStatus ->
                println("scan status - $scanStatus")

                when (scanStatus) {
                    ScanStatus.STARTED -> {
                        startScanningButton.isEnabled = false
                        stopScanningButton.isEnabled = true
                        startNavigationButton.isEnabled = false
                    }
                    ScanStatus.STOPPED -> {
                        startScanningButton.isEnabled = vpsLocationSource.state == WemapVPSARCoreLocationSource.State.NOT_POSITIONING
                        stopScanningButton.isEnabled = false
                    }
                }
            }, onStateChanged = { state ->
                println("state - $state")
                startScanningButton.isEnabled = !state.isAccurate
                startNavigationButton.isEnabled = !state.isLost
            }
        )
    }

    private val poiListener by lazy {
        PointOfInterestManagerListener(
            onSelected = {
                startNavigationButton.isEnabled = true
            },
            onUnselected = {
                startNavigationButton.isEnabled = false
            }
        )
    }

    private val navListener by lazy {
        NavigationManagerListener(
            onStopped = {
                startNavigationButton.isEnabled = pointOfInterestManager.getSelectedPOI() != null
                stopNavigationButton.isEnabled = false
            }
        )
    }

    private val locationManagerListener by lazy {
        ARLocationManagerListener { error ->
            println("LocationManager failed with error - $error")
        }
    }

    // region ------ Lifecycle ------
    override fun onStart() {
        super.onStart()
        if (geoARView.isLoaded) {
            locationManager.addListener(locationManagerListener)
            pointOfInterestManager.addListener(poiListener)
            navigationManager.addListener(navListener)
            vpsLocationSource.vpsListeners.add(vpsListener)
        }
    }

    override fun onStop() {
        if (geoARView.isLoaded) {
            locationManager.removeListener(locationManagerListener)
            pointOfInterestManager.removeListener(poiListener)
            navigationManager.removeListener(navListener)
            vpsLocationSource.vpsListeners.remove(vpsListener)
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
        val requiredPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            listOf(permission.CAMERA, permission.ACTIVITY_RECOGNITION)
        else
            listOf(permission.CAMERA)

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