package com.getwemap.example.positioning

import android.content.Context
import androidx.preference.PreferenceManager
import com.getwemap.example.common.IPreferenceKey
import com.getwemap.example.common.getBoolean
import com.getwemap.sdk.core.CoreConstants

object Config {

    fun applyGlobalOptions(context: Context) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        with(CoreConstants) {
            USER_LOCATION_PROJECTION_ON_ITINERARY_ENABLED = prefs
                .getBoolean(PreferenceKey.USER_LOCATION_PROJECTION_ON_ITINERARY_ENABLED, USER_LOCATION_PROJECTION_ON_ITINERARY_ENABLED)
            USER_LOCATION_PROJECTION_ON_GRAPH_ENABLED = prefs
                .getBoolean(PreferenceKey.USER_LOCATION_PROJECTION_ON_GRAPH_ENABLED, USER_LOCATION_PROJECTION_ON_GRAPH_ENABLED)
        }
    }
}

enum class PreferenceKey: IPreferenceKey {
    // Global
    USER_LOCATION_PROJECTION_ON_ITINERARY_ENABLED,
    USER_LOCATION_PROJECTION_ON_GRAPH_ENABLED
}