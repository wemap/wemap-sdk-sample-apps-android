package com.getwemap.example.map.compose

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import com.getwemap.example.map.LocationSourceType
import com.getwemap.example.map.Config
import com.getwemap.example.map.insetCompassBelowTransparentAppBar
import com.getwemap.sdk.core.model.entities.Coordinate
import com.getwemap.sdk.map.MapSession
import com.getwemap.sdk.map.WemapMapView
import com.getwemap.sdk.map.compose.MapCameraPositionState
import com.getwemap.sdk.map.compose.WemapMap
import com.getwemap.sdk.map.compose.rememberMapCameraPositionState
import kotlinx.coroutines.flow.emptyFlow

/**
 * The map in Compose and nothing else — the smallest screen that puts a Wemap map on screen with `:map-compose`.
 *
 * It exercises both of `:map-compose`'s public entry points and nothing more — `WemapMap` and
 * [rememberMapCameraPositionState] — and shows the pattern that makes the rest of the SDK reachable: everything
 * comes off the [WemapMapView] handed to `onLoaded`, read as ordinary flows. There is no parallel Compose API to
 * learn, which is why this screen can stay this short.
 *
 * The readout is deliberately built from plain Material 3 rather than a widget: it is there to prove the map's
 * state is reachable, not to demonstrate a control.
 *
 * @param session the shared session. Owned by the host `ViewModel`, so it survives a configuration change.
 * @param locationSource which location source to attach, as chosen on the initial screen.
 */
@Composable
fun ComposeMapScreen(
    session: MapSession,
    locationSource: LocationSourceType,
    modifier: Modifier = Modifier
) {

    val context = LocalContext.current
    val cameraState = rememberMapCameraPositionState()
    var mapView by remember { mutableStateOf<WemapMapView?>(null) }
    var loadError by remember { mutableStateOf<Throwable?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val mapViewConfig = remember { Config.makeMapViewConfig(context) }

    // Permissions are a first-class Compose API, so the `PermissionHelper` the View screens need is gone here.
    val permissionLauncher = rememberLauncherForActivityResult(RequestMultiplePermissions()) { results ->
        if (results.values.all { it }) {
            mapView?.attachLocationSource(session, locationSource, context)
        } else {
            loadError = IllegalStateException(
                "In order to make sample app work properly you have to accept required permission"
            )
        }
    }

    Box(modifier = modifier.fillMaxSize()) {

        WemapMap(
            session = session,
            modifier = Modifier.fillMaxSize(),
            config = mapViewConfig,
            cameraPositionState = cameraState,
            onLoaded = { view ->
                // The compass lives inside the map, so the transparent app bar it would otherwise sit under is
                // cleared on its own margins rather than by a modifier.
                view.insetCompassBelowTransparentAppBar()
                mapView = view
            },
            onFailed = { loadError = it }
        )

        MapReadout(
            view = mapView,
            cameraState = cameraState,
            modifier = Modifier
                .align(Alignment.TopCenter)
                // The app bar floats over the map and is reported as part of this content's top inset, so one
                // call on the overlay clears both it and the status bar — the map itself is meant to be up
                // there and is deliberately not padded (see `insetOverlayBelowTransparentAppBar`).
                .safeDrawingPadding()
                .padding(16.dp)
        )

        SnackbarHost(snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
    }

    LaunchedEffect(mapView) {
        val view = mapView ?: return@LaunchedEffect
        val required = locationSource.requiredPermissions
        if (required.isEmpty()) {
            view.attachLocationSource(session, locationSource, context)
        } else {
            permissionLauncher.launch(required.toTypedArray())
        }
    }

    LaunchedEffect(loadError) {
        loadError?.let { snackbarHostState.showSnackbar("Failed to load MapView with error - $it") }
    }
}

/**
 * Shows where the camera is and where the user is, as plain text.
 *
 * The two values come from opposite directions on purpose, because that is the whole lesson of this screen: the
 * camera is Compose state the caller owns ([MapCameraPositionState], which survives recreation), while the user
 * coordinate is a `Flow` read off the loaded view like every other manager on the SDK.
 *
 * `emptyFlow()` before the view loads rather than a nullable collect — a `null` view is the normal first frame,
 * not a state worth branching the layout on.
 */
@Composable
private fun MapReadout(
    view: WemapMapView?,
    cameraState: MapCameraPositionState,
    modifier: Modifier = Modifier
) {

    val coordinate: Coordinate? by (view?.locationManager?.coordinates ?: emptyFlow())
        .collectAsStateWithLifecycle(null)

    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Both are null before the map has reported anything, which is the normal first frame rather than
            // an error — so each line says what it is waiting for instead of the screen branching on it.
            val camera = cameraState.position
            Text(
                text = camera?.let {
                    "Camera %.5f, %.5f · zoom %.1f".format(it.center.latitude, it.center.longitude, it.zoom)
                } ?: "Waiting for the map…",
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = coordinate
                    ?.let { "You %.5f, %.5f".format(it.latitude, it.longitude) }
                    ?: "Waiting for a position…",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}
