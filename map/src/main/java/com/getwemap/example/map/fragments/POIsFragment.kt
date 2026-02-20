package com.getwemap.example.map.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.activityViewModels
import com.getwemap.example.common.multiline
import com.getwemap.example.map.databinding.FragmentPOIsBinding
import com.getwemap.sdk.core.internal.extensions.disposedBy
import com.getwemap.sdk.core.model.entities.Coordinate
import com.getwemap.sdk.core.model.entities.PointOfInterest
import com.getwemap.sdk.core.poi.PointOfInterestManagerListener
import com.getwemap.sdk.core.poi.TagMatchMode
import com.google.android.material.snackbar.Snackbar
import com.google.gson.JsonArray
import org.maplibre.android.MapLibre
import org.maplibre.android.plugins.annotation.Circle
import org.maplibre.android.plugins.annotation.CircleManager
import org.maplibre.android.plugins.annotation.CircleOptions

class POIsFragment : MapFragment() {

    override val mapView get() = binding.mapView
    override val levelsSwitcher get() = binding.levelsSwitcher

    private val buttonApplyFilter get() = binding.applyFilter
    private val buttonRemoveFilters get() = binding.removeFilters
    private val buttonShowHiddenPOI get() = binding.showHiddenPOI
    private val buttonHideRandomPOI get() = binding.hideRandomPOI
    private val buttonShowAllPOIs get() = binding.showAllPOIs
    private val buttonHideAllPOIs get() = binding.hideAllPOIs
    private val userLocationTextView get() = binding.userLocationTextView
    private val poisSortedByDistance get() = binding.poisSortedByDistance
    private val poisSortedByTime get() = binding.poisSortedByTime
    private val toggleSelectionModeButton get() = binding.toggleSelectionModeButton

    private var _binding: FragmentPOIsBinding? = null
    private val binding get() = _binding!!

    private var _circleManager: CircleManager? = null
    private val circleManager get() = _circleManager!!

    private val viewModel: PoisViewModel by activityViewModels()

    private var hiddenPOI: PointOfInterest? = null
    private var simulatedUserPosition: Circle? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        MapLibre.getInstance(requireContext())
        _binding = FragmentPOIsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        mapView.getMapViewAsync { mapView, map, style, mapData ->
            pointOfInterestManager.addListener(PointOfInterestManagerListener(
                onClicked = {
                    Snackbar.make(mapView, "onPointOfInterestClick - $it", Snackbar.LENGTH_LONG)
                        .multiline().show()
                }
            ))

            map.addOnMapClickListener {
                if (pointOfInterestManager.selectionMode.isSingle)
                    pointOfInterestManager.unselectPOI()
                else
                    pointOfInterestManager.unselectAllPOIs()
                true
            }

            _circleManager = CircleManager(mapView, map, style)

            map.addOnMapLongClickListener {
                if (simulatedUserPosition != null)
                    circleManager.delete(simulatedUserPosition)

                val array = JsonArray()
                if (focusedBuilding != null && focusedBuilding!!.boundingBox.contains(it))
                    array.add(focusedBuilding!!.activeLevel.id)

                val options = CircleOptions()
                    .withLatLng(it)
                    .withData(array)

                simulatedUserPosition = circleManager.create(options)
                enableSortButtons()

                return@addOnMapLongClickListener true
            }

            viewModel.mapData = mapData
            viewModel.poiManager = mapView.pointOfInterestManager
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

        buttonShowHiddenPOI.setOnClickListener { showHiddenPOI() }
        buttonHideRandomPOI.setOnClickListener { hideRandomPOI() }

        buttonShowAllPOIs.setOnClickListener { showAllPOIs() }
        buttonHideAllPOIs.setOnClickListener { hideAllPOIs() }

        poisSortedByDistance.setOnClickListener { showSortedPOIsFragment(SortingType.DISTANCE) }
        poisSortedByTime.setOnClickListener { showSortedPOIsFragment(SortingType.TIME) }

        binding.userSelectionSwitch.setOnClickListener {
            pointOfInterestManager.isUserSelectionEnabled = !pointOfInterestManager.isUserSelectionEnabled
        }

        toggleSelectionModeButton.setOnClickListener {
            val nextMode = pointOfInterestManager.selectionMode.next()
            pointOfInterestManager.selectionMode = nextMode
            toggleSelectionModeButton.text = "Selection: $nextMode"
        }
    }

    override fun onStart() {
        super.onStart()
        val text = "If you use simulator, long tap at any place on the map to simulate user location. " +
                "After you'll be able to sort POIs by time/distance"
        Snackbar.make(mapView, text, Snackbar.LENGTH_LONG).multiline().show()
    }

    override fun locationManagerReady() {
        super.locationManagerReady()
        mapView.locationManager
            .coordinate
            .subscribe {
                enableSortButtons()
                userLocationTextView.text = it.toStringCompact()
            }.disposedBy(disposeBag)
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

    private fun showSortedPOIsFragment(type: SortingType) {
        viewModel.apply {
            userCoordinate = getLastCoordinate()
            sortingType = type
        }
        val overlay = PoisListFragment()
        overlay.show(parentFragmentManager, null)
    }

    private fun showHiddenPOI() {
        val hiddenPOI = hiddenPOI
            ?: throw IllegalStateException("Hidden POI is null")

        Snackbar.make(mapView, "Showing POI - ${hiddenPOI.name}", Snackbar.LENGTH_LONG).multiline().show()
        pointOfInterestManager.centerToPOI(hiddenPOI)
        if (pointOfInterestManager.showPOI(hiddenPOI)) {
            this.hiddenPOI = null
            updateShowHidePOIButtons()
        } else {
            Snackbar.make(mapView, "Failed to show POI - ${hiddenPOI!!.name}", Snackbar.LENGTH_LONG)
                .multiline().show()
        }
    }

    private fun hideRandomPOI() {
        val randomPOI = pointOfInterestManager.getPOIs().random()
        Snackbar.make(mapView, "Hiding POI - ${randomPOI.name}", Snackbar.LENGTH_LONG).multiline().show()
        pointOfInterestManager.centerToPOI(randomPOI)
        if (pointOfInterestManager.hidePOI(randomPOI)) {
            hiddenPOI = randomPOI
            updateShowHidePOIButtons()
        } else {
            Snackbar.make(mapView, "Failed to hide POI - ${randomPOI.name}", Snackbar.LENGTH_LONG)
                .multiline().show()
        }
    }

    private fun showAllPOIs() {
        val shown = pointOfInterestManager.showAllPOIs()
        buttonHideAllPOIs.isEnabled = shown
        buttonShowAllPOIs.isEnabled = !shown
    }

    private fun hideAllPOIs() {
        val hidden = pointOfInterestManager.hideAllPOIs()
        buttonHideAllPOIs.isEnabled = !hidden
        buttonShowAllPOIs.isEnabled = hidden
        if (hidden) {
            hiddenPOI = null
            updateShowHidePOIButtons()
        }
    }

    private fun updateShowHidePOIButtons() {
        val hiddenPOIExists = hiddenPOI != null
        buttonShowHiddenPOI.isEnabled = hiddenPOIExists
        buttonHideRandomPOI.isEnabled = !hiddenPOIExists
    }

    private fun getLevelFromAnnotation(annotation: Circle): List<Float> {
        return annotation.data!!.asJsonArray.map { it.asFloat }
    }
}