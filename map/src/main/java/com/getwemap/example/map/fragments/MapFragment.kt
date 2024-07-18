package com.getwemap.example.map.fragments

import android.Manifest.permission.ACCESS_FINE_LOCATION
import android.Manifest.permission.BLUETOOTH_SCAN
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.getwemap.example.map.Constants
import com.getwemap.example.map.GareDeLyonSimulatorsLocationSource
import com.getwemap.example.map.multiline
import com.getwemap.sdk.core.location.LocationSource
import com.getwemap.sdk.core.model.entities.Level
import com.getwemap.sdk.map.WemapMapView
import com.getwemap.sdk.map.buildings.Building
import com.getwemap.sdk.map.buildings.OnActiveLevelChangeListener
import com.getwemap.sdk.map.buildings.OnBuildingFocusChangeListener
import com.getwemap.sdk.map.location.simulation.SimulatorLocationSource
import com.getwemap.sdk.map.model.entities.MapData
import com.getwemap.sdk.positioning.fusedgms.GmsFusedLocationSource
import com.getwemap.sdk.positioning.polestar.PolestarLocationSource
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.snackbar.Snackbar
import io.reactivex.rxjava3.disposables.CompositeDisposable
import kotlinx.serialization.json.Json
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.location.modes.RenderMode

abstract class MapFragment : Fragment() {

    protected abstract val mapView: WemapMapView
    protected abstract val levelToggle: MaterialButtonToggleGroup

    protected val disposeBag = CompositeDisposable()

    // also you can use simulator to generate locations along the itinerary
    protected val simulator by lazy { SimulatorLocationSource() }

    protected var locationSourceId: Int = -1

    protected val pointOfInterestManager get() = mapView.pointOfInterestManager
    protected val focusedBuilding get() = buildingManager.focusedBuilding

    private val buildingManager get() = mapView.buildingManager

    private val activityResultLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
        if (isGranted) {
            checkPermissionsAndSetupLocationSource()
        } else {
            Snackbar.make(
                mapView,
                "In order to make sample app work properly you have to accept required permission",
                Snackbar.LENGTH_LONG)
                .multiline()
                .show()
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

        mapView.getWemapMapAsync { _, _, _ ->

            checkPermissionsAndSetupLocationSource()

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
                checkedButton.setTextColor(Color.BLACK)
                return@addOnButtonCheckedListener
            }
            val focused = focusedBuilding ?: return@addOnButtonCheckedListener
            checkedButton.setBackgroundColor(Color.BLUE)
            checkedButton.setTextColor(Color.WHITE)
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
        levelToggle.check(building.activeLevelIndex)
    }

    private fun checkPermissionsAndSetupLocationSource() {
        val permissionsAccepted = when (locationSourceId) {
            1, 3 -> checkGPSPermission() && checkBluetoothPermission()
            0, 2, 4, 5 -> checkGPSPermission()
            else -> throw Exception("Location source id should be passed in Bundle")
        }
        if (!permissionsAccepted) return

        setupLocationSource()
    }

    private fun checkGPSPermission(): Boolean {
        return if (ContextCompat.checkSelfPermission(requireContext(), ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            activityResultLauncher.launch(ACCESS_FINE_LOCATION)
            false
        } else {
            true
        }
    }

    private fun checkBluetoothPermission(): Boolean {
        return if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            ContextCompat.checkSelfPermission(requireContext(), BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
            activityResultLauncher.launch(BLUETOOTH_SCAN)
            false
        } else {
            true
        }
    }

    @SuppressLint("MissingPermission")
    private fun setupLocationSource() {
        val locationSource: LocationSource? = when (locationSourceId) {
            0 -> simulator
            1 -> PolestarLocationSource(requireContext(), Constants.polestarApiKey)
            2 -> null
            3 -> PolestarLocationSource(requireContext(), "emulator")
            4 -> GmsFusedLocationSource(requireContext())
            5 -> GareDeLyonSimulatorsLocationSource.FromIndoorToOutdoor
            else -> throw Exception("Location source id should be passed in Bundle")
        }
        mapView.locationManager.apply {
            source = locationSource
            isEnabled = true
        }

        mapView.map.locationComponent.apply {
            cameraMode = CameraMode.TRACKING_COMPASS
            renderMode = RenderMode.COMPASS
        }

        locationManagerReady()
        // this way you can specify user location indicator appearance
//        mapView.locationManager.userLocationViewStyle = UserLocationViewStyle(
//            foregroundDrawable = R.drawable.custom_user_puck_icon,
//            backgroundTintColor = Color.TRANSPARENT,
//            bearingDrawable = R.drawable.custom_user_arrow,
//            outOfActiveLevelStyle = UserLocationViewStyle.OutOfActiveLevelStyle(
//                foregroundDrawable = R.drawable.ic_layers_clear
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
        disposeBag.dispose()
        mapView.onDestroy()
    }
}