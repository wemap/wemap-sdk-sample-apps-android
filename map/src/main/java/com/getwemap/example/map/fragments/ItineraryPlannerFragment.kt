package com.getwemap.example.map.fragments

import android.app.AlertDialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.dimensionResource
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import com.getwemap.example.common.R as CommonR
import com.getwemap.example.common.map.GlobalOptions
import com.getwemap.example.common.multiline
import com.getwemap.example.map.compose.CardAndRail
import com.getwemap.example.map.databinding.FragmentItineraryPlannerBinding
import com.getwemap.example.map.insetOverlayBelowTransparentAppBar
import com.getwemap.sdk.core.awaitLoaded
import com.getwemap.sdk.core.model.entities.Itinerary
import com.getwemap.sdk.core.model.services.ItinerarySearchRules
import com.getwemap.sdk.core.navigation.manager.NavigationEvent
import com.getwemap.sdk.map.WemapMapView
import com.getwemap.sdk.map.widgets.itinerary.ItineraryEndpoint
import com.getwemap.sdk.map.widgets.itinerary.ItineraryForm
import com.getwemap.sdk.map.widgets.itinerary.ItineraryFormFailure
import com.getwemap.sdk.map.widgets.itinerary.ItineraryFormState
import com.getwemap.sdk.map.widgets.itinerary.MapPointPickerPin
import com.getwemap.sdk.map.widgets.itinerary.rememberItineraryFormState
import com.getwemap.sdk.map.widgets.levels.LevelsSwitcher
import com.getwemap.sdk.map.widgets.levels.LevelsSwitcherView
import com.getwemap.sdk.map.widgets.levels.rememberLevelsSwitcherState
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch
import org.maplibre.android.MapLibre
import org.maplibre.android.location.modes.RenderMode

/**
 * Planning an itinerary and then navigating it, which is the flow a maps app has:
 * **plan → preview → start → stop**.
 *
 * The planning half is entirely the SDK's itinerary form — picking the two ends, resolving the user's
 * position, resolving the level of a point picked on the map, computing, drawing and framing. What is left for
 * a screen is the three buttons below, and handing the previewed itinerary to the navigation manager.
 *
 * It is the only View sample on the itinerary form. Keeping the planning here is what keeps `NavigationFragment`
 * about navigation between long-pressed annotations. iOS splits its samples the same way,
 * `ItineraryPlannerViewController` beside `NavigationViewController`.
 *
 * The form and the levels rail are composed into one `ComposeView` rather than placed as the widgets' `View`
 * hosts, so that [CardAndRail] can keep the card out from under the rail on a small phone — the arrangement
 * `ItineraryFormView`, which places its card inside itself, cannot express.
 *
 * **Known limitation: on a small phone in landscape the card covers the button row.** At 568×320dp the card
 * reaches almost to the bottom edge, over *Start navigation* — which is enabled only once the form has drawn an
 * itinerary, so exactly while the card is up — and dismissing the form clears the previewed itinerary with it.
 * Left as is for now; [CardAndRail] keeps the card clear of the rail only, not of the row.
 */
class ItineraryPlannerFragment : MapFragment() {

    override val mapView get() = binding.mapView
    /** `null`: this screen composes its rail beside the itinerary form, in [Planner]. */
    override val levelsSwitcher: LevelsSwitcherView? = null

    private val textView get() = binding.textView

    private val buttonPlanItinerary get() = binding.planItinerary
    private val buttonStartNavigation get() = binding.startNavigation
    private val buttonStopNavigation get() = binding.stopNavigation
    private val userLocationTextView get() = binding.userLocationTextView

    private val navigationManager get() = mapView.navigationManager
    private val locationManager get() = mapView.locationManager

    /**
     * The itinerary the form last drew — what *Start navigation* navigates along, so that the navigation
     * follows exactly the line the user was shown instead of a fresh search that may answer differently.
     */
    private var previewedItinerary: Itinerary? = null

    /** The loaded map, which is what lets [Planner] compose anything at all. */
    private var loadedMapView by mutableStateOf<WemapMapView?>(null)

    /** Whether the itinerary form is presented. Taking it out of composition is what erases what it drew. */
    private var isPlanning by mutableStateOf(false)

    /** The end the form opens with, read when it is presented. */
    private var seededDestination by mutableStateOf<ItineraryEndpoint?>(null)

    /** The presented form's state, for the wheelchair switch *Start navigation* carries forward. */
    private var formState: ItineraryFormState? = null

    private var _binding: FragmentItineraryPlannerBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        MapLibre.getInstance(requireContext())
        _binding = FragmentItineraryPlannerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // One call for the whole screen. `planner` is deliberately outside `overlay`: it takes the map's bounds
        // so its picker pin marks the map's centre, and insets its card and rail itself.
        binding.overlay.insetOverlayBelowTransparentAppBar()

        binding.planner.apply {
            // Disposed with the view lifecycle rather than on detach, which comes after the map has torn itself
            // down: leaving composition is what has the form erase its line, and it must find the map alive.
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent { loadedMapView?.let { Planner(it) } }
        }

        lifecycleScope.launch {
            runCatching {
                mapView.awaitLoaded()
            }.onSuccess {
                onMapViewReady(it)
            }.onFailure { error ->
                println("Failed to load MapView with error - $error")
            }
        }

        buttonPlanItinerary.setOnClickListener { presentItineraryForm() }
        buttonStartNavigation.setOnClickListener { startNavigation() }
        buttonStopNavigation.setOnClickListener { stopNavigation() }
    }

    private fun onMapViewReady(mapView: WemapMapView) {

        lifecycleScope.launch {
            runCatching {
                mapView.itineraryManager.searchRuleNames()
            }.onSuccess {
                println("Available rule names - $it")
            }.onFailure {
                println("Failed to get rule names with error - $it")
            }
        }

        loadedMapView = mapView
        observeNavigationManager()
        updateUI()

        // Clears the selection the way tapping empty map does everywhere else, since a selected point of
        // interest is what the form opens with as its destination. Not while the form is up: it turns
        // selection off for as long as it is presented, and a tap there is part of the panning a point pick is
        // made of.
        mapView.map.addOnMapClickListener {
            if (!isPlanning) {
                pointOfInterestManager.unselectPoi()
            }
            false
        }
    }

    override fun locationManagerReady() {
        super.locationManagerReady()
        lifecycleScope.launch {
            locationManager
                .coordinates
                .collect {
                    userLocationTextView.text = "$it"
                    userLocationTextView.isVisible = true
                }
        }
    }

    /**
     * The form, its pin and the rail over the map. The form is in composition only while [isPlanning], and
     * leaving it is what erases the itinerary it drew, unmarks the chosen ends and puts point-of-interest
     * selection back.
     */
    @Composable
    private fun Planner(mapView: WemapMapView) {
        val state = if (isPlanning) {
            rememberItineraryFormState(
                mapView = mapView,
                destination = seededDestination,
                itineraryOptions = GlobalOptions.itineraryOptions,
                onItineraries = { itineraries ->
                    previewedItinerary = itineraries.firstOrNull()
                    updateUI()
                },
                onFailure = ::handleFormFailure
            )
        } else {
            null
        }
        SideEffect { formState = state }

        Box(modifier = Modifier.fillMaxSize()) {
            val inset = dimensionResource(CommonR.dimen.overlay_inset)
            CardAndRail(
                card = { state?.let { ItineraryForm(state = it, onClose = ::dismissItineraryForm) } },
                rail = {
                    LevelsSwitcher(
                        state = rememberLevelsSwitcherState(mapView.buildingManager, mapView.locationManager)
                    )
                },
                spacing = inset,
                // `InitialActivity` folds its transparent app bar into the inset it dispatches, so this clears
                // the bar as well as the system bars.
                modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(inset)
            )
            state?.let { MapPointPickerPin(state = it, modifier = Modifier.align(Alignment.Center)) }
        }
    }

    private fun presentItineraryForm() {
        // The livemap way in: a user who already picked a point of interest is asking for directions *to it*,
        // so the selection seeds the end instead of being asked for again.
        seededDestination = pointOfInterestManager.getSelectedPoi()?.let {
            ItineraryEndpoint(it.name, it.coordinate)
        }
        isPlanning = true
        updateUI()
    }

    private fun dismissItineraryForm() {
        isPlanning = false
        previewedItinerary = null
        updateUI()
    }

    /**
     * The form draws no error of its own, so this is where a failure becomes visible.
     *
     * A failure carrying a retry is a **question**, and a question goes in a dialog: the retry is only good
     * while the question is unchanged, and a dialog is answered before the form can be touched. A fading
     * snackbar would take the way back off screen and leave a form with both ends filled, no line and nothing
     * to press. A failure with nothing to re-ask is only news, and news goes on the snackbar.
     */
    private fun handleFormFailure(failure: ItineraryFormFailure) {
        // The form erases the line it had drawn, so the previewed itinerary is gone with it — left set,
        // *Start navigation* stays enabled for a route that is no longer on the map.
        previewedItinerary = null
        updateUI()

        val retry = failure.retry
        if (retry == null) {
            // Nothing to re-ask: the form's own *My position* button is still there, and tapping it again is
            // the way to try again.
            Snackbar
                .make(mapView, "Failed to resolve your position - ${failure.error}", Snackbar.LENGTH_LONG)
                .multiline()
                .show()
            return
        }

        AlertDialog.Builder(requireContext())
            .setTitle("Failed to plan the itinerary")
            .setMessage("${failure.error}")
            .setPositiveButton("Retry") { _, _ -> retry() }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun startNavigation() {

        val itinerary = previewedItinerary ?: return

        buttonStartNavigation.isEnabled = false

        // The form's switch is the only place the user expressed this, and these rules are what a
        // recalculation on the way will use — so a navigation started from a wheelchair-accessible preview has
        // to carry the constraint forward or it can quietly lose it at the first recalculation.
        val rules = if (formState?.isWheelchairAccessible == true) {
            ItinerarySearchRules.WHEELCHAIR
        } else {
            ItinerarySearchRules()
        }

        lifecycleScope.launch {
            runCatching {
                navigationManager.startNavigation(
                    itinerary,
                    options = GlobalOptions.navigationOptions(requireContext()),
                    searchRules = rules,
                    itineraryOptions = GlobalOptions.itineraryOptions
                )
            }.onSuccess {
                // also you can use simulator to generate locations along the itinerary
                simulator?.setItinerary(it.itinerary)
                locationManager.renderMode = RenderMode.COMPASS
                dismissItineraryForm()
            }.onFailure {
                val text = "Failed to start navigation along the planned itinerary with error - $it"
                Snackbar.make(mapView, text, Snackbar.LENGTH_LONG).multiline().show()
                updateUI()
            }
        }
    }

    private fun stopNavigation() {
        navigationManager.stopNavigation()
            .onSuccess {
                simulator?.reset()
                updateUI()
            }.onFailure {
                val text = "Failed to stop navigation with error - $it"
                Snackbar.make(mapView, text, Snackbar.LENGTH_LONG).multiline().show()
            }
    }

    private fun observeNavigationManager() {
        viewLifecycleOwner.lifecycleScope.launch {
            launch {
                navigationManager.navigationInfoUpdates.collect { info ->
                    val nextStepInstructions = info.nextStep?.getNavigationInstructions(requireContext())?.instructions
                    textView.text = info.toCompactString() + "\nNext - $nextStepInstructions"
                    textView.visibility = View.VISIBLE
                }
            }
            launch {
                navigationManager.navigationEvents.collect { event ->
                    when (event) {
                        is NavigationEvent.Started -> {
                            textView.visibility = View.VISIBLE
                            Snackbar.make(mapView, "Navigation started", Snackbar.LENGTH_LONG).multiline().show()
                            updateUI()

                            event.navigation.itinerary.legsSteps.forEach {
                                println(it.getNavigationInstructions(requireContext()))
                            }
                        }
                        is NavigationEvent.Stopped -> {
                            textView.visibility = View.GONE
                            Snackbar.make(mapView, "Navigation stopped", Snackbar.LENGTH_LONG).multiline().show()
                            updateUI()
                        }
                        is NavigationEvent.Arrived ->
                            Snackbar.make(mapView, "Navigation arrived at destination", Snackbar.LENGTH_LONG)
                                .multiline().show()
                        is NavigationEvent.Recalculated ->
                            Snackbar.make(mapView, "Navigation recalculated", Snackbar.LENGTH_LONG).multiline().show()
                    }
                }
            }
            launch {
                navigationManager.errors.collect { error ->
                    textView.visibility = View.GONE
                    Snackbar.make(mapView, "Navigation failed with error - $error", Snackbar.LENGTH_LONG)
                        .multiline().show()
                }
            }
        }
    }

    private fun updateUI() {
        // The manager is the source of truth for this — a flag of our own would have to be kept in step with
        // navigations the SDK ends by itself, arrival being the obvious one.
        val isNavigating = navigationManager.hasActiveNavigation
        buttonPlanItinerary.isEnabled = !isPlanning && !isNavigating
        buttonStartNavigation.isEnabled = previewedItinerary != null && !isNavigating
        buttonStopNavigation.isEnabled = isNavigating
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
