package com.getwemap.example.map.positioning

import android.content.Context
import androidx.preference.PreferenceManager
import com.getwemap.example.common.IPreferenceKey
import com.getwemap.example.common.getBoolean
import com.getwemap.example.common.getString
import com.getwemap.sdk.core.CoreConstants
import com.getwemap.sdk.map.helpers.MapConstants
import com.getwemap.sdk.positioning.wemapvpsarcore.constants.StateManagerConstants
import com.getwemap.sdk.positioning.wemapvpsarcore.constants.VPSControllerConstants
import com.getwemap.sdk.positioning.wemapvpsarcore.internal.WemapVPSARCoreConstants

object AppConstants {
    var ENABLE_HAPTIC_FEEDBACK: Boolean = true
    var USE_WHEELCHAIR: Boolean = false
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

    // StateManager
    DEGRADED_DISTANCE_THRESHOLD,
    NOT_POSITIONING_DISTANCE_THRESHOLD
}