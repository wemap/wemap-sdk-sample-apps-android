package com.getwemap.example.map.positioning

object Constants {

    val mapId: Int get() {
        return 19158 // TODO: Modify this value with your own map id
    }

    val token: String get() {
        return "GUHTU6TYAWWQHUSR5Z5JZNMXX" // TODO: Modify this value with your own wemap token
    }

    val polestarApiKey: String get () {
        throw Exception("Specify polestarApiKey and remove exception")
    }

    val vpsEndpoint: String get() {
        throw Exception("Specify vpsEndpoint and remove exception")
    }
}