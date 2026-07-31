package com.getwemap.example.map.positioning.fragments

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.getwemap.example.map.positioning.VpsLocalSessionHistory
import com.getwemap.example.map.positioning.databinding.FragmentVpsLocalHistoryMapBinding
import com.getwemap.sdk.core.model.entities.MapData
import com.getwemap.sdk.map.OnMapViewReadyCallback
import com.getwemap.sdk.map.WemapMapView
import kotlinx.serialization.json.Json
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.Style
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.LineString
import org.maplibre.geojson.Point
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Draws one recorded session's fixes on the venue map: the order they arrived in as a line, each fix as a
 * point, tappable for its detail.
 *
 * The trace is the part a summary cannot convey. Fix count and rate say how *often* the SDK localized;
 * only the shape says whether it localized *correctly* — a trace that follows the corridors is healthy,
 * while one that zig-zags across walls or jumps between rooms is the signature of ambiguous matches, and
 * neither shows up in an accepted-percentage.
 */
class VpsLocalHistoryMapFragment : Fragment(), OnMapViewReadyCallback {

    private var _binding: FragmentVpsLocalHistoryMapBinding? = null
    private val binding get() = _binding!!

    private val mapView get() = binding.mapView

    private lateinit var session: VpsLocalSessionHistory.Session

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = FragmentVpsLocalHistoryMapBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val file = File(requireArguments().getString(ARG_SESSION_FILE)!!)
        val loaded = VpsLocalSessionHistory.read(file)
        if (loaded == null || loaded.fixes.isEmpty()) {
            binding.traceInfo.text = getString(com.getwemap.example.map.positioning.R.string.vps_local_history_no_fixes)
            return
        }
        session = loaded

        val mapData = Json.decodeFromString<MapData>(requireArguments().getString(ARG_MAP_DATA)!!)
        mapView.mapData = mapData
        mapView.getMapViewAsync(this)
    }

    override fun onMapViewReady(mapView: WemapMapView, map: MapLibreMap, style: Style, data: MapData) {
        binding.levelsSwitcher.bind(mapView.buildingManager)

        style.addSource(GeoJsonSource(TRACE_SOURCE_ID, traceLine()))
        style.addLayer(
            LineLayer(TRACE_LINE_LAYER_ID, TRACE_SOURCE_ID).withProperties(
                PropertyFactory.lineColor(Color.BLUE),
                PropertyFactory.lineWidth(2f),
                PropertyFactory.lineOpacity(0.6f),
                PropertyFactory.lineJoin(Property.LINE_JOIN_ROUND),
            )
        )

        style.addSource(GeoJsonSource(FIXES_SOURCE_ID, fixFeatures()))
        style.addLayer(
            CircleLayer(FIXES_LAYER_ID, FIXES_SOURCE_ID).withProperties(
                // Radius carries the fix's own accuracy estimate, so a session where the SDK was unsure
                // reads differently from one where it was confident, at a glance.
                PropertyFactory.circleRadius(
                    org.maplibre.android.style.expressions.Expression.interpolate(
                        org.maplibre.android.style.expressions.Expression.linear(),
                        org.maplibre.android.style.expressions.Expression.get(PROPERTY_ACCURACY),
                        org.maplibre.android.style.expressions.Expression.stop(0f, 4f),
                        org.maplibre.android.style.expressions.Expression.stop(20f, 12f),
                    )
                ),
                PropertyFactory.circleColor(Color.BLUE),
                PropertyFactory.circleOpacity(0.5f),
                PropertyFactory.circleStrokeColor(Color.WHITE),
                PropertyFactory.circleStrokeWidth(1f),
            )
        )

        map.easeCamera(CameraUpdateFactory.newLatLngBounds(traceBounds(), CAMERA_PADDING_PX))
        showSummary()
        map.addOnMapClickListener { latLng -> showNearestFix(latLng) }
    }

    private fun traceLine(): LineString =
        LineString.fromLngLats(session.fixes.map { Point.fromLngLat(it.longitude, it.latitude) })

    private fun fixFeatures(): FeatureCollection = FeatureCollection.fromFeatures(
        session.fixes.mapIndexed { index, fix ->
            Feature.fromGeometry(Point.fromLngLat(fix.longitude, fix.latitude)).apply {
                addNumberProperty(PROPERTY_ACCURACY, fix.accuracy)
                addNumberProperty(PROPERTY_INDEX, index)
            }
        }
    )

    private fun traceBounds(): LatLngBounds = LatLngBounds.Builder()
        .includes(session.fixes.map { LatLng(it.latitude, it.longitude) })
        .build()

    private fun showSummary() {
        binding.traceInfo.text = buildString {
            append(session.title)
            append("\n")
            append(session.summary)
            append("\n")
            append(session.outcomeBreakdown)
            append("\n")
            append(getString(com.getwemap.example.map.positioning.R.string.vps_local_history_tap_hint))
        }
    }

    /**
     * Reports the fix nearest the tap. A `queryRenderedFeatures` hit-test would be more precise, but the
     * circles are a few pixels wide and this screen is used one-handed while walking a venue — nearest-fix
     * always answers, where an exact hit-test mostly misses.
     */
    private fun showNearestFix(latLng: LatLng): Boolean {
        val nearest = session.fixes.minByOrNull { fix ->
            val dLat = fix.latitude - latLng.latitude
            val dLon = fix.longitude - latLng.longitude
            dLat * dLat + dLon * dLon
        } ?: return false

        val index = session.fixes.indexOf(nearest)
        val elapsedS = (nearest.atMs - session.startedAtMs) / 1000
        val gapToPrevious = if (index > 0) {
            " · +%.1fs after previous".format((nearest.atMs - session.fixes[index - 1].atMs) / 1000.0)
        } else {
            ""
        }
        binding.traceInfo.text = buildString {
            append("fix ${index + 1}/${session.fixes.size} at ${TIME_STAMP.format(Date(nearest.atMs))}")
            append(" (+${elapsedS}s into the session)$gapToPrevious\n")
            append("accuracy %.1f m".format(nearest.accuracy))
            nearest.level?.let { append(" · level $it") }
            nearest.headingDegrees?.let { append(" · heading %.0f°".format(it)) }
        }
        return true
    }

    override fun onStart() { super.onStart(); mapView.onStart() }
    override fun onResume() { super.onResume(); mapView.onResume() }
    override fun onPause() { mapView.onPause(); super.onPause() }
    override fun onStop() { mapView.onStop(); super.onStop() }

    override fun onDestroyView() {
        binding.levelsSwitcher.unbind()
        mapView.onDestroy()
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val ARG_SESSION_FILE = "sessionFile"
        const val ARG_MAP_DATA = "mapData"

        private const val TRACE_SOURCE_ID = "vps-local-history-trace-source"
        private const val TRACE_LINE_LAYER_ID = "vps-local-history-trace-line"
        private const val FIXES_SOURCE_ID = "vps-local-history-fixes-source"
        private const val FIXES_LAYER_ID = "vps-local-history-fixes-circles"
        private const val PROPERTY_ACCURACY = "accuracy"
        private const val PROPERTY_INDEX = "index"
        private const val CAMERA_PADDING_PX = 96

        private val TIME_STAMP = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    }
}
