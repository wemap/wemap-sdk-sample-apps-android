package com.getwemap.example.map.positioning.fragments

import android.Manifest.permission
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import com.getwemap.example.map.positioning.LocationSourceType
import com.getwemap.example.map.positioning.databinding.FragmentMapBinding
import com.getwemap.sdk.core.extensions.toCoordinate
import com.getwemap.sdk.core.location.LocationSource
import com.getwemap.sdk.core.location.simulation.SimulationOptions
import com.getwemap.sdk.core.location.simulation.SimulatorLocationSource
import com.getwemap.sdk.positioning.fusedgms.GmsFusedLocationSource
import com.getwemap.sdk.positioning.gps.GpsLocationSource
import org.maplibre.android.MapLibre
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.location.modes.RenderMode

class MapFragment : BaseFragment() {

    override val mapView get() = binding.mapView
    override val levelsSwitcher get() = binding.levelsSwitcher

    private lateinit var locationSource: LocationSourceType

    private var _binding: FragmentMapBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        MapLibre.getInstance(requireContext())
        _binding = FragmentMapBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        locationSource = LocationSourceType.from(requireArguments())
    }

    override fun checkPermissionsAndSetupLocationSource() {
        val permissionsAccepted = when (locationSource) {
            LocationSourceType.SIMULATOR -> true // no permissions needed for simulator
            LocationSourceType.SYSTEM_DEFAULT,
            LocationSourceType.GPS,
            LocationSourceType.FUSED_GMS -> checkGpsPermission()
            // Kept from the pre-enum version, and unreachable today: InitialFragment routes the offline VPS
            // to a screen of its own, which asks for its own permissions. Left in place rather than folded
            // into the throw below because the beacon scan is what it would need if it ever came back.
            LocationSourceType.VPS_LOCAL -> checkGpsPermission() && checkBluetoothPermission()
            // VPS has its own fragment and never lands here.
            LocationSourceType.VPS -> throw IllegalArgumentException("$locationSource has its own screen")
        }
        if (!permissionsAccepted) return
        setupLocationSource()
    }

    private fun checkBluetoothPermission(): Boolean {
        return if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            ContextCompat.checkSelfPermission(
                requireContext(),
                permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
            activityResultLauncher.launch(permission.BLUETOOTH_SCAN)
            false
        } else {
            true
        }
    }

    @SuppressLint("MissingPermission")
    override fun setupLocationSource() {
        val source: LocationSource? = when (locationSource) {
            LocationSourceType.SIMULATOR ->
                SimulatorLocationSource(session, SimulationOptions(deviationRange = -20.0..20.0)).apply {
                    setCoordinates(listOf(session.mapCenter.toCoordinate()), sample = false)
                }
            // The platform's own fused provider, which the SDK uses when no source is set.
            LocationSourceType.SYSTEM_DEFAULT -> null
            LocationSourceType.GPS -> GpsLocationSource(requireContext(), session)
            LocationSourceType.FUSED_GMS -> GmsFusedLocationSource(requireContext(), session)
            else -> throw IllegalArgumentException("$locationSource has its own screen")
        }
        mapView.locationManager.apply {
            this.locationSource = source
            isEnabled = true
            cameraMode = CameraMode.TRACKING_COMPASS
            renderMode = RenderMode.COMPASS
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}