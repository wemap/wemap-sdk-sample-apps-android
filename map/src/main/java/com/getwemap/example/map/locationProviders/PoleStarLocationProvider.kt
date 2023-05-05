package com.getwemap.example.map.locationProviders

import android.app.PendingIntent
import android.content.Context
import android.location.Location
import android.os.Looper
import com.getwemap.sdk.core.model.entities.Coordinate
import com.getwemap.sdk.map.locationProviders.IndoorLocationProvider
import com.mapbox.mapboxsdk.location.engine.LocationEngine
import com.mapbox.mapboxsdk.location.engine.LocationEngineCallback
import com.mapbox.mapboxsdk.location.engine.LocationEngineRequest
import com.mapbox.mapboxsdk.location.engine.LocationEngineResult
import com.polestar.naosdk.api.external.NAOERRORCODE
import com.polestar.naosdk.api.external.NAOLocationHandle
import com.polestar.naosdk.api.external.NAOLocationListener
import com.polestar.naosdk.api.external.NAOSensorsListener
import com.polestar.naosdk.api.external.NAOSyncListener
import com.polestar.naosdk.api.external.TNAOFIXSTATUS
import com.polestar.naosdk.managers.NaoServiceManager

class PolestarIndoorLocationProviderServiceManager : NaoServiceManager()

class PolestarIndoorLocationProvider(context: Context, polestarApiKey: String)
    : IndoorLocationProvider(), NAOLocationListener, NAOSensorsListener, NAOSyncListener, LocationEngine {

    var isStarted = false
        private set

    private var dataSynchronized = false
    private var shouldStart = false
    private val context: Context
    private val naoLocationHandle: NAOLocationHandle
    private var floorByAltitudeMap: Map<Double, Double>? = null

    init {
        this.context = context
        naoLocationHandle = NAOLocationHandle(this.context, PolestarIndoorLocationProviderServiceManager::class.java, polestarApiKey, this, this)
        naoLocationHandle.synchronizeData(this)

    }

    fun setFloorByAltitudeMap(floorByAltitudeMap: Map<Double, Double>?) {
        this.floorByAltitudeMap = floorByAltitudeMap
    }

    override fun supportsFloor(): Boolean {
        return true
    }

    override fun start() {
        if (!isStarted) {
            if (dataSynchronized) {
                naoLocationHandle.start()
                isStarted = true
                shouldStart = false
                this.dispatchOnProviderStarted()
            } else {
                shouldStart = true
            }
        }
    }

    override fun stop() {
        if (isStarted) {
            naoLocationHandle.stop()
            isStarted = false
            this.dispatchOnProviderStopped()
        }
    }

    /*
    NAOLocationListener
     */
    override fun onLocationChanged(location: Location) {

        println("onLocationChanged: $location")
        val standardLocation = Location(name)
        standardLocation.latitude = location.latitude
        standardLocation.longitude = location.longitude
        standardLocation.time = System.currentTimeMillis()
        _callback?.onSuccess(LocationEngineResult.create(standardLocation))

        val altitude = location.altitude
        val indoorLocation: Coordinate = if (floorByAltitudeMap == null) {
            Coordinate(standardLocation, (altitude / 5).toFloat())
        } else {
            val floor = floorByAltitudeMap!![altitude]
            if (floor == null) {
                Coordinate(standardLocation, (altitude / 5).toFloat())
            } else {
                Coordinate(standardLocation, floor.toFloat())
            }
        }
        dispatchIndoorLocationChange(indoorLocation)
    }

    override fun onLocationStatusChanged(tnaofixstatus: TNAOFIXSTATUS?) {}
    override fun onEnterSite(s: String?) {}
    override fun onExitSite(s: String?) {}

    /*
    NAOErrorListener
     */
    override fun onError(naoerrorcode: NAOERRORCODE?, s: String?) {
        this.dispatchOnProviderError(Error(s))
    }

    /*
    NAOSensorsListener
     */
    override fun requiresCompassCalibration() {}
    override fun requiresWifiOn() {}
    override fun requiresBLEOn() {}
    override fun requiresLocationOn() {}

    /*
    NAOSyncListener
     */
    override fun onSynchronizationSuccess() {
        dataSynchronized = true
        if (shouldStart) {
            start()
        }
    }

    override fun onSynchronizationFailure(naoerrorcode: NAOERRORCODE?, s: String?) {
        this.dispatchOnProviderError(Error(s))
    }

    /*
    LocationEngine
     */

    private var _callback: LocationEngineCallback<LocationEngineResult>? = null

    override fun getLastLocation(callback: LocationEngineCallback<LocationEngineResult>) {
        _callback = callback
        callback.onSuccess(LocationEngineResult.create(lastLocation?.location))
    }

    override fun requestLocationUpdates(request: LocationEngineRequest, callback: LocationEngineCallback<LocationEngineResult>, looper: Looper?) {
        start()
        _callback = callback
        callback.onSuccess(LocationEngineResult.create(lastLocation?.location))
    }

    override fun requestLocationUpdates(request: LocationEngineRequest, pendingIntent: PendingIntent?) {
        TODO("Not yet implemented")
    }

    override fun removeLocationUpdates(callback: LocationEngineCallback<LocationEngineResult>) {
        stop()
        _callback = null
        callback.onSuccess(LocationEngineResult.create(lastLocation?.location))
    }

    override fun removeLocationUpdates(pendingIntent: PendingIntent?) {
        TODO("Not yet implemented")
    }
}