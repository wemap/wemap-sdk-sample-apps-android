package com.getwemap.example.positioning.ar

import android.Manifest.permission
import android.os.Bundle
import com.getwemap.example.positioning.ar.LocationSourceType.Companion.ARG
import com.getwemap.example.positioning.ar.LocationSourceType.Companion.from

/**
 * The location sources this sample offers, in the order the samples list shows them.
 *
 * Unusually, this app has no source picker: **each row of the samples list is its own source**, so the enum
 * carries the row's text as well and the list is built from [entries]. One extra row follows them — the
 * Compose AR screen, a rendering variant rather than a source — which is why `SamplesListFragment` appends it
 * rather than the enum carrying it.
 */
enum class LocationSourceType(val title: String, val details: String) {
    SIMULATOR(
        "Simulator Location Source",
        "Shows how to simulate user movements using simulator location source in AR"
    ),
    VPS(
        "VPS Location Source",
        "Shows how to track user movements using VPS location source in AR"
    ),
    ANDROID_FUSED_ADAPTIVE(
        "Android Fused Adaptive Location Source",
        "Shows how to track user movements using Android Fused Adaptive location source in AR"
    ),
    FUSED_GMS(
        "Fused GMS Location Source",
        "Shows how to track user movements using GMS Fused location source in AR"
    ),
    GPS(
        "GPS Location Source",
        "Shows how to track user movements using GPS location source in AR"
    );

    /**
     * `CAMERA` is in every branch because none of these sources supplies camera capture; everything but the
     * simulator additionally needs a real position.
     */
    val requiredPermissions: List<String>
        get() = if (this == SIMULATOR) {
            listOf(permission.CAMERA)
        } else {
            listOf(permission.CAMERA, permission.ACCESS_FINE_LOCATION, permission.ACCESS_COARSE_LOCATION)
        }

    /** Writes this source into [bundle] under [ARG], for [from] to read back. */
    fun putInto(bundle: Bundle): Bundle =
        bundle.apply { putString(ARG, name) }

    companion object {

        const val ARG: String = "locationSource"

        /**
         * The name, not the ordinal: a name survives a reordering of this enum and an ordinal does not.
         */
        fun from(arguments: Bundle): LocationSourceType {
            val name = arguments.getString(ARG)
                ?: throw IllegalArgumentException("No $ARG in arguments — pass one with putInto()")

            return valueOf(name)
        }
    }
}
