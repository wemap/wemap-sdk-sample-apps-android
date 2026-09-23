package com.getwemap.example.positioning.ar.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.getwemap.example.common.SessionViewModel
import com.getwemap.example.positioning.ar.LocationSourceType
import com.getwemap.example.positioning.ar.compose.ComposeARScreen

/**
 * Hosts the Compose AR sample — see [ComposeARScreen].
 *
 * It exists only because this app navigates with a nav graph, and holds no AR state of its own. It deliberately
 * does not extend [ARFragment]: that base class is built around a `GeoARView` inflated from a layout and
 * configured in `onViewCreated`, whereas `WemapGeoAR` creates and configures the view itself and hands it over
 * once it has loaded.
 */
class ComposeARFragment : Fragment() {

    private val sessionViewModel: SessionViewModel by activityViewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {

        // The session was created in InitialFragment and shared via the activity-scoped ViewModel
        val session = sessionViewModel.session!!
        val locationSource = LocationSourceType.from(requireArguments())

        return ComposeView(requireContext()).apply {
            setContent {
                MaterialTheme {
                    ComposeARScreen(session = session, locationSource = locationSource)
                }
            }
        }
    }
}
