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
import com.getwemap.example.map.compose.ExampleTheme
import com.getwemap.example.map.compose.LevelsScreen
import org.maplibre.android.MapLibre

/**
 * Hosts the levels sample, which is written in Compose — see [LevelsScreen].
 *
 * The fragment exists only because this app navigates with a nav graph; it holds no map state of its own. It
 * deliberately does not extend `MapFragment`: that base class is built around a `WemapMapView` that exists from
 * `onCreateView` onwards, whereas `WemapMap` hands the view over once it has loaded — and permissions and the
 * location source are handled inside the composable instead.
 */
class LevelsFragment : Fragment() {

    private val sessionViewModel: SessionViewModel by activityViewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        MapLibre.getInstance(requireContext())

        // The session was created in InitialFragment and shared via the activity-scoped ViewModel
        val session = sessionViewModel.session!!
        val locationSource = LocationSourceType.from(requireArguments())

        return ComposeView(requireContext()).apply {
            setContent {
                ExampleTheme {
                    LevelsScreen(
                        session = session,
                        locationSource = locationSource
                    )
                }
            }
        }
    }
}
