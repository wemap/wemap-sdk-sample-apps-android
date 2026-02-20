package com.getwemap.example.common

import android.content.SharedPreferences

interface IPreferenceKey {
    val name: String
}

fun SharedPreferences.getString(key: IPreferenceKey): String? {
    return getString(key.name, null)
}

fun SharedPreferences.getBoolean(key: IPreferenceKey, default: Boolean): Boolean {
    return getBoolean(key.name, default)
}