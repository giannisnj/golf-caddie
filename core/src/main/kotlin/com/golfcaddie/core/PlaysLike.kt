package com.golfcaddie.core

import kotlinx.serialization.Serializable
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * Ground height over a course, sampled on a regular lat/lon grid and stored with the course,
 * so uphill/downhill works offline. Heights come from Open-Meteo's free elevation API
 * (Copernicus 90 m terrain model): good for slopes across a hole, too coarse for small mounds.
 */
@Serializable
data class ElevationGrid(
    val south: Double,
    val west: Double,
    val stepLat: Double,
    val stepLon: Double,
    val rows: Int,
    val cols: Int,
    /** Row-major from the south-west corner, in meters above sea level. */
    val meters: List<Double>,
) {
    init {
        require(meters.size == rows * cols) { "expected ${rows * cols} heights, got ${meters.size}" }
    }

    /** Height at [p] by bilinear interpolation, or null outside the grid. */
    fun at(p: GeoPoint): Double? {
        val r = (p.lat - south) / stepLat
        val c = (p.lon - west) / stepLon
        if (r < 0 || c < 0 || r > rows - 1 || c > cols - 1) return null
        val r0 = minOf(r.toInt(), rows - 2).coerceAtLeast(0)
        val c0 = minOf(c.toInt(), cols - 2).coerceAtLeast(0)
        val fr = r - r0
        val fc = c - c0
        fun h(row: Int, col: Int) = meters[minOf(row, rows - 1) * cols + minOf(col, cols - 1)]
        val lower = h(r0, c0) * (1 - fc) + h(r0, c0 + 1) * fc
        val upper = h(r0 + 1, c0) * (1 - fc) + h(r0 + 1, c0 + 1) * fc
        return lower * (1 - fr) + upper * fr
    }

    companion object {
        /** Open-Meteo's elevation API takes up to this many points per request. */
        const val POINTS_PER_REQUEST = 100

        /**
         * The grid layout to fetch for a course: its holes plus a margin, about every [spacingMeters].
         * 90 m matches the terrain model's resolution; denser sampling adds calls, not detail.
         * Open-Meteo counts every point as one call (free: 600/minute, 10,000/day).
         */
        fun planFor(course: Course, spacingMeters: Double = 90.0, maxPoints: Int = 400): Plan {
            val pts = course.holes.flatMap { it.line + it.green } + course.areas.flatten()
            require(pts.isNotEmpty()) { "course has no geometry" }
            val margin = 60.0 / Geo.EARTH_RADIUS_M * 180 / PI
            val south = pts.minOf { it.lat } - margin
            val north = pts.maxOf { it.lat } + margin
            val west = pts.minOf { it.lon } - margin / cos(south * PI / 180)
            val east = pts.maxOf { it.lon } + margin / cos(south * PI / 180)
            val heightM = Geo.distance(GeoPoint(south, west), GeoPoint(north, west))
            val widthM = Geo.distance(GeoPoint(south, west), GeoPoint(south, east))
            // Widen the spacing on big sites so the number of points stays reasonable.
            val spacing = max(spacingMeters, kotlin.math.sqrt(heightM * widthM / maxPoints))
            val rows = ceil(heightM / spacing).toInt() + 1
            val cols = ceil(widthM / spacing).toInt() + 1
            return Plan(south, west, (north - south) / (rows - 1), (east - west) / (cols - 1), rows, cols)
        }
    }

    data class Plan(val south: Double, val west: Double, val stepLat: Double, val stepLon: Double, val rows: Int, val cols: Int) {
        val points: List<GeoPoint>
            get() = (0 until rows).flatMap { r -> (0 until cols).map { c -> GeoPoint(south + r * stepLat, west + c * stepLon) } }

        fun withHeights(meters: List<Double>) = ElevationGrid(south, west, stepLat, stepLon, rows, cols, meters)
    }
}

/** Weather on the course right now (from Open-Meteo); wind is the 10 m wind. */
@Serializable
data class Conditions(
    val windSpeedMs: Double,
    /** Meteorological direction the wind blows FROM, degrees clockwise from north. */
    val windFromDeg: Double,
    val temperatureC: Double,
    val fetchedAtMillis: Long,
)

/** How far a shot plays, and why. All values in meters; effects are added to [actualMeters]. */
data class PlaysLike(
    val actualMeters: Double,
    /** Target height minus player height; null when the course has no elevation data. */
    val riseMeters: Double?,
    val elevationEffect: Double,
    /** Positive = into your face. */
    val headwindMs: Double,
    /** Positive = blowing from your right to your left (the ball drifts left). */
    val crosswindFromRightMs: Double,
    val windEffect: Double,
    val temperatureEffect: Double,
) {
    val playsMeters: Double get() = actualMeters + elevationEffect + windEffect + temperatureEffect
    val adjusted: Boolean get() = riseMeters != null || windEffect != 0.0 || temperatureEffect != 0.0

    /** Ground distance a club travels in these conditions, per meter of its normal distance. */
    val conditionFactor: Double get() = if (playsMeters <= 0) 1.0 else actualMeters / playsMeters
}

/**
 * Golf rules of thumb, deliberately simple and listed here so they're easy to tune:
 *  - Elevation: about one meter longer per meter uphill (shorter downhill).
 *  - Wind: into the wind costs ~1% per mph (2.2% per m/s); downwind gains about half that.
 *  - Temperature: ~0.12% per °C away from 21 °C (cold air is denser, the ball goes shorter).
 * Altitude isn't adjusted: your club distances were recorded at the altitude you play.
 */
object PlaysLikeCalculator {
    const val ELEVATION_FACTOR = 1.0
    const val HEADWIND_PER_MS = 0.0224
    const val TAILWIND_PER_MS = 0.0112
    const val TEMP_PER_DEGREE = 0.0012
    const val REFERENCE_TEMP_C = 21.0

    fun compute(
        player: GeoPoint,
        target: GeoPoint,
        elevation: ElevationGrid?,
        conditions: Conditions?,
    ): PlaysLike {
        val d = Geo.distance(player, target)
        val rise = elevation?.let { g ->
            val a = g.at(player)
            val b = g.at(target)
            if (a != null && b != null) b - a else null
        }
        val elevationEffect = (rise ?: 0.0) * ELEVATION_FACTOR

        var head = 0.0
        var cross = 0.0
        var windEffect = 0.0
        var tempEffect = 0.0
        if (conditions != null && d > 1.0) {
            val shotBearing = Geo.bearing(player, target)
            val rel = (conditions.windFromDeg - shotBearing) * PI / 180
            head = conditions.windSpeedMs * cos(rel)
            cross = conditions.windSpeedMs * sin(rel)
            windEffect = d * if (head >= 0) head * HEADWIND_PER_MS else head * TAILWIND_PER_MS
            tempEffect = d * TEMP_PER_DEGREE * (REFERENCE_TEMP_C - conditions.temperatureC)
        }
        return PlaysLike(d, rise, elevationEffect, head, cross, windEffect, tempEffect)
    }
}
