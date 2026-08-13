package com.myvu.client.nav

class RouteTracker(private val route: Route) {

    class State internal constructor(
        @JvmField val travelledM: Double,
        @JvmField val remainingM: Double,
        @JvmField val offRoute: Boolean,
        @JvmField val deviationM: Double,
        @JvmField val nextStep: Route.Step?,
        @JvmField val distToNextM: Double
    )

    fun update(lat: Double, lon: Double): State {
        var bestDistance = Double.MAX_VALUE
        var travelled = 0.0

        for (v in route.vertices) {
            val d = Geo.haversine(lat, lon, v.lat, v.lon)
            if (d < bestDistance) {
                bestDistance = d
                travelled = v.cumulativeM
            }
        }
        if (route.vertices.isEmpty()) bestDistance = 0.0

        var next: Route.Step? = null
        var distToNext = 0.0
        for (s in route.steps) {
            if (s.atM > travelled + PASSED_MARGIN_M) {
                next = s
                distToNext = s.atM - travelled
                break
            }
        }

        return State(
            travelled,
            Math.max(0.0, route.totalDistanceM - travelled),
            bestDistance > OFF_ROUTE_M,
            bestDistance,
            next,
            distToNext
        )
    }

    companion object {
        const val OFF_ROUTE_M: Double = 45.0
        private const val PASSED_MARGIN_M = 5.0
    }
}
