package com.getwemap.example.map

import com.getwemap.sdk.map.internal.model.serializers.LatLngSerializer
import kotlinx.serialization.Serializable
import org.maplibre.android.geometry.LatLng

@Serializable
data class ConsumerData(
    val level: Float,
    val externalID: String,

    @Serializable(with = LatLngSerializer::class)
    val coordinate: LatLng,
    val name: String
)
