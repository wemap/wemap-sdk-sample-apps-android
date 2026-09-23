package com.getwemap.example.positioning

import android.content.Context
import com.getwemap.example.common.AppPreferences
import com.getwemap.example.common.IPreferenceKey
import com.getwemap.example.common.getBoolean
import com.getwemap.sdk.core.configs.SessionConfig

object Config {

    fun makeSessionConfig(context: Context): SessionConfig {
        val prefs = AppPreferences.get(context)
        return SessionConfig(
            userLocationProjectionOnItineraryEnabled = prefs
                .getBoolean(PreferenceKey.USER_LOCATION_PROJECTION_ON_ITINERARY_ENABLED, true),
            userLocationProjectionOnGraphEnabled = prefs
                .getBoolean(PreferenceKey.USER_LOCATION_PROJECTION_ON_GRAPH_ENABLED, false)
        )
    }
}

enum class PreferenceKey: IPreferenceKey {
    // Global
    USER_LOCATION_PROJECTION_ON_ITINERARY_ENABLED,
    USER_LOCATION_PROJECTION_ON_GRAPH_ENABLED
}
