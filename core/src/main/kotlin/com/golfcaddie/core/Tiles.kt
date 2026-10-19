package com.golfcaddie.core

import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.sinh
import kotlin.math.tan

/** A web map tile (the z/x/y scheme used by OSM, Esri and most tile servers). */
data class Tile(val z: Int, val x: Int, val y: Int)

object WebMercator {
    private const val HALF_WORLD_M = 20037508.342789244

    fun tileX(lon: Double, z: Int): Int = floor((lon + 180) / 360 * (1 shl z)).toInt()

    fun tileY(lat: Double, z: Int): Int {
        val r = lat * PI / 180
        return floor((1 - ln(tan(r) + 1 / cos(r)) / PI) / 2 * (1 shl z)).toInt()
    }

    /** EPSG:3857 meters (as in ArcGIS service extents) to degrees. */
    fun lonOf(x: Double): Double = x / HALF_WORLD_M * 180
    fun latOf(y: Double): Double = atan(sinh(y / HALF_WORLD_M * PI)) * 180 / PI

    /** Every tile covering [course] (holes plus [marginMeters]) at each zoom in [zooms]. */
    fun tilesFor(course: Course, zooms: IntRange, marginMeters: Double = 80.0): List<Tile> {
        val pts = course.holes.flatMap { it.line + it.green }
        if (pts.isEmpty()) return emptyList()
        val dLat = marginMeters / Geo.EARTH_RADIUS_M * 180 / PI
        val south = pts.minOf { it.lat } - dLat
        val north = pts.maxOf { it.lat } + dLat
        val dLon = dLat / cos(south * PI / 180)
        val west = pts.minOf { it.lon } - dLon
        val east = pts.maxOf { it.lon } + dLon
        return zooms.flatMap { z ->
            (tileX(west, z)..tileX(east, z)).flatMap { x ->
                (tileY(north, z)..tileY(south, z)).map { y -> Tile(z, x, y) }
            }
        }
    }
}
