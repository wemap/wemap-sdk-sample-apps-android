package com.getwemap.example.map

import android.Manifest.permission
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Location
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
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
import com.getwemap.sdk.map.itineraries.ItineraryOptions
import com.getwemap.sdk.map.navigation.NavigationOptions
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar
import com.mapbox.geojson.Feature
import com.mapbox.mapboxsdk.Mapbox
import com.mapbox.mapboxsdk.geometry.LatLngBounds
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

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        Mapbox.getInstance(requireContext())
        binding = FragmentMapBinding.inflate(inflater, container, false)
        return binding.root
    }

    // TODO: it's due to Parcelable usage. Pass initial bounds in another way if it's blocking for you
    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val style = requireArguments().getString("styleUrl")
        val bounds = requireArguments().getParcelable("initialBounds", LatLngBounds::class.java)
        mapView.styleUrl = style
        mapView.initialBounds = bounds
        mapView.onCreate(savedInstanceState)

        mapView.onMapViewClickListener = object : OnMapViewClickListener {
            override fun onFeatureClick(feature: Feature) {
                val externalId = feature.getStringProperty("externalId")
                Snackbar.make(mapView, "Map feature clicked with id $externalId", Snackbar.LENGTH_LONG).show()
            }
        }

        mapView.getMapAsync {
            it.getStyle {
                startLocationService()
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

            createItinerary()
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

    private fun createItinerary() {
        val from = Coordinate(Location("Hardcoded"), 0F)
        from.location.latitude = 48.8445445
        from.location.longitude = 2.3732009

        val to = Coordinate(Location("Hardcoded"), 1F)
        to.location.latitude = 48.844423524854314
        to.location.longitude = 2.373660055406727

        val disposable = mapView.itineraryManager
            .getItineraries(from, to)
            .subscribe({ itineraries ->
                println("Successfully received itineraries $itineraries")
                if (itineraries.isEmpty()) {
                    return@subscribe
                }
                val itinerary = itineraries[0]
                mapView.itineraryManager.addItinerary(
                    itinerary,
                    ItineraryOptions(10f, 1f, Color.RED)
                )
                Handler().postDelayed({
                    mapView.itineraryManager.removeItinerary(itinerary)
                }, 10000)

            }, {
                println("Failed to get itineraries $it")
            })

        // Navigation to a coordinate using user location as start
//        val disposable = mapView.navigationManager
//            .startNavigation(
//                to,
//                NavigationOptions(ItineraryOptions(10f, 1f, Color.RED))
//            )
//            .subscribe()

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