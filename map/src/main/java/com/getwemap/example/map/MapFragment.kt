package com.getwemap.example.map

import android.Manifest.permission
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Location
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.getwemap.example.map.databinding.FragmentMapBinding
import com.getwemap.example.map.locationProviders.PolestarIndoorLocationProvider
import com.getwemap.sdk.core.model.entities.Coordinate
import com.getwemap.sdk.core.model.entities.Level
import com.getwemap.sdk.map.OnMapViewClickListener
import com.getwemap.sdk.map.buildings.Building
import com.getwemap.sdk.map.buildings.OnActiveLevelChangeListener
import com.getwemap.sdk.map.buildings.OnBuildingFocusChangeListener
import com.getwemap.sdk.map.extensions.latLng
import com.getwemap.sdk.map.itineraries.ItineraryOptions
import com.getwemap.sdk.map.model.entities.MapData
import com.getwemap.sdk.map.navigation.NavigationInfo
import com.getwemap.sdk.map.navigation.NavigationOptions
import com.getwemap.sdk.map.navigation.OnNavigationInfoChangedListener
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import com.mapbox.geojson.Feature
import com.mapbox.mapboxsdk.Mapbox
import com.mapbox.mapboxsdk.geometry.LatLngBounds
import com.mapbox.mapboxsdk.location.modes.CameraMode
import com.mapbox.mapboxsdk.maps.Style
import com.mapbox.mapboxsdk.plugins.annotation.SymbolManager
import com.mapbox.mapboxsdk.plugins.annotation.SymbolOptions
import com.mapbox.mapboxsdk.utils.BitmapUtils
import io.reactivex.rxjava3.disposables.CompositeDisposable

class MapFragment : Fragment() {

    private val disposeBag = CompositeDisposable()
    private val activityResultLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
        if (isGranted) {
            setupLocationProvider()
        }
    }

    private lateinit var binding: FragmentMapBinding
    private val mapView get() = binding.mapView
    private val levelToggle get() = binding.levelToggle
    private val textView get() = binding.textView

    // also you can use simulator to generate locations along the itinerary
//    private val simulator = IndoorLocationProviderSimulator(SimulationOptions(true))

    private val maxBounds = LatLngBounds
        .from(48.84811619854466, 2.377353558713054,
            48.84045277048898, 2.371600716985739)

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        Mapbox.getInstance(requireContext())
        binding = FragmentMapBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val args = requireArguments()
        var maxBounds: LatLngBounds? = null
        if (args.containsKey("hasMaxBounds")) {
            maxBounds = LatLngBounds.from(
                args.getDouble("maxLatitudeNorth"),
                args.getDouble("maxLongitudeEast"),
                args.getDouble("maxLatitudeSouth"),
                args.getDouble("maxLongitudeWest")
            )
        }
        val data = MapData(
            args.getInt("id"),
            args.getString("styleUrl")!!,
            LatLngBounds.from(
                args.getDouble("latitudeNorth"),
                args.getDouble("longitudeEast"),
                args.getDouble("latitudeSouth"),
                args.getDouble("longitudeWest")
            ),
            maxBounds
        )
        mapView.mapData = data
        // camera bounds can be specified even if they don't exist in MapData
//        mapView.cameraBounds = maxBounds
        mapView.onCreate(savedInstanceState)

        mapView.onMapViewClickListener = object : OnMapViewClickListener {
            override fun onFeatureClick(feature: Feature) {
                selectFeatureByPOI(feature)
//                selectFeatureByWemapID(feature)
            }
        }

        mapView.getMapAsync {

            it.uiSettings.apply {
                isLogoEnabled = false
                isAttributionEnabled = false
            }

            it.getStyle {
                startLocationService()
                createItinerary()
            }

            mapView.buildingManager.addOnBuildingFocusChangeListener(object : OnBuildingFocusChangeListener {
                override fun onBuildingFocusChange(building: Building?) {
                    populateLevels(building)
                }
            })

            mapView.buildingManager.addOnActiveLevelChangeListener(object : OnActiveLevelChangeListener {
                override fun onActiveLevelChange(building: Building, level: Level) {
                    levelToggle.check(building.activeLevelIndex)
                }
            })

            mapView.navigationManager.addOnNavigationInfoChangedListener(object : OnNavigationInfoChangedListener {
                override fun onNavigationInfoChanged(info: NavigationInfo) {
                    textView.text = info.shortDescription
                }
            })
        }

        levelToggle.addOnButtonCheckedListener { group, checkedId, isChecked ->
            val checkedButton = group.findViewById<MaterialButton>(checkedId)
            if (!isChecked) {
                checkedButton.setBackgroundColor(Color.WHITE)
                return@addOnButtonCheckedListener
            }
            val focused = mapView.buildingManager.focusedBuilding ?: return@addOnButtonCheckedListener
            checkedButton.setBackgroundColor(Color.BLUE)
            val desiredLevel = focused.levels.find { it.shortName == checkedButton.text }
            focused.activeLevel = desiredLevel!!
        }
    }

    private fun selectFeatureByPOI(feature: Feature) {
        val wemapId = feature.getNumberProperty("wemapId").toInt()

        val poi = mapView.pointOfInterestManager.getPOIs().firstOrNull { it.id == wemapId }
            ?: return println("failed to find cached POI for feature - $feature")

        mapView.pointOfInterestManager.centerToPOI(poi, true)

        addUserImageToStyle(mapView.map.style!!)

        val manager = SymbolManager(mapView, mapView.map, mapView.map.style!!)

        val symbolOptions = SymbolOptions()
            .withLatLng(poi.latLng)
            .withIconImage("user")
            .withIconSize(1.3F)

        val annotation = manager.create(symbolOptions)

        showSnackbar(wemapId) { manager.delete(annotation) }
    }

    private fun addUserImageToStyle(style: Style) {
        style.addImage(
            "user",
            BitmapUtils.getBitmapFromDrawable(resources.getDrawable(com.mapbox.mapboxsdk.R.drawable.maplibre_user_icon))!!,
            true
        )
    }

    private fun selectFeatureByWemapID(feature: Feature) {
        val wemapId = feature.getNumberProperty("wemapId").toInt()

        mapView.pointOfInterestManager.centerToPOI(wemapId, true)

        showSnackbar(wemapId)
    }

    private fun showSnackbar(wemapId: Int, onDismissed: (() -> Unit)? = null) {

        Snackbar
            .make(mapView, "Map feature clicked with id $wemapId", Snackbar.LENGTH_LONG)
            .addCallback(object : Snackbar.Callback() {
                override fun onDismissed(transientBottomBar: Snackbar?, event: Int) {
                    mapView.pointOfInterestManager.showPOI(wemapId)
                    onDismissed?.invoke()
                    super.onDismissed(transientBottomBar, event)
                }
            })
            .show()

        mapView.pointOfInterestManager.hidePOI(wemapId)
    }

    private fun createItinerary() {
        val from = Coordinate(Location("Hardcoded"), 0F)
        from.latitude = 48.844548658057306
        from.longitude = 2.3732023740778025

        val to = Coordinate(Location("Hardcoded"), 1F)
        to.latitude = 48.84442126724909
        to.longitude = 2.373656619804761

        // Navigation to a coordinate using user location as start or optional custom location
        val disposable = mapView.navigationManager
            .startNavigation(
                from,
                to,
                NavigationOptions(ItineraryOptions(10f, 1f, Color.RED), CameraMode.TRACKING_COMPASS)
            )
            .subscribe({
                // also you can use simulator to generate locations along the itinerary
//                simulator.setItinerary(it)
                println("Successfully started navigation itinerary: $it")
            }, {
                println("Failed to start navigation with error: $it")
            })

        // to stop navigation
//        val disp = mapView.navigationManager
//            .stopNavigation()
//            .subscribe({
//                println("Successfully stopped navigation itinerary: $it")
//            }, {
//                println("Failed to stop navigation with error: $it")
//            })
//        disposeBag.add(disp)

//        val disposable = mapView.itineraryManager
//            .getItineraries(from, to)
//            .flatMap { itineraries ->
//                println("Successfully received itineraries $itineraries")
//                if (itineraries.isEmpty()) {
//                    return@flatMap Single.error(NavigationError.noItinerariesFoundToDestination)
//                }
//                return@flatMap Single.just(itineraries.first())
//            }
//            .subscribe({
//                mapView.itineraryManager.addItinerary(
//                    it,
//                    ItineraryOptions(10f, 1f, Color.RED)
//                )
//            }, {
//                println("Failed to get itineraries with error: $it")
//            })

        disposeBag.add(disposable)
    }

    private fun populateLevels(building: Building?) {
        if (building == null) {
            levelToggle.visibility = View.INVISIBLE
            return
        }

        levelToggle.removeAllViews()
        levelToggle.visibility = View.VISIBLE

        val layout = LinearLayout.LayoutParams(
            150,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        )

        println("received levels count - ${building.levels.count()}")

        building.levels
            .map { level ->
                MaterialButton(requireContext()).apply {
                    id = building.levels.indexOf(level)
                    text = level.shortName
                    layoutParams = layout
                    setTextColor(Color.BLACK)
                    setBackgroundColor(Color.WHITE)
                }
            }
            .forEach {
                levelToggle.addView(it)
            }
        levelToggle.check(building.defaultLevelIndex)
    }

    private fun startLocationService() {
        if (ContextCompat.checkSelfPermission(requireContext(), permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            activityResultLauncher.launch(permission.ACCESS_FINE_LOCATION)
        } else {
            setupLocationProvider()
        }
    }

    @SuppressLint("MissingPermission")
    private fun setupLocationProvider() {
        val provider = PolestarIndoorLocationProvider(requireContext(), "emulator")
        mapView.indoorLocationProvider = provider
        // also you can use simulator to generate locations along the itinerary
//        mapView.indoorLocationProvider = simulator
    }

    override fun onStart() {
        super.onStart()
        mapView.onStart()
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
    }

    override fun onStop() {
        super.onStop()
        mapView.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        mapView.onSaveInstanceState(outState)
    }

    override fun onLowMemory() {
        super.onLowMemory()
        mapView.onLowMemory()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        mapView.onDestroy()
    }
}