package com.getwemap.example.map.fragments

import android.content.DialogInterface
import android.content.Context
import android.util.AttributeSet
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import com.getwemap.sdk.map.WemapMapView
import org.maplibre.android.maps.AttributionDialogManager
import org.maplibre.android.maps.MapLibreMap

class NavigationMapView(context: Context, attrs: AttributeSet?) : WemapMapView(context, attrs) {

    override fun createAttributionDialogManager(context: Context, map: MapLibreMap): AttributionDialogManager {
        val fm = (context as? FragmentActivity)?.supportFragmentManager
            ?: return super.createAttributionDialogManager(context, map)
        return CreditsAttributionDialogManager(context, map, fm)
    }
}

private class CreditsAttributionDialogManager(
    context: Context, map: MapLibreMap, private val fm: FragmentManager
) : AttributionDialogManager(context, map) {

    override fun showAttributionDialog(attributionTitles: Array<out String>) {
        CreditsBottomSheetFragment().show(fm, "credits")
    }

    override fun onClick(dialog: DialogInterface?, which: Int) = Unit
}
