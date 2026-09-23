
package com.getwemap.example.map

import android.content.Context
import com.getwemap.example.common.AppPreferences
import com.getwemap.example.common.CommonAppConstants
import com.getwemap.example.common.IPreferenceKey
import com.getwemap.example.common.getBoolean
import com.getwemap.example.common.getString
import com.getwemap.sdk.core.configs.SessionConfig
import com.getwemap.sdk.map.configs.MapViewConfig
import kotlin.time.Duration.Companion.seconds

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
            prefs
                .getString(PreferenceKey.STALE_TIMEOUT_SECONDS)?.toLong()?.seconds ?: 5.seconds,
        )
    }

    fun applyAppOptions(context: Context) {
        val prefs = AppPreferences.get(context)
        with(CommonAppConstants) {
            SIMULATOR_DEVIATION_RANGE = prefs.getString(PreferenceKey.SIMULATOR_DEVIATION_RANGE)
                ?.toDouble() ?: SIMULATOR_DEVIATION_RANGE
        }
    }
}

enum class PreferenceKey: IPreferenceKey {
    // App
    SIMULATOR_DEVIATION_RANGE,
    // Core
    ITINERARY_RECALCULATION_ENABLED,
    USER_LOCATION_PROJECTION_ON_ITINERARY_ENABLED,
    USER_LOCATION_PROJECTION_ON_GRAPH_ENABLED,
    POINTS_OF_INTEREST_LOADING_TIMEOUT_SECONDS,

    // Map
    SWITCH_LEVELS_AUTOMATICALLY_ON_USER_MOVEMENTS,
    STALE_TIMEOUT_SECONDS
}
