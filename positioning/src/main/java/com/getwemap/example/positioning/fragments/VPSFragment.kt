package com.getwemap.example.positioning.fragments

import android.Manifest.permission
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.getwemap.example.positioning.databinding.FragmentVpsBinding
import com.getwemap.sdk.core.internal.extensions.disposedBy
import com.getwemap.sdk.core.location.LocationSourceListener
import com.getwemap.sdk.core.model.ServiceFactory
import com.getwemap.sdk.core.model.entities.Attitude
import com.getwemap.sdk.core.model.entities.Coordinate
import com.getwemap.sdk.core.model.entities.GeoJsonItinerarySerializer
import com.getwemap.sdk.core.model.entities.Incline
import com.getwemap.sdk.core.model.entities.Itinerary
import com.getwemap.sdk.core.model.entities.LegSegment
import com.getwemap.sdk.core.model.entities.LevelChange
import com.getwemap.sdk.core.model.entities.LevelChangeType
import com.getwemap.sdk.core.model.entities.MapData
import com.getwemap.sdk.positioning.wemapvpsarcore.WemapVPSARCoreLocationSource
import com.getwemap.sdk.positioning.wemapvpsarcore.WemapVPSARCoreLocationSource.ScanStatus
import com.getwemap.sdk.positioning.wemapvpsarcore.WemapVPSARCoreLocationSource.State
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

    private val itinerarySourceSwitch get() = binding.itinerarySourceSwitch

    private val cameraLayout get() = binding.cameraLayout
    private lateinit var vpsLocationSource: WemapVPSARCoreLocationSource

    private var currentSnackbar: Snackbar? = null
    private var rescanRequested = false

    private val disposeBag = CompositeDisposable()
    private lateinit var mapData: MapData

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentVpsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val mapDataString = requireArguments().getString("mapData")!!
        mapData = Json.decodeFromString(mapDataString)

        setupLocationSource(mapData)

        startScanButton.setOnClickListener { vpsLocationSource.startScan() }
        stopScanButton.setOnClickListener { vpsLocationSource.stopScan() }
        itinerarySourceSwitch.setOnCheckedChangeListener { _, isChecked ->
            itinerarySourceSwitch.text = if (isChecked) "Use manually created Itinerary" else "Use Itinerary provided by Wemap API"
        }

        rescanButton.setOnClickListener {
            rescanRequested = true
            showCamera()
        }
    }

    private fun setupLocationSource(mapData: MapData) {
        vpsLocationSource = WemapVPSARCoreLocationSource(requireContext(), mapData)
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

    /**
     * Once you start receiving updated `Coordinate`s, you can request and then assign an itinerary to `VPSARKitLocationSource`.
     * Assigning an itinerary to `VPSARKitLocationSource` when a user is following an A→B itinerary enhances the overall navigation experience (e.g., itinerary projections, conveyor detection, etc.).
     *
     * For example, when conveying is detected, the system will prompt you to rescan the environment to restore tracking.
     */
    private fun showMapPlaceholder() {
        mapPlaceholder.visibility = View.VISIBLE
        cameraLayout.visibility = View.INVISIBLE
        if (itinerarySourceSwitch.isChecked) {
//            loadItineraryFromGeoJSON()
            hardcodedItinerary()
        } else {
            calculateItinerary()
        }
    }

    private fun calculateItinerary() {

        val origin = Coordinate(48.88007462, 2.35591097, 0f)
        val destination = Coordinate(48.88141308, 2.35747255, -2f)

        ServiceFactory
            .getItineraryProvider()
            .itineraries(origin, destination, mapId = mapData.id)
            .subscribe({ itineraries ->
                vpsLocationSource.itinerary = itineraries.first()
            }, { error ->
                println("Failed to calculate itineraries with error: $error")
            })
            .disposedBy(disposeBag)
    }

    private fun hardcodedItinerary() {
        val origin = Coordinate(48.88007462, 2.35591097, 0f)
        val destination = Coordinate(48.88141308, 2.35747255, -2f)

        val coordinatesLevel0 = listOf(
            listOf(2.3559003, 48.88005135),
            listOf(2.35613366, 48.88000508),
            listOf(2.35623278, 48.87998696),
            listOf(2.35636233, 48.87996258),
            listOf(2.3564454, 48.88014336),
            listOf(2.35657153, 48.88013655)
        ).map { Coordinate(it[1], it[0], 0f) }

        val legSegmentsLevel0 = LegSegment.fromCoordinates(coordinatesLevel0)

        val coordinatesFrom0ToMinus1 = listOf(
            listOf(2.35657153, 48.88013655),
            listOf(2.3567008, 48.8801748)
        ).map { Coordinate(it[1], it[0], listOf(-1f, 0f)) }

        val levelChangeFrom0ToMinus1 = LevelChange(-1f, Incline.DOWN, LevelChangeType.Escalator)
        val legSegmentsFrom0ToMinus1 = LegSegment.fromCoordinates(coordinatesFrom0ToMinus1, levelChangeFrom0ToMinus1)

        val coordinatesLevelMinus1 = listOf(
            listOf(2.3567008, 48.8801748),
            listOf(2.35672744, 48.88017653),
            listOf(2.35684126, 48.88020268),
            listOf(2.35688225, 48.88028507),
            listOf(2.35702803, 48.88032342),
            listOf(2.35714357, 48.88055641),
            listOf(2.35716058, 48.88058641),
            listOf(2.35719467, 48.8805796),
            listOf(2.35723088, 48.88057183),
            listOf(2.357253, 48.88061996)
        ).map { Coordinate(it[1], it[0], -1f) }

        val legSegmentsLevelMinus1 = LegSegment.fromCoordinates(coordinatesLevelMinus1)

        val coordinatesFromMinus1ToMinus2 = listOf(
            listOf(2.357253, 48.88061996),
            listOf(2.35727559, 48.88066565)
        ).map { Coordinate(it[1], it[0], listOf(-2f, -1f)) }

        val levelChangeFromMinus1ToMinus2 = LevelChange(-1f, Incline.DOWN, LevelChangeType.Escalator)
        val legSegmentsFromMinus1ToMinus2 = LegSegment.fromCoordinates(coordinatesFromMinus1ToMinus2, levelChangeFromMinus1ToMinus2)

        val coordinatesLevelMinus2 = listOf(
            listOf(2.35727559, 48.88066565),
            listOf(2.35731332, 48.88074658),
            listOf(2.35728039, 48.88075276),
            listOf(2.35723537, 48.88076096),
            listOf(2.35731739, 48.88094625),
            listOf(2.35738281, 48.88110437),
            listOf(2.35745716, 48.88126764),
            listOf(2.35749811, 48.88135969),
            listOf(2.35752604, 48.88142664),
            listOf(2.35748253, 48.88143515)
        ).map { Coordinate(it[1], it[0], -2f) }

        val legSegmentsLevelMinus2 = LegSegment.fromCoordinates(coordinatesLevelMinus2)

        val segments = legSegmentsLevel0 + legSegmentsFrom0ToMinus1 + legSegmentsLevelMinus1 +
                legSegmentsFromMinus1ToMinus2 + legSegmentsLevelMinus2

        vpsLocationSource.itinerary = Itinerary.fromSegments(origin, destination, segments)
    }

    private fun loadItineraryFromGeoJSON() {
        try {
            val inputStream = requireContext().assets.open("geoJsonItinerary.json")
            val jsonString = inputStream.bufferedReader().use { it.readText() }

            val jsonConverter = Json { ignoreUnknownKeys = true }
            val geoJsonItinerary = jsonConverter.decodeFromString(GeoJsonItinerarySerializer, jsonString)

            vpsLocationSource.itinerary = geoJsonItinerary
        } catch (e: Exception) {
            e.printStackTrace()
            println("Failed to load geo itinerary from file with error - ${e.message}")
        }
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

            override fun onError(error: Throwable) {
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
        return checkCameraPermission() && if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) checkActivityPermission() else true
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

    @RequiresApi(Build.VERSION_CODES.Q)
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