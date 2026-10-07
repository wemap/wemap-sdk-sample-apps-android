package com.getwemap.example.map.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.getwemap.example.common.map.SessionViewModel
import com.getwemap.example.map.compose.ExampleTheme
import com.getwemap.example.map.compose.ItineraryPlannerScreen
import org.maplibre.android.MapLibre

/**
 * Hosts the Compose itinerary planner sample — see
 * [com.getwemap.example.map.compose.ItineraryPlannerScreen].
 *
 * The fragment exists only because this app navigates with a nav graph; it holds no map state of its own, and
 * deliberately does not extend `MapFragment` — that base class attaches a location source, which is the one
 * thing this sample is built to do without.
 */
class ComposeItineraryPlannerFragment : Fragment() {

    private val sessionViewModel: SessionViewModel by activityViewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        MapLibre.getInstance(requireContext())

        // The session was created in InitialFragment and shared via the activity-scoped ViewModel
        val session = sessionViewModel.session!!

        return ComposeView(requireContext()).apply {
            setContent { ExampleTheme { ItineraryPlannerScreen(session = session) } }
        }
    }
}
