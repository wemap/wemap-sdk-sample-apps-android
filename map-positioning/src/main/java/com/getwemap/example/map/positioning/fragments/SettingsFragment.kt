package com.getwemap.example.map.positioning.fragments

import android.os.Bundle
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import com.getwemap.example.map.positioning.Config
import com.getwemap.example.map.positioning.R

class SettingsFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.root_preferences, rootKey)
        applySourceFilter(requireArguments().getInt(ARG_LOCATION_SOURCE_ID))
    }

    /**
     * Hides the preference categories that don't apply to the location source selected on the initial
     * screen, so only the relevant knobs are shown:
     * - VPS (ARCore) → its five VPS categories; the VPS Local category is hidden.
     * - VPS Local (offline) → only the VPS Local category; the ARCore VPS categories are hidden.
     * - any other source (simulator, GPS, fused, system default) → all VPS categories hidden.
     *
     * Shared categories (App, Global, Map, Navigation, Visual Debugger) are always visible.
     */
    private fun applySourceFilter(locationSourceId: Int) {
        val showArcoreVps = locationSourceId == LOCATION_SOURCE_VPS
        val showLocalVps = locationSourceId == LOCATION_SOURCE_VPS_LOCAL

        ARCORE_VPS_CATEGORY_KEYS.forEach { setCategoryVisible(it, showArcoreVps) }
        setCategoryVisible(VPS_LOCAL_CATEGORY_KEY, showLocalVps)
    }

    private fun setCategoryVisible(key: String, visible: Boolean) {
        findPreference<PreferenceCategory>(key)?.isVisible = visible
    }

    override fun onDestroyView() {
        Config.applyGlobalOptions(requireContext())
        super.onDestroyView()
    }

    private companion object {
        const val ARG_LOCATION_SOURCE_ID = "locationSourceId"

        // Indices into R.array.location_sources (see InitialFragment).
        const val LOCATION_SOURCE_VPS = 0
        const val LOCATION_SOURCE_VPS_LOCAL = 5

        val ARCORE_VPS_CATEGORY_KEYS = listOf(
            "category_vps",
            "category_state_manager"
        )
        const val VPS_LOCAL_CATEGORY_KEY = "category_vps_local"
    }
}
