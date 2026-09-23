package com.getwemap.example.map.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.getwemap.example.common.multiline
import com.getwemap.example.map.databinding.FragmentPoisBinding
import com.getwemap.example.map.insetOverlayBelowTransparentAppBar
import com.getwemap.sdk.core.awaitLoaded
import com.getwemap.sdk.core.model.entities.Coordinate
import com.getwemap.sdk.core.model.entities.Levels
import com.getwemap.sdk.core.model.entities.PointOfInterest
import com.getwemap.sdk.core.poi.TagMatchMode
import com.getwemap.sdk.map.WemapMapView
import com.google.android.material.snackbar.Snackbar
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import kotlinx.coroutines.launch
import org.maplibre.android.MapLibre
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.plugins.annotation.Circle
import org.maplibre.android.plugins.annotation.CircleManager
import org.maplibre.android.plugins.annotation.CircleOptions

class PoisFragment : MapFragment() {

    override val mapView get() = binding.mapView
    override val levelsSwitcher get() = binding.levelsSwitcher

    private val buttonApplyFilter get() = binding.applyFilter
    private val buttonRemoveFilters get() = binding.removeFilters
    private val buttonShowHiddenPoi get() = binding.showHiddenPoi
    private val buttonHideRandomPoi get() = binding.hideRandomPoi
    private val buttonShowAllPois get() = binding.showAllPois
    private val buttonHideAllPois get() = binding.hideAllPois
    private val userLocationTextView get() = binding.userLocationTextView
    private val poisSortedByDistance get() = binding.poisSortedByDistance
    private val poisSortedByTime get() = binding.poisSortedByTime
    private val toggleSelectionModeButton get() = binding.toggleSelectionModeButton

    private var _binding: FragmentPoisBinding? = null
    private val binding get() = _binding!!

    private var _circleManager: CircleManager? = null
    private val circleManager get() = _circleManager!!

    private val viewModel: PoisViewModel by activityViewModels()

    private var hiddenPoi: PointOfInterest? = null
    private var simulatedUserPosition: Circle? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        MapLibre.getInstance(requireContext())
        _binding = FragmentPoisBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // One call for the whole screen: the app bar floats over the map, and every control this screen
        // puts over it lives in `overlay`.
        binding.overlay.insetOverlayBelowTransparentAppBar()

        lifecycleScope.launch {
            runCatching {
                mapView.awaitLoaded()
            }.onSuccess {
                onMapViewReady(it, it.map, it.map.style!!)
            }.onFailure { error ->
                val message = "Failed to load map with error - $error"
                Snackbar.make(mapView, message, Snackbar.LENGTH_LONG).multiline().show()
            }
        }

        buttonApplyFilter.setOnClickListener {
            if (pointOfInterestManager.filterByTags(listOf("53003", "53014"), TagMatchMode.AND)) {
                buttonApplyFilter.isEnabled = false
                buttonRemoveFilters.isEnabled = true
            }
        }

        buttonRemoveFilters.setOnClickListener {
            pointOfInterestManager.removeFilters()
            buttonApplyFilter.isEnabled = true
            buttonRemoveFilters.isEnabled = false
        }

        buttonShowHiddenPoi.setOnClickListener { showHiddenPoi() }
        buttonHideRandomPoi.setOnClickListener { hideRandomPoi() }

        buttonShowAllPois.setOnClickListener { showAllPois() }
        buttonHideAllPois.setOnClickListener { hideAllPois() }

        poisSortedByDistance.setOnClickListener { showSortedPoisFragment(SortingType.DISTANCE) }
        poisSortedByTime.setOnClickListener { showSortedPoisFragment(SortingType.TIME) }

        binding.userSelectionSwitch.setOnClickListener {
            pointOfInterestManager.isUserSelectionEnabled = !pointOfInterestManager.isUserSelectionEnabled
        }

        toggleSelectionModeButton.setOnClickListener {
            val nextMode = pointOfInterestManager.selectionMode.next()
            pointOfInterestManager.selectionMode = nextMode
            toggleSelectionModeButton.text = "Selection: $nextMode"
        }
    }

    fun onMapViewReady(mapView: WemapMapView, map: MapLibreMap, style: Style) {
        viewLifecycleOwner.lifecycleScope.launch {
            pointOfInterestManager.touchedPois.collect { poi ->
                val text = "onPointOfInterestClick - $poi"
                Snackbar.make(mapView, text, Snackbar.LENGTH_LONG).multiline().show()
            }
        }

        map.addOnMapClickListener {
            if (pointOfInterestManager.selectionMode.isSingle)
                pointOfInterestManager.unselectPoi()
            else
                pointOfInterestManager.unselectAllPois()
            true
        }

        _circleManager = CircleManager(mapView, map, style)

        map.addOnMapLongClickListener {
            if (simulatedUserPosition != null)
                circleManager.delete(simulatedUserPosition)

            val properties = JsonObject()
            focusedBuilding?.let { building ->
                if (building.boundingBox.contains(it))
                    properties.add("level", JsonPrimitive(building.activeLevel.id))
            }

            val options = CircleOptions()
                .withLatLng(it)
                .withData(properties)

            simulatedUserPosition = circleManager.create(options)
            enableSortButtons()

            return@addOnMapLongClickListener true
        }

        viewModel.poiManager = mapView.pointOfInterestManager
    }

    override fun onStart() {
        super.onStart()
        val text = "If you use simulator, long tap at any place on the map to simulate user location. " +
                "After you'll be able to sort POIs by time/distance"
        Snackbar.make(mapView, text, Snackbar.LENGTH_LONG).multiline().show()
    }

    override fun locationManagerReady() {
        super.locationManagerReady()
        lifecycleScope.launch {
            mapView.locationManager
                .coordinates
                .collect {
                    enableSortButtons()
                    userLocationTextView.text = it.toCompactString()
                    userLocationTextView.isVisible = true
                }
        }
    }

    override fun onDestroyView() {
        _circleManager?.onDestroy()
        super.onDestroyView()
        _binding = null
    }

    // Private

    private fun enableSortButtons() {
        poisSortedByDistance.isEnabled = true
        poisSortedByTime.isEnabled = true
    }

    private fun getLastCoordinate(): Coordinate {
        return mapView.locationManager.lastCoordinate ?: getSimulatedCoordinate()
    }

    private fun getSimulatedCoordinate(): Coordinate {
        val simulated = simulatedUserPosition!!
        val latLng = simulated.latLng
        return Coordinate(latLng.latitude, latLng.longitude, getLevelFromAnnotation(simulated))
    }

    private fun showSortedPoisFragment(type: SortingType) {
        if (pointOfInterestManager.getPois().isEmpty()) {
            val message = "This map has no POIs. So nothing to sort by distance or time"
            Snackbar.make(mapView, message, Snackbar.LENGTH_LONG).multiline().show()
            return
        }

        viewModel.apply {
            userCoordinate = getLastCoordinate()
            sortingType = type
        }
        val overlay = PoisListFragment()
        overlay.show(parentFragmentManager, null)
    }

    private fun showHiddenPoi() {
        val hiddenPoi = hiddenPoi
            ?: return Snackbar.make(mapView, "Hidden POI is null", Snackbar.LENGTH_LONG).multiline().show()

        Snackbar.make(mapView, "Showing POI - ${hiddenPoi.name}", Snackbar.LENGTH_LONG).multiline().show()
        pointOfInterestManager.centerToPoi(hiddenPoi)
        if (pointOfInterestManager.showPoi(hiddenPoi)) {
            this.hiddenPoi = null
            updateShowHidePoiButtons()
        } else {
            Snackbar.make(mapView, "Failed to show POI - ${hiddenPoi.name}", Snackbar.LENGTH_LONG)
                .multiline().show()
        }
    }

    private fun hideRandomPoi() {
        val randomPoi = pointOfInterestManager.getPois().randomOrNull()
            ?: return Snackbar.make(mapView, "Random POI is nil", Snackbar.LENGTH_SHORT).multiline().show()

        Snackbar.make(mapView, "Hiding POI - ${randomPoi.name}", Snackbar.LENGTH_LONG).multiline().show()
        pointOfInterestManager.centerToPoi(randomPoi)
        if (pointOfInterestManager.hidePoi(randomPoi)) {
            hiddenPoi = randomPoi
            updateShowHidePoiButtons()
        } else {
            Snackbar.make(mapView, "Failed to hide POI - ${randomPoi.name}", Snackbar.LENGTH_LONG)
                .multiline().show()
        }
    }

    private fun showAllPois() {
        val shown = pointOfInterestManager.showAllPois()
        buttonHideAllPois.isEnabled = shown
        buttonShowAllPois.isEnabled = !shown
    }

    private fun hideAllPois() {
        val hidden = pointOfInterestManager.hideAllPois()
        buttonHideAllPois.isEnabled = !hidden
        buttonShowAllPois.isEnabled = hidden
        if (hidden) {
            hiddenPoi = null
            updateShowHidePoiButtons()
        }
    }

    private fun updateShowHidePoiButtons() {
        val hiddenPoiExists = hiddenPoi != null
        buttonShowHiddenPoi.isEnabled = hiddenPoiExists
        buttonHideRandomPoi.isEnabled = !hiddenPoiExists
    }

    private fun getLevelFromAnnotation(annotation: Circle): Levels {
        val level = annotation.data?.asJsonObject?.get("level")?.asFloat
            ?: return Levels.Outdoor

        return Levels.Single(level)
    }
}