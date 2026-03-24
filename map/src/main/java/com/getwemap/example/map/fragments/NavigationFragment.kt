package com.getwemap.example.map.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.lifecycleScope
import com.getwemap.example.common.map.GlobalOptions
import com.getwemap.example.common.multiline
import com.getwemap.example.map.databinding.FragmentNavigationBinding
import com.getwemap.sdk.core.model.entities.Coordinate
import com.getwemap.sdk.core.model.entities.MapData
import com.getwemap.sdk.core.model.services.parameters.ItinerarySearchRules
import com.getwemap.sdk.core.navigation.manager.NavigationManagerListener
import com.getwemap.sdk.map.OnMapViewReadyCallback
import com.getwemap.sdk.map.WemapMapView
import com.google.android.material.snackbar.Snackbar
import com.google.gson.JsonArray
import kotlinx.coroutines.launch
import org.maplibre.android.MapLibre
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.location.modes.RenderMode
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.plugins.annotation.Circle
import org.maplibre.android.plugins.annotation.CircleManager
import org.maplibre.android.plugins.annotation.CircleOptions

class NavigationFragment : MapFragment(), OnMapViewReadyCallback {

    override val mapView get() = binding.mapView
    override val levelsSwitcher get() = binding.levelsSwitcher

    private val textView get() = binding.textView

    private val buttonStartNavigation get() = binding.startNavigation
    private val buttonStopNavigation get() = binding.stopNavigation
    private val buttonStartNavigationFromUserCreatedAnnotations get() = binding.startNavigationFromUserCreatedAnnotations
    private val buttonRemoveUserCreatedAnnotations get() = binding.removeUserCreatedAnnotations
    private val userLocationTextView get() = binding.userLocationTextView

    private val navigationManager get() = mapView.navigationManager

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

        mapView.getMapViewAsync(this)

        buttonStartNavigation.setOnClickListener { startNavigation() }
        buttonStopNavigation.setOnClickListener { stopNavigation() }
        buttonStartNavigationFromUserCreatedAnnotations.setOnClickListener { startNavigationFromUserCreatedAnnotations() }
        buttonRemoveUserCreatedAnnotations.setOnClickListener { removeUserCreatedAnnotations() }
    }

    override fun onMapViewReady(mapView: WemapMapView, map: MapLibreMap, style: Style, data: MapData) {

        lifecycleScope.launch {
            runCatching {
                mapView.itineraryManager.searchRuleNames(mapData.extras?.graphId ?: "")
            }.onSuccess {
                println("Available rule names - $it")
            }.onFailure {
                println("Failed to get rule names with error - $it")
            }
        }

        _circleManager = CircleManager(mapView, map, style)
        setupNavigationManagerListener()

        map.addOnMapLongClickListener {
            if (userCreatedAnnotations.size >= 2) {
                Snackbar.make(mapView,
                    "You already created 2 annotations. Remove old ones to be able to add new",
                    Snackbar.LENGTH_LONG).multiline().show()
                return@addOnMapLongClickListener false
            }

            val array = JsonArray()
            if (focusedBuilding != null && focusedBuilding!!.boundingBox.contains(it))
                array.add(focusedBuilding!!.activeLevel.id)

            val options = CircleOptions()
                .withLatLng(it)
                .withData(array)

            val point = circleManager.create(options)
            userCreatedAnnotations.add(point)
            updateUI()

            return@addOnMapLongClickListener true
        }
    }

    override fun locationManagerReady() {
        super.locationManagerReady()
        lifecycleScope.launch {
            mapView.locationManager
                .coordinateFlow
                .collect {
                    userLocationTextView.text = "$it"
                }
        }
    }

    private fun startNavigation() {
        startNavigation(null, getDestinationCoordinate())
    }

    private fun stopNavigation() {
        navigationManager.stopNavigation()
            .onSuccess {
                simulator?.reset()
                buttonStopNavigation.isEnabled = false
                updateUI()
            }.onFailure {
                val text = "Failed to stop navigation with error - $it"
                Snackbar.make(mapView, text, Snackbar.LENGTH_LONG).multiline().show()
            }
    }

    private fun startNavigationFromUserCreatedAnnotations() {
        val origin = getOriginCoordinate()
        val destination = getDestinationCoordinate()

        startNavigation(origin, destination)
    }

    private fun startNavigation(origin: Coordinate?, destination: Coordinate) {
        disableStartButtons()

        val navOptions = GlobalOptions.navigationOptions(requireContext())
        val rules = if (binding.wheelchairSwitch.isChecked) ItinerarySearchRules.WHEELCHAIR else ItinerarySearchRules()

        lifecycleScope.launch {
            runCatching {
                navigationManager
                    .startNavigation(
                        origin, destination,
                        options = navOptions,
                        searchRules = rules,
                        itineraryOptions = GlobalOptions.itineraryOptions
                    )
            }.onSuccess {
                // also you can use simulator to generate locations along the itinerary
                simulator?.setItinerary(it.itinerary)
                buttonStopNavigation.isEnabled = true
                mapView.locationManager.apply {
                    cameraMode = CameraMode.TRACKING_COMPASS
                    renderMode = RenderMode.COMPASS
                }
            }.onFailure {
                val text = "Failed to start navigation with error - $it"
                Snackbar.make(mapView, text, Snackbar.LENGTH_LONG).multiline().show()
                updateUI()
            }
        }
    }

    private fun setupNavigationManagerListener() {
        navigationManager.addListener(NavigationManagerListener(
            onInfoChanged = { info ->
                val nextStepInstructions = info.nextStep?.getNavigationInstructions(requireContext())?.instructions
                textView.text = info.shortDescription + "\nNext - $nextStepInstructions"
                textView.visibility = View.VISIBLE
            },
            onStarted = { navigation ->
                textView.visibility = View.VISIBLE
                Snackbar.make(mapView, "Navigation started", Snackbar.LENGTH_LONG).multiline().show()
                buttonStopNavigation.isEnabled = true

                navigation.itinerary.legsSteps.forEach {
                    println(it.getNavigationInstructions(requireContext()))
                }
            },
            onStopped = {
                textView.visibility = View.GONE
                Snackbar.make(mapView, "Navigation stopped", Snackbar.LENGTH_LONG).multiline().show()
                buttonStopNavigation.isEnabled = false
                updateUI()
            },
            onArrived = {
                Snackbar.make(mapView, "Navigation arrived at destination", Snackbar.LENGTH_LONG).multiline().show()
            },
            onFailed = { error ->
                textView.visibility = View.GONE
                Snackbar.make(mapView, "Navigation failed with error - $error", Snackbar.LENGTH_LONG).multiline().show()
            },
            onRecalculated = {
                Snackbar.make(mapView, "Navigation recalculated", Snackbar.LENGTH_LONG).multiline().show()
            }
        ))
    }

    private fun updateUI() {
        buttonStartNavigation.isEnabled = userCreatedAnnotations.size == 1 && !buttonStopNavigation.isEnabled
        buttonStartNavigationFromUserCreatedAnnotations.isEnabled = userCreatedAnnotations.size == 2 && !buttonStopNavigation.isEnabled
        buttonRemoveUserCreatedAnnotations.isEnabled = userCreatedAnnotations.isNotEmpty()
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

    private fun getLevelFromAnnotation(annotation: Circle): List<Float> {
        return annotation.data!!.asJsonArray.map { it.asFloat }
    }

    private fun getDestinationCoordinate(): Coordinate {
        return getCoordinateFrom(userCreatedAnnotations.first())
    }

    private fun getOriginCoordinate(): Coordinate {
        return getCoordinateFrom(userCreatedAnnotations[1])
    }

    private fun getCoordinateFrom(annotation: Circle): Coordinate {
        val to = annotation.latLng
        return Coordinate(to.latitude, to.longitude, getLevelFromAnnotation(annotation))
    }

    override fun onDestroyView() {
        _circleManager?.onDestroy()
        super.onDestroyView()
        _binding = null
    }
}