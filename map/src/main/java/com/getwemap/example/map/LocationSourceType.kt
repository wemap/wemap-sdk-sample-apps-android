package com.getwemap.example.map

import android.Manifest.permission.ACCESS_FINE_LOCATION
import android.os.Bundle
import com.getwemap.example.map.LocationSourceType.Companion.ARG
import com.getwemap.example.map.LocationSourceType.Companion.from

/**
 * The location sources this sample offers, in the order the initial screen lists them.
 *
 * The enum is the list: the spinner is built from [entries] and there is no `R.array.location_sources`
 * beside it. Until v1 the source was a bare index into that array, and the two `when`s reading it had
 * branches for an id `3` the three-row array could never produce — which is what an index scheme looks like
 * after a row is removed and the readers are not.
 */
enum class LocationSourceType(val title: String) {
    SIMULATOR("Simulator"),

    /** No source of our own: the SDK stays on its own default, which is why it maps to a `null` source. */
    SYSTEM_DEFAULT("System Default"),
    FUSED_GMS("Fused GMS");

    /**
     * The permissions this source needs, empty when it needs none.
     *
     * A list rather than a launcher so a caller can skip the permission round-trip entirely for the
     * simulator — which is what makes the simulated samples start with no dialog at all.
     */
    val requiredPermissions: List<String>
        get() = if (this == SIMULATOR) listOf() else listOf(ACCESS_FINE_LOCATION)

    /** Writes this source into [bundle] under [ARG], for [from] to read back. */
    fun putInto(bundle: Bundle): Bundle =
        bundle.apply { putString(ARG, name) }

    companion object {

        const val ARG: String = "locationSource"

        /** The titles the spinner shows, in declaration order, so its position maps to `entries[position]`. */
        val titles: List<String>
            get() = entries.map { it.title }

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
