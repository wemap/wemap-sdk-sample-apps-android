@file:OptIn(AlphaVpsLocalApi::class)

package com.getwemap.example.map.positioning.fragments

import android.Manifest.permission
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.res.ColorStateList
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.getwemap.example.common.AlertFactory
import com.getwemap.example.common.HapticGenerator
import com.getwemap.example.common.PermissionHelper
import com.getwemap.example.common.multiline
import com.getwemap.example.map.positioning.AppConstants
import com.getwemap.example.map.positioning.R
import com.getwemap.example.map.positioning.VpsLocalMapDownloader
import com.getwemap.example.map.positioning.VpsLocalSessionRecorder
import com.getwemap.example.map.positioning.databinding.FragmentMapVpsLocalBinding
import com.getwemap.sdk.core.model.entities.Attitude
import com.getwemap.sdk.core.model.entities.Coordinate
import com.getwemap.sdk.core.model.entities.MapData
import com.getwemap.sdk.core.model.entities.PointOfInterest
import com.getwemap.sdk.core.poi.PointOfInterestManagerListener
import com.getwemap.sdk.map.OnMapViewReadyCallback
import com.getwemap.sdk.map.WemapMapView
import com.getwemap.sdk.map.helpers.MapConstants
import com.getwemap.sdk.map.location.UserLocationManager
import com.getwemap.sdk.map.location.UserLocationManagerListener
import com.getwemap.sdk.map.poi.IMapPointOfInterestManager
import com.getwemap.sdk.positioning.wemapvpslocal.AlphaVpsLocalApi
import com.getwemap.sdk.positioning.wemapvpslocal.VpsLocalForegroundService
import com.getwemap.sdk.positioning.wemapvpslocal.VpsLocalLocationSource
import com.getwemap.sdk.positioning.wemapvpslocal.VpsLocalLocationSource.ScanOutcome
import com.getwemap.sdk.positioning.wemapvpslocal.VpsLocalLocationSource.ScanStatus
import com.getwemap.sdk.positioning.wemapvpslocal.VpsLocalLocationSourceListener
import com.getwemap.sdk.positioning.wemapvpslocal.constants.VpsLocalConstants
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.serialization.json.Json
import org.maplibre.android.MapLibre
import org.maplibre.android.location.OnCameraTrackingChangedListener
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.location.modes.RenderMode
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.time.Duration.Companion.seconds

/** A scan event this recent means the loop is actively producing results. */
private const val SCAN_EVENT_FRESH_MS = 3_000L

/** Grace on top of `MAX_SCAN_INTERVAL_MS` before a silent loop is reported as stalled. */
private const val SCAN_EVENT_STALLED_SLACK_MS = 5_000L

/** Duration of the activity-dot flash fired on every scan event. */
private const val SCAN_DOT_FADE_MS = 900L

/**
 * Map + offline VPS positioning sample. Adapted from [MapVPSFragment], but backed by the offline,
 * ARCore-free [VpsLocalLocationSource]:
 * - the map database is loaded from a downloaded directory (see [VpsLocalMapDownloader]),
 * - the camera preview is rendered by the SDK into the [PreviewView] we pass in,
 * - there is no ARCore tracking / degraded-positioning state machine — positioning is discrete
 *   (a fix per successful scan) with live, VPS-anchored heading in between.
 *
 * Deliberately scoped to scanning and positioning: unlike the other map samples it carries no
 * itinerary or navigation flow, so nothing competes with the scan UI. POI selection is kept — it is
 * a convenient way to check a fix against a known place on the map.
 */
@SuppressLint("MissingPermission")
class MapVPSLocalFragment : Fragment(), OnMapViewReadyCallback {

    enum class AppState { BROWSING, POI_SELECTED, SCANNING }

    private var _binding: FragmentMapVpsLocalBinding? = null
    private val binding get() = _binding!!

    private val applicationContext get() = requireContext().applicationContext
    private val mapView get() = binding.mapView
    private val pointOfInterestManager: IMapPointOfInterestManager get() = mapView.pointOfInterestManager
    private val locationManager: UserLocationManager get() = mapView.locationManager

    private lateinit var permissionHelper: PermissionHelper
    private lateinit var vpsLocationSource: VpsLocalLocationSource

    private var scanningTimerJob: Job? = null
    private var errorTimerJob: Job? = null
    private var isScreenWakeLockEnabled = false

    // region Scan liveness — see updateScanStatus / renderScanStatus
    /** Latest scan event message, rendered under the liveness header. */
    private var lastScanMessage = "Scanning…"
    /** [SystemClock.elapsedRealtime] of the last scan event, or `null` while none has arrived yet. */
    private var lastScanEventElapsedMs: Long? = null
    /** Number of successful localizations of this scan session. */
    private var fixCount = 0

    /** Writes this session to disk for later review; null only after teardown. */
    private var sessionRecorder: VpsLocalSessionRecorder? = null
    /** Ticker refreshing the "…s ago" readout (and the dot colour) while scanning. */
    private var scanActivityJob: Job? = null
    // endregion

    private val haptic: HapticGenerator? by lazy {
        if (AppConstants.ENABLE_HAPTIC_FEEDBACK) HapticGenerator(requireContext()) else null
    }

    // region Lifecycle
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        MapConstants.STALE_TIMEOUT_MILLISECONDS = 30_000
        MapLibre.getInstance(applicationContext)
        _binding = FragmentMapVpsLocalBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        createPermissionsHelper()

        mapView.onCreate(savedInstanceState)

        val mapDataString = requireArguments().getString("mapData")!!
        val mapData = Json.decodeFromString<MapData>(mapDataString)
        mapView.mapData = mapData

        // Map database directory downloaded in advance (see VpsLocalMapDownloader) — one per map id.
        val mapDir = requireArguments().getString("mapDir")?.let { File(it) }
            ?: VpsLocalMapDownloader.mapDir(applicationContext, mapData.id)

        // Create the offline location source. The SDK owns the camera and renders the live preview
        // into the PreviewView we pass in. Background scanning (keeps running when the screen is off /
        // the app is backgrounded) is opt-in via a foreground-service config, toggleable in Preferences.
        val foregroundServiceConfig =
            if (AppConstants.VPS_LOCAL_BACKGROUND_SCANNING_ENABLED) buildForegroundServiceConfig() else null
        // Passing no PreviewView makes the SDK bind still-capture only. Hide the (then permanently
        // black) surface so the overlay reads as intentional rather than broken.
        val previewView =
            if (AppConstants.VPS_LOCAL_CAMERA_PREVIEW_ENABLED) binding.previewView else null
        binding.previewView.isVisible = previewView != null
        vpsLocationSource = VpsLocalLocationSource(
            applicationContext, mapDir, mapData, previewView,
            foregroundService = foregroundServiceConfig,
        )
        // Records this session to `<map dir>/history/`, so a walk with the screen unwatched (lanyard,
        // pocket, screen off) can be reviewed afterwards — see VpsLocalHistoryFragment.
        sessionRecorder = VpsLocalSessionRecorder(applicationContext, mapData.id)

        // to prevent interactions with MapView before it's loaded
        binding.locateMe.isEnabled = false

        mapView.getMapViewAsync(this)

        binding.locateMe.setOnClickListener { locateMeButtonClicked() }
        binding.camera.setOnClickListener { cameraButtonClicked() }
        binding.stopScanButton.setOnClickListener { stopScan() }

        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : androidx.activity.OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    handleBackPressed()
                }
            })
    }

    override fun onMapViewReady(mapView: WemapMapView, map: MapLibreMap, style: Style, data: MapData) {
        // Register the offline VPS scan listener
        vpsLocationSource.listeners.add(vpsListener)
        // Bind location source to the map to show the blue dot from VPS
        // This action can be done only when mapView is ready
        locationManager.locationSource = vpsLocationSource
        // It enables the blue dot orientation rendering
        locationManager.renderMode = RenderMode.COMPASS

        locationManager.addListener(locationManagerListener)
        pointOfInterestManager.addListener(poiListener)

        mapView.map.addOnMapClickListener {
            if (getAppState() == AppState.POI_SELECTED)
                pointOfInterestManager.unselectPOI()

            return@addOnMapClickListener true
        }

        locationManager.addOnCameraTrackingChangedListener(object : OnCameraTrackingChangedListener {
            override fun onCameraTrackingDismissed() {
                updateLocateMeButtonIcon()
            }
            override fun onCameraTrackingChanged(currentMode: Int) {
                updateLocateMeButtonIcon()
            }
        })

        binding.levelsSwitcher.bind(mapView.buildingManager)
        binding.locateMe.isEnabled = true

        mapView.map.uiSettings.attributionGravity = Gravity.START or Gravity.BOTTOM
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
        errorTimerJob?.cancel()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        mapView.onSaveInstanceState(outState)
    }

    @Deprecated("Deprecated in Java")
    override fun onLowMemory() {
        super.onLowMemory()
        mapView.onLowMemory()
    }

    override fun onDestroyView() {
        super.onDestroyView()

        scanActivityJob?.cancel()
        binding.levelsSwitcher.unbind()

        if (mapView.isLoaded) {
            pointOfInterestManager.removeListener(poiListener)
            locationManager.removeListener(locationManagerListener)
            locationManager.locationSource = null
        }
        vpsLocationSource.listeners.remove(vpsListener)
        vpsLocationSource.deinit()
        // Closed after deinit(), so events emitted while the source shuts down still reach the file.
        sessionRecorder?.close()
        sessionRecorder = null

        // Ensure screen wake lock is disabled when fragment is destroyed
        if (isScreenWakeLockEnabled) {
            requireActivity().window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            isScreenWakeLockEnabled = false
        }

        mapView.onDestroy()
        MapConstants.STALE_TIMEOUT_MILLISECONDS = 5_000

        _binding = null

        System.gc()
    }
    // endregion

    // region Location
    private fun locateMeButtonClicked() {
        if (!locationManager.isEnabled) {
            locateUser()
            return
        }
        // Already localized — cycle camera tracking modes.
        toggleNextUserTrackingMode()
    }

    private fun cameraButtonClicked() {
        locateUser("Do you think your position is inaccurate? Scan again")
    }

    private fun toggleNextUserTrackingMode() {
        locationManager.cameraMode = when (locationManager.cameraMode) {
            CameraMode.NONE -> CameraMode.TRACKING
            CameraMode.TRACKING -> CameraMode.TRACKING_COMPASS
            else /* TRACKING_COMPASS */ -> CameraMode.NONE
        }
    }

    private fun enableFollowIfNotAlreadyEnabled() {
        if (locationManager.cameraMode < CameraMode.TRACKING)
            locationManager.cameraMode = CameraMode.TRACKING
    }

    private fun locateUser(message: String? = null) {
        val message = message ?: "In order to be localized we will use your camera"

        lifecycleScope.launch {
            runCatching {
                checkLocationSource(message)
            }.onSuccess {
                startScan()
                enableFollowIfNotAlreadyEnabled()
            }.onFailure {
                Snackbar.make(mapView, it.message ?: "Failed to locate you", Snackbar.LENGTH_SHORT).show()
            }
        }
    }

    private suspend fun checkLocationSource(message: String) {
        checkPermissions()
        askForScan(message)
        startLocationSource()
    }

    private fun startLocationSource() {
        locationManager.isEnabled = true
    }

    private fun startScan() {
        vpsLocationSource.startScan()
        binding.camera.isVisible = true
        binding.cameraLayout.visibility = View.VISIBLE
        lastScanMessage = "Scanning…"
        lastScanEventElapsedMs = null
        fixCount = 0
        startScanActivityTicker()
        renderScanStatus()
        createScanningTimer()
        updateScreenWakeLock()
    }

    private fun stopScan() {
        vpsLocationSource.stopScan()
        _binding?.cameraLayout?.visibility = View.INVISIBLE
        // Scanning has fully stopped — hide both status surfaces.
        scanActivityJob?.cancel()
        _binding?.scanStatusText?.isVisible = false
        _binding?.mapScanStatusChip?.isVisible = false
        scanningTimerJob?.cancel()
        updateScreenWakeLock()
    }

    /**
     * Continuous mode: dismiss the full-screen scan overlay to reveal the map, but keep the SDK
     * scanning in the background so each new fix re-anchors dead reckoning (drift auto-correction).
     */
    private fun revealMapKeepScanning() {
        _binding?.cameraLayout?.visibility = View.INVISIBLE
        scanningTimerJob?.cancel()
        updateScreenWakeLock()
        // Overlay is now hidden but the SDK keeps scanning — move the status onto the map chip.
        renderScanStatus()
    }

    private fun updateLocateMeButtonIcon() {
        val iconID: Int = when (locationManager.cameraMode) {
            CameraMode.TRACKING -> R.drawable.baseline_my_location_24
            CameraMode.TRACKING_COMPASS -> R.drawable.explore_24px
            else /* NONE */ -> R.drawable.location_searching_24px
        }
        binding.locateMe.setImageDrawable(ContextCompat.getDrawable(requireContext(), iconID))
    }

    private suspend fun askForScan(message: String) {
        return AlertFactory.showSimpleAlert(
            requireContext(), message, "User refused to open camera", "Open camera"
        )
    }

    private val locationManagerListener by lazy {
        UserLocationManagerListener { error ->
            setErrorMessageAndStartTimer(error)
        }
    }

    private fun setErrorMessageAndStartTimer(error: Throwable) {
        binding.cameraDebugText.apply {
            isVisible = true
            text = error.message
        }
        errorTimerJob = lifecycleScope.launch {
            delay(1.seconds)
            _binding?.cameraDebugText?.apply {
                isVisible = false
                text = ""
            }
        }
    }

    private val vpsListener by lazy {
        object : VpsLocalLocationSourceListener {
            override fun onLocalized(coordinate: Coordinate, attitude: Attitude) {
                Log.d("WEMAP", "onLocalized. Coordinate: $coordinate")
                // Recorded before hopping to the UI thread, and unconditionally: the session file must
                // reflect what the SDK did, not what this screen happened to be showing.
                sessionRecorder?.recordFix(coordinate, attitude)
                runOnUi {
                    fixCount++
                    // Every fix buzzes — in continuous mode this is the only feedback that a scan
                    // succeeded while the phone is pocketed / on a lanyard and the map is not watched.
                    haptic?.success()
                    onScanEvent("Localized ✓")
                    // Only act on the first fix that ends the scan-overlay session; later continuous
                    // fixes just update the dot underneath the (already-hidden) overlay.
                    if (_binding?.cameraLayout?.visibility != View.VISIBLE)
                        return@runOnUi
                    if (binding.continuousScanSwitch.isChecked) {
                        // Continuous: reveal the map but keep scanning so fixes keep re-anchoring PDR.
                        revealMapKeepScanning()
                    } else {
                        // One-shot: stop scanning; the dot then rides on dead reckoning until re-scan.
                        stopScan()
                    }
                }
            }

            override fun onScanFailed(outcome: ScanOutcome) {
                Log.d("WEMAP", "onScanFailed. Outcome: ${outcome.name}")
                sessionRecorder?.recordOutcome(outcome)
                runOnUi { onScanEvent(scanOutcomeMessage(outcome)) }
            }

            override fun onError(error: Throwable) {
                Log.e("WEMAP", "VPS local error", error)
                sessionRecorder?.recordError(error)
                runOnUi { onScanEvent("⚠ Error: ${error.message ?: error}") }
            }
        }
    }

    // region Scan status / liveness
    /**
     * Records a scan event — a localization, a [ScanOutcome], or an error. The SDK emits exactly one
     * per scan attempt, so an event is also the proof that the loop just ran: it flashes the activity
     * dot and restarts the "…s ago" countdown on top of storing the message.
     */
    private fun onScanEvent(message: String) {
        lastScanMessage = message
        lastScanEventElapsedMs = SystemClock.elapsedRealtime()
        flashScanActivityDot()
        renderScanStatus()
    }

    /**
     * Live scan-status readout: a liveness header (see [scanActivity]) plus the latest scan event, so
     * the scanner's behaviour is visible during on-device tests instead of only in logcat. Routed to
     * whichever surface is showing: the full-screen overlay readout while the overlay is up, otherwise
     * the compact map chip (continuous background scanning, when the overlay is hidden).
     */
    private fun renderScanStatus() {
        val binding = _binding ?: return
        val overlayVisible = binding.cameraLayout.visibility == View.VISIBLE
        val activity = scanActivity()
        val text = "${scanLivenessHeader(activity)}\n$lastScanMessage"

        binding.scanStatusText.apply {
            isVisible = overlayVisible
            this.text = text
        }
        binding.mapScanStatusChip.isVisible = !overlayVisible && activity != ScanActivity.STOPPED
        binding.mapScanStatusText.text = text
        binding.scanActivityDot.apply {
            backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(context, activity.colorRes))
        }
    }

    /**
     * Whether the scan loop is running, and whether it is actually producing events. The loop is
     * deliberately quiet while the movement-aware cadence holds off a scan (no event is emitted then),
     * hence [QUIET]; going silent for longer than the SDK's own max scan interval is not expected.
     */
    private enum class ScanActivity(val colorRes: Int) {
        ACTIVE(R.color.scan_active),
        QUIET(R.color.scan_idle),
        STALLED(R.color.scan_stalled),
        STOPPED(R.color.scan_stopped),
    }

    private fun scanActivity(): ScanActivity {
        if (vpsLocationSource.scanStatus != ScanStatus.STARTED)
            return ScanActivity.STOPPED

        val last = lastScanEventElapsedMs
            ?: return ScanActivity.ACTIVE // starting up — the first scan has not completed yet
        val ageMs = SystemClock.elapsedRealtime() - last
        return when {
            ageMs <= SCAN_EVENT_FRESH_MS -> ScanActivity.ACTIVE
            // A stationary user is re-scanned only every MAX_SCAN_INTERVAL_MS; anything beyond that
            // (plus the duration of a scan itself) means the loop is no longer getting through.
            ageMs <= VpsLocalConstants.MAX_SCAN_INTERVAL_MS + SCAN_EVENT_STALLED_SLACK_MS -> ScanActivity.QUIET
            else -> ScanActivity.STALLED
        }
    }

    private fun scanLivenessHeader(activity: ScanActivity): String {
        if (activity == ScanActivity.STOPPED)
            return "Scan stopped"

        val fixes = "$fixCount ${if (fixCount == 1) "fix" else "fixes"}"
        val last = lastScanEventElapsedMs
            ?: return "◆ Scanning • $fixes • starting…"
        val ageSec = (SystemClock.elapsedRealtime() - last) / 1_000
        return when (activity) {
            ScanActivity.ACTIVE -> "◆ Scanning • $fixes • last scan ${ageSec}s ago"
            ScanActivity.QUIET -> "◆ Scanning • $fixes • quiet ${ageSec}s (waiting for movement)"
            else -> "⚠ No scan for ${ageSec}s • $fixes"
        }
    }

    /** Bright flash fading back down, so a completed scan is visible even at a glance. */
    private fun flashScanActivityDot() {
        val dot = _binding?.scanActivityDot ?: return
        dot.animate().cancel()
        dot.alpha = 1f
        dot.animate().alpha(0.25f).setDuration(SCAN_DOT_FADE_MS).start()
    }

    /** Keeps the "…s ago" readout and the dot colour honest while no event arrives. */
    private fun startScanActivityTicker() {
        scanActivityJob?.cancel()
        scanActivityJob = lifecycleScope.launch {
            while (isActive) {
                renderScanStatus()
                delay(1.seconds)
            }
        }
    }
    // endregion

    /** Human-readable description of every non-success [ScanOutcome], with the enum name for reference. */
    private fun scanOutcomeMessage(outcome: ScanOutcome): String {
        val hint = when (outcome) {
            ScanOutcome.SUCCESS -> "Localized"
            ScanOutcome.OUTLIER_REJECTED -> "Fix rejected as an outlier — keep scanning"
            ScanOutcome.BUFFERING -> "Collecting frames…"
            ScanOutcome.NO_CANDIDATES -> "No matching place found — try another spot"
            ScanOutcome.NO_POSITIVE_CLUSTER -> "No confident match — try another spot"
            ScanOutcome.NO_DOMINANT_CLUSTER -> "Ambiguous match — move and scan again"
            ScanOutcome.INCLINATION_TOO_LOW -> "Hold your phone vertically to scan your surroundings"
            ScanOutcome.LOW_TEXTURE -> "Too plain — point at shops, signs or landmarks"
            ScanOutcome.TOO_BLURRY -> "Hold still — the image is too blurry"
            ScanOutcome.PAUSED_FLAT -> "Raise your phone upright to resume scanning"
            ScanOutcome.CAPTURE_TIMED_OUT -> "Camera stalled — restarting it"
        }
        return "$hint (${outcome.name})"
    }

    /** Listener callbacks arrive on a background scan thread; hop to the main thread for UI. */
    private fun runOnUi(block: () -> Unit) {
        val activity = activity ?: return
        activity.runOnUiThread {
            if (_binding == null) return@runOnUiThread
            block()
        }
    }

    private fun createScanningTimer() {
        scanningTimerJob = lifecycleScope.launch {
            delay(20.seconds)
            askToContinue()
        }
    }

    private fun updateScreenWakeLock() {
        // Keep the screen awake whenever the SDK is scanning — including continuous background
        // scanning, when the overlay is hidden.
        val shouldEnable = vpsLocationSource.scanStatus == ScanStatus.STARTED

        if (shouldEnable && !isScreenWakeLockEnabled) {
            requireActivity().window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            isScreenWakeLockEnabled = true
        } else if (!shouldEnable && isScreenWakeLockEnabled) {
            requireActivity().window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            isScreenWakeLockEnabled = false
        }
    }

    private fun askToContinue() {
        scanningTimerJob = lifecycleScope.launch {
            runCatching {
                AlertFactory.showSimpleAlert(
                    requireContext(), "We cannot localize you. Do you want to continue to try?",
                    "You decided to get back to the map", "Continue", "Back to map"
                )
            }.onSuccess {
                createScanningTimer()
            }.onFailure {
                stopScan()
                val text = "Failed to localize you in reasonable time. Try again later"
                Snackbar.make(mapView, text, Snackbar.LENGTH_LONG).multiline().show()
            }
        }
    }
    // endregion

    // region PoIs
    private fun renderPoI(poi: PointOfInterest) {
        hideAllStatesUI()
        binding.poiContainer.visibility = View.VISIBLE
        binding.poiInfo.text = poi.name
    }

    private val poiListener by lazy {
        PointOfInterestManagerListener(
            {
                renderPoI(it)
            },
            {
                hideAllStatesUI()
            }
        )
    }
    // endregion

    // region Misc
    private fun hideAllStatesUI() {
        binding.poiContainer.visibility = View.GONE
    }

    private fun getAppState(): AppState {
        return when {
            // "Scanning" is the full-screen overlay session, not the SDK's scan status: in continuous
            // mode the SDK keeps scanning in the background while the app is browsing.
            _binding?.cameraLayout?.visibility == View.VISIBLE -> AppState.SCANNING
            pointOfInterestManager.getSelectedPOI() != null -> AppState.POI_SELECTED
            else -> AppState.BROWSING
        }
    }

    private fun handleBackPressed() {
        if (!mapView.isLoaded) {
            findNavController().navigateUp()
            return
        }

        when (getAppState()) {
            AppState.SCANNING -> stopScan()
            AppState.POI_SELECTED -> pointOfInterestManager.unselectPOI()
            // Navigate back to the previous fragment
            else -> findNavController().navigateUp()
        }
    }
    // endregion

    // region Background scanning (foreground service)
    /**
     * Builds the config that enables background scanning. The app owns the notification UX entirely:
     * we create the channel, then build an ongoing notification whose tap action reopens the app. The
     * SDK just posts this while its [VpsLocalForegroundService] holds the camera privilege + wake lock.
     */
    private fun buildForegroundServiceConfig(): VpsLocalForegroundService.Config {
        val context = requireContext()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Offline VPS scanning",
                NotificationManager.IMPORTANCE_LOW,
            )
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)
        val contentIntent = PendingIntent.getActivity(
            context, 0, launchIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
            .setContentTitle("Positioning active")
            .setContentText("Scanning your surroundings to keep your location up to date")
            .setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setOngoing(true)
            .setContentIntent(contentIntent)
            .build()
        return VpsLocalForegroundService.Config(NOTIFICATION_ID, notification)
    }
    // endregion

    // region Permissions
    private fun createPermissionsHelper() {
        // Only CAMERA is mandatory (see requestPermissions); the other two are optional, and each one
        // degrades a distinct feature when denied:
        //  - ACTIVITY_RECOGNITION (API 29+) gates Sensor.TYPE_STEP_DETECTOR, so without it the SDK's
        //    dead reckoning is disabled — the position holds at the last fix between scans and the scan
        //    loop falls back to MAX_SCAN_INTERVAL_MS. Silent apart from a PdrHandler warning, so it is
        //    requested here rather than left to chance.
        //  - POST_NOTIFICATIONS (API 33+) makes the background-scan notification visible.
        val permissions = mutableListOf(permission.CAMERA)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            permissions.add(permission.ACTIVITY_RECOGNITION)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions.add(permission.POST_NOTIFICATIONS)
        }
        permissionHelper = PermissionHelper(this, permissions)
    }

    private suspend fun checkPermissions() {
        if (permissionHelper.allGranted())
            return

        AlertFactory.showSimpleAlert(
            requireContext(),
            "In order to be localized, we will use your camera. Please accept following permissions",
            "User refused to review permissions"
        )
        requestPermissions()
    }

    private suspend fun requestPermissions() = suspendCancellableCoroutine { continuation ->
        permissionHelper.request { _, denied ->
            // Only CAMERA is mandatory; a denied POST_NOTIFICATIONS just hides the scan notification,
            // and a denied ACTIVITY_RECOGNITION only disables dead reckoning between fixes.
            if (denied.none { it == permission.CAMERA })
                continuation.resume(Unit)
            else
                continuation.resumeWithException(Throwable("User denied required permissions"))
        }
    }
    // endregion

    private companion object {
        const val NOTIFICATION_CHANNEL_ID = "vps_local_scanning"
        const val NOTIFICATION_ID = 2_001
    }
}
