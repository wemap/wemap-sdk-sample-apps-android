package com.getwemap.example.positioning.ar.compose

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.getwemap.example.positioning.ar.LocationSourceType
import com.getwemap.example.positioning.ar.Config
import com.getwemap.sdk.core.CoreSession
import com.getwemap.sdk.core.extensions.toLocation
import com.getwemap.sdk.core.location.simulation.SimulationOptions
import com.getwemap.sdk.core.location.simulation.SimulatorLocationSource
import com.getwemap.sdk.core.model.entities.Coordinate
import com.getwemap.sdk.geoar.GeoARView
import com.getwemap.sdk.geoar.compose.WemapGeoAR
import com.getwemap.sdk.positioning.androidfusedadaptive.AndroidFusedAdaptiveLocationSource
import com.getwemap.sdk.positioning.fusedgms.GmsFusedLocationSource
import com.getwemap.sdk.positioning.gps.GpsLocationSource
import kotlinx.coroutines.flow.emptyFlow

/**
 * The AR scene in Compose — the smallest screen that puts a Wemap AR view on screen with `:geo-ar-compose`.
 *
 * It is the AR counterpart of `:examples:map`'s `ComposeMapScreen`, and the same rule holds: everything the SDK
 * offers comes off the [GeoARView] handed to `onLoaded` and is read as ordinary flows. There is no parallel
 * Compose API, which is why one composable and a location source is the whole screen.
 *
 * **There is no camera state here, unlike the map.** The AR camera follows the device and the user's position,
 * so it is not app state to drive — `WemapGeoAR` deliberately has no `cameraPositionState`.
 *
 * **This sample resolves permissions before `WemapGeoAR` enters composition.** The view does not start an ARCore
 * session on its own. Camera ownership and additional permissions follow the selected location source: a
 * camera-capable source can provide capture and projection, while GPS, fused and simulator sources make the view
 * open its own camera background.
 *
 * @param session the shared session. Owned by the host `ViewModel`, so it survives a configuration change.
 * @param locationSource which location source to attach, as chosen on the initial screen.
 */
@Composable
fun ComposeARScreen(
    session: CoreSession,
    locationSource: LocationSourceType,
    modifier: Modifier = Modifier
) {

    val context = LocalContext.current
    var permissionsGranted by remember { mutableStateOf(false) }
    var permissionsDenied by remember { mutableStateOf(false) }
    var arView by remember { mutableStateOf<GeoARView?>(null) }
    var loadError by remember { mutableStateOf<Throwable?>(null) }
    val arViewConfig = remember { Config.makeGeoARViewConfig(context) }

    val permissionLauncher = rememberLauncherForActivityResult(RequestMultiplePermissions()) { results ->
        if (results.values.all { it }) {
            permissionsGranted = true
        } else {
            permissionsDenied = true
        }
    }

    LaunchedEffect(Unit) {
        permissionLauncher.launch(locationSource.requiredPermissions.toTypedArray())
    }

    Box(modifier = modifier.fillMaxSize()) {

        if (permissionsGranted) {
            WemapGeoAR(
                session = session,
                modifier = Modifier.fillMaxSize(),
                config = arViewConfig,
                onLoaded = { arView = it },
                onFailed = { loadError = it }
            )
        }

        StatusReadout(
            view = arView,
            message = when {
                permissionsDenied ->
                    "In order to make sample app work properly you have to accept required permission"
                loadError != null -> "Failed to load GeoARView with error - $loadError"
                !permissionsGranted -> "Waiting for camera and location permissions…"
                arView == null -> "Starting the AR session…"
                else -> null
            },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .safeDrawingPadding()
                .padding(16.dp)
        )
    }

    LaunchedEffect(arView) {
        val view = arView ?: return@LaunchedEffect
        view.locationManager.locationSource = buildLocationSource(session, locationSource, context)
    }
}

/**
 * The screen's one piece of chrome: whatever the user is waiting for, or where they are once nothing is.
 *
 * AR draws no error of its own and the scene is a camera feed, so a failure that is only logged leaves the
 * person holding the phone looking at a live camera with no AR in it and no way to tell why.
 */
@Composable
private fun StatusReadout(
    view: GeoARView?,
    message: String?,
    modifier: Modifier = Modifier
) {

    val coordinate: Coordinate? by (view?.locationManager?.coordinates ?: emptyFlow())
        .collectAsStateWithLifecycle(null)

    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = message
                    ?: coordinate?.let { "You %.5f, %.5f".format(it.latitude, it.longitude) }
                    ?: "Searching for your location…",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

/**
 * Builds the source the samples list picked.
 *
 * `LocationSourceType` is this app's own and does **not** match `:examples:map`'s. Kept beside this screen
 * rather than shared with `GenericLSFragment`: that one is a `Fragment` built around `PermissionHelper` and a
 * View binding, so there is no seam to share short of reshaping it. The permissions are the other half and
 * live on the enum, so the two cannot drift.
 */
private fun buildLocationSource(
    session: CoreSession,
    locationSource: LocationSourceType,
    context: android.content.Context
) =
    when (locationSource) {
        LocationSourceType.SIMULATOR ->
            SimulatorLocationSource(session, SimulationOptions(altitude = 1.6)).apply {
                setCoordinates(listOf(Coordinate(session.mapCenter.toLocation())), sample = false)
            }
        LocationSourceType.ANDROID_FUSED_ADAPTIVE -> AndroidFusedAdaptiveLocationSource(context, session)
        LocationSourceType.FUSED_GMS -> GmsFusedLocationSource(context, session)
        LocationSourceType.GPS -> GpsLocationSource(context, session)
        // VPS has a screen of its own (`VpsLSFragment`) and never reaches here.
        LocationSourceType.VPS -> throw IllegalArgumentException("$locationSource has its own screen")
    }
