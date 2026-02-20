package com.getwemap.example.map.positioning

import android.content.Context
import androidx.preference.PreferenceManager
import com.getwemap.example.common.IPreferenceKey
import com.getwemap.example.common.getBoolean
import com.getwemap.example.common.getString
import com.getwemap.sdk.core.CoreConstants
import com.getwemap.sdk.map.helpers.MapConstants
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
    SLOW_CONNECTION_SECONDS
}