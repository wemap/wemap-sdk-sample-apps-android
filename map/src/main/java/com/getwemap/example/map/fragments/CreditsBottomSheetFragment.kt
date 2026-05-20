package com.getwemap.example.map.fragments

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import com.getwemap.example.map.databinding.FragmentCreditsBottomSheetBinding
import com.getwemap.sdk.map.BuildConfig
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class CreditsBottomSheetFragment : BottomSheetDialogFragment() {

    private var _binding: FragmentCreditsBottomSheetBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentCreditsBottomSheetBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        ViewCompat.setAccessibilityHeading(binding.titleLabel, true)

        binding.creditsLabel.text =
            "This map was created using Wemap SDK (v${BuildConfig.LIBRARY_VERSION}) " +
            "with data from OpenStreetMap contributors."

        binding.wemapButton.setOnClickListener { openUrl("https://getwemap.com") }
        binding.osmButton.setOnClickListener { openUrl("https://www.openstreetmap.org/copyright") }
        binding.closeButton.setOnClickListener { dismiss() }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun openUrl(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: ActivityNotFoundException) {
        }
    }
}
