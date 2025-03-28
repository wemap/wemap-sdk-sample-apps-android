package com.getwemap.example.map.positioning.fragments

import android.Manifest.permission.ACCESS_FINE_LOCATION
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.getwemap.example.common.map.MapLevelsSwitcher
import com.getwemap.sdk.core.model.entities.MapData
import com.getwemap.sdk.map.WemapMapView
import com.google.android.material.snackbar.Snackbar
import io.reactivex.rxjava3.disposables.CompositeDisposable
import kotlinx.serialization.json.Json

abstract class BaseFragment : Fragment() {

    protected abstract val mapView: WemapMapView
    protected abstract val levelsSwitcher: MapLevelsSwitcher

    protected lateinit var mapData: MapData

    protected val focusedBuilding get() = buildingManager.focusedBuilding

    private val buildingManager get() = mapView.buildingManager

    protected val disposeBag = CompositeDisposable()

    protected val activityResultLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
        if (isGranted) {
            checkPermissionsAndSetupLocationSource()
        } else {
            val text = "In order to make sample app work properly you have to accept required permission"
            Snackbar.make(mapView, text, Snackbar.LENGTH_LONG).show()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val mapDataString = requireArguments().getString("mapData")!!
        mapData = Json.decodeFromString(mapDataString)
        mapView.mapData = mapData

        mapView.onCreate(savedInstanceState)
        levelsSwitcher.bind(mapView)
        mapView.getMapViewAsync { _, _, _, _ ->
            checkPermissionsAndSetupLocationSource()
        }
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
        disposeBag.clear()
        mapView.onDestroy()
    }

    override fun onDestroy() {
        super.onDestroy()
        disposeBag.dispose()
    }
}