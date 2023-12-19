package com.getwemap.example.map.positioning

import android.Manifest.permission
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.getwemap.example.map.positioning.databinding.FragmentMapBinding
import com.getwemap.sdk.core.location.LocationSource
import com.getwemap.sdk.map.model.entities.MapData
import com.getwemap.sdk.positioning.fusedgms.GmsFusedLocationSource
import com.getwemap.sdk.positioning.polestar.PolestarLocationSource
import com.google.android.material.snackbar.Snackbar
import com.mapbox.mapboxsdk.Mapbox
import com.mapbox.mapboxsdk.location.modes.CameraMode
import com.mapbox.mapboxsdk.location.modes.RenderMode
import kotlinx.serialization.json.Json

class MapFragment : Fragment() {

    private var locationSourceId: Int = -1
    private val mapView get() = binding.mapView

    private lateinit var binding: FragmentMapBinding

    private val activityResultLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                checkPermissionsAndSetupLocationSource()
            } else {
                Snackbar.make(
                    mapView,
                    "In order to make sample app work properly you have to accept required permission",
                    Snackbar.LENGTH_LONG)
                    .show()
            }
        }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        Mapbox.getInstance(requireContext())
        binding = FragmentMapBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val args = requireArguments()
        locationSourceId = args.getInt("locationSourceId")

        val mapDataString = args.getString("mapData")!!
        val mapData: MapData = Json.decodeFromString(mapDataString)
        mapView.mapData = mapData

        mapView.onCreate(savedInstanceState)

        mapView.getWemapMapAsync { _, _, _ ->
            checkPermissionsAndSetupLocationSource()
        }
    }

    private fun checkPermissionsAndSetupLocationSource() {
        val permissionsAccepted = when (locationSourceId) {
            1, 2 -> checkGPSPermission() && checkBluetoothPermission()
            0, 3, 4, 5 -> checkGPSPermission()
            7 -> checkGPSPermission() && checkCameraPermission()
            else -> throw Exception("Location source id should be passed in Bundle")
        }
        if (!permissionsAccepted) return

        setupLocationSource()
    }

    private fun checkGPSPermission(): Boolean {
        return if (ContextCompat.checkSelfPermission(
                requireContext(),
                permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            activityResultLauncher.launch(permission.ACCESS_FINE_LOCATION)
            false
        } else {
            true
        }
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

    private fun checkCameraPermission(): Boolean {
        return if (ContextCompat.checkSelfPermission(
                requireContext(),
                permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            activityResultLauncher.launch(permission.CAMERA)
            false
        } else {
            true
        }
    }

    @SuppressLint("MissingPermission")
    private fun setupLocationSource() {
        val locationSource: LocationSource = when (locationSourceId) {
            0 -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                GmsFusedLocationSource(requireContext())
            } else {
                throw Exception(
                    "Requested FusedLocationSource, but it available only starting Android version ${Build.VERSION_CODES.S}."
                            + " Current Android version is ${Build.VERSION.SDK_INT}"
                )
            }
            1 -> PolestarLocationSource(requireContext(), "emulator")
            2 -> PolestarLocationSource(requireContext(), Constants.polestarApiKey)
            else -> throw Exception("Location source id should be passed in Bundle")
        }
        mapView.locationManager.apply {
            source = locationSource
            isEnabled = true
        }
        mapView.map.locationComponent.apply {
            cameraMode = CameraMode.TRACKING_COMPASS
            renderMode = RenderMode.COMPASS
        }
    }

    override fun onStart() {
        super.onStart()
        mapView.onStart()
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
    }

    override fun onStop() {
        super.onStop()
        mapView.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        mapView.onSaveInstanceState(outState)
    }

    override fun onLowMemory() {
        super.onLowMemory()
        mapView.onLowMemory()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        mapView.onDestroy()
    }
}