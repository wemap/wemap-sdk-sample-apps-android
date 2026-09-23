package com.getwemap.example.positioning.ar.fragments

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.getwemap.example.common.SessionViewModel
import com.getwemap.example.positioning.ar.Config
import com.getwemap.sdk.core.CoreSession
import com.getwemap.sdk.core.awaitLoaded
import com.getwemap.sdk.geoar.GeoARView
import com.getwemap.sdk.geoar.managers.ARLocationManager
import com.getwemap.sdk.geoar.managers.ARNavigationManager
import kotlinx.coroutines.launch

abstract class ARFragment : Fragment() {

    protected abstract val geoARView: GeoARView

    private val sessionViewModel: SessionViewModel by activityViewModels()
    protected lateinit var session: CoreSession

    protected val navigationManager: ARNavigationManager get() = geoARView.navigationManager
    protected val locationManager: ARLocationManager get() = geoARView.locationManager

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        session = sessionViewModel.session!!

        lifecycleScope.launch {
            runCatching {
                geoARView.awaitLoaded()
            }.onSuccess {
                onARViewLoaded(it)
            }.onFailure { error ->
                println("Failed to load geoARView with error - $error")
            }
        }

        geoARView.configure(session, Config.makeGeoARViewConfig(requireContext()))
    }

    override fun onDestroyView() {
        super.onDestroyView()
    }

    abstract fun onARViewLoaded(arView: GeoARView)
}
