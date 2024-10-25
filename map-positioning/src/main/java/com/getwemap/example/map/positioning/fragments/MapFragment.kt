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
import com.getwemap.example.common.Constants
import com.getwemap.example.map.positioning.databinding.FragmentMapBinding
import com.getwemap.sdk.core.location.LocationSource
import com.getwemap.sdk.positioning.fusedgms.GmsFusedLocationSource
import com.getwemap.sdk.positioning.polestar.PolestarLocationSource
import org.maplibre.android.MapLibre
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.location.modes.RenderMode

class MapFragment : BaseFragment() {

    override val mapView get() = binding.mapView
    override val levelsSwitcher get() = binding.levelsSwitcher

    private var locationSourceId: Int = -1

    private var _binding: FragmentMapBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        MapLibre.getInstance(requireContext())
        _binding = FragmentMapBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val args = requireArguments()
        locationSourceId = args.getInt("locationSourceId")
    }

    override fun checkPermissionsAndSetupLocationSource() {
        val permissionsAccepted = when (locationSourceId) {
            0 -> checkGPSPermission()
            1, 2 -> checkGPSPermission() && checkBluetoothPermission()
            else -> throw Exception("Location source id should be passed in Bundle")
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
        val locationSource: LocationSource = when (locationSourceId) {
            0 -> GmsFusedLocationSource(requireContext())
            1 -> PolestarLocationSource(requireContext(), "emulator")
            2 -> PolestarLocationSource(requireContext(), Constants.polestarApiKey)
            else -> throw Exception("Location source id should be passed in Bundle")
        }
        mapView.locationManager.apply {
            this.locationSource = locationSource
            isEnabled = true
            cameraMode = CameraMode.TRACKING_COMPASS
            renderMode = RenderMode.COMPASS
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        disposeBag.clear()
        _binding = null
    }

    override fun onDestroy() {
        super.onDestroy()
        disposeBag.dispose()
    }
}