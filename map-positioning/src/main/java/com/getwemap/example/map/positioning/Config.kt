@file:OptIn(AlphaVpsLocalApi::class)

package com.getwemap.example.map.positioning

import android.content.Context
import androidx.preference.PreferenceManager
import com.getwemap.example.common.IPreferenceKey
import com.getwemap.example.common.getBoolean
import com.getwemap.example.common.getString
import com.getwemap.sdk.core.CoreConstants
import com.getwemap.sdk.map.helpers.MapConstants
import com.getwemap.sdk.map.internal.debugger.VisualDebugger
import com.getwemap.sdk.positioning.wemapvpsarcore.constants.StateManagerConstants
import com.getwemap.sdk.positioning.wemapvpsarcore.constants.VPSControllerConstants
import com.getwemap.sdk.positioning.wemapvpsarcore.internal.WemapVPSARCoreConstants
import com.getwemap.sdk.positioning.wemapvpslocal.AlphaVpsLocalApi
import com.getwemap.sdk.positioning.wemapvpslocal.constants.VpsLocalConstants

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
    fun applyGlobalOptions(context: Context) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
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
        with(CoreConstants) {
            USER_LOCATION_PROJECTION_ON_ITINERARY_ENABLED = prefs.getBoolean(
                PreferenceKey.USER_LOCATION_PROJECTION_ON_ITINERARY_ENABLED,
                USER_LOCATION_PROJECTION_ON_ITINERARY_ENABLED
            )
            USER_LOCATION_PROJECTION_ON_GRAPH_ENABLED = prefs.getBoolean(
                PreferenceKey.USER_LOCATION_PROJECTION_ON_GRAPH_ENABLED, USER_LOCATION_PROJECTION_ON_GRAPH_ENABLED)
            ITINERARY_RECALCULATION_ENABLED = prefs
                .getBoolean(PreferenceKey.ITINERARY_RECALCULATION_ENABLED, ITINERARY_RECALCULATION_ENABLED)
        }
        with(MapConstants) {
            SWITCH_LEVELS_AUTOMATICALLY_ON_USER_MOVEMENTS = prefs.getBoolean(
                PreferenceKey.SWITCH_LEVELS_AUTOMATICALLY_ON_USER_MOVEMENTS,
                SWITCH_LEVELS_AUTOMATICALLY_ON_USER_MOVEMENTS
            )
            STALE_TIMEOUT_MILLISECONDS = prefs.getString(PreferenceKey.STALE_TIMEOUT_MILLISECONDS)
                ?.toLong() ?: STALE_TIMEOUT_MILLISECONDS
        }
        with(WemapVPSARCoreConstants) {
            SLOW_CONNECTION_SECONDS = prefs.getString(PreferenceKey.SLOW_CONNECTION_SECONDS)
                ?.toLong() ?: SLOW_CONNECTION_SECONDS
            MIN_INCLINATION_ANGLE = prefs.getString(PreferenceKey.MIN_INCLINATION_ANGLE)
                ?.toDouble() ?: MIN_INCLINATION_ANGLE
        }
        with(VPSControllerConstants) {
            BACKGROUND_SCAN_MIN_INCLINATION_ANGLE = prefs.getString(PreferenceKey.BACKGROUND_SCAN_MIN_INCLINATION_ANGLE)
                ?.toDouble() ?: BACKGROUND_SCAN_MIN_INCLINATION_ANGLE
            BACKGROUND_SCAN_TIME_INTERVAL = prefs.getString(PreferenceKey.BACKGROUND_SCAN_TIME_INTERVAL)
                ?.toDouble() ?: BACKGROUND_SCAN_TIME_INTERVAL
            BACKGROUND_SCAN_DISTANCE_THRESHOLD = prefs.getString(PreferenceKey.BACKGROUND_SCAN_DISTANCE_THRESHOLD)
                ?.toDouble() ?: BACKGROUND_SCAN_DISTANCE_THRESHOLD
        }
        with(VpsLocalConstants) {
            CAMERA_AUTO_SWITCH_ENABLED = prefs.getBoolean(
                PreferenceKey.VPS_LOCAL_CAMERA_AUTO_SWITCH_ENABLED, CAMERA_AUTO_SWITCH_ENABLED
            )
            MIN_SCAN_INTERVAL_MS = prefs.getString(PreferenceKey.VPS_LOCAL_MIN_SCAN_INTERVAL_MS)
                ?.toLong() ?: MIN_SCAN_INTERVAL_MS
            MAX_SCAN_INTERVAL_MS = prefs.getString(PreferenceKey.VPS_LOCAL_MAX_SCAN_INTERVAL_MS)
                ?.toLong() ?: MAX_SCAN_INTERVAL_MS
            RESCAN_DISTANCE_METERS = prefs.getString(PreferenceKey.VPS_LOCAL_RESCAN_DISTANCE_METERS)
                ?.toDouble() ?: RESCAN_DISTANCE_METERS
            TEXTURE_MAX_LOW_TILE_RATIO = prefs.getString(PreferenceKey.VPS_LOCAL_TEXTURE_MAX_LOW_TILE_RATIO)
                ?.toFloat() ?: TEXTURE_MAX_LOW_TILE_RATIO
            MIN_SHARPNESS = prefs.getString(PreferenceKey.VPS_LOCAL_MIN_SHARPNESS)
                ?.toDouble() ?: MIN_SHARPNESS
            MIN_INCLINATION_ANGLE = prefs.getString(PreferenceKey.VPS_LOCAL_MIN_INCLINATION_ANGLE)
                ?.toDouble() ?: MIN_INCLINATION_ANGLE
            STEP_LENGTH_METERS = prefs.getString(PreferenceKey.VPS_LOCAL_STEP_LENGTH_METERS)
                ?.toDouble() ?: STEP_LENGTH_METERS
            SHORTLIST_SIZE = prefs.getString(PreferenceKey.VPS_LOCAL_SHORTLIST_SIZE)
                ?.toInt() ?: SHORTLIST_SIZE
            RERANK_SIZE = prefs.getString(PreferenceKey.VPS_LOCAL_RERANK_SIZE)
                ?.toInt() ?: RERANK_SIZE
            DOMINANCE_RATIO = prefs.getString(PreferenceKey.VPS_LOCAL_DOMINANCE_RATIO)
                ?.toDouble() ?: DOMINANCE_RATIO
        }
        with(StateManagerConstants) {
            DEGRADED_DISTANCE_THRESHOLD = prefs.getString(PreferenceKey.DEGRADED_DISTANCE_THRESHOLD)
                ?.toDouble() ?: DEGRADED_DISTANCE_THRESHOLD
            NOT_POSITIONING_DISTANCE_THRESHOLD = prefs.getString(PreferenceKey.NOT_POSITIONING_DISTANCE_THRESHOLD)
                ?.toDouble() ?: NOT_POSITIONING_DISTANCE_THRESHOLD
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

    // Map
    SWITCH_LEVELS_AUTOMATICALLY_ON_USER_MOVEMENTS,
    STALE_TIMEOUT_MILLISECONDS,

    // VPS

    // VPSController
    SLOW_CONNECTION_SECONDS,
    MIN_INCLINATION_ANGLE,
    BACKGROUND_SCAN_MIN_INCLINATION_ANGLE,
    BACKGROUND_SCAN_TIME_INTERVAL,
    BACKGROUND_SCAN_DISTANCE_THRESHOLD,

    // VPS Local (offline)
    VPS_LOCAL_BACKGROUND_SCANNING_ENABLED, // app-level (read into AppConstants), not a VpsLocalConstants knob
    VPS_LOCAL_CAMERA_PREVIEW_ENABLED, // app-level (read into AppConstants), not a VpsLocalConstants knob
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
    NOT_POSITIONING_DISTANCE_THRESHOLD
}