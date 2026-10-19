package com.golfcaddie.core

import kotlin.math.sqrt

/** Distances from the player to the green, all in meters. */
data class GreenDistances(
    val front: Double,
    val center: Double,
    val back: Double,
    /** Distance to the mapped flag, when OSM has one. */
    val pin: Double?,
)

/** A bunker or water hazard on the line between the player and the green. */
data class HazardOnLine(
    val type: HazardType,
    /** Distance to the near edge (what you must stay short of). */
    val reachMeters: Double,
    /** Distance to the far edge (what you must carry). */
    val carryMeters: Double,
)

data class Situation(
    val hole: Hole,
    val player: GeoPoint,
    val green: GreenDistances,
    val onGreen: Boolean,
    val hazards: List<HazardOnLine>,
    /** How far off the mapped tee-to-green line the player is (meters). */
    val offLineMeters: Double,
) {
    /**
     * What the club recommendation should cover: the middle of the green. A flag mapped in OSM
     * is wherever it stood on the day it was traced, not where it is today.
     */
    val targetMeters: Double get() = green.center
}

object Caddie {
    /** Within this distance of a hole's line you count as playing that hole. */
    private const val HOLE_SWITCH_MARGIN_M = 15.0
    /** Hazards whose far edge is closer than this are behind you, not in play. */
    private const val MIN_HAZARD_CARRY_M = 5.0

    /**
     * Works out which hole the player is on. Prefers [currentHoleId] unless another hole is
     * clearly closer, so walking past a neighbouring fairway doesn't flip the hole.
     */
    fun locateHole(course: Course, player: GeoPoint, currentHoleId: Long? = null): Hole? {
        if (course.holes.isEmpty()) return null
        fun score(h: Hole): Double =
            if (h.green.size >= 3 && Geo.contains(h.green, player)) 0.0
            else Geo.distanceToPolyline(player, h.line)

        val best = course.holes.minBy(::score)
        val current = course.holes.firstOrNull { it.id == currentHoleId } ?: return best
        return if (score(current) <= score(best) + HOLE_SWITCH_MARGIN_M) current else best
    }

    fun situation(course: Course, hole: Hole, player: GeoPoint): Situation {
        val center = hole.greenCenter
        val centerDist = Geo.distance(player, center)
        val onGreen = hole.green.size >= 3 && Geo.contains(hole.green, player)

        val (front, back) = if (hole.green.size >= 3 && !onGreen) {
            crossings(player, center, hole.green)?.let { it.first to it.second } ?: (centerDist to centerDist)
        } else {
            centerDist to centerDist
        }

        return Situation(
            hole = hole,
            player = player,
            green = GreenDistances(front, centerDist, back, hole.pin?.let { Geo.distance(player, it) }),
            onGreen = onGreen,
            hazards = if (onGreen) emptyList() else hazardsOnLine(course, player, center),
            offLineMeters = Geo.distanceToPolyline(player, hole.line),
        )
    }

    /**
     * Hazards the ball would fly over or into on a straight line to the green,
     * nearest first. A corridor of [corridorMeters] either side is considered.
     */
    fun hazardsOnLine(
        course: Course,
        player: GeoPoint,
        target: GeoPoint,
        corridorMeters: Double = 12.0,
    ): List<HazardOnLine> {
        val targetDist = Geo.distance(player, target)
        val (tx, ty) = Geo.toLocal(player, target)
        val ux = tx / targetDist
        val uy = ty / targetDist
        val limit = targetDist + 30
        return course.hazards.mapNotNull { hz ->
            // Already in it (or standing on its edge): nothing to carry.
            if (Geo.contains(hz.polygon, player)) return@mapNotNull null
            // Hazard outline in along-track / cross-track coordinates.
            val pts = hz.polygon.map { Geo.toLocal(player, it) }.map { (x, y) ->
                (x * ux + y * uy) to (-x * uy + y * ux)
            }
            // Clip every edge to the corridor ahead (not just its corners, so a long straight
            // shoreline running beside the shot still counts).
            val alongs = mutableListOf<Double>()
            for (i in pts.indices) {
                val (a1, c1) = pts[i]
                val (a2, c2) = pts[(i + 1) % pts.size]
                clipToBox(a1, c1, a2, c2, 0.0, limit, -corridorMeters, corridorMeters)?.let { alongs += it }
            }
            if (alongs.isEmpty() || alongs.max() <= MIN_HAZARD_CARRY_M) null
            else HazardOnLine(hz.type, alongs.min(), alongs.max())
        }.sortedBy { it.reachMeters }
    }

    /**
     * Liang-Barsky clip of segment (a1,c1)-(a2,c2) to the box along in [aMin,aMax],
     * cross in [cMin,cMax]. Returns the along-track values of the clipped ends, or null.
     */
    private fun clipToBox(
        a1: Double, c1: Double, a2: Double, c2: Double,
        aMin: Double, aMax: Double, cMin: Double, cMax: Double,
    ): List<Double>? {
        val da = a2 - a1
        val dc = c2 - c1
        var t0 = 0.0
        var t1 = 1.0
        for ((p, q) in listOf(-da to a1 - aMin, da to aMax - a1, -dc to c1 - cMin, dc to cMax - c1)) {
            if (p == 0.0) {
                if (q < 0) return null
            } else {
                val r = q / p
                if (p < 0) t0 = maxOf(t0, r) else t1 = minOf(t1, r)
                if (t0 > t1) return null
            }
        }
        return listOf(a1 + t0 * da, a1 + t1 * da)
    }

    /**
     * Where the ray from [from] through [toward] enters and leaves [polygon]:
     * (nearest crossing, farthest crossing) in meters, or null if it misses.
     */
    internal fun crossings(from: GeoPoint, toward: GeoPoint, polygon: List<GeoPoint>): Pair<Double, Double>? {
        val (dx0, dy0) = Geo.toLocal(from, toward)
        val len = sqrt(dx0 * dx0 + dy0 * dy0)
        if (len == 0.0) return null
        val dx = dx0 / len
        val dy = dy0 / len
        val pts = polygon.map { Geo.toLocal(from, it) }
        val hits = mutableListOf<Double>()
        for (i in pts.indices) {
            val (ax, ay) = pts[i]
            val (bx, by) = pts[(i + 1) % pts.size]
            val ex = bx - ax
            val ey = by - ay
            val denom = dx * ey - dy * ex
            if (kotlin.math.abs(denom) < 1e-12) continue
            val t = (ax * ey - ay * ex) / denom // distance along the ray
            val s = (ax * dy - ay * dx) / denom // position along the edge
            if (t >= 0 && s in 0.0..1.0) hits += t
        }
        return if (hits.isEmpty()) null else hits.min() to hits.max()
    }
}
