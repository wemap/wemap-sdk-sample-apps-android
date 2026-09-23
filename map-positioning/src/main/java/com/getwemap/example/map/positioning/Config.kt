@file:OptIn(AlphaVpsLocalApi::class)

package com.getwemap.example.map.positioning

import android.content.Context
import com.getwemap.example.common.AppPreferences
import com.getwemap.example.common.IPreferenceKey
import com.getwemap.example.common.getBoolean
import com.getwemap.example.common.getString
import com.getwemap.example.map.positioning.Config.makeMapViewConfig
import com.getwemap.sdk.core.configs.SessionConfig
import com.getwemap.sdk.map.configs.MapViewConfig
import com.getwemap.sdk.positioning.wemapvpsarcore.configs.VpsConfig
import com.getwemap.sdk.positioning.wemapvpsarcore.configs.VpsControllerConfig
import com.getwemap.sdk.positioning.wemapvpsarcore.configs.VpsConveyingDetectorConfig
import com.getwemap.sdk.positioning.wemapvpsarcore.configs.VpsLocationSourceConfig
import com.getwemap.sdk.positioning.wemapvpsarcore.configs.VpsStateManagerConfig
import com.getwemap.sdk.positioning.wemapvpsarcore.configs.VpsStaticPositionDetectorConfig
import com.getwemap.sdk.positioning.wemapvpslocal.AlphaVpsLocalApi
import com.getwemap.sdk.positioning.wemapvpslocal.configs.VpsLocalConfig
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

object AppConstants {
    var ENABLE_HAPTIC_FEEDBACK: Boolean = true
    var USE_WHEELCHAIR: Boolean = false

    /**
     * Whether the offline VPS sample opts into background scanning (passes a foreground-service config
     * to `VpsLocalLocationSource`). App-level toggle — the SDK's off switch is simply passing `null`.
     */
    var VPS_LOCAL_BACKGROUND_SCANNING_ENABLED: Boolean = true

    /**
     * Whether the offline VPS sample passes its `PreviewView` to `VpsLocalLocationSource` (live camera
     * feed behind the scan overlay). App-level toggle — the SDK's off switch is passing `null`.
     *
     * Turning it **off** also isolates the cost of the preview stream: without one the SDK binds still
     * capture alone, so a scan carries no `Preview` use case at all.
     */
    var VPS_LOCAL_CAMERA_PREVIEW_ENABLED: Boolean = true
}

object Config {

    fun makeSessionConfig(context: Context): SessionConfig {
        val prefs = AppPreferences.get(context)
        return SessionConfig(
            prefs
                .getBoolean(PreferenceKey.ITINERARY_RECALCULATION_ENABLED, true),
            prefs
                .getBoolean(PreferenceKey.USER_LOCATION_PROJECTION_ON_ITINERARY_ENABLED, true),
            prefs
                .getBoolean(PreferenceKey.USER_LOCATION_PROJECTION_ON_GRAPH_ENABLED, false),
            pointsOfInterestLoadingTimeout = prefs
                .getString(PreferenceKey.POINTS_OF_INTEREST_LOADING_TIMEOUT_SECONDS)?.toInt()?.seconds ?: 10.seconds
        )
    }

    fun makeMapViewConfig(context: Context): MapViewConfig {
        val prefs = AppPreferences.get(context)
        return MapViewConfig(
            prefs
                .getBoolean(PreferenceKey.SWITCH_LEVELS_AUTOMATICALLY_ON_USER_MOVEMENTS, true),
            prefs.getString(PreferenceKey.STALE_TIMEOUT_SECONDS)?.toLong()?.seconds ?: 5.seconds
        )
    }

    /**
     * [makeMapViewConfig] with a much longer stale-state timeout, for the offline VPS sample.
     *
     * Offline VPS fixes are **discrete** — one per successful scan, and the movement-aware cadence means a
     * stationary user may go tens of seconds without one. At the 5 s default the blue dot would spend most of
     * a session greyed out as stale, which is misleading: dead reckoning is still tracking the walker between
     * fixes. (This used to be a `MapConstants.STALE_TIMEOUT_MILLISECONDS` global assignment in the fragment;
     * in v1 it is a per-view config value, so it belongs here with the other config factories.)
     */
    fun makeVpsLocalMapViewConfig(context: Context): MapViewConfig =
        makeMapViewConfig(context).copy(staleStateTimeout = VPS_LOCAL_STALE_TIMEOUT)

    private val VPS_LOCAL_STALE_TIMEOUT = 30.seconds

    fun makeVpsConfig(context: Context): VpsConfig {
        val prefs = AppPreferences.get(context)
        val rawScanInterval = prefs.getString(PreferenceKey.BACKGROUND_SCAN_TIME_INTERVAL)?.toDouble() ?: 30.0
        val rawForegroundAngle = prefs.getString(PreferenceKey.MIN_INCLINATION_ANGLE)?.toDouble() ?: 65.0
        return VpsConfig(
            VpsLocationSourceConfig(
                prefs.getString(PreferenceKey.ACCURATE_STATE_ACCURACY)?.toDouble() ?: 1.0,
                prefs.getString(PreferenceKey.DEGRADED_STATE_ACCURACY)?.toDouble() ?: 5.0,
                prefs.getString(PreferenceKey.ATTITUDE_ACCURACY)?.toDouble() ?: 35.0
            ),
            VpsControllerConfig(
                if (rawScanInterval == 30.0) null else rawScanInterval.seconds,
                prefs
                    .getString(PreferenceKey.BACKGROUND_SCAN_DISTANCE_THRESHOLD)?.toDouble() ?: 15.0,
                foregroundScanMinInclinationAngle = if (rawForegroundAngle == 65.0) null else rawForegroundAngle,
                backgroundScanMinInclinationAngle = prefs
                    .getString(PreferenceKey.BACKGROUND_SCAN_MIN_INCLINATION_ANGLE)?.toDouble() ?: 75.0,
                slowConnectionTimeout = prefs.getString(PreferenceKey.SLOW_CONNECTION_SECONDS)?.toLong()?.seconds ?: 5.seconds,
                useGrayscaleImageForVps = prefs.getBoolean(PreferenceKey.CONVERT_IMAGES_TO_GRAYSCALE, false),
                useJpgImageForVps = prefs.getBoolean(PreferenceKey.USE_JPG_IMAGE_FOR_VPS, true),
                jpgImageCompressionQuality = prefs
                    .getString(PreferenceKey.JPG_IMAGE_COMPRESSION_QUALITY)?.toInt() ?: 70
            ),
            VpsStateManagerConfig(
                prefs
                    .getString(PreferenceKey.DEGRADED_DISTANCE_THRESHOLD)?.toDouble() ?: 75.0,
                prefs
                    .getString(PreferenceKey.NOT_POSITIONING_DISTANCE_THRESHOLD)?.toDouble() ?: 150.0
            ),
            VpsStaticPositionDetectorConfig(
                prefs.getString(PreferenceKey.WINDOW_DURATION_SECONDS)?.toDouble()?.seconds ?: 3.seconds,
                prefs.getString(PreferenceKey.GEOFENCE_RADIUS_METERS)?.toDouble() ?: 1.0
            ),
            VpsConveyingDetectorConfig(
                prefs.getString(PreferenceKey.DURATION_SECONDS)?.toLong()?.seconds ?: 3.seconds,
                prefs.getString(PreferenceKey.ELEVATOR_BUFFER_WIDTH)?.toDouble() ?: 5.0,
                prefs
                    .getString(PreferenceKey.LINEAR_CONVEYING_BUFFERS_WIDTH)?.toDouble() ?: 3.0
            )
        )
    }

    fun makeVpsLocalConfig(context: Context): VpsLocalConfig {
        val prefs = AppPreferences.get(context)
        // Defaults come from VpsLocalConfig()'s own parameter defaults, so this file never restates an SDK
        // default — the same reason makeMapViewConfig/makeVpsConfig read the pref and fall back inline.
        val defaults = VpsLocalConfig()
        return VpsLocalConfig(
            minScanInterval = prefs.getString(PreferenceKey.VPS_LOCAL_MIN_SCAN_INTERVAL_MS)
                ?.toLong()?.milliseconds ?: defaults.minScanInterval,
            maxScanInterval = prefs.getString(PreferenceKey.VPS_LOCAL_MAX_SCAN_INTERVAL_MS)
                ?.toLong()?.milliseconds ?: defaults.maxScanInterval,
            rescanDistanceMeters = prefs.getString(PreferenceKey.VPS_LOCAL_RESCAN_DISTANCE_METERS)
                ?.toDouble() ?: defaults.rescanDistanceMeters,
            shortlistSize = prefs.getString(PreferenceKey.VPS_LOCAL_SHORTLIST_SIZE)
                ?.toInt() ?: defaults.shortlistSize,
            rerankSize = prefs.getString(PreferenceKey.VPS_LOCAL_RERANK_SIZE)
                ?.toInt() ?: defaults.rerankSize,
            dominanceRatio = prefs.getString(PreferenceKey.VPS_LOCAL_DOMINANCE_RATIO)
                ?.toDouble() ?: defaults.dominanceRatio,
            minInclinationAngle = prefs.getString(PreferenceKey.VPS_LOCAL_MIN_INCLINATION_ANGLE)
                ?.toDouble() ?: defaults.minInclinationAngle,
            stepLengthMeters = prefs.getString(PreferenceKey.VPS_LOCAL_STEP_LENGTH_METERS)
                ?.toDouble() ?: defaults.stepLengthMeters,
            textureMaxLowTileRatio = prefs.getString(PreferenceKey.VPS_LOCAL_TEXTURE_MAX_LOW_TILE_RATIO)
                ?.toFloat() ?: defaults.textureMaxLowTileRatio,
            minSharpness = prefs.getString(PreferenceKey.VPS_LOCAL_MIN_SHARPNESS)
                ?.toDouble() ?: defaults.minSharpness,
            cameraAutoSwitchEnabled = prefs.getBoolean(
                PreferenceKey.VPS_LOCAL_CAMERA_AUTO_SWITCH_ENABLED, defaults.cameraAutoSwitchEnabled
            ),
        )
    }

    /**
     * App-level (non-SDK) global state only — see the other apps' `applyAppOptions`.
     *
     * SDK tunables do **not** belong here: they are immutable and read at creation time by the `make*Config`
     * factories above. This used to also mutate a `VpsLocalConstants` singleton of `var`s, which was the
     * pre-v1 pattern and the one thing in this file out of step with `makeVpsConfig`.
     */
    fun applyAppOptions(context: Context) {
        val prefs = AppPreferences.get(context)
        with(AppConstants) {
            ENABLE_HAPTIC_FEEDBACK = prefs.getBoolean(
                PreferenceKey.ENABLE_HAPTIC_FEEDBACK, ENABLE_HAPTIC_FEEDBACK
            )
            USE_WHEELCHAIR = prefs.getBoolean(
                PreferenceKey.USE_WHEELCHAIR, USE_WHEELCHAIR
            )
            VPS_LOCAL_BACKGROUND_SCANNING_ENABLED = prefs.getBoolean(
                PreferenceKey.VPS_LOCAL_BACKGROUND_SCANNING_ENABLED, VPS_LOCAL_BACKGROUND_SCANNING_ENABLED
            )
            VPS_LOCAL_CAMERA_PREVIEW_ENABLED = prefs.getBoolean(
                PreferenceKey.VPS_LOCAL_CAMERA_PREVIEW_ENABLED, VPS_LOCAL_CAMERA_PREVIEW_ENABLED
            )
        }
    }
}

enum class PreferenceKey: IPreferenceKey {
    ENABLE_HAPTIC_FEEDBACK,
    USE_WHEELCHAIR,

    // Global - Core
    ITINERARY_RECALCULATION_ENABLED,
    USER_LOCATION_PROJECTION_ON_ITINERARY_ENABLED,
    USER_LOCATION_PROJECTION_ON_GRAPH_ENABLED,
    POINTS_OF_INTEREST_LOADING_TIMEOUT_SECONDS,

    // Map
    SWITCH_LEVELS_AUTOMATICALLY_ON_USER_MOVEMENTS,
    STALE_TIMEOUT_SECONDS,

    // VPS
    ACCURATE_STATE_ACCURACY,
    DEGRADED_STATE_ACCURACY,
    ATTITUDE_ACCURACY,
    CONVERT_IMAGES_TO_GRAYSCALE,
    USE_JPG_IMAGE_FOR_VPS,
    JPG_IMAGE_COMPRESSION_QUALITY,

    // VpsController
    SLOW_CONNECTION_SECONDS,
    MIN_INCLINATION_ANGLE,
    BACKGROUND_SCAN_MIN_INCLINATION_ANGLE,
    BACKGROUND_SCAN_TIME_INTERVAL,
    BACKGROUND_SCAN_DISTANCE_THRESHOLD,

    // VPS Local (offline)
    VPS_LOCAL_BACKGROUND_SCANNING_ENABLED, // app-level (read into AppConstants), not a VpsLocalConfig field
    VPS_LOCAL_CAMERA_PREVIEW_ENABLED, // app-level (read into AppConstants), not a VpsLocalConfig field
    VPS_LOCAL_CAMERA_AUTO_SWITCH_ENABLED,
    VPS_LOCAL_MIN_SCAN_INTERVAL_MS,
    VPS_LOCAL_MAX_SCAN_INTERVAL_MS,
    VPS_LOCAL_RESCAN_DISTANCE_METERS,
    VPS_LOCAL_TEXTURE_MAX_LOW_TILE_RATIO,
    VPS_LOCAL_MIN_SHARPNESS,
    VPS_LOCAL_MIN_INCLINATION_ANGLE,
    VPS_LOCAL_STEP_LENGTH_METERS,
    VPS_LOCAL_SHORTLIST_SIZE,
    VPS_LOCAL_RERANK_SIZE,
    VPS_LOCAL_DOMINANCE_RATIO,

    // StateManager
    DEGRADED_DISTANCE_THRESHOLD,
    NOT_POSITIONING_DISTANCE_THRESHOLD,

    // VPS static position detector
    WINDOW_DURATION_SECONDS,
    GEOFENCE_RADIUS_METERS,

    // VPS conveying detector
    DURATION_SECONDS,
    ELEVATOR_BUFFER_WIDTH,
    LINEAR_CONVEYING_BUFFERS_WIDTH
}
