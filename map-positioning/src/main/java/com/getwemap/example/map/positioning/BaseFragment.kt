package com.getwemap.example.map.positioning

import android.Manifest.permission.ACCESS_FINE_LOCATION
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.LinearLayout
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.getwemap.sdk.core.model.entities.Level
import com.getwemap.sdk.map.WemapMapView
import com.getwemap.sdk.map.buildings.Building
import com.getwemap.sdk.map.buildings.OnActiveLevelChangeListener
import com.getwemap.sdk.map.buildings.OnBuildingFocusChangeListener
import com.getwemap.sdk.map.model.entities.MapData
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.snackbar.Snackbar
import io.reactivex.rxjava3.disposables.CompositeDisposable
import kotlinx.serialization.json.Json

abstract class BaseFragment : Fragment() {

    protected abstract val mapView: WemapMapView
    protected abstract val levelToggle: MaterialButtonToggleGroup

    protected val focusedBuilding get() = buildingManager.focusedBuilding

    private val buildingManager get() = mapView.buildingManager

    protected val disposeBag = CompositeDisposable()

    protected val activityResultLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
        if (isGranted) {
            checkPermissionsAndSetupLocationSource()
        } else {
            Snackbar.make(
                mapView,
                "In order to make sample app work properly you have to accept required permission",
                Snackbar.LENGTH_LONG)
                .show()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val mapDataString = requireArguments().getString("mapData")!!
        val mapData: MapData = Json.decodeFromString(mapDataString)
        mapView.mapData = mapData

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

    protected abstract fun checkPermissionsAndSetupLocationSource()

    protected fun checkGPSPermission(): Boolean {
        return if (ContextCompat.checkSelfPermission(requireContext(), ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            activityResultLauncher.launch(ACCESS_FINE_LOCATION)
            false
        } else {
            true
        }
    }

    @SuppressLint("MissingPermission")
    protected abstract fun setupLocationSource()

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