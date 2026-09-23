package com.getwemap.example.map.compose

import android.content.Context
import com.getwemap.example.common.CommonAppConstants
import com.getwemap.example.map.LocationSourceType
import com.getwemap.sdk.core.location.LocationSource
import com.getwemap.sdk.core.location.simulation.SimulationOptions
import com.getwemap.sdk.core.location.simulation.SimulatorLocationSource
import com.getwemap.sdk.map.MapSession
import com.getwemap.sdk.map.WemapMapView
import com.getwemap.sdk.positioning.fusedgms.GmsFusedLocationSource

/**
 * Attaching the location source the user picked on the initial screen, shared by this app's Compose screens.
 *
 * Every Compose screen needs the same two things and they have to agree: the permissions asked for and the
 * source built must be the ones the same [LocationSourceType] means, or a screen asks for a permission it
 * never uses — or worse, attaches a source whose permission it never requested and reports no position with
 * no error. [LocationSourceType.requiredPermissions] is the other half and lives on the enum.
 *
 * The View screens reach the same behaviour through `MapFragment` + `PermissionHelper`; Compose screens are not
 * fragments, so they cannot inherit it.
 */

/**
 * Builds the picked source and enables the map's location manager.
 *
 * `null` is a real case, not a failure: [LocationSourceType.SYSTEM_DEFAULT] leaves the SDK on its own default
 * source, so the manager is still enabled and still reports a position.
 */
internal fun WemapMapView.attachLocationSource(
    session: MapSession,
    locationSource: LocationSourceType,
    context: Context
) {

    val rangeBound = CommonAppConstants.SIMULATOR_DEVIATION_RANGE
    val simulationOptions = if (rangeBound == .0) {
        SimulationOptions()
    } else {
        SimulationOptions(deviationRange = -rangeBound / 2..rangeBound / 2)
    }

    val source: LocationSource? = when (locationSource) {
        LocationSourceType.SIMULATOR -> SimulatorLocationSource(session, simulationOptions)
        LocationSourceType.SYSTEM_DEFAULT -> null
        LocationSourceType.FUSED_GMS -> GmsFusedLocationSource(context, session)
    }

    locationManager.apply {
        // Qualified: the parameter of the same name would otherwise win over the manager's property.
        this.locationSource = source
        isEnabled = true
    }
}
