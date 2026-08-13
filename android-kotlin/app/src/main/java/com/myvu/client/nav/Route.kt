package com.myvu.client.nav

class Route(
    @JvmField val steps: List<Step>,
    @JvmField val totalDistanceM: Int,
    @JvmField val totalDurationS: Double,
    @JvmField val vertices: List<Vertex>
) {
    class Step(
        @JvmField val ic: Int,
        @JvmField val road: String,
        @JvmField val distanceM: Int,
        @JvmField val durationS: Double,
        @JvmField val type: String,
        @JvmField val modifier: String,
        @JvmField val atM: Double
    )

    class Vertex(
        @JvmField val lat: Double,
        @JvmField val lon: Double,
        @JvmField val cumulativeM: Double
    )
}
