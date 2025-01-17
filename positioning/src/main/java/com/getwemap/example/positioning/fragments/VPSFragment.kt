package com.getwemap.example.positioning.fragments

import android.Manifest.permission
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.getwemap.example.common.Constants
import com.getwemap.example.positioning.databinding.FragmentVpsBinding
import com.getwemap.sdk.core.internal.extensions.disposedBy
import com.getwemap.sdk.core.location.LocationSourceListener
import com.getwemap.sdk.core.model.entities.Attitude
import com.getwemap.sdk.core.model.entities.Coordinate
import com.getwemap.sdk.core.model.entities.MapData
import com.getwemap.sdk.positioning.wemapvpsarcore.WemapVPSARCoreLocationSource
import com.getwemap.sdk.positioning.wemapvpsarcore.WemapVPSARCoreLocationSource.ScanStatus
import com.getwemap.sdk.positioning.wemapvpsarcore.WemapVPSARCoreLocationSource.State
import com.getwemap.sdk.positioning.wemapvpsarcore.WemapVPSARCoreLocationSourceError
import com.getwemap.sdk.positioning.wemapvpsarcore.WemapVPSARCoreLocationSourceListener
import com.google.android.material.snackbar.Snackbar
import com.google.ar.core.TrackingFailureReason
import io.reactivex.rxjava3.android.schedulers.AndroidSchedulers
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.disposables.Disposable
import kotlinx.serialization.json.Json

class VPSFragment : Fragment() {

    private var _binding: FragmentVpsBinding? = null
    private val binding get() = _binding!!

    private val mapPlaceholder get() = binding.mapLayout
    private val rescanButton get() = binding.rescanButton

    private val debugTextState get() = binding.debugTextState
    private val debugTextScanStatus get() = binding.debugTextScanStatus
    private val debugTextCoordinate get() = binding.debugTextCoordinate
    private val debugTextAttitude get() = binding.debugTextAttitude
    private val debugTextHeading get() = binding.debugTextHeading

    private val startScanButton get() = binding.startScanButton
    private val stopScanButton get() = binding.stopScanButton

    private val cameraLayout get() = binding.cameraLayout
    private lateinit var vpsLocationSource: WemapVPSARCoreLocationSource

    private var currentSnackbar: Snackbar? = null
    private var rescanRequested = false

    private val disposeBag = CompositeDisposable()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentVpsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val mapDataString = requireArguments().getString("mapData")!!
        val mapData: MapData = Json.decodeFromString(mapDataString)

        setupLocationSource(mapData)

        startScanButton.setOnClickListener { vpsLocationSource.startScan() }
        stopScanButton.setOnClickListener { vpsLocationSource.stopScan() }

        rescanButton.setOnClickListener {
            rescanRequested = true
            showCamera()
        }
    }

    private fun setupLocationSource(mapData: MapData) {
        vpsLocationSource = WemapVPSARCoreLocationSource(
            requireContext(), mapData.extras?.vpsEndpoint ?: Constants.vpsEndpoint
        )
        vpsLocationSource.bind(requireContext(), binding.surfaceView)

        vpsLocationSource.vpsListeners.add(vpsListener)
        vpsLocationSource.listener = locationListener

        checkPermissionsAndStartLocationSource()
    }

    private fun startLocationSource() {
        vpsLocationSource.start()

        debugTextState.text = "${vpsLocationSource.state}"
        debugTextScanStatus.text = "${vpsLocationSource.scanStatus}"
    }

    // UI

    private fun showCamera() {
        mapPlaceholder.visibility = View.INVISIBLE
        cameraLayout.visibility = View.VISIBLE
    }

    private fun showMapPlaceholder() {
        mapPlaceholder.visibility = View.VISIBLE
        cameraLayout.visibility = View.INVISIBLE
    }

    private fun updateScanButtons(status: ScanStatus) {
        val isScanning = status == ScanStatus.STARTED
        startScanButton.isEnabled = !isScanning
        stopScanButton.isEnabled = isScanning
    }

    private fun showError(message: String) {
        currentSnackbar?.dismiss()
        currentSnackbar = Snackbar.make(requireView(), message, Snackbar.LENGTH_LONG)
        currentSnackbar!!.show()
    }

    // Lifecycle

    override fun onStart() {
        if (permissionsAccepted)
            vpsLocationSource.start()
        super.onStart()
    }

    override fun onStop() {
        super.onStop()
        if (permissionsAccepted)
            vpsLocationSource.stop()
    }

    override fun onDestroyView() {
        println("onDestroyView")
        super.onDestroyView()
        disposeBag.dispose()
        vpsLocationSource.listener = null
        vpsLocationSource.vpsListeners.remove(vpsListener)
        vpsLocationSource.unbind()
        _binding = null
    }

    // Listeners

    private val locationListener by lazy {
        object : LocationSourceListener {
            override fun onCoordinateChanged(coordinate: Coordinate) {
                runOnMainThread {
                    debugTextCoordinate.text = String
                        .format("lat: %.6f, lng: %.6f, lvl: ${coordinate.levels}", coordinate.latitude, coordinate.longitude)
                }.disposedBy(disposeBag)
            }

            override fun onAttitudeChanged(attitude: Attitude) {
                runOnMainThread {
                    val q = attitude.quaternion
                    debugTextAttitude.text = String.format("w: %.2f, x: %.2f, y: %.2f, z: %.2f", q.w, q.x, q.y, q.z)
                    debugTextHeading.text = String.format("%.2f", attitude.headingDegrees)
                }.disposedBy(disposeBag)
            }

            override fun onError(error: Error) {
                runOnMainThread {
                    showError("LS: $error")
                }.disposedBy(disposeBag)
            }
        }
    }

    private fun runOnMainThread(closure: Runnable): Disposable {
        return AndroidSchedulers.mainThread().scheduleDirect(closure)
    }

    private val vpsListener by lazy {

        object : WemapVPSARCoreLocationSourceListener {

            override fun onStateChanged(state: State) {
                debugTextState.text = "$state"

                // if rescan requested - don't update UI on state changes. UI will be updated on scan status change
                if (rescanRequested)
                    return

                when(state) {
                    State.ACCURATE_POSITIONING, State.DEGRADED_POSITIONING ->
                        showMapPlaceholder()
                    State.NOT_POSITIONING ->
                        showCamera()
                }
            }

            override fun onScanStatusChanged(status: ScanStatus) {
                debugTextScanStatus.text = "$status"
                updateScanButtons(status)

                // rescan successful, reset rescanRequested and update UI
                if (status == ScanStatus.STOPPED && vpsLocationSource.state == State.ACCURATE_POSITIONING) {
                    rescanRequested = false
                    showMapPlaceholder()
                }
            }

            override fun onNotPositioningReasonChanged(reason: WemapVPSARCoreLocationSource.NotPositioningReason) {
                showError("Not positioning reason: $reason")
            }

            override fun onTrackingFailureReasonChanged(reason: TrackingFailureReason) {
                showError("Tracking failure reason: $reason")
            }

            override fun onError(error: WemapVPSARCoreLocationSourceError) {
                showError("VPS LS: ${error.message}")
            }
        }
    }

    // Permissions

    private val activityResultLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) {
            checkPermissionsAndStartLocationSource()
        } else {
            val text = "In order to make sample app work properly you have to accept required permission"
            Snackbar.make(requireView(), text, Snackbar.LENGTH_LONG).show()
        }
    }

    private fun checkPermissionsAndStartLocationSource() {
        if (!permissionsAccepted) return
        startLocationSource()
    }

    private val permissionsAccepted: Boolean get() {
        return checkCameraPermission() && checkActivityPermission()
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

    private fun checkActivityPermission(): Boolean {
        return if (ContextCompat.checkSelfPermission(
                requireContext(),
                permission.ACTIVITY_RECOGNITION) != PackageManager.PERMISSION_GRANTED) {
            activityResultLauncher.launch(permission.ACTIVITY_RECOGNITION)
            false
        } else {
            true
        }
    }
}