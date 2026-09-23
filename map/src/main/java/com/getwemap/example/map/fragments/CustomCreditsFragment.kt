package com.getwemap.example.map.fragments

import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.appcompat.content.res.AppCompatResources
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.getwemap.example.common.map.SessionViewModel
import com.getwemap.example.map.R
import com.getwemap.example.map.databinding.FragmentCustomCreditsBinding
import com.getwemap.example.map.insetOverlayBelowTransparentAppBar
import com.getwemap.sdk.core.awaitLoaded
import com.getwemap.sdk.map.MapSession
import com.google.android.material.color.MaterialColors
import kotlinx.coroutines.launch
import org.maplibre.android.MapLibre

class CustomCreditsFragment : Fragment() {

    private var _binding: FragmentCustomCreditsBinding? = null
    private val binding get() = _binding!!

    private val sessionViewModel: SessionViewModel by activityViewModels()

    private lateinit var session: MapSession

    private val mapView get() = binding.mapView

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        MapLibre.getInstance(requireContext())
        _binding = FragmentCustomCreditsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // One call for the whole screen: both overlay buttons live in `overlay`.
        binding.overlay.insetOverlayBelowTransparentAppBar()

        // The session was created in InitialFragment and shared via the activity-scoped ViewModel.
        session = sessionViewModel.session!!
        mapView.configure(session)
        // WemapMapView drives its own MapLibre lifecycle from the fragment's ViewTree lifecycle owner.

        lifecycleScope.launch {
            runCatching {
                mapView.awaitLoaded()
            }.onSuccess {
                customizeMapOrnaments()
            }.onFailure { error ->
                println("Failed to load mapView with error - $error")
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    /**
     * The size, border and placement of the credits button are yours to change. Its visibility is not - the
     * attribution has to stay on screen and tappable.
     */
    private fun customizeMapOrnaments() {

        val uiSettings = mapView.map.uiSettings
        val margin = resources.getDimensionPixelSize(R.dimen.overlay_button_margin)

        // The SDK places the attribution bottom-end, for parity with the iOS SDK, once the map is ready - so a
        // screen that wants it elsewhere has to say so afterwards. The SDK also hides the MapLibre logo, which
        // is what leaves the bottom-start corner free.
        uiSettings.attributionGravity = Gravity.BOTTOM or Gravity.START
        uiSettings.setAttributionMargins(margin, 0, 0, margin)
        // The only thing MapLibre tints is the icon, never the background applied below.
        val tint = MaterialColors.getColor(mapView, androidx.appcompat.R.attr.colorPrimary)
        uiSettings.setAttributionTintColor(tint)

        // The compass appears in the top-end corner as soon as the map is rotated - where this screen already
        // put a button of its own.
        uiSettings.compassGravity = Gravity.BOTTOM or Gravity.END
        uiSettings.setCompassMargins(0, 0, margin, margin)

        mapView.creditsButton?.let { styleAsOverlayButton(it) }
    }

    /**
     * The screen's own controls get this from `@style/OverlayIconButton`. The credits button is MapLibre's own
     * view, so it gets the same resources applied by hand - which is what makes the three read as one set.
     */
    private fun styleAsOverlayButton(button: ImageView) {

        val side = resources.getDimensionPixelSize(R.dimen.overlay_button_size)
        val padding = resources.getDimensionPixelSize(R.dimen.overlay_button_padding)

        button.background = AppCompatResources.getDrawable(requireContext(), R.drawable.bg_overlay_button)
        button.setPadding(padding, padding, padding, padding)
        button.contentDescription = "En savoir plus sur la cartographie de cette gare"

        // MapLibre measures the button WRAP_CONTENT and keeps repositioning the ornament through these same
        // LayoutParams, so the size belongs on them - reassigned, not just mutated, to trigger a layout pass.
        button.layoutParams = button.layoutParams.apply {
            width = side
            height = side
        }
    }
}
