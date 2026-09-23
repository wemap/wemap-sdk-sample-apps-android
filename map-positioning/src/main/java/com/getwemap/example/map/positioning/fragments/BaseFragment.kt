package com.getwemap.example.map.positioning.fragments

import android.Manifest.permission.ACCESS_FINE_LOCATION
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.getwemap.example.common.map.MapLevelsSwitcher
import com.getwemap.example.common.map.SessionViewModel
import com.getwemap.example.map.positioning.Config
import com.getwemap.sdk.core.awaitLoaded
import com.getwemap.sdk.map.MapSession
import com.getwemap.sdk.map.WemapMapView
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch

abstract class BaseFragment : Fragment() {

    protected abstract val mapView: WemapMapView
    protected abstract val levelsSwitcher: MapLevelsSwitcher

    private val sessionViewModel: SessionViewModel by activityViewModels()

    protected lateinit var session: MapSession

    protected val focusedBuilding get() = buildingManager.focusedBuilding

    private val buildingManager get() = mapView.buildingManager

    protected val activityResultLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
        if (isGranted) {
            checkPermissionsAndSetupLocationSource()
        } else {
            val text = "In order to make sample app work properly you have to accept required permission"
            Snackbar.make(mapView, text, Snackbar.LENGTH_LONG).show()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        session = sessionViewModel.session!!
        mapView.configure(session, Config.makeMapViewConfig(requireContext()))

        lifecycleScope.launch {
            runCatching {
                mapView.awaitLoaded()
            }.onSuccess {
                checkPermissionsAndSetupLocationSource()
                levelsSwitcher.bind(mapView.buildingManager, viewLifecycleOwner.lifecycleScope)
            }.onFailure {
                println("Failed to load MapView with error - $it")
            }
        }
    }

    protected abstract fun checkPermissionsAndSetupLocationSource()

    protected fun checkGpsPermission(): Boolean {
        return if (ContextCompat.checkSelfPermission(requireContext(), ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            activityResultLauncher.launch(ACCESS_FINE_LOCATION)
            false
        } else {
            true
        }
    }

    @SuppressLint("MissingPermission")
    protected abstract fun setupLocationSource()
}