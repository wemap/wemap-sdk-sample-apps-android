package com.getwemap.example.common

import android.view.View

/**
 * Shows or hides a camera/AR surface that has to keep rendering while hidden.
 *
 * Setting the surface — or its container — to `INVISIBLE` is not enough: on a number of devices (Samsung A52,
 * and anything on Android 9 when this was first hit) its surface keeps punching through the map's own
 * `SurfaceView`, so the map shows up as a black rectangle. Pausing or detaching it is not an option either —
 * the VPS location source keeps consuming frames from it while it background-scans. So the surface stays
 * alive and is simply moved off-screen.
 *
 * Callers pass either the `SurfaceView` itself or an ancestor of one — `GeoARView` holds its `SceneView`
 * privately, so the AR samples translate that container. A child surface is not guaranteed to follow an
 * ancestor transform, which is why the devices named above are where this has to be re-checked.
 */
fun View.setSurfaceVisible(visible: Boolean) {
    val translation = if (visible) 0f else OFFSCREEN_TRANSLATION
    translationX = translation
    translationY = translation
}

private const val OFFSCREEN_TRANSLATION = 5000f
