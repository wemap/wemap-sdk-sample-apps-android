package com.getwemap.example.map.positioning

import android.os.Bundle
import com.getwemap.example.map.positioning.LocationSourceType.Companion.ARG
import com.getwemap.example.map.positioning.LocationSourceType.Companion.from

/**
 * The location sources this sample offers, in the order the initial screen lists them.
 *
 * The enum is the list: the spinner is built from [entries] and there is no `R.array.location_sources`
 * beside it. That is the point of the type. Until v1 the source was a bare index into that array, written
 * out as a literal in three files, so removing a row renumbered every row after it and nothing said so.
 */
enum class LocationSourceType(val title: String) {
    VPS("VPS"),
    SIMULATOR("Simulator"),
    SYSTEM_DEFAULT("System Default"),
    GPS("GPS"),
    FUSED_GMS("Fused GMS"),
    VPS_LOCAL("VPS Local (offline)");

    /** Needs ARCore and a VPS-enabled map. */
    val usesVps: Boolean
        get() = this == VPS

    /** Writes this source into [bundle] under [ARG], for [from] to read back. */
    fun putInto(bundle: Bundle): Bundle =
        bundle.apply { putString(ARG, name) }

    companion object {

        const val ARG: String = "locationSource"

        /** The titles the spinner shows, in declaration order, so its position maps to `entries[position]`. */
        val titles: List<String>
            get() = entries.map { it.title }

        /**
         * The name, not the ordinal. A name survives a reordering of this enum and an ordinal does not —
         * and reordering is precisely what the old index scheme made dangerous.
         */
        fun from(arguments: Bundle): LocationSourceType {
            val name = arguments.getString(ARG)
                ?: throw IllegalArgumentException("No $ARG in arguments — pass one with putInto()")

            return valueOf(name)
        }
    }
}
