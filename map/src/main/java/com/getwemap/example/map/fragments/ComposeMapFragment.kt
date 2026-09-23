package com.getwemap.example.map.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.getwemap.example.common.map.SessionViewModel
import com.getwemap.example.map.LocationSourceType
import com.getwemap.example.map.compose.ComposeMapScreen
import com.getwemap.example.map.compose.ExampleTheme
import org.maplibre.android.MapLibre

/**
 * Hosts the plain Compose map sample — see [ComposeMapScreen].
 *
 * Like [LevelsFragment] it exists only because this app navigates with a nav graph, holds no map state of its
 * own, and deliberately does not extend `MapFragment`: that base class is built around a `WemapMapView` that
 * exists from `onCreateView` onwards, whereas `WemapMap` hands the view over once it has loaded.
 */
class ComposeMapFragment : Fragment() {

    private val sessionViewModel: SessionViewModel by activityViewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        MapLibre.getInstance(requireContext())

        // The session was created in InitialFragment and shared via the activity-scoped ViewModel
        val session = sessionViewModel.session!!
        val locationSource = LocationSourceType.from(requireArguments())

        return ComposeView(requireContext()).apply {
            setContent {
                ExampleTheme {
                    ComposeMapScreen(session = session, locationSource = locationSource)
                }
            }
        }
    }
}
