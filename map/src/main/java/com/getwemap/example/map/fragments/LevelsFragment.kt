package com.getwemap.example.map.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.getwemap.example.map.databinding.FragmentLevelsBinding
import com.getwemap.sdk.core.internal.geo.LevelUtils
import com.getwemap.sdk.core.internal.helpers.Logger
import com.getwemap.sdk.core.model.entities.PointOfInterest
import org.maplibre.android.MapLibre

class LevelsFragment : MapFragment() {

    override val mapView get() = binding.mapView
    override val levelsSwitcher get() = binding.levelsSwitcher

    private val buttonFirstPOI get() = binding.firstPOI
    private val buttonSecondPOI get() = binding.secondPOI

    private var _binding: FragmentLevelsBinding? = null
    private val binding get() = _binding!!

    private val pois: Set<PointOfInterest> get() = pointOfInterestManager.getPOIs()
    private var uniqueLevels: Set<Float> = emptySet()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        MapLibre.getInstance(requireContext())
        _binding = FragmentLevelsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        buttonFirstPOI.setOnClickListener { firstClicked() }
        buttonSecondPOI.setOnClickListener { secondClicked() }

        mapView.getMapViewAsync { mapView, map, style, data ->
            uniqueLevels = pois.mapNotNull { it.coordinate.levels.firstOrNull() }.toSet()
        }
    }

    private fun firstClicked() {
        val minLevel = uniqueLevels.minOrNull()
            ?: return Logger.e("Failed to select POI on min level because there are no levels")

        selectPOI(minLevel)
    }

    private fun secondClicked() {
        val maxLevel = uniqueLevels.maxOrNull()
            ?: return Logger.e("Failed to select POI on max level because there are no levels")

        selectPOI(maxLevel)
    }

    private fun selectPOI(level: Float) {
        val randomPOI = pois.filter { LevelUtils.intersects(it.coordinate.levels, listOf(level)) }.randomOrNull()
            ?: return Logger.e("Failed to get random POI at level $level")

        pointOfInterestManager.selectPOI(randomPOI)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}