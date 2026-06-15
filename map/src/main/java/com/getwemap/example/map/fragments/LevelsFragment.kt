package com.getwemap.example.map.fragments

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.getwemap.example.map.databinding.FragmentLevelsBinding
import com.getwemap.sdk.core.internal.geo.LevelUtils
import com.getwemap.sdk.core.internal.helpers.Logger
import com.getwemap.sdk.core.model.entities.PointOfInterest
import org.maplibre.android.MapLibre
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Point
import org.maplibre.turf.TurfConstants
import org.maplibre.turf.TurfTransformation

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

        mapView.getMapViewAsync { _, map, style, _ ->
            uniqueLevels = pois.mapNotNull { it.coordinate.levels.firstOrNull() }.toSet()
            drawCircleAroundCenter(map, style)
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

    private fun drawCircleAroundCenter(map: MapLibreMap, style: Style) {
        style.getLayer(CIRCLE_LAYER_ID)?.let { style.removeLayer(it) }
        style.getSource(CIRCLE_SOURCE_ID)?.let { style.removeSource(it) }

        val center = map.cameraPosition.target ?: return
        val circle = TurfTransformation.circle(
            Point.fromLngLat(center.longitude, center.latitude),
            CIRCLE_RADIUS_METERS, CIRCLE_VERTICES, TurfConstants.UNIT_METERS
        )

        style.addSource(GeoJsonSource(CIRCLE_SOURCE_ID, circle))
        style.addLayer(
            LineLayer(CIRCLE_LAYER_ID, CIRCLE_SOURCE_ID).withProperties(
                PropertyFactory.lineColor(Color.RED),
                PropertyFactory.lineWidth(2f),
                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND)
            )
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val CIRCLE_SOURCE_ID = "center-circle-source"
        private const val CIRCLE_LAYER_ID = "center-circle-layer"
        private const val CIRCLE_RADIUS_METERS = 100.0
        private const val CIRCLE_VERTICES = 64
    }
}