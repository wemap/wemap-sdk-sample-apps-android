package com.getwemap.example.common.map

import android.content.Context
import android.graphics.Color
import com.getwemap.example.common.AppPreferences
import com.getwemap.example.common.IPreferenceKey
import com.getwemap.example.common.getString
import com.getwemap.sdk.core.navigation.NavigationOptions
import com.getwemap.sdk.map.itineraries.ItineraryOptions
import com.getwemap.sdk.map.itineraries.LineOptions
import org.maplibre.android.style.layers.PropertyFactory
import kotlin.time.Duration.Companion.seconds

enum class NavigationPreferenceKey: IPreferenceKey {
    // Navigation
    ARRIVED_DISTANCE_THRESHOLD,
    USER_POSITION_THRESHOLD,
    NAVIGATION_RECALCULATION_TIME_INTERVAL
}

object GlobalOptions {

    fun navigationOptions(context: Context): NavigationOptions {
        val prefs = AppPreferences.get(context)
        return NavigationOptions(
            arrivedDistanceThreshold = prefs.getString(NavigationPreferenceKey.ARRIVED_DISTANCE_THRESHOLD)
                ?.toDouble() ?: 15.0,
            userPositionThreshold = prefs.getString(NavigationPreferenceKey.USER_POSITION_THRESHOLD)
                ?.toDouble() ?: 15.0,
            navigationRecalculationTimeInterval = prefs
                .getString(NavigationPreferenceKey.NAVIGATION_RECALCULATION_TIME_INTERVAL)?.toLong()?.seconds ?: 5.seconds
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