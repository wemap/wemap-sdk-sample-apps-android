package com.getwemap.example.positioning.ar.fragments

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.getwemap.sdk.geoar.GeoARView
import com.getwemap.sdk.geoar.managers.ARLocationManager
import com.getwemap.sdk.geoar.managers.IARNavigationManager
import com.getwemap.sdk.core.model.entities.MapData
import io.reactivex.rxjava3.disposables.CompositeDisposable
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

abstract class ARFragment : Fragment() {

    protected val disposeBag = CompositeDisposable()

    protected abstract val geoARView: GeoARView
    protected lateinit var mapData: MapData

    protected val navigationManager: IARNavigationManager get() = geoARView.navigationManager
    protected val locationManager: ARLocationManager get() = geoARView.locationManager

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val mapDataString = requireArguments().getString("mapData")!!
        mapData = Json.decodeFromString(mapDataString)

        viewLifecycleOwner.lifecycleScope.launch {
            geoARView.getARViewAsync { arView, mapData ->
                onARViewLoaded(arView, mapData)
            }
        }

        geoARView.mapData = mapData
    }

    abstract fun onARViewLoaded(arView: GeoARView, mapData: MapData)

    override fun onDestroyView() {
        super.onDestroyView()
        disposeBag.clear()
    }

    override fun onDestroy() {
        super.onDestroy()
        disposeBag.dispose()
    }
}