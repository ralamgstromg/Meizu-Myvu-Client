package com.myvu.client.nav

import com.myvu.client.ai.HttpRetry
import com.myvu.client.core.HttpCache
import com.myvu.client.core.LogBus
import com.myvu.client.core.SslUtils
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.ArrayList
import java.util.Locale

object Osrm {
    private const val OSRM_BASE = "https://router.project-osrm.org"
    private const val NOMINATIM_BASE = "https://nominatim.openstreetmap.org"
    private const val USER_AGENT = "myvu-android-client/1.0"
    private const val TIMEOUT_MS = 20000
    private const val GEOCODE_CACHE_TTL_MS = 86_400_000L
    private const val ROUTE_CACHE_TTL_MS = 300_000L

    @JvmStatic
    @Throws(IOException::class)
    fun parsePoint(s: String): DoubleArray {
        val t = s.trim()
        val parts = t.split(",")
        if (parts.size == 2) {
            try {
                return doubleArrayOf(
                    parts[0].trim().toDouble(),
                    parts[1].trim().toDouble()
                )
            } catch (ignored: NumberFormatException) {
            }
        }
        return geocode(t)
    }

    @JvmStatic
    @Throws(IOException::class)
    fun geocode(place: String): DoubleArray {
        val url = NOMINATIM_BASE + "/search?" +
                "q=" + URLEncoder.encode(place, "UTF-8") +
                "&format=json&limit=1"
        try {
            val results = JSONArray(get(url, GEOCODE_CACHE_TTL_MS))
            if (results.length() == 0) {
                throw IOException("no geocode result for \"$place\"")
            }
            val first = results.getJSONObject(0)
            return doubleArrayOf(
                first.getString("lat").toDouble(),
                first.getString("lon").toDouble()
            )
        } catch (e: JSONException) {
            throw IOException("unparseable geocode response: ${e.message}", e)
        }
    }

    @JvmStatic
    @Throws(IOException::class)
    fun route(
        originLat: Double,
        originLon: Double,
        destLat: Double,
        destLon: Double,
        profile: String
    ): Route {
        val coords = String.format(
            Locale.US, "%f,%f;%f,%f",
            originLon, originLat, destLon, destLat
        )
        val url = "$OSRM_BASE/route/v1/$profile/$coords?overview=full&geometries=geojson&steps=true&annotations=false"

        try {
            val data = JSONObject(get(url, ROUTE_CACHE_TTL_MS))
            val code = data.optString("code")
            if ("Ok" != code || data.optJSONArray("routes") == null) {
                throw IOException("OSRM returned $code: ${data.optString("message", "no route")}")
            }

            val r = data.getJSONArray("routes").getJSONObject(0)
            val vertices = buildVertices(
                r.getJSONObject("geometry").getJSONArray("coordinates")
            )
            val steps = buildSteps(r.getJSONArray("legs"), vertices)

            val distance = r.optDouble("distance", 0.0).toInt()
            val duration = r.optDouble("duration", 0.0)
            LogBus.log("route: ${steps.size} steps, ${distance}m, ${Math.round(duration)}s")
            return Route(steps, distance, duration, vertices)
        } catch (e: JSONException) {
            throw IOException("unparseable OSRM response: ${e.message}", e)
        }
    }

    @Throws(JSONException::class)
    private fun buildVertices(coordinates: JSONArray): List<Route.Vertex> {
        val out = ArrayList<Route.Vertex>(coordinates.length())
        var acc = 0.0
        var prevLat = 0.0
        var prevLon = 0.0
        for (i in 0 until coordinates.length()) {
            val c = coordinates.getJSONArray(i)
            val lon = c.getDouble(0)
            val lat = c.getDouble(1)
            if (i > 0) acc += Geo.haversine(prevLat, prevLon, lat, lon)
            out.add(Route.Vertex(lat, lon, acc))
            prevLat = lat
            prevLon = lon
        }
        return out
    }

    @Throws(JSONException::class)
    private fun buildSteps(legs: JSONArray, vertices: List<Route.Vertex>): List<Route.Step> {
        val steps = ArrayList<Route.Step>()
        for (li in 0 until legs.length()) {
            val legSteps = legs.getJSONObject(li).getJSONArray("steps")
            for (si in 0 until legSteps.length()) {
                val st = legSteps.getJSONObject(si)
                val man = st.optJSONObject("maneuver")

                val type = man?.optString("type", "") ?: ""
                val modifier = man?.optString("modifier", "") ?: ""

                var at = 0.0
                val loc = man?.optJSONArray("location")
                if (loc != null && loc.length() >= 2) {
                    at = nearestCumulative(vertices, loc.getDouble(1), loc.getDouble(0))
                }

                steps.add(
                    Route.Step(
                        IcMap.forManeuver(type, modifier),
                        st.optString("name", ""),
                        st.optDouble("distance", 0.0).toInt(),
                        st.optDouble("duration", 0.0),
                        type, modifier, at
                    )
                )
            }
        }
        return steps
    }

    private fun nearestCumulative(vertices: List<Route.Vertex>, lat: Double, lon: Double): Double {
        var bestCum = 0.0
        var bestDistance = Double.MAX_VALUE
        for (v in vertices) {
            val d = Geo.haversine(lat, lon, v.lat, v.lon)
            if (d < bestDistance) {
                bestDistance = d
                bestCum = v.cumulativeM
            }
        }
        return bestCum
    }

    @Throws(IOException::class)
    private fun get(url: String, ttlMs: Long): String {
        val cached = HttpCache.getInstance().get(url)
        if (cached != null) {
            return cached
        }

        val body = HttpRetry.execute("OSRM") { fetchDirect(url) }
        HttpCache.getInstance().put(url, body, ttlMs)
        return body
    }

    @Throws(IOException::class)
    private fun fetchDirect(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        SslUtils.applySslBypass(conn)
        try {
            conn.requestMethod = "GET"
            conn.setRequestProperty("User-Agent", USER_AGENT)
            conn.connectTimeout = TIMEOUT_MS
            conn.readTimeout = TIMEOUT_MS

            val status = conn.responseCode
            val input = if (status >= 400) conn.errorStream else conn.inputStream
            val body = readAll(input)
            if (status >= 400) {
                throw HttpRetry.statusError(
                    status,
                    "HTTP $status from $url: ${body.substring(0, Math.min(200, body.length))}"
                )
            }
            return body
        } finally {
            conn.disconnect()
        }
    }

    @Throws(IOException::class)
    private fun readAll(input: InputStream?): String {
        if (input == null) return ""
        val out = ByteArrayOutputStream()
        val buf = ByteArray(8192)
        var n: Int
        while (input.read(buf).also { n = it } > 0) out.write(buf, 0, n)
        return String(out.toByteArray(), StandardCharsets.UTF_8)
    }
}
