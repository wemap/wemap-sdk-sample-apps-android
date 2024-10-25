package com.getwemap.example.map

import android.content.Context
import android.graphics.Color
import androidx.preference.PreferenceManager
import com.getwemap.sdk.core.navigation.NavigationOptions
import com.getwemap.sdk.map.helpers.MapConstants
import com.getwemap.sdk.map.itineraries.ItineraryOptions
import com.getwemap.sdk.map.itineraries.LineOptions
import org.maplibre.android.style.layers.PropertyFactory

object Config {

    fun globalNavigationOptions(context: Context): NavigationOptions {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        return NavigationOptions(
            NavigationOptions.StopOptions(stopDistanceThreshold = prefs.getString(PreferenceKey.STOP_DISTANCE_THRESHOLD.name, "15")!!.toFloat()),
            prefs.getString(PreferenceKey.USER_POSITION_THRESHOLD.name, "25")!!.toFloat(),
            prefs.getString(PreferenceKey.NAVIGATION_RECALCULATION_TIME_INTERVAL.name, "5")!!.toLong(),
        )
    }

    val globalItineraryOptions: ItineraryOptions get() =
        ItineraryOptions(
            projectionLine = LineOptions(
                5F, color = Color.LTGRAY, dashPattern = PropertyFactory.lineDasharray(arrayOf(0.5F, 2F))
            ),
            outdoorLine = LineOptions(10F, color = Color.DKGRAY)
        )

    fun applyGlobalOptions(context: Context) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        with(MapConstants) {
            SWITCH_LEVELS_AUTOMATICALLY_ON_USER_MOVEMENTS = prefs.getBoolean(PreferenceKey.SWITCH_LEVELS_AUTOMATICALLY_ON_USER_MOVEMENTS.name, true)
        }
    }
}

enum class PreferenceKey {
    // Global
    SWITCH_LEVELS_AUTOMATICALLY_ON_USER_MOVEMENTS,

    // Navigation
    STOP_DISTANCE_THRESHOLD,
    USER_POSITION_THRESHOLD,
    NAVIGATION_RECALCULATION_TIME_INTERVAL
}