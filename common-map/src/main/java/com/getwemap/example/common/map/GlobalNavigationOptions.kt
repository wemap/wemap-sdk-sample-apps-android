package com.getwemap.example.common.map

import android.content.Context
import android.graphics.Color
import androidx.preference.PreferenceManager
import com.getwemap.example.common.IPreferenceKey
import com.getwemap.example.common.getString
import com.getwemap.sdk.core.navigation.NavigationOptions
import com.getwemap.sdk.map.itineraries.ItineraryOptions
import com.getwemap.sdk.map.itineraries.LineOptions
import org.maplibre.android.style.layers.PropertyFactory

enum class NavigationPreferenceKey: IPreferenceKey {
    // Navigation
    ARRIVED_DISTANCE_THRESHOLD,
    USER_POSITION_THRESHOLD,
    NAVIGATION_RECALCULATION_TIME_INTERVAL
}

object GlobalOptions {

    fun navigationOptions(context: Context): NavigationOptions {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        return NavigationOptions(
            arrivedDistanceThreshold = prefs.getString(NavigationPreferenceKey.ARRIVED_DISTANCE_THRESHOLD)
                ?.toFloat() ?: 15f,
            userPositionThreshold = prefs.getString(NavigationPreferenceKey.USER_POSITION_THRESHOLD)
                ?.toFloat() ?: 15f,
            navigationRecalculationTimeInterval = prefs
                .getString(NavigationPreferenceKey.NAVIGATION_RECALCULATION_TIME_INTERVAL)?.toLong() ?: 5
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