package com.getwemap.example.map

import android.util.TypedValue
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.getwemap.sdk.map.WemapMapView

/**
 * Clears the transparent app bar for every control inside this container, in one call.
 *
 * The sample screens draw their map full-bleed under the app bar, and `InitialActivity` reports that bar as
 * part of the content's system-bars inset — so this one number covers the status bar and the bar together.
 *
 * **Call it once per screen, on the container holding the screen's overlay controls** — not on each control.
 * Grouping them is the whole point: a screen that insets control-by-control needs a call per control and a
 * new one every time a control is added, and forgetting it is invisible until someone looks. This is the
 * View-side answer to what iOS gets from a safe area, which propagates to descendants on its own; Android has
 * no equivalent for chrome the *app* draws, `fitsSystemWindows` knowing only about system windows.
 *
 * The map is deliberately *not* inside that container: the map is what is meant to be up there. Neither is an
 * overlay that was given the map's own bounds because something it draws marks the map's centre — padding it
 * would carry that mark down with it, and an overlay like that insets its own contents.
 *
 * Padding and not a margin, so the container stays the size of the map and only its contents move; the
 * original padding is read once and the inset added to it, so a listener firing again — a rotation, a window
 * resize, a destination change — cannot accumulate.
 */
internal fun View.insetOverlayBelowTransparentAppBar() {

    val originalPaddingTop = paddingTop

    ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
        val top = insets.getInsets(WindowInsetsCompat.Type.systemBars()).top
        view.updatePadding(top = originalPaddingTop + top)
        insets
    }
}

/**
 * Pushes the map's compass down by the reported top inset.
 *
 * MapLibre's ornaments are child views positioned by **fixed pixel margins from the map's own edge** — there
 * is no inset-aware API and the SDK installs no inset listener — so on these screens, where the map is drawn
 * full-bleed under the status bar and a transparent app bar, the compass lands in the status bar. It is only
 * visible once the map is rotated, which is why it went unnoticed: MapLibre fades it while the map faces north.
 *
 * The same one number covers both, for the reason [insetOverlayBelowTransparentAppBar] gives. **It has to be applied
 * sample-side and not in `:map`**: the SDK does not know this app floats a bar over the map, and going
 * full-bleed at the top is this app's own choice — `InitialActivity` pads its root `(left, 0, right, bottom)`,
 * so only the top is deliberately left to the map. iOS draws the same line: its SDK positions no ornaments and
 * `CustomCreditsViewController` moves them itself.
 *
 * **Call it once the map has loaded**, since it reads `map.uiSettings` — the original margin is read once and
 * the inset added to it, so a listener firing again cannot accumulate.
 */
internal fun WemapMapView.insetCompassBelowTransparentAppBar() {

    val uiSettings = map.uiSettings
    val originalTopMargin = uiSettings.compassMarginTop

    ViewCompat.setOnApplyWindowInsetsListener(this) { _, insets ->
        val top = insets.getInsets(WindowInsetsCompat.Type.systemBars()).top
        uiSettings.setCompassMargins(
            uiSettings.compassMarginLeft,
            originalTopMargin + top,
            uiSettings.compassMarginRight,
            uiSettings.compassMarginBottom
        )
        insets
    }
    // The map finishes loading long after the window's first inset pass, so nothing would dispatch to the
    // listener just installed without asking.
    requestApplyInsets()
}

/** `?attr/actionBarSize` in pixels — the height the app bar has whether or not it is painted. */
internal val android.content.Context.actionBarSize: Int
    get() {
        val value = TypedValue()
        theme.resolveAttribute(androidx.appcompat.R.attr.actionBarSize, value, true)
        return TypedValue.complexToDimensionPixelSize(value.data, resources.displayMetrics)
    }
