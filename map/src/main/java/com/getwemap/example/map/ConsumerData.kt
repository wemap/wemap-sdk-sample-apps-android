package com.getwemap.example.map

import com.getwemap.sdk.core.internal.model.serializers.PointSerializer
import kotlinx.serialization.Serializable
import org.maplibre.geojson.Point

@Serializable
data class ConsumerData(
    val level: Float,
    val externalID: String,

    @Serializable(with = PointSerializer::class)
    val coordinate: Point,
    val name: String
)
