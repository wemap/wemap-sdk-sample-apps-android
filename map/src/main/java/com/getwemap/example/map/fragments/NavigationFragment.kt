package com.getwemap.example.map.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.getwemap.example.common.map.GlobalOptions
import com.getwemap.example.common.multiline
import com.getwemap.example.map.databinding.FragmentNavigationBinding
import com.getwemap.example.map.insetOverlayBelowTransparentAppBar
import com.getwemap.sdk.core.awaitLoaded
import com.getwemap.sdk.core.model.entities.Coordinate
import com.getwemap.sdk.core.model.entities.Levels
import com.getwemap.sdk.core.model.services.ItinerarySearchRules
import com.getwemap.sdk.core.navigation.manager.NavigationEvent
import com.google.android.material.snackbar.Snackbar
import com.google.gson.JsonObject
import com.google.gson.JsonPrimitive
import kotlinx.coroutines.launch
import org.maplibre.android.MapLibre
import org.maplibre.android.location.modes.RenderMode
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.plugins.annotation.Circle
import org.maplibre.android.plugins.annotation.CircleManager
import org.maplibre.android.plugins.annotation.CircleOptions

/**
 * Navigating between two points the user picks on the map, and reporting progress along the way.
 *
 * Long-press the map to drop an annotation. One is a destination navigated to from the user's position; two
 * are an origin and a destination navigated between, which is how the sample can be driven without a fix.
 *
 * The manager reports through `Flow` properties rather than a listener interface, so the screen collects
 * [com.getwemap.sdk.core.navigation.manager.NavigationManager.navigationEvents],
 * `navigationInfoUpdates` and `errors` for as long as the view is alive.
 */
class NavigationFragment : MapFragment() {

    override val mapView get() = binding.mapView
    override val levelsSwitcher get() = binding.levelsSwitcher

    private val textView get() = binding.textView

    private val buttonStartNavigation get() = binding.startNavigation
    private val buttonStopNavigation get() = binding.stopNavigation
    private val buttonStartNavigationFromUserCreatedAnnotations get() = binding.startNavigationFromUserCreatedAnnotations
    private val buttonRemoveUserCreatedAnnotations get() = binding.removeUserCreatedAnnotations
    private val userLocationTextView get() = binding.userLocationTextView

    private val navigationManager get() = mapView.navigationManager
    private val locationManager get() = mapView.locationManager

    private val userCreatedAnnotations: MutableList<Circle> = mutableListOf()

    private var _binding: FragmentNavigationBinding? = null
    private val binding get() = _binding!!

    private var _circleManager: CircleManager? = null
    private val circleManager get() = _circleManager!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        MapLibre.getInstance(requireContext())
        _binding = FragmentNavigationBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // One call for the whole screen, on the container holding its overlay controls.
        binding.overlay.insetOverlayBelowTransparentAppBar()

        buttonStartNavigation.setOnClickListener { startNavigation() }
        buttonStopNavigation.setOnClickListener { stopNavigation() }
        buttonStartNavigationFromUserCreatedAnnotations.setOnClickListener { startNavigationFromUserCreatedAnnotations() }
        buttonRemoveUserCreatedAnnotations.setOnClickListener { removeUserCreatedAnnotations() }

        lifecycleScope.launch {
            runCatching {
                mapView.awaitLoaded()
            }.onSuccess {
                onMapLoaded(mapView.map, mapView.map.style!!)
                // `navigationManager`, like every other manager, is only reachable once the map has loaded.
                observeNavigationManager()
                updateUI()
            }.onFailure { error ->
                println("Failed to load mapView with error - $error")
            }
        }
    }

    override fun locationManagerReady() {
        super.locationManagerReady()
        lifecycleScope.launch {
            locationManager
                .coordinates
                .collect {
                    userLocationTextView.text = "$it"
                    userLocationTextView.isVisible = true
                }
        }
    }

    override fun onDestroyView() {
        _circleManager?.onDestroy()
        super.onDestroyView()
        _binding = null
    }

    // region ------ Private ------
    private fun onMapLoaded(map: MapLibreMap, style: Style) {

        lifecycleScope.launch {
            runCatching {
                mapView.itineraryManager.searchRuleNames()
            }.onSuccess {
                println("Available rule names - $it")
            }.onFailure {
                println("Failed to get rule names with error - $it")
            }
        }

        _circleManager = CircleManager(mapView, map, style)

        map.addOnMapLongClickListener { latLng ->
            if (userCreatedAnnotations.size >= 2) {
                Snackbar.make(mapView,
                    "You already created 2 annotations. Remove old ones to be able to add new",
                    Snackbar.LENGTH_LONG).multiline().show()
                return@addOnMapLongClickListener false
            }

            val data = JsonObject()
            val building = focusedBuilding
            if (building != null && building.boundingBox.contains(latLng)) {
                data.add("level", JsonPrimitive(building.activeLevel.id))
            }

            val point = circleManager.create(CircleOptions().withLatLng(latLng).withData(data))
            userCreatedAnnotations.add(point)
            updateUI()

            return@addOnMapLongClickListener true
        }
    }

    private fun startNavigation() {
        startNavigation(null, getDestinationCoordinate())
    }

    private fun startNavigationFromUserCreatedAnnotations() {
        startNavigation(getOriginCoordinate(), getDestinationCoordinate())
    }

    private fun startNavigation(origin: Coordinate?, destination: Coordinate) {
        disableStartButtons()

        val navOptions = GlobalOptions.navigationOptions(requireContext())
        val rules = if (binding.wheelchairSwitch.isChecked) {
            ItinerarySearchRules.WHEELCHAIR
        } else {
            ItinerarySearchRules()
        }

        lifecycleScope.launch {
            runCatching {
                navigationManager.startNavigation(
                    origin, destination,
                    options = navOptions,
                    searchRules = rules,
                    itineraryOptions = GlobalOptions.itineraryOptions
                )
            }.onSuccess {
                // also you can use simulator to generate locations along the itinerary
                simulator?.setItinerary(it.itinerary)
                locationManager.renderMode = RenderMode.COMPASS
                updateUI()
            }.onFailure {
                val text = "Failed to start navigation with error - $it"
                Snackbar.make(mapView, text, Snackbar.LENGTH_LONG).multiline().show()
                updateUI()
            }
        }
    }

    private fun stopNavigation() {
        navigationManager.stopNavigation()
            .onSuccess {
                simulator?.reset()
                updateUI()
            }.onFailure {
                val text = "Failed to stop navigation with error - $it"
                Snackbar.make(mapView, text, Snackbar.LENGTH_LONG).multiline().show()
            }
    }

    private fun observeNavigationManager() {
        viewLifecycleOwner.lifecycleScope.launch {
            launch {
                navigationManager.navigationInfoUpdates.collect { info ->
                    val nextStepInstructions = info.nextStep?.getNavigationInstructions(requireContext())?.instructions
                    textView.text = info.toCompactString() + "\nNext - $nextStepInstructions"
                    textView.visibility = View.VISIBLE
                }
            }
            launch {
                navigationManager.navigationEvents.collect { event ->
                    when (event) {
                        is NavigationEvent.Started -> {
                            textView.visibility = View.VISIBLE
                            Snackbar.make(mapView, "Navigation started", Snackbar.LENGTH_LONG).multiline().show()
                            updateUI()

                            event.navigation.itinerary.legsSteps.forEach {
                                println(it.getNavigationInstructions(requireContext()))
                            }
                        }
                        is NavigationEvent.Stopped -> {
                            textView.visibility = View.GONE
                            Snackbar.make(mapView, "Navigation stopped", Snackbar.LENGTH_LONG).multiline().show()
                            updateUI()
                        }
                        is NavigationEvent.Arrived ->
                            Snackbar.make(mapView, "Navigation arrived at destination", Snackbar.LENGTH_LONG)
                                .multiline().show()
                        is NavigationEvent.Recalculated ->
                            Snackbar.make(mapView, "Navigation recalculated", Snackbar.LENGTH_LONG).multiline().show()
                    }
                }
            }
            launch {
                navigationManager.errors.collect { error ->
                    textView.visibility = View.GONE
                    Snackbar.make(mapView, "Navigation failed with error - $error", Snackbar.LENGTH_LONG)
                        .multiline().show()
                }
            }
        }
    }

    private fun updateUI() {
        // The manager is the source of truth for this — a flag of our own would have to be kept in step with
        // navigations the SDK ends by itself, arrival being the obvious one.
        val isNavigating = navigationManager.hasActiveNavigation
        buttonStartNavigation.isEnabled = userCreatedAnnotations.size == 1 && !isNavigating
        buttonStartNavigationFromUserCreatedAnnotations.isEnabled = userCreatedAnnotations.size == 2 && !isNavigating
        buttonRemoveUserCreatedAnnotations.isEnabled = userCreatedAnnotations.isNotEmpty()
        buttonStopNavigation.isEnabled = isNavigating
    }

    private fun disableStartButtons() {
        buttonStartNavigation.isEnabled = false
        buttonStartNavigationFromUserCreatedAnnotations.isEnabled = false
    }

    private fun removeUserCreatedAnnotations() {
        circleManager.delete(userCreatedAnnotations)
        userCreatedAnnotations.clear()
        updateUI()
    }

    private fun getDestinationCoordinate(): Coordinate = getCoordinateFrom(userCreatedAnnotations.first())

    private fun getOriginCoordinate(): Coordinate = getCoordinateFrom(userCreatedAnnotations[1])

    private fun getCoordinateFrom(annotation: Circle): Coordinate {
        val to = annotation.latLng
        return Coordinate(to.latitude, to.longitude, getLevelsFrom(annotation))
    }

    private fun getLevelsFrom(annotation: Circle): Levels {
        val level = annotation.data?.asJsonObject?.get("level")?.asFloat
            ?: return Levels.Outdoor

        return Levels.Single(level)
    }
    // endregion ------ Private ------
}
