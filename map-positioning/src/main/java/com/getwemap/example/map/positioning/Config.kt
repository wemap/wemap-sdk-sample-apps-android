package com.getwemap.example.map.positioning

import android.content.Context
import androidx.preference.PreferenceManager
import com.getwemap.sdk.core.CoreConstants
import com.getwemap.sdk.map.helpers.MapConstants
import com.getwemap.sdk.positioning.wemapvpsarcore.internal.WemapVPSARCoreConstants

object Config {

    fun applyGlobalOptions(context: Context) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        with(CoreConstants) {
            USER_LOCATION_PROJECTION_ON_ITINERARY_ENABLED = prefs.getBoolean(
                PreferenceKey.USER_LOCATION_PROJECTION_ON_ITINERARY_ENABLED.name,
                USER_LOCATION_PROJECTION_ON_ITINERARY_ENABLED
            )
            USER_LOCATION_PROJECTION_ON_GRAPH_ENABLED = prefs.getBoolean(
                PreferenceKey.USER_LOCATION_PROJECTION_ON_GRAPH_ENABLED.name, USER_LOCATION_PROJECTION_ON_GRAPH_ENABLED)
            ITINERARY_RECALCULATION_ENABLED = prefs
                .getBoolean(PreferenceKey.ITINERARY_RECALCULATION_ENABLED.name, ITINERARY_RECALCULATION_ENABLED)
        }
        with(MapConstants) {
            SWITCH_LEVELS_AUTOMATICALLY_ON_USER_MOVEMENTS = prefs.getBoolean(
                PreferenceKey.SWITCH_LEVELS_AUTOMATICALLY_ON_USER_MOVEMENTS.name,
                SWITCH_LEVELS_AUTOMATICALLY_ON_USER_MOVEMENTS
            )
        }
        with(WemapVPSARCoreConstants) {
            SLOW_CONNECTION_SECONDS = prefs.getString(
                PreferenceKey.SLOW_CONNECTION_SECONDS.name, null
            )?.toLong() ?: SLOW_CONNECTION_SECONDS
        }
    }
}

enum class PreferenceKey {
    // Global - Core
    USER_LOCATION_PROJECTION_ON_ITINERARY_ENABLED,
    USER_LOCATION_PROJECTION_ON_GRAPH_ENABLED,
    ITINERARY_RECALCULATION_ENABLED,

    // Map
    SWITCH_LEVELS_AUTOMATICALLY_ON_USER_MOVEMENTS,

    // VPS
    SLOW_CONNECTION_SECONDS
}