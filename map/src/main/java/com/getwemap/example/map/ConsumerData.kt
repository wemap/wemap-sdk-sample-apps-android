package com.getwemap.example.map

import com.getwemap.sdk.map.model.serializers.LatLngSerializer
import com.mapbox.mapboxsdk.geometry.LatLng
import kotlinx.serialization.Serializable

@Serializable
data class ConsumerData(
    val level: Float,
    val externalID: String,

    @Serializable(with = LatLngSerializer::class)
    val coordinate: LatLng,
    val name: String
)
