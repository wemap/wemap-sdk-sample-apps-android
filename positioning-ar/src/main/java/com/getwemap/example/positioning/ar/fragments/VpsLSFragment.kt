package com.getwemap.example.positioning.ar.fragments

import android.Manifest.permission
import android.annotation.SuppressLint
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.getwemap.example.common.PermissionHelper
import com.getwemap.example.common.multiline
import com.getwemap.example.positioning.ar.databinding.FragmentVpsLsBinding
import com.getwemap.sdk.core.navigation.manager.NavigationEvent
import com.getwemap.sdk.geoar.GeoARView
import com.getwemap.sdk.geoar.managers.ARPointOfInterestManager
import com.getwemap.sdk.positioning.wemapvpsarcore.VpsARCoreLocationSource
import com.getwemap.sdk.positioning.wemapvpsarcore.VpsARCoreLocationSource.ScanStatus
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

class VpsLSFragment : ARFragment() {

    override val geoARView get() = binding.geoSceneView

    private var _binding: FragmentVpsLsBinding? = null
    private val binding get() = _binding!!

    private val startScanningButton get() = binding.startScanning
    private val stopScanningButton get() = binding.stopScanning

    private val startNavigationButton get() = binding.startNavigation
    private val stopNavigationButton get() = binding.stopNavigation

    private val vpsLocationSource: VpsARCoreLocationSource
        get() = locationManager.locationSource as VpsARCoreLocationSource

    private val pointOfInterestManager: ARPointOfInterestManager
        get() = geoARView.pointOfInterestManager

    private lateinit var permissionHelper: PermissionHelper

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentVpsLsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {

        createPermissionsHelper()

        super.onViewCreated(view, savedInstanceState)

        startNavigationButton.setOnClickListener { startNavigation() }
        stopNavigationButton.setOnClickListener { stopNavigation() }

        startScanningButton.setOnClickListener { vpsLocationSource.startScan() }
        stopScanningButton.setOnClickListener { vpsLocationSource.stopScan() }
    }

    override fun onARViewLoaded(arView: GeoARView) {
        checkPermissionsAndSetupLocationSource()
        observePointOfInterestManager()
        observeNavigationManager()
        observeLocationManager()
    }

    @SuppressLint("MissingPermission")
    private fun setupLocationSource() {
        locationManager.locationSource = VpsARCoreLocationSource(requireContext(), session)
        observeVps()
        startScanningButton.isEnabled = true
    }

    private fun startNavigation() {
        val selectedPoi = pointOfInterestManager.getSelectedPoi()
            ?: return println("Failed to start navigation because selected POI is nil")

        startNavigationButton.isEnabled = false

        lifecycleScope.launch {
            try {
                navigationManager.startNavigation(destination = selectedPoi.coordinate)
            } catch(e: Exception) {
                println("failed to start navigation with error - $e")
            } finally {
                stopNavigationButton.isEnabled = true
            }
        }
    }

    private fun stopNavigation() {
        navigationManager.stopNavigation()
            .onSuccess {
                startNavigationButton.isEnabled = true
                stopNavigationButton.isEnabled = false
            }.onFailure {
                println("failed to stop navigation with error - $it")
            }
    }

    private fun observeVps() {
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    vpsLocationSource.scanStatuses.collect { status ->
                        println("scan status - $status")
                        when (status) {
                            ScanStatus.STARTED -> {
                                startScanningButton.isEnabled = false
                                stopScanningButton.isEnabled = true
                                startNavigationButton.isEnabled = false
                            }
                            ScanStatus.STOPPED -> {
                                startScanningButton.isEnabled = !vpsLocationSource.state.isAccurate
                                stopScanningButton.isEnabled = false
                            }
                        }
                    }
                }
                launch {
                    vpsLocationSource.states.collect { state ->
                        println("state - $state")
                        startScanningButton.isEnabled = !state.isAccurate
                        startNavigationButton.isEnabled = !state.isLost
                    }
                }
            }
        }
    }

    private fun observePointOfInterestManager() {
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                pointOfInterestManager.selectionUpdates.collect { update ->
                    if (update.unselected.isNotEmpty())
                        startNavigationButton.isEnabled = false
                    if (update.selected.isNotEmpty())
                        startNavigationButton.isEnabled = true
                }
            }
        }
    }

    private fun observeNavigationManager() {
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                navigationManager.navigationEvents.collect { event ->
                    if (event is NavigationEvent.Stopped) {
                        startNavigationButton.isEnabled = pointOfInterestManager.getSelectedPoi() != null
                        stopNavigationButton.isEnabled = false
                    }
                }
            }
        }
    }

    private fun observeLocationManager() {
        viewLifecycleOwner.lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                locationManager.errors.collect { error ->
                    println("LocationManager failed with error - $error")
                }
            }
        }
    }

    // region Lifecycle
    override fun onResume() {
        super.onResume()
        if (geoARView.loadPhase.isReady) {
            startScanningButton.isEnabled = !vpsLocationSource.state.isAccurate
            stopScanningButton.isEnabled = false
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
    // endregion Lifecycle

    // region Permissions
    private fun createPermissionsHelper() {
        val requiredPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
            listOf(permission.CAMERA, permission.ACTIVITY_RECOGNITION)
        else
            listOf(permission.CAMERA)

        permissionHelper = PermissionHelper(this, requiredPermissions)
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