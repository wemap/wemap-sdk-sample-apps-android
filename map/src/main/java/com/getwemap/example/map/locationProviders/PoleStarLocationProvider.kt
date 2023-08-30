package com.getwemap.example.map.locationProviders

import android.content.Context
import android.location.Location
import android.os.Build
import com.getwemap.sdk.core.model.entities.Coordinate
import com.getwemap.sdk.map.locationProviders.IndoorLocationProvider
import com.getwemap.sdk.map.locationProviders.IndoorLocationProviderListener
import com.polestar.naosdk.api.external.NAOERRORCODE
import com.polestar.naosdk.api.external.NAOLocationHandle
import com.polestar.naosdk.api.external.NAOLocationListener
import com.polestar.naosdk.api.external.NAOSensorsListener
import com.polestar.naosdk.api.external.NAOSyncListener
import com.polestar.naosdk.api.external.TNAOFIXSTATUS
import com.polestar.naosdk.managers.NaoServiceManager

class PolestarIndoorLocationProviderServiceManager : NaoServiceManager()

class PolestarIndoorLocationProvider(context: Context, polestarApiKey: String)
    : IndoorLocationProvider, NAOLocationListener, NAOSensorsListener, NAOSyncListener {

    override var listener: IndoorLocationProviderListener? = null

    var isStarted = false
        private set

    private var dataSynchronized = false
    private var shouldStart = false
    private val context: Context
    private val naoLocationHandle: NAOLocationHandle

    init {
        this.context = context
        naoLocationHandle = NAOLocationHandle(this.context, PolestarIndoorLocationProviderServiceManager::class.java, polestarApiKey, this, this)
        naoLocationHandle.synchronizeData(this)
    }

    override fun start() {
        if (!isStarted) {
            if (dataSynchronized) {
                naoLocationHandle.start()
                isStarted = true
                shouldStart = false
            } else {
                shouldStart = true
            }
        }
    }

    override fun stop() {
        if (isStarted) {
            naoLocationHandle.stop()
            isStarted = false
        }
    }

    /*
    NAOLocationListener
     */
    override fun onLocationChanged(location: Location) {

        println("onLocationChanged: $location")
        val coordinate = polestarLocationToCoordinate(location)
        listener?.onLocationChanged(coordinate)
    }

    private fun polestarLocationToCoordinate(location: Location) : Coordinate {
        val standardLocation = Location("PoleStar")
        standardLocation.latitude = location.latitude
        standardLocation.longitude = location.longitude
        standardLocation.time = System.currentTimeMillis()

        val verticalAccuracy = location.extras?.getFloat("vertical_accuracy")
        val isOutdoor = !location.hasAltitude() || location.hasAltitude() && verticalAccuracy == -500f

        val altitude = location.altitude
        return Coordinate(standardLocation, if (isOutdoor) emptyList() else listOf((altitude / 5).toFloat()))
    }

    override fun onLocationStatusChanged(tnaofixstatus: TNAOFIXSTATUS?) {}
    override fun onEnterSite(s: String?) {}
    override fun onExitSite(s: String?) {}

    /*
    NAOErrorListener
     */
    override fun onError(naoerrorcode: NAOERRORCODE?, s: String?) {
        listener?.onError(Error(s))
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
        listener?.onError(Error(s))
    }
}