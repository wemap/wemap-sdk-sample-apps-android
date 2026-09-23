package com.getwemap.example.map.fragments

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.lifecycleScope
import com.getwemap.example.map.databinding.FragmentLevelsBinding
import com.getwemap.sdk.core.awaitLoaded
import com.getwemap.sdk.core.model.entities.Levels
import com.getwemap.sdk.core.model.entities.PointOfInterest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.constants.MapLibreConstants
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Point
import org.maplibre.turf.TurfConstants
import org.maplibre.turf.TurfTransformation

/**
 * Demonstrates level switching, POI selection, and state restoration across activity recreation.
 *
 * [MapFragment] forwards the saved instance state to MapLibre, which restores the complete camera before the
 * map loads; the map then applies the map-data camera once its style is ready, so this fragment keeps
 * MapLibre's saved camera and reapplies it afterwards. The SDK does not put the active level in that bundle,
 * so the fragment saves its ID and reapplies it once the recreated map focuses a building.
 *
 * Rotate the device to check both: the camera should stay where you left it, on the level you chose.
 */
class LevelsFragment : MapFragment() {

    override val mapView get() = binding.mapView
    override val levelsSwitcher get() = binding.levelsSwitcher

    private val buttonFirstPOI get() = binding.firstPOI
    private val buttonSecondPOI get() = binding.secondPOI

    private var _binding: FragmentLevelsBinding? = null
    private val binding get() = _binding!!

    private val pois: Set<PointOfInterest> get() = pointOfInterestManager.getPois()
    private var uniqueLevels: Set<Float> = emptySet()
    private var cameraPositionToRestore: CameraPosition? = null
    private var currentLevelId: Float? = null
    private var levelIdToRestore: Float? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        MapLibre.getInstance(requireContext())
        _binding = FragmentLevelsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        cameraPositionToRestore = savedInstanceState?.savedCameraPosition()
        currentLevelId = savedInstanceState?.takeIf { it.containsKey(SAVED_LEVEL_ID) }?.getFloat(SAVED_LEVEL_ID)
        levelIdToRestore = currentLevelId

        super.onViewCreated(view, savedInstanceState)

        buttonFirstPOI.setOnClickListener { firstClicked() }
        buttonSecondPOI.setOnClickListener { secondClicked() }

        lifecycleScope.launch {
            runCatching {
                mapView.awaitLoaded()
            }.onSuccess {
                val map = mapView.map
                cameraPositionToRestore?.let {
                    map.cameraPosition = it
                    cameraPositionToRestore = null
                }

                uniqueLevels = pois.mapNotNull { it.coordinate.levels.single }.toSet()
                map.style?.let { drawCircleAroundCenter(map, it) }

                // Reading `buildingManager` before the map is loaded throws — the manager itself is gated,
                // not just the values it reports — so both collectors start here rather than above.
                val buildingManager = mapView.buildingManager

                launch {
                    // A StateFlow, so a building focused already arrives at once; otherwise this waits.
                    val building = buildingManager.focusedBuildings.filterNotNull().first()
                    val savedLevelId = levelIdToRestore
                    if (savedLevelId != null && building.levels.any { it.id == savedLevelId }) {
                        building.activeLevelId = savedLevelId
                    }
                    currentLevelId = building.activeLevelId
                    levelIdToRestore = null
                }

                launch {
                    buildingManager.activeLevelChanges.collect { (_, level) -> currentLevelId = level.id }
                }
            }.onFailure { error ->
                println("Failed to load mapView with error - $error")
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        currentLevelId?.let { outState.putFloat(SAVED_LEVEL_ID, it) }
        super.onSaveInstanceState(outState)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // region ------ Private ------
    private fun firstClicked() {
        val minLevel = uniqueLevels.minOrNull()
            ?: return println("Failed to select POI on min level because there are no levels")

        selectPOI(minLevel)
    }

    private fun secondClicked() {
        val maxLevel = uniqueLevels.maxOrNull()
            ?: return println("Failed to select POI on max level because there are no levels")

        selectPOI(maxLevel)
    }

    private fun selectPOI(level: Float) {
        val randomPOI = pois.filter { it.coordinate.levels.intersects(Levels.Single(level)) }.randomOrNull()
            ?: return println("Failed to get random POI at level $level")

        pointOfInterestManager.selectPoi(randomPOI)
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

    @Suppress("DEPRECATION")
    private fun Bundle.savedCameraPosition(): CameraPosition? =
        getParcelable(MapLibreConstants.STATE_CAMERA_POSITION)
    // endregion ------ Private ------

    companion object {
        private const val SAVED_LEVEL_ID = "savedLevelId"
        private const val CIRCLE_SOURCE_ID = "center-circle-source"
        private const val CIRCLE_LAYER_ID = "center-circle-layer"
        private const val CIRCLE_RADIUS_METERS = 100.0
        private const val CIRCLE_VERTICES = 64
    }
}
