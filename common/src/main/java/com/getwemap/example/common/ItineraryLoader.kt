package com.getwemap.example.common

import android.content.Context
import com.getwemap.sdk.core.model.entities.GeoJsonItinerarySerializer
import com.getwemap.sdk.core.model.entities.Itinerary
import kotlinx.serialization.json.Json

object ItineraryLoader {

    fun loadFromGeoJSON(context: Context): Itinerary? {
        try {
            val inputStream = context.assets.open("geoJsonItinerary.json")
            val jsonString = inputStream.bufferedReader().use { it.readText() }

            val jsonConverter = Json { ignoreUnknownKeys = true }
            return jsonConverter.decodeFromString(GeoJsonItinerarySerializer, jsonString)
        } catch (e: Exception) {
            e.printStackTrace()
            println("Failed to load geo itinerary from file with error - ${e.message}")
            return null
        }
    }
}