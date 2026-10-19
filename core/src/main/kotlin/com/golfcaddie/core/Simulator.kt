package com.golfcaddie.core

/**
 * Fake positions on a course, for testing without walking it. Used by unit tests
 * and by the app's simulation mode.
 */
object Simulator {
    /** Standing on the tee of [hole]. */
    fun atTee(hole: Hole): GeoPoint = hole.tee

    /** [metersFromGreen] short of the green center, measured back along the hole's line. */
    fun onLine(hole: Hole, metersFromGreen: Double): GeoPoint {
        val total = hole.lengthMeters
        return Geo.pointAlong(hole.line, (total - metersFromGreen).coerceIn(0.0, total))
    }

    /** [metersFromGreen] short along the line, then [sideMeters] to the right (negative = left). */
    fun offLine(hole: Hole, metersFromGreen: Double, sideMeters: Double): GeoPoint {
        val p = onLine(hole, metersFromGreen)
        val heading = Geo.bearing(p, hole.greenCenter)
        return Geo.destination(p, heading + 90.0, sideMeters)
    }

    /** One step of [meters] from [from] toward the green of [hole]. */
    fun stepTowardGreen(from: GeoPoint, hole: Hole, meters: Double): GeoPoint {
        val remaining = Geo.distance(from, hole.greenCenter)
        if (remaining <= meters) return hole.greenCenter
        return Geo.destination(from, Geo.bearing(from, hole.greenCenter), meters)
    }
}
