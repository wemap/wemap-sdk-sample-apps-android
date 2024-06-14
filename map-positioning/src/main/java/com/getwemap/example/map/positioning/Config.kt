package com.getwemap.example.map.positioning

import android.content.Context
import androidx.preference.PreferenceManager
import com.getwemap.sdk.map.helpers.MapConstants
import com.getwemap.sdk.positioning.wemapvpsarcore.internal.WemapVPSARCoreConstants

object Config {

    fun applyGlobalOptions(context: Context) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        with(MapConstants) {
            VISUAL_DEBUGGER_ENABLED = prefs.getBoolean(PreferenceKey.VISUAL_DEBUGGER_ENABLED.name, false)
            SWITCH_LEVELS_AUTOMATICALLY_ON_USER_MOVEMENTS = prefs.getBoolean(PreferenceKey.SWITCH_LEVELS_AUTOMATICALLY_ON_USER_MOVEMENTS.name, true)
            USER_LOCATION_PROJECTION_ON_ITINERARY_ENABLED = prefs.getBoolean(PreferenceKey.USER_LOCATION_PROJECTION_ON_ITINERARY_ENABLED.name, true)
            ITINERARY_RECALCULATION_ENABLED = prefs.getBoolean(PreferenceKey.ITINERARY_RECALCULATION_ENABLED.name, true)
        }
        with(WemapVPSARCoreConstants.StaticPositionDetector) {
            WINDOW_DURATION_SECONDS = prefs.getString(PreferenceKey.WINDOW_DURATION_SECONDS.name, "3")!!.toFloat()
            GEOFENCE_RADIUS_METERS = prefs.getString(PreferenceKey.GEOFENCE_RADIUS_METERS.name, "1")!!.toFloat()
        }
        with(WemapVPSARCoreConstants.ConveyingDetector) {
            DURATION_SECONDS = prefs.getString(PreferenceKey.DURATION_SECONDS.name, "3")!!.toLong()
            ELEVATOR_BUFFER_WIDTH = prefs.getString(PreferenceKey.ELEVATOR_BUFFER_WIDTH.name, "5")!!.toDouble()
            LINEAR_CONVEYING_BUFFERS_WIDTH = prefs.getString(PreferenceKey.LINEAR_CONVEYING_BUFFERS_WIDTH.name, "2")!!.toDouble()
        }
    }
}

enum class PreferenceKey {
    // Global
    VISUAL_DEBUGGER_ENABLED,
    SWITCH_LEVELS_AUTOMATICALLY_ON_USER_MOVEMENTS,
    USER_LOCATION_PROJECTION_ON_ITINERARY_ENABLED,
    ITINERARY_RECALCULATION_ENABLED,

    // VPS static position detector
    WINDOW_DURATION_SECONDS,
    GEOFENCE_RADIUS_METERS,

    // VPS conveying detector
    DURATION_SECONDS,
    ELEVATOR_BUFFER_WIDTH,
    LINEAR_CONVEYING_BUFFERS_WIDTH
}