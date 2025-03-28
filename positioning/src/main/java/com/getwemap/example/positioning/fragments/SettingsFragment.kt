package com.getwemap.example.positioning.fragments

import android.os.Bundle
import androidx.preference.PreferenceFragmentCompat
import com.getwemap.example.positioning.Config
import com.getwemap.example.positioning.R

class SettingsFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.root_preferences, rootKey)
    }

    override fun onDestroyView() {
        Config.applyGlobalOptions(requireContext())
        super.onDestroyView()
    }
}