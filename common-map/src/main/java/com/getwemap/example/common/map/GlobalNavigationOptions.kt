package com.getwemap.example.common.map

import android.content.Context
import android.graphics.Color
import androidx.preference.PreferenceManager
import com.getwemap.sdk.core.navigation.NavigationOptions
import com.getwemap.sdk.map.itineraries.ItineraryOptions
import com.getwemap.sdk.map.itineraries.LineOptions
import org.maplibre.android.style.layers.PropertyFactory

enum class NavigationPreferenceKey {
    // Navigation
    ARRIVED_DISTANCE_THRESHOLD,
    USER_POSITION_THRESHOLD,
    NAVIGATION_RECALCULATION_TIME_INTERVAL
}

object GlobalOptions {

    fun navigationOptions(context: Context): NavigationOptions {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        return NavigationOptions(
            arrivedDistanceThreshold = prefs.getString(NavigationPreferenceKey.ARRIVED_DISTANCE_THRESHOLD.name, "15")!!.toFloat(),
            userPositionThreshold = prefs.getString(NavigationPreferenceKey.USER_POSITION_THRESHOLD.name, "25")!!.toFloat(),
            navigationRecalculationTimeInterval = prefs.getString(NavigationPreferenceKey.NAVIGATION_RECALCULATION_TIME_INTERVAL.name, "5")!!.toLong(),
        )
    }

    val itineraryOptions: ItineraryOptions get() {
        return ItineraryOptions(
            projectionLine = LineOptions(
                5F, color = Color.LTGRAY, dashPattern = PropertyFactory.lineDasharray(arrayOf(0.5F, 2F))
            ),
            outdoorLine = LineOptions(10F, color = Color.DKGRAY)
        )
    }
}