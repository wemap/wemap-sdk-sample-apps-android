package com.getwemap.example.map

import android.content.Context
import android.graphics.Color
import androidx.preference.PreferenceManager
import com.getwemap.sdk.map.MapConstants
import com.getwemap.sdk.map.itineraries.ItineraryOptions
import com.getwemap.sdk.map.navigation.NavigationOptions
import com.mapbox.mapboxsdk.location.modes.CameraMode
import com.mapbox.mapboxsdk.location.modes.RenderMode

object Config {

    fun globalNavigationOptions(context: Context): NavigationOptions {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        return NavigationOptions(
            ItineraryOptions(color = Color.CYAN, projectionOptions = ItineraryOptions.ProjectionOptions(5F, color = Color.LTGRAY)),
            CameraMode.TRACKING_COMPASS,
            stopOptions = NavigationOptions.StopOptions(stopDistanceThreshold = prefs.getString(PreferenceKey.STOP_DISTANCE_THRESHOLD.name, "15")!!.toFloat()),
            userPositionThreshold = prefs.getString(PreferenceKey.USER_POSITION_THRESHOLD.name, "25")!!.toFloat(),
            navigationRecalculationTimeInterval = prefs.getString(PreferenceKey.NAVIGATION_RECALCULATION_TIME_INTERVAL.name, "5")!!.toLong(),
            renderMode = RenderMode.COMPASS
        )
    }

    fun applyGlobalOptions(context: Context) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        with(MapConstants) {
            VISUAL_DEBUGGER_ENABLED = prefs.getBoolean(PreferenceKey.VISUAL_DEBUGGER_ENABLED.name, false)
            SWITCH_LEVELS_AUTOMATICALLY_ON_USER_MOVEMENTS = prefs.getBoolean(PreferenceKey.SWITCH_LEVELS_AUTOMATICALLY_ON_USER_MOVEMENTS.name, false)
            USER_LOCATION_PROJECTION_ON_ITINERARY_ENABLED = prefs.getBoolean(PreferenceKey.USER_LOCATION_PROJECTION_ON_ITINERARY_ENABLED.name, true)
            ITINERARY_RECALCULATION_ENABLED = prefs.getBoolean(PreferenceKey.ITINERARY_RECALCULATION_ENABLED.name, true)
        }
    }
}

enum class PreferenceKey {
    // Global
    VISUAL_DEBUGGER_ENABLED,
    SWITCH_LEVELS_AUTOMATICALLY_ON_USER_MOVEMENTS,
    USER_LOCATION_PROJECTION_ON_ITINERARY_ENABLED,
    ITINERARY_RECALCULATION_ENABLED,

    // Navigation
    STOP_DISTANCE_THRESHOLD,
    USER_POSITION_THRESHOLD,
    NAVIGATION_RECALCULATION_TIME_INTERVAL
}