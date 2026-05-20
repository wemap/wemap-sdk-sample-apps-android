package com.getwemap.example.positioning.ar

import android.content.Context
import androidx.preference.PreferenceManager
import com.getwemap.example.common.IPreferenceKey
import com.getwemap.example.common.getString
import com.getwemap.sdk.geoar.ARConstants

object Config {

    fun applyGlobalOptions(context: Context) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        with(ARConstants) {
            NAVIGATION_VISIBILITY_DISTANCE = prefs.getString(PreferenceKey.NAVIGATION_VISIBILITY_DISTANCE)
                ?.toDouble() ?: NAVIGATION_VISIBILITY_DISTANCE
        }
    }
}

enum class PreferenceKey : IPreferenceKey {
    NAVIGATION_VISIBILITY_DISTANCE
}
