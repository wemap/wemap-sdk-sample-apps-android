package com.getwemap.example.positioning.ar.fragments

import android.os.Bundle
import androidx.preference.PreferenceFragmentCompat
import com.getwemap.example.positioning.ar.R

class SettingsFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.root_preferences, rootKey)
    }
}
