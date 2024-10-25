package com.getwemap.example.map.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.activityViewModels
import com.getwemap.example.common.multiline
import com.getwemap.example.map.Config
import com.getwemap.example.map.databinding.FragmentPOIsBinding
import com.getwemap.sdk.core.model.entities.Coordinate
import com.getwemap.sdk.core.model.entities.PointOfInterest
import com.getwemap.sdk.core.navigation.manager.NavigationManagerListener
import com.getwemap.sdk.core.poi.PointOfInterestManagerListener
import com.google.android.material.snackbar.Snackbar
import com.google.gson.JsonArray
import org.maplibre.android.MapLibre
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.location.modes.RenderMode
import org.maplibre.android.plugins.annotation.Circle
import org.maplibre.android.plugins.annotation.CircleManager
import org.maplibre.android.plugins.annotation.CircleOptions

class POIsFragment : MapFragment() {

    override val mapView get() = binding.mapView
    override val levelsSwitcher get() = binding.levelsSwitcher
    private val textView get() = binding.textView

    private val buttonApplyFilter get() = binding.applyFilter
    private val buttonRemoveFilters get() = binding.removeFilters
    private val buttonStartNavigation get() = binding.startNavigation
    private val buttonStopNavigation get() = binding.stopNavigation
    private val buttonStartNavigationFromSimulatedUserPosition get() = binding.startNavigationFromSimulatedUserPosition
    private val buttonRemoveSimulatedUserPosition get() = binding.removeSimulatedUserPosition
    private val userLocationTextView get() = binding.userLocationTextView

    private val navigationManager get() = mapView.navigationManager

    private var _binding: FragmentPOIsBinding? = null
    private val binding get() = _binding!!

    private var _circleManager: CircleManager? = null
    private val circleManager get() = _circleManager!!

    private val selectedPOI: PointOfInterest?
        get() = pointOfInterestManager.getSelectedPOI()

    private var simulatedUserPosition: Circle? = null

    private val viewModel: PoisViewModel by activityViewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        MapLibre.getInstance(requireContext())
        _binding = FragmentPOIsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        mapView.getMapViewAsync { mapView, map, style, mapData ->
            pointOfInterestManager.addListener(PointOfInterestManagerListener(
                onSelected = {
                    updateUI()
                },
                onUnselected = {
                    updateUI()
                },
                onClicked = {
                    Snackbar.make(mapView, "onPointOfInterestClick - $it", Snackbar.LENGTH_LONG)
                        .multiline().show()
                }
            ))

            map.addOnMapClickListener {
                if (selectedPOI != null)
                    pointOfInterestManager.unselectPOI(selectedPOI!!)
                true
            }

            _circleManager = CircleManager(mapView, map, style)
            setupNavigationManagerListener()

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
                updateUI()

                return@addOnMapLongClickListener true
            }

            viewModel.mapData = mapData
            viewModel.poiManager = mapView.pointOfInterestManager
        }

        buttonApplyFilter.setOnClickListener {
            if (pointOfInterestManager.filterByTag("52970")) {
                buttonApplyFilter.isEnabled = false
                buttonRemoveFilters.isEnabled = true
            }
        }

        buttonRemoveFilters.setOnClickListener {
            pointOfInterestManager.removeFilters()
            buttonApplyFilter.isEnabled = true
            buttonRemoveFilters.isEnabled = false
        }

        buttonStartNavigation.setOnClickListener {
            startNavigation()
        }

        buttonStopNavigation.setOnClickListener {
            stopNavigation()
        }

        buttonStartNavigationFromSimulatedUserPosition.setOnClickListener {
            startNavigationFromSimulatedUserPosition()
        }

        buttonRemoveSimulatedUserPosition.setOnClickListener {
            removeSimulatedUserPosition()
        }

        binding.poisButton.setOnClickListener {
            viewModel.userCoordinate = getLastCoordinate()
            val overlay = PoisListFragment()

            overlay.show(parentFragmentManager, null)
        }

        binding.userSelectionSwitch.setOnClickListener {
            pointOfInterestManager.isUserSelectionEnabled = !pointOfInterestManager.isUserSelectionEnabled
        }

        binding.toggleSelectionModeButton.setOnClickListener {
            val nextMode = pointOfInterestManager.selectionMode.next()
            pointOfInterestManager.selectionMode = nextMode
            binding.toggleSelectionModeButton.text = "Selection: $nextMode"
        }
    }

    override fun onStart() {
        super.onStart()
        Snackbar.make(
            mapView, "Select one POI on the map and after click on start navigation button. " +
                    "If you use simulator - perform long tap at any place on the map and then select at least one POI " +
                    "to start navigation", Snackbar.LENGTH_LONG
        )
            .multiline().show()
    }

    override fun locationManagerReady() {
        super.locationManagerReady()
        val coordinateUpdate = mapView.locationManager.rxCoordinate.subscribe {
            userLocationTextView.text = "$it"
        }
        disposeBag.add(coordinateUpdate)
    }

    private fun getLastCoordinate(): Coordinate {
        return mapView.locationManager.lastCoordinate ?: getSimulatedCoordinate()
    }

    private fun getSimulatedCoordinate(): Coordinate {
        val simulated = simulatedUserPosition!!
        val latLng = simulated.latLng
        return Coordinate(latLng.latitude, latLng.longitude, getLevelFromAnnotation(simulated))
    }

    private fun startNavigation() {
        startNavigationToSelectedPOI()
    }

    private fun stopNavigation() {
        navigationManager
            .stopNavigation()
            .fold(
                {
                    simulator.reset()
                    buttonStopNavigation.isEnabled = false
                    updateUI()
                }, {
                    Snackbar.make(mapView, "Failed to stop navigation with error - $it", Snackbar.LENGTH_LONG)
                        .multiline().show()
                }
            )
    }

    private fun startNavigationFromSimulatedUserPosition() {
        startNavigationToSelectedPOI(getSimulatedCoordinate())
    }

    private fun startNavigationToSelectedPOI(origin: Coordinate? = null) {
        disableStartButtons()

        val destination = selectedPOI!!.coordinate

        val disposable = navigationManager
            .startNavigation(
                origin, destination,
                options = Config.globalNavigationOptions(requireContext()),
                itineraryOptions = Config.globalItineraryOptions
            )
            .subscribe({
                // also you can use simulator to generate locations along the itinerary
                simulator.setItinerary(it.itinerary)
                buttonStopNavigation.isEnabled = true
                mapView.locationManager.apply {
                    cameraMode = CameraMode.TRACKING_COMPASS
                    renderMode = RenderMode.COMPASS
                }
            }, {
                Snackbar.make(mapView, "Failed to start navigation with error - $it", Snackbar.LENGTH_LONG)
                    .multiline().show()
                updateUI()
            })

        disposeBag.add(disposable)
    }

    private fun setupNavigationManagerListener() {
        navigationManager.addListener(NavigationManagerListener(
            onInfoChanged = { info ->
                textView.text = info.shortDescription
                textView.visibility = View.VISIBLE
            },
            onStarted = {
                textView.visibility = View.VISIBLE
                Snackbar.make(mapView, "Navigation started", Snackbar.LENGTH_LONG).multiline().show()
                buttonStopNavigation.isEnabled = true
            },
            onStopped = {
                textView.visibility = View.GONE
                Snackbar.make(mapView, "Navigation stopped", Snackbar.LENGTH_LONG).multiline().show()
                buttonStopNavigation.isEnabled = false
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
        buttonStartNavigation.isEnabled = selectedPOI != null && !buttonStopNavigation.isEnabled
        buttonStartNavigationFromSimulatedUserPosition.isEnabled =
            selectedPOI != null && simulatedUserPosition != null && !buttonStopNavigation.isEnabled
        buttonRemoveSimulatedUserPosition.isEnabled = simulatedUserPosition != null
        binding.poisButton.isEnabled = (mapView.locationManager.lastCoordinate ?: simulatedUserPosition) != null
    }

    private fun disableStartButtons() {
        buttonStartNavigation.isEnabled = false
        buttonStartNavigationFromSimulatedUserPosition.isEnabled = false
    }

    private fun removeSimulatedUserPosition() {
        circleManager.delete(simulatedUserPosition!!)
        simulatedUserPosition = null
        updateUI()
    }

    private fun getLevelFromAnnotation(annotation: Circle): List<Float> {
        return annotation.data!!.asJsonArray.map { it.asFloat }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
        _circleManager = null
    }
}