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
import com.getwemap.sdk.map.buildings.Building
import com.getwemap.sdk.map.buildings.BuildingManager
import com.getwemap.sdk.map.buildings.BuildingManagerListener
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
 * [MapFragment] forwards the saved instance state to MapLibre, which initially restores the complete camera.
 * Pre-v1 [com.getwemap.sdk.map.WemapMapView] then applies the map-data camera after its style loads, so this
 * fragment retains MapLibre's saved camera and reapplies it when Wemap reports the map ready. The SDK does not
 * put the active level in that bundle, so the fragment saves its ID and reapplies it when the recreated map
 * focuses a building.
 */
class LevelsFragment : MapFragment() {

    override val mapView get() = binding.mapView
    override val levelsSwitcher get() = binding.levelsSwitcher

    private val buttonFirstPOI get() = binding.firstPOI
    private val buttonSecondPOI get() = binding.secondPOI

    private var _binding: FragmentLevelsBinding? = null
    private val binding get() = _binding!!

    private val pois: Set<PointOfInterest> get() = pointOfInterestManager.getPOIs()
    private var uniqueLevels: Set<Float> = emptySet()
    private var observedBuildingManager: BuildingManager? = null
    private var cameraPositionToRestore: CameraPosition? = null
    private var currentLevelId: Float? = null
    private var levelIdToRestore: Float? = null

    private val buildingManagerListener = BuildingManagerListener(
        onActiveLevelChanged = { _, level -> currentLevelId = level.id },
        onFocusedBuildingChanged = ::restoreOrRememberLevel
    )

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

        mapView.getMapViewAsync { loadedView, map, style, _ ->
            val manager = loadedView.buildingManager
            if (manager.addListener(buildingManagerListener)) {
                observedBuildingManager = manager
            }

            cameraPositionToRestore?.let {
                map.cameraPosition = it
                cameraPositionToRestore = null
            }

            uniqueLevels = pois.mapNotNull { it.coordinate.levels.firstOrNull() }.toSet()
            drawCircleAroundCenter(map, style)
            restoreOrRememberLevel(manager.focusedBuilding)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        currentLevelId?.let { outState.putFloat(SAVED_LEVEL_ID, it) }
        super.onSaveInstanceState(outState)
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
        observedBuildingManager?.removeListener(buildingManagerListener)
        observedBuildingManager = null
        super.onDestroyView()
        _binding = null
    }

    private fun restoreOrRememberLevel(building: Building?) {
        val focusedBuilding = building ?: return
        val savedLevelId = levelIdToRestore
        if (savedLevelId != null) {
            if (focusedBuilding.levels.any { it.id == savedLevelId }) {
                focusedBuilding.activeLevelId = savedLevelId
                currentLevelId = savedLevelId
            } else {
                currentLevelId = focusedBuilding.activeLevelId
            }
            levelIdToRestore = null
        } else {
            currentLevelId = focusedBuilding.activeLevelId
        }
    }

    @Suppress("DEPRECATION")
    private fun Bundle.savedCameraPosition(): CameraPosition? =
        getParcelable(MapLibreConstants.STATE_CAMERA_POSITION)

    companion object {
        private const val SAVED_LEVEL_ID = "savedLevelId"
        private const val CIRCLE_SOURCE_ID = "center-circle-source"
        private const val CIRCLE_LAYER_ID = "center-circle-layer"
        private const val CIRCLE_RADIUS_METERS = 100.0
        private const val CIRCLE_VERTICES = 64
    }
}
