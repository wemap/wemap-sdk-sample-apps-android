package com.getwemap.example.map.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.getwemap.example.map.Config
import com.getwemap.example.map.databinding.FragmentNavigationBinding
import com.getwemap.example.map.multiline
import com.getwemap.example.map.onDismissed
import com.getwemap.sdk.core.model.entities.Coordinate
import com.getwemap.sdk.map.navigation.NavigationManagerListener
import com.getwemap.sdk.map.poi.PointOfInterestManagerListener
import com.google.android.material.snackbar.Snackbar
import com.google.gson.JsonArray
import org.maplibre.android.MapLibre
import org.maplibre.android.plugins.annotation.Circle
import org.maplibre.android.plugins.annotation.CircleManager
import org.maplibre.android.plugins.annotation.CircleOptions

class NavigationFragment : MapFragment() {

    override val mapView get() = binding.mapView
    override val levelToggle get() = binding.levelToggle
    private val textView get() = binding.textView

    private val buttonStartNavigation get() = binding.startNavigation
    private val buttonStopNavigation get() = binding.stopNavigation
    private val buttonStartNavigationFromUserCreatedAnnotations get() = binding.startNavigationFromUserCreatedAnnotations
    private val buttonRemoveUserCreatedAnnotations get() = binding.removeUserCreatedAnnotations
    private val userLocationTextView get() = binding.userLocationTextView

    private val navigationManager get() = mapView.navigationManager

    private val userCreatedAnnotations: MutableList<Circle> = mutableListOf()

    private lateinit var binding: FragmentNavigationBinding
    private lateinit var circleManager: CircleManager

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        MapLibre.getInstance(requireContext())
        binding = FragmentNavigationBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        mapView.getWemapMapAsync { mapView, map, _ ->
            pointOfInterestManager.addPointOfInterestManagerListener(PointOfInterestManagerListener(
                onSelected = {
                    showSnackbar(it.id) {
                        pointOfInterestManager.unselectPOI(it.id)
                    }
                }
            ))

            circleManager = CircleManager(mapView, map, map.style!!)
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

        buttonStartNavigation.setOnClickListener {
            startNavigation()
        }

        buttonStopNavigation.setOnClickListener {
            stopNavigation()
        }

        buttonStartNavigationFromUserCreatedAnnotations.setOnClickListener {
            startNavigationFromUserCreatedAnnotations()
        }

        buttonRemoveUserCreatedAnnotations.setOnClickListener {
            removeUserCreatedAnnotations()
        }
    }

    override fun locationManagerReady() {
        super.locationManagerReady()
        val coordinateUpdate = mapView.locationManager.coordinateUpdated.subscribe {
            userLocationTextView.text = "$it"
        }
        disposeBag.add(coordinateUpdate)
    }

    private fun startNavigation() {
        startNavigation(null, getDestinationCoordinate())
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

    private fun startNavigationFromUserCreatedAnnotations() {

        val origin: Coordinate
        val destination: Coordinate
        if (locationSourceId != 1) { // not polestar emulator
            origin = getOriginCoordinate()
            destination = getDestinationCoordinate()
        } else {

            // Default path
//            origin = Coordinate(48.84487592, 2.37362684, -1F)
//            destination = Coordinate(48.84428454, 2.37390447, 0F)

            // Path at less than 3 meters from network
//            origin = Coordinate(48.84458308799957, 2.3731548097070134, 0F)
//            destination = Coordinate(48.84511200990592, 2.3738383127780676, 0F)

            // Path at less than 3 meters from network and route recalculation
//            origin = Coordinate(48.84458308799957, 2.3731548097070134, 0F)
//            destination = Coordinate(48.84511200990592, 2.3738383127780676, 0F)

            // Path from level -1 to 0 and route recalculation
            origin = Coordinate(48.84445563, 2.37319782, -1F)
            destination = Coordinate(48.84502948, 2.37451864, 0F)

            // Path indoor to outdoor
//            origin = Coordinate(48.84482873, 2.37378956, 0F)
//            destination = Coordinate(48.8455159, 2.37305333)
        }

        startNavigation(origin, destination)
    }

    private fun startNavigation(origin: Coordinate?, destination: Coordinate) {
        disableStartButtons()

        val navOptions = Config.globalNavigationOptions(requireContext())

        val disposable = navigationManager
            .startNavigation(origin, destination, options = navOptions/*, ItinerarySearchOptions(avoidStairs = true)*/)
            .subscribe({
                // also you can use simulator to generate locations along the itinerary
                simulator.setItinerary(it)
                buttonStopNavigation.isEnabled = true
            }, {
                Snackbar.make(mapView, "Failed to start navigation with error - $it", Snackbar.LENGTH_LONG)
                    .multiline().show()
                updateUI()
            })

        disposeBag.add(disposable)
    }

    private fun setupNavigationManagerListener() {
        navigationManager.addNavigationManagerListener(NavigationManagerListener(
            onInfoChanged = { info ->
                val nextStepInstructions = info.nextStep?.getNavigationInstructions(requireContext())?.instructions
                textView.text = info.shortDescription + "\nNext - $nextStepInstructions"
                textView.visibility = View.VISIBLE
            },
            onStarted = { itinerary ->
                textView.visibility = View.VISIBLE
                Snackbar.make(mapView, "Navigation started", Snackbar.LENGTH_LONG).multiline().show()
                buttonStopNavigation.isEnabled = true

                itinerary.legs.flatMap { it.steps }.forEach {
                    println(it.getNavigationInstructions(requireContext()))
                }
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

    private fun showSnackbar(id: Int, onDismissed: () -> Unit) {
        Snackbar
            .make(mapView, "POI clicked with id $id", Snackbar.LENGTH_LONG)
            .onDismissed(onDismissed)
            .show()
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
        super.onDestroyView()
        disposeBag.dispose()
    }
}