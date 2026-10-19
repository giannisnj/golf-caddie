package com.golfcaddie.core

import kotlinx.serialization.Serializable
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

@Serializable
data class GeoPoint(val lat: Double, val lon: Double)

/** Distance units shown to the player. Everything is stored in meters. */
enum class DistanceUnit(val label: String, val metersPerUnit: Double) {
    YARDS("yd", 0.9144),
    METERS("m", 1.0);

    fun fromMeters(meters: Double): Double = meters / metersPerUnit
    fun toMeters(value: Double): Double = value * metersPerUnit
}

object Geo {
    const val EARTH_RADIUS_M = 6_371_008.8

    private fun Double.rad() = this * PI / 180.0
    private fun Double.deg() = this * 180.0 / PI

    /** Great-circle distance in meters (haversine). */
    fun distance(a: GeoPoint, b: GeoPoint): Double {
        val dLat = (b.lat - a.lat).rad()
        val dLon = (b.lon - a.lon).rad()
        val h = sin(dLat / 2).let { it * it } +
            cos(a.lat.rad()) * cos(b.lat.rad()) * sin(dLon / 2).let { it * it }
        return 2 * EARTH_RADIUS_M * asin(sqrt(h.coerceIn(0.0, 1.0)))
    }

    /** Initial bearing from [a] to [b] in degrees, 0 = north, clockwise. */
    fun bearing(a: GeoPoint, b: GeoPoint): Double {
        val lat1 = a.lat.rad()
        val lat2 = b.lat.rad()
        val dLon = (b.lon - a.lon).rad()
        val y = sin(dLon) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)
        return (atan2(y, x).deg() + 360.0) % 360.0
    }

    /** Point reached by travelling [meters] from [start] along [bearingDeg]. */
    fun destination(start: GeoPoint, bearingDeg: Double, meters: Double): GeoPoint {
        val d = meters / EARTH_RADIUS_M
        val brg = bearingDeg.rad()
        val lat1 = start.lat.rad()
        val lon1 = start.lon.rad()
        val lat2 = asin(sin(lat1) * cos(d) + cos(lat1) * sin(d) * cos(brg))
        val lon2 = lon1 + atan2(sin(brg) * sin(d) * cos(lat1), cos(d) - sin(lat1) * sin(lat2))
        return GeoPoint(lat2.deg(), ((lon2.deg() + 540.0) % 360.0) - 180.0)
    }

    /**
     * Local flat projection (meters east/north of [origin]). Accurate to well under a
     * meter across a golf hole, and lets us use plain 2D geometry.
     */
    fun toLocal(origin: GeoPoint, p: GeoPoint): Pair<Double, Double> {
        val x = (p.lon - origin.lon).rad() * cos(origin.lat.rad()) * EARTH_RADIUS_M
        val y = (p.lat - origin.lat).rad() * EARTH_RADIUS_M
        return x to y
    }

    /** Shortest distance in meters from [p] to the segment [a]-[b]. */
    fun distanceToSegment(p: GeoPoint, a: GeoPoint, b: GeoPoint): Double {
        val (ax, ay) = toLocal(p, a)
        val (bx, by) = toLocal(p, b)
        val dx = bx - ax
        val dy = by - ay
        val len2 = dx * dx + dy * dy
        val t = if (len2 == 0.0) 0.0 else (-(ax * dx + ay * dy) / len2).coerceIn(0.0, 1.0)
        val cx = ax + t * dx
        val cy = ay + t * dy
        return sqrt(cx * cx + cy * cy)
    }

    fun distanceToPolyline(p: GeoPoint, line: List<GeoPoint>): Double = when (line.size) {
        0 -> Double.POSITIVE_INFINITY
        1 -> distance(p, line[0])
        else -> line.zipWithNext().minOf { (a, b) -> distanceToSegment(p, a, b) }
    }

    /** Ray-casting point-in-polygon test. The ring may or may not repeat its first point. */
    fun contains(polygon: List<GeoPoint>, p: GeoPoint): Boolean {
        if (polygon.size < 3) return false
        var inside = false
        var j = polygon.size - 1
        for (i in polygon.indices) {
            val pi = polygon[i]
            val pj = polygon[j]
            if ((pi.lat > p.lat) != (pj.lat > p.lat) &&
                p.lon < (pj.lon - pi.lon) * (p.lat - pi.lat) / (pj.lat - pi.lat) + pi.lon
            ) {
                inside = !inside
            }
            j = i
        }
        return inside
    }

    /** Area-weighted centroid of a polygon ring; falls back to the vertex mean. */
    fun centroid(polygon: List<GeoPoint>): GeoPoint {
        require(polygon.isNotEmpty())
        val origin = polygon[0]
        val pts = polygon.map { toLocal(origin, it) }
        var area2 = 0.0
        var cx = 0.0
        var cy = 0.0
        for (i in pts.indices) {
            val (x0, y0) = pts[i]
            val (x1, y1) = pts[(i + 1) % pts.size]
            val cross = x0 * y1 - x1 * y0
            area2 += cross
            cx += (x0 + x1) * cross
            cy += (y0 + y1) * cross
        }
        if (kotlin.math.abs(area2) < 1e-6) {
            return GeoPoint(polygon.map { it.lat }.average(), polygon.map { it.lon }.average())
        }
        cx /= (3 * area2)
        cy /= (3 * area2)
        val dist = sqrt(cx * cx + cy * cy)
        val brg = (atan2(cx, cy).deg() + 360.0) % 360.0
        return destination(origin, brg, dist)
    }

    /** Total length of a polyline in meters. */
    fun length(line: List<GeoPoint>): Double =
        line.zipWithNext().sumOf { (a, b) -> distance(a, b) }

    /** Point [meters] along a polyline from its start (clamped to the ends). */
    fun pointAlong(line: List<GeoPoint>, meters: Double): GeoPoint {
        require(line.isNotEmpty())
        var remaining = meters.coerceAtLeast(0.0)
        for ((a, b) in line.zipWithNext()) {
            val seg = distance(a, b)
            if (remaining <= seg) return destination(a, bearing(a, b), remaining)
            remaining -= seg
        }
        return line.last()
    }
}
