package com.getwemap.example.map

import android.content.Context
import androidx.preference.PreferenceManager
import com.getwemap.example.common.IPreferenceKey
import com.getwemap.example.common.getBoolean
import com.getwemap.example.common.getString
import com.getwemap.sdk.core.CoreConstants
import com.getwemap.sdk.map.helpers.MapConstants

object Config {

    fun applyGlobalOptions(context: Context) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        with(CoreConstants) {
            ITINERARY_RECALCULATION_ENABLED = prefs
                .getBoolean(PreferenceKey.ITINERARY_RECALCULATION_ENABLED, ITINERARY_RECALCULATION_ENABLED)
            USER_LOCATION_PROJECTION_ON_ITINERARY_ENABLED = prefs
                .getBoolean(PreferenceKey.USER_LOCATION_PROJECTION_ON_ITINERARY_ENABLED, USER_LOCATION_PROJECTION_ON_ITINERARY_ENABLED)
            USER_LOCATION_PROJECTION_ON_GRAPH_ENABLED = prefs
                .getBoolean(PreferenceKey.USER_LOCATION_PROJECTION_ON_GRAPH_ENABLED, USER_LOCATION_PROJECTION_ON_GRAPH_ENABLED)
        }
        with(MapConstants) {
            SWITCH_LEVELS_AUTOMATICALLY_ON_USER_MOVEMENTS = prefs
                .getBoolean(PreferenceKey.SWITCH_LEVELS_AUTOMATICALLY_ON_USER_MOVEMENTS, true)
            STALE_TIMEOUT_MILLISECONDS = prefs.getString(PreferenceKey.STALE_TIMEOUT_MILLISECONDS)
                ?.toLong() ?: STALE_TIMEOUT_MILLISECONDS
        }
    }
}

enum class PreferenceKey: IPreferenceKey {
    // Core
    ITINERARY_RECALCULATION_ENABLED,
    USER_LOCATION_PROJECTION_ON_ITINERARY_ENABLED,
    USER_LOCATION_PROJECTION_ON_GRAPH_ENABLED,

    // Map
    SWITCH_LEVELS_AUTOMATICALLY_ON_USER_MOVEMENTS,
    STALE_TIMEOUT_MILLISECONDS,
}