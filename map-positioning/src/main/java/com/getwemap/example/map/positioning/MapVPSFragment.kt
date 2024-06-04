package com.getwemap.example.map.positioning

import android.Manifest.permission
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.getwemap.example.map.positioning.databinding.FragmentMapVpsBinding
import com.getwemap.sdk.map.model.entities.MapData
import com.getwemap.sdk.positioning.wemapvpsarcore.WemapVPSARCoreLocationSource
import com.getwemap.sdk.positioning.wemapvpsarcore.WemapVPSARCoreLocationSource.*
import com.getwemap.sdk.positioning.wemapvpsarcore.WemapVPSARCoreLocationSourceError
import com.getwemap.sdk.positioning.wemapvpsarcore.WemapVPSARCoreLocationSourceListener
import com.getwemap.sdk.positioning.wemapvpsarcore.WemapVPSARCoreLocationSourceObserver
import com.google.android.material.snackbar.Snackbar
import com.google.ar.core.TrackingFailureReason
import com.mapbox.mapboxsdk.Mapbox
import com.mapbox.mapboxsdk.location.modes.CameraMode
import com.mapbox.mapboxsdk.location.modes.RenderMode
import kotlinx.serialization.json.Json

class MapVPSFragment : BaseFragment() {

    override val mapView get() = binding.mapView
    override val levelToggle get() = binding.levelToggle

    private lateinit var binding: FragmentMapVpsBinding
    private val surfaceView get() = binding.surfaceView

    private var rescanRequested = false

    private val vpsLocationSource by lazy {
        WemapVPSARCoreLocationSource(requireContext(), Constants.vpsEndpoint)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        Mapbox.getInstance(requireContext())
        binding = FragmentMapVpsBinding.inflate(inflater, container, false)
        vpsLocationSource.bind(surfaceView) // you can't create GLSurfaceView and set renderer later
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.startScanButton.setOnClickListener { vpsLocationSource.startScan() }
        binding.stopScanButton.setOnClickListener { vpsLocationSource.stopScan() }

        binding.rescanButton.setOnClickListener {
            rescanRequested = true
            hideMapShowCamera()
        }
    }

    override fun checkPermissionsAndSetupLocationSource() {
        val permissionsAccepted = checkGPSPermission() && checkCameraPermission()
        if (!permissionsAccepted) return
        setupLocationSource()
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
    override fun setupLocationSource() {
        vpsLocationSource.listeners.add(vpsListener)
        vpsLocationSource.observers.add(vpsObserver)

        mapView.locationManager.apply {
            source = vpsLocationSource
            isEnabled = true
        }
        mapView.map.locationComponent.apply {
            cameraMode = CameraMode.TRACKING_COMPASS
            renderMode = RenderMode.COMPASS
        }
    }

    private fun hideMapShowCamera() {
        binding.mapLayout.visibility = View.INVISIBLE
        binding.cameraLayout.visibility = View.VISIBLE
        binding.scanButtons.isEnabled = true
    }

    override fun onStop() {
        super.onStop()
        vpsLocationSource.stop()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        vpsLocationSource.unbind()
    }

    private fun updateScanButtonsState(status: ScanStatus) {
        val isScanning = status == ScanStatus.STARTED
        binding.startScanButton.isEnabled = !isScanning
        binding.stopScanButton.isEnabled = isScanning
    }

    private fun showCameraHideMap() {
        binding.mapLayout.visibility = View.VISIBLE
        binding.cameraLayout.visibility = View.INVISIBLE
        binding.scanButtons.isEnabled = true
    }

    private val vpsListener by lazy {
        object : WemapVPSARCoreLocationSourceListener {

            override fun onScanStatusChanged(status: ScanStatus) {
                binding.debugTextTitle.text = "Scan status - $status"
                updateScanButtonsState(status)
                if (status == ScanStatus.STOPPED && vpsLocationSource.state == State.NORMAL) {
                    showCameraHideMap()
                }
            }

            override fun onStateChanged(state: State) {
                println("state -> $state")
                when(state) {
                    State.NORMAL -> {
                        if (rescanRequested) return
                        showCameraHideMap()
                    }
                    State.SCAN_REQUIRED -> {
                        rescanRequested = false
                        hideMapShowCamera()
                    }
                    State.NO_TRACKING -> {
                        binding.scanButtons.isEnabled = false
                    }
                }
            }

            override fun onTrackingFailureReasonChanged(reason: TrackingFailureReason) {
                binding.debugTextMessage.text = "Tracking failure reason - $reason"
            }

            override fun onError(error: WemapVPSARCoreLocationSourceError) {
                binding.debugTextMessage.text = "Error occurred - $error"
            }
        }
    }

    private val vpsObserver by lazy {
        object : WemapVPSARCoreLocationSourceObserver {
            override fun onImageSend(bitmap: Bitmap) {
                println("onImageSend")
                binding.imageview.setImageBitmap(bitmap)
            }
        }
    }
}