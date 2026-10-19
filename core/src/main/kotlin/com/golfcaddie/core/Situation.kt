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
    /** What the club recommendation should cover: the pin if mapped, otherwise the green center. */
    val targetMeters: Double get() = green.pin ?: green.center
}

object Caddie {
    /** Within this distance of a hole's line you count as playing that hole. */
    private const val HOLE_SWITCH_MARGIN_M = 15.0
    /** Hazards whose far edge is closer than this are behind you, not in play. */
    private const val MIN_HAZARD_CARRY_M = 5.0

    /**
     * Works out which hole the player is on. Prefers [currentHole] unless another hole is
     * clearly closer, so walking past a neighbouring fairway doesn't flip the hole.
     */
    fun locateHole(course: Course, player: GeoPoint, currentHole: Int? = null): Hole? {
        if (course.holes.isEmpty()) return null
        fun score(h: Hole): Double =
            if (h.green.size >= 3 && Geo.contains(h.green, player)) 0.0
            else Geo.distanceToPolyline(player, h.line)

        val best = course.holes.minBy(::score)
        val current = course.holes.firstOrNull { it.number == currentHole } ?: return best
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
        return course.hazards.mapNotNull { hz ->
            // Already in it (or standing on its edge): nothing to carry.
            if (Geo.contains(hz.polygon, player)) return@mapNotNull null
            // Along-track / cross-track position of every hazard vertex.
            val pts = hz.polygon.map { Geo.toLocal(player, it) }.map { (x, y) ->
                (x * ux + y * uy) to (-x * uy + y * ux)
            }
            val hit = crossings(player, target, hz.polygon)
            when {
                hit != null && hit.second > MIN_HAZARD_CARRY_M && hit.first < targetDist + 30 ->
                    HazardOnLine(hz.type, hit.first, hit.second)
                else -> {
                    val near = pts.filter { (along, cross) -> along > 0 && along < targetDist + 30 && kotlin.math.abs(cross) <= corridorMeters }
                    if (near.isEmpty() || near.maxOf { it.first } <= MIN_HAZARD_CARRY_M) null
                    else HazardOnLine(hz.type, near.minOf { it.first }, near.maxOf { it.first })
                }
            }
        }.sortedBy { it.reachMeters }
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
