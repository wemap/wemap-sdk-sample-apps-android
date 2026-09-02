package com.getwemap.example.map.fragments

import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.appcompat.content.res.AppCompatResources
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.getwemap.example.map.R
import com.getwemap.example.map.databinding.FragmentCustomCreditsBinding
import com.getwemap.sdk.core.model.entities.MapData
import com.google.android.material.color.MaterialColors
import kotlinx.serialization.json.Json
import org.maplibre.android.MapLibre
import org.maplibre.android.maps.MapLibreMap

class CustomCreditsFragment : Fragment() {

    private var _binding: FragmentCustomCreditsBinding? = null
    private val binding get() = _binding!!

    private val mapView get() = binding.mapView
    private val closeButton get() = binding.closeButton

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        MapLibre.getInstance(requireContext())
        _binding = FragmentCustomCreditsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val mapData: MapData = Json.decodeFromString(requireArguments().getString("mapData")!!)

        closeButton.setOnClickListener { findNavController().popBackStack() }

        mapView.mapData = mapData
        mapView.onCreate(savedInstanceState)

        mapView.getMapViewAsync { _, map, _, _ ->
            customizeMapOrnaments(map)
        }
    }

    override fun onStart() {
        super.onStart()
        mapView.onStart()
    }

    override fun onResume() {
        super.onResume()
        mapView.onResume()
    }

    override fun onPause() {
        super.onPause()
        mapView.onPause()
    }

    override fun onStop() {
        super.onStop()
        mapView.onStop()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        mapView.onSaveInstanceState(outState)
    }

    override fun onLowMemory() {
        super.onLowMemory()
        mapView.onLowMemory()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        mapView.onDestroy()
        _binding = null
    }

    /**
     * The size, border and placement of the credits button are yours to change. Its visibility is not - the
     * attribution has to stay on screen and tappable.
     */
    private fun customizeMapOrnaments(map: MapLibreMap) {

        val uiSettings = map.uiSettings
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
