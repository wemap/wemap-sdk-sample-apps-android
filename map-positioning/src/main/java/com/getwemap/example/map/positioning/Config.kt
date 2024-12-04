package com.getwemap.example.map.positioning

import android.content.Context
import androidx.preference.PreferenceManager
import com.getwemap.sdk.core.CoreConstants
import com.getwemap.sdk.map.helpers.MapConstants

object Config {

    fun applyGlobalOptions(context: Context) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        with(MapConstants) {
            SWITCH_LEVELS_AUTOMATICALLY_ON_USER_MOVEMENTS = prefs.getBoolean(PreferenceKey.SWITCH_LEVELS_AUTOMATICALLY_ON_USER_MOVEMENTS.name, true)
        }
        with(CoreConstants) {
            USER_LOCATION_PROJECTION_ON_ITINERARY_ENABLED = prefs.getBoolean(PreferenceKey.USER_LOCATION_PROJECTION_ON_ITINERARY_ENABLED.name, true)
            ITINERARY_RECALCULATION_ENABLED = prefs.getBoolean(PreferenceKey.ITINERARY_RECALCULATION_ENABLED.name, true)
        }
    }
}

enum class PreferenceKey {
    // Global
    SWITCH_LEVELS_AUTOMATICALLY_ON_USER_MOVEMENTS,
    USER_LOCATION_PROJECTION_ON_ITINERARY_ENABLED,
    ITINERARY_RECALCULATION_ENABLED,
}