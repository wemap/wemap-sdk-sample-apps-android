package com.getwemap.example.map.compose

import android.view.Gravity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.unit.dp
import com.getwemap.example.common.R as CommonR
import com.getwemap.example.map.Config
import com.getwemap.sdk.core.model.entities.Itinerary
import com.getwemap.sdk.map.MapSession
import com.getwemap.sdk.map.WemapMapView
import com.getwemap.sdk.map.compose.WemapMap
import com.getwemap.sdk.map.widgets.itinerary.ItineraryForm
import com.getwemap.sdk.map.widgets.itinerary.ItineraryFormFailure
import com.getwemap.sdk.map.widgets.levels.LevelsSwitcher
import com.getwemap.sdk.map.widgets.itinerary.MapPointPickerPin
import com.getwemap.sdk.map.widgets.itinerary.rememberItineraryFormState
import com.getwemap.sdk.map.widgets.levels.rememberLevelsSwitcherState

/**
 * Planning an itinerary on a map with **no location source at all** — the case the other samples cannot show,
 * because they all set one on the way in.
 *
 * Nothing here asks for the user's position, so nothing starts location services and no permission prompt
 * appears. The form notices there is no position to offer and leaves *My position* out, which is why both ends
 * are picked on the map. That is a supported way to use it rather than a degraded one: pick a start, pick a
 * destination, and the itinerary between them is computed and drawn.
 *
 * It is also the shortest complete map screen in the app — a session, a `WemapMap`, the levels rail and the
 * planner.
 *
 * @param session the shared session. Owned by the host `ViewModel`, so it survives a configuration change.
 * @param modifier the modifier to apply to the screen.
 */
@Composable
fun ItineraryPlannerScreen(session: MapSession, modifier: Modifier = Modifier) {

    val context = LocalContext.current
    val mapViewConfig = remember { Config.makeMapViewConfig(context) }
    var mapView by remember { mutableStateOf<WemapMapView?>(null) }
    var itineraries by remember { mutableStateOf<List<Itinerary>>(emptyList()) }
    var failure by remember { mutableStateOf<SampleFailure?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    Box(modifier = modifier.fillMaxSize()) {

        WemapMap(
            session = session,
            modifier = Modifier.fillMaxSize(),
            config = mapViewConfig,
            onLoaded = { view ->
                // Bottom-start, unlike the other map samples, which only inset the compass below the app bar:
                // this screen's form card is permanent and spans the top, so a compass anchored there stays
                // behind it. The corner is free because the SDK hides the MapLibre logo and puts attribution
                // bottom-end, and the activity already pads the content clear of the navigation bar.
                view.map.uiSettings.compassGravity = Gravity.BOTTOM or Gravity.START
                mapView = view
            },
            onFailed = { failure = SampleFailure("Failed to load the map — $it", retry = null) }
        )

        mapView?.let { view ->

            val formState = rememberItineraryFormState(
                mapView = view,
                onItineraries = { itineraries = it },
                // Every failure the form has — the route that could not be computed, the position it could not
                // resolve — arrives here and nowhere else: the widget draws none of them. A sample that only
                // logged it would leave the user looking at a form that had silently given up.
                //
                // Where it goes depends on whether there is anything to decide. A failure carrying a `retry`
                // is a question, and a question goes in a dialog: it cannot be missed, and it is answered
                // before the form can be touched, so the retry cannot go stale in the user's hands. One with
                // nothing to re-ask is only news, and news goes on the snackbar.
                onFailure = { formFailure: ItineraryFormFailure ->
                    // The form erases the line it had drawn, so the readout describing it goes too.
                    itineraries = emptyList()
                    failure = SampleFailure(
                        message = formFailure.retry?.let { "Failed to compute an itinerary — ${formFailure.error}" }
                            ?: "Failed to resolve your position — ${formFailure.error}",
                        retry = formFailure.retry
                    )
                }
            )

            // The card floats at the top and the rail on the end edge, kept apart by `CardAndRail` — on a small
            // phone the card would otherwise run under the rail. The pin sits at the centre of the map, outside
            // that layout, because that is the point the picker confirms. The SDK ships no container that places
            // any of them.
            val inset = dimensionResource(CommonR.dimen.overlay_inset)
            CardAndRail(
                card = { ItineraryForm(state = formState) },
                rail = {
                    LevelsSwitcher(state = rememberLevelsSwitcherState(view.buildingManager, view.locationManager))
                },
                spacing = inset,
                modifier = Modifier
                    .fillMaxSize()
                    // `InitialActivity` reports its floating app bar as part of the status-bar inset, so this
                    // one modifier clears the bar as well as the status bar.
                    .statusBarsPadding()
                    .padding(inset)
            )
            MapPointPickerPin(state = formState, modifier = Modifier.align(Alignment.Center))

            itineraries.firstOrNull()?.let { itinerary ->
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(dimensionResource(CommonR.dimen.overlay_inset)),
                    shape = RoundedCornerShape(16.dp),
                    tonalElevation = 4.dp
                ) {
                    Text(
                        text = "${itinerary.distance.toInt()} m, ${(itinerary.duration / 60).toInt()} min",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    )
                }
            }
        }

        SnackbarHost(snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
    }

    val current = failure
    if (current?.retry != null) {
        SampleFailureDialog(
            failure = current,
            onDismiss = { failure = null }
        )
    }

    LaunchedEffect(current) {
        if (current != null && current.retry == null) {
            snackbarHostState.showSnackbar(current.message)
            failure = null
        }
    }
}
