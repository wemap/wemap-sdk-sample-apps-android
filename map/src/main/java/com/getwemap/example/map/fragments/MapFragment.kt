package com.getwemap.example.map.fragments

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.getwemap.example.map.Constants
import com.getwemap.sdk.core.LocationSource
import com.getwemap.sdk.core.model.entities.Level
import com.getwemap.sdk.locationsources.GmsFusedLocationSource
import com.getwemap.sdk.locationsources.PolestarLocationSource
import com.getwemap.sdk.map.WemapMapView
import com.getwemap.sdk.map.buildings.Building
import com.getwemap.sdk.map.buildings.OnActiveLevelChangeListener
import com.getwemap.sdk.map.buildings.OnBuildingFocusChangeListener
import com.getwemap.sdk.map.location.sources.LocationSourceSimulator
import com.getwemap.sdk.map.model.entities.MapData
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import kotlinx.serialization.json.Json

abstract class MapFragment : Fragment() {

    protected abstract val mapView: WemapMapView
    protected abstract val levelToggle: MaterialButtonToggleGroup

    // also you can use simulator to generate locations along the itinerary
    protected val simulator by lazy { LocationSourceSimulator() }

    protected var locationSourceId: Int = -1

    private val buildingManager get() = mapView.buildingManager

    private val activityResultLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
        if (isGranted) {
            setupLocationSource()
        }
    }

//    private val maxBounds = LatLngBounds
//        .from(48.84811619854466, 2.377353558713054,
//            48.84045277048898, 2.371600716985739)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val args = requireArguments()
        locationSourceId = args.getInt("locationSourceId")

        val mapDataString = args.getString("mapData")!!
        val mapData: MapData = Json.decodeFromString(mapDataString)

        mapView.mapData = mapData
        // camera bounds can be specified even if they don't exist in MapData
//        mapView.cameraBounds = maxBounds
        mapView.onCreate(savedInstanceState)

        mapView.getMapAsync { map ->

            map.getStyle {
                checkPermissionsAndSetupLocationSource()
            }

            buildingManager.addOnBuildingFocusChangeListener(object : OnBuildingFocusChangeListener {
                override fun onBuildingFocusChange(building: Building?) {
                    populateLevels(building)
                }
            })

            buildingManager.addOnActiveLevelChangeListener(object : OnActiveLevelChangeListener {
                override fun onActiveLevelChange(building: Building, level: Level) {
                    levelToggle.check(building.activeLevelIndex)
                }
            })
        }

        levelToggle.addOnButtonCheckedListener { group, checkedId, isChecked ->
            val checkedButton = group.findViewById<MaterialButton>(checkedId)
            if (!isChecked) {
                checkedButton.setBackgroundColor(Color.WHITE)
                return@addOnButtonCheckedListener
            }
            val focused = buildingManager.focusedBuilding ?: return@addOnButtonCheckedListener
            checkedButton.setBackgroundColor(Color.BLUE)
            val desiredLevel = focused.levels.find { it.shortName == checkedButton.text }
            focused.activeLevel = desiredLevel!!
        }
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

    private fun checkPermissionsAndSetupLocationSource() {
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            activityResultLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        } else {
            setupLocationSource()
        }
    }

    @SuppressLint("MissingPermission")
    private fun setupLocationSource() {
        val locationSource: LocationSource = when (locationSourceId) {
            0 -> PolestarLocationSource(requireContext(), "emulator")
            1 -> PolestarLocationSource(requireContext(), Constants.polestarKey)
            2 -> simulator
            3 -> GmsFusedLocationSource(requireContext())
            else -> throw Exception("Location source id should be passed in Bundle")
        }
        mapView.locationManager.apply {
            source = locationSource
            isEnabled = true
        }
        locationManagerReady()
        // this way you can specify user location indicator appearance
//        mapView.locationManager.userLocationViewStyle = UserLocationViewStyle(
//            Color.parseColor("#FFC0CB"), // pink
//            Color.BLACK,
//            Color.GREEN,
//            UserLocationViewStyle.OutOfActiveLevelStyle(
//                Color.WHITE,
//                Color.RED,
//                0.8F
//            )
//        )
    }

    open fun locationManagerReady() {
        // no-op
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