package com.getwemap.example.map.fragments

import android.content.DialogInterface
import android.content.Context
import android.util.AttributeSet
import android.widget.ImageView
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import com.getwemap.sdk.map.WemapMapView
import org.maplibre.android.maps.AttributionDialogManager
import org.maplibre.android.maps.MapLibreMap

class CustomCreditsMapView(context: Context, attrs: AttributeSet?) : WemapMapView(context, attrs) {

    /**
     * The credits (attribution) button, once MapLibre has built it.
     *
     * MapLibre creates the button itself and hands it to no one - `UiSettings` reaches it only through its
     * own gravity/margins/tint setters, and the view carries no id. Overriding the hook that creates it is
     * the supported way to get hold of it, which is what lets a screen change its size and appearance.
     */
    var creditsButton: ImageView? = null
        private set

    override fun initialiseAttributionView(): ImageView =
        super.initialiseAttributionView().also { creditsButton = it }

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
