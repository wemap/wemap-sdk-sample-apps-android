package com.getwemap.example.positioning.ar

import android.content.Context
import com.getwemap.example.common.AppPreferences
import com.getwemap.example.common.IPreferenceKey
import com.getwemap.example.common.getString
import com.getwemap.sdk.core.configs.SessionConfig
import com.getwemap.sdk.geoar.GeoARViewConfig
import kotlin.time.Duration.Companion.seconds

object Config {

    fun makeSessionConfig(context: Context): SessionConfig {
        val prefs = AppPreferences.get(context)
        return SessionConfig(
            pointsOfInterestLoadingTimeout = prefs
                .getString(PreferenceKey.POINTS_OF_INTEREST_LOADING_TIMEOUT_SECONDS)?.toInt()?.seconds ?: 10.seconds,
        )
    }

    fun makeGeoARViewConfig(context: Context): GeoARViewConfig {
        val prefs = AppPreferences.get(context)
        val rawVisibilityDistance = prefs
            .getString(PreferenceKey.NAVIGATION_VISIBILITY_DISTANCE)?.toDouble() ?: 10.0
        return GeoARViewConfig(
            if (rawVisibilityDistance == 10.0) null else rawVisibilityDistance
        )
    }
}

enum class PreferenceKey : IPreferenceKey {
    // Core
    POINTS_OF_INTEREST_LOADING_TIMEOUT_SECONDS,

    // AR
    NAVIGATION_VISIBILITY_DISTANCE
}
