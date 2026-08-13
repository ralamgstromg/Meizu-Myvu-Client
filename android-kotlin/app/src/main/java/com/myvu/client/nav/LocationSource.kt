package com.myvu.client.nav

interface LocationSource {
    fun interface Listener {
        fun onFix(lat: Double, lon: Double, speedMps: Float, bearing: Float)
        fun onUnavailable(reason: String) {}
    }

    fun start(listener: Listener)
    fun stop()
}
