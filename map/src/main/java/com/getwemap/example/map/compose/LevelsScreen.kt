package com.getwemap.example.map.compose

import android.graphics.Color
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.unit.dp
import com.getwemap.example.common.R
import com.getwemap.example.common.R as CommonR
import com.getwemap.example.map.LocationSourceType
import com.getwemap.example.map.Config
import com.getwemap.example.map.insetCompassBelowTransparentAppBar
import com.getwemap.sdk.core.model.entities.Levels
import com.getwemap.sdk.core.model.entities.PointOfInterest
import com.getwemap.sdk.map.MapSession
import com.getwemap.sdk.map.WemapMapView
import com.getwemap.sdk.map.compose.WemapMap
import com.getwemap.sdk.map.compose.rememberMapCameraPositionState
import com.getwemap.sdk.map.widgets.levels.LevelsSwitcher
import com.getwemap.sdk.map.widgets.levels.rememberLevelsSwitcherState
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Point
import org.maplibre.turf.TurfConstants
import org.maplibre.turf.TurfTransformation

/**
 * The levels sample, written in Compose.
 *
 * The View version of this screen is `LevelsFragment` + `fragment_levels.xml` + the `MapFragment` base class.
 * This file replaces all three, and the reason it can is that everything the SDK offers is reached through the
 * map view handed to `onLoaded` — there is no parallel Compose API to learn.
 *
 * The rail itself is not written here any more: it is `LevelsSwitcher` from `:map-widgets`, wired to the map by
 * `rememberLevelsSwitcherState`. What this screen used to carry — mirroring `activeLevelChanges` into state,
 * seeding it off the focused building, sorting the levels highest-first — is the SDK's job now, which is the
 * point of the widgets module.
 *
 * This screen also demonstrates state restoration: the camera uses [rememberMapCameraPositionState], while the
 * active level ID is mirrored into [rememberSaveable] and reapplied after a recreated map focuses its building.
 * Pan or zoom, select another level, and rotate the device to verify that both values survive.
 *
 * @param session the shared session. Owned by the host `ViewModel`, so it survives a configuration change.
 * @param locationSource which location source to attach, as chosen on the initial screen.
 */
@Composable
fun LevelsScreen(
    session: MapSession,
    locationSource: LocationSourceType,
    modifier: Modifier = Modifier
) {

    val context = LocalContext.current
    val cameraState = rememberMapCameraPositionState()
    var savedLevelId by rememberSaveable { mutableFloatStateOf(Float.NaN) }
    var levelIdToRestore by remember { mutableFloatStateOf(savedLevelId) }
    var mapView by remember { mutableStateOf<WemapMapView?>(null) }
    var loadError by remember { mutableStateOf<Throwable?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val mapViewConfig = remember { Config.makeMapViewConfig(context) }

    // Permissions are a first-class Compose API, so the `PermissionHelper` the Fragment needed is gone too.
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
                drawCircleAroundCenter(view.map, view.map.style!!)
                // The compass lives inside the map, so the transparent app bar it would otherwise sit under is
                // cleared on its own margins rather than by a modifier.
                view.insetCompassBelowTransparentAppBar()
                mapView = view
            },
            onFailed = {
                loadError = it
            }
        )

        mapView?.let { view ->
            LevelsSwitcher(
                state = rememberLevelsSwitcherState(view.buildingManager),
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(dimensionResource(CommonR.dimen.overlay_inset))
            )
            PoiPickers(
                view = view,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(8.dp)
            )
        }

        SnackbarHost(snackbarHostState, modifier = Modifier.align(Alignment.TopCenter))
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

    LaunchedEffect(mapView) {
        val buildingManager = mapView?.buildingManager
            ?: return@LaunchedEffect

        val building = buildingManager.focusedBuildings.filterNotNull().first()
        val restoredLevelId = levelIdToRestore
        if (!restoredLevelId.isNaN() && building.levels.any { it.id == restoredLevelId }) {
            building.activeLevelId = restoredLevelId
        }
        levelIdToRestore = Float.NaN
    }

    LaunchedEffect(mapView) {
        val buildingManager = mapView?.buildingManager
            ?: return@LaunchedEffect

        buildingManager.activeLevelChanges.collect { (_, level) -> savedLevelId = level.id }
    }

    LaunchedEffect(loadError) {
        loadError?.let { snackbarHostState.showSnackbar("Failed to load MapView with error - $it") }
    }
}

@Composable
private fun PoiPickers(view: WemapMapView, modifier: Modifier = Modifier) {

    val pois: Set<PointOfInterest> = remember(view) { view.pointOfInterestManager.getPois() }
    val uniqueLevels = remember(pois) { pois.mapNotNull { it.coordinate.levels.single }.toSet() }

    // Filled and tonal rather than two filled buttons, mirroring the iOS sample's `filled` + `tinted` pair:
    // the min-level pick is the one to try first, and two identical buttons say nothing about which.
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        Button(
            onClick = { uniqueLevels.minOrNull()?.let { view.selectRandomPoi(pois, it) } },
            enabled = uniqueLevels.isNotEmpty()
        ) {
            Text(stringResource(R.string.first_poi))
        }
        FilledTonalButton(
            onClick = { uniqueLevels.maxOrNull()?.let { view.selectRandomPoi(pois, it) } },
            enabled = uniqueLevels.isNotEmpty()
        ) {
            Text(stringResource(R.string.second_poi))
        }
    }
}

private fun WemapMapView.selectRandomPoi(pois: Set<PointOfInterest>, level: Float) {
    val poi = pois.filter { it.coordinate.levels.intersects(Levels.Single(level)) }.randomOrNull()
        ?: return println("Failed to get random POI at level $level")

    pointOfInterestManager.selectPoi(poi)
}

private fun drawCircleAroundCenter(map: MapLibreMap, style: Style) {
    style.getLayer(CIRCLE_LAYER_ID)?.let { style.removeLayer(it) }
    style.getSource(CIRCLE_SOURCE_ID)?.let { style.removeSource(it) }

    val center = map.cameraPosition.target ?: return
    val circle = TurfTransformation.circle(
        Point.fromLngLat(center.longitude, center.latitude),
        CIRCLE_RADIUS_METERS, CIRCLE_VERTICES, TurfConstants.UNIT_METERS
    )

    style.addSource(GeoJsonSource(CIRCLE_SOURCE_ID, circle))
    style.addLayer(
        LineLayer(CIRCLE_LAYER_ID, CIRCLE_SOURCE_ID).withProperties(
            PropertyFactory.lineColor(Color.RED),
            PropertyFactory.lineWidth(2f),
            PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND)
        )
    )
}

private const val CIRCLE_SOURCE_ID = "center-circle-source"
private const val CIRCLE_LAYER_ID = "center-circle-layer"
private const val CIRCLE_RADIUS_METERS = 100.0
private const val CIRCLE_VERTICES = 64
