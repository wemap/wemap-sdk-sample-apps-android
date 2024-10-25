package com.getwemap.example.map.positioning

import android.content.Context
import androidx.preference.PreferenceManager
import com.getwemap.sdk.map.helpers.MapConstants

object Config {

    fun applyGlobalOptions(context: Context) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        with(MapConstants) {
            SWITCH_LEVELS_AUTOMATICALLY_ON_USER_MOVEMENTS = prefs.getBoolean(PreferenceKey.SWITCH_LEVELS_AUTOMATICALLY_ON_USER_MOVEMENTS.name, true)
        }
    }
}

enum class PreferenceKey {
    // Global
    SWITCH_LEVELS_AUTOMATICALLY_ON_USER_MOVEMENTS
}