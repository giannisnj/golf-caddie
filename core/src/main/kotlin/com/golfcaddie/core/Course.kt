package com.golfcaddie.core

import kotlinx.serialization.Serializable

@Serializable
data class Course(
    val id: Long,
    val name: String,
    /** Outline(s) of the course property; empty when OSM has no boundary. */
    val areas: List<List<GeoPoint>>,
    /** In playing order; a 27-hole club comes out as loops, e.g. 1-18 then the other 1-9. */
    val holes: List<Hole>,
    val hazards: List<Hazard>,
) {
    fun contains(p: GeoPoint): Boolean = areas.any { Geo.contains(it, p) }

    val center: GeoPoint
        get() = when {
            areas.isNotEmpty() -> Geo.centroid(areas.maxBy { it.size })
            holes.isNotEmpty() -> GeoPoint(
                holes.flatMap { it.line }.map { it.lat }.average(),
                holes.flatMap { it.line }.map { it.lon }.average(),
            )
            else -> GeoPoint(0.0, 0.0)
        }
}

@Serializable
data class Hole(
    /** OSM way id: unique, unlike [number] at clubs with several loops. */
    val id: Long,
    /** Hole number from the OSM `ref` tag (1..18). */
    val number: Int,
    /** Hole name, if the course names its holes. */
    val name: String? = null,
    val par: Int? = null,
    val handicap: Int? = null,
    /** Playing line from tee to green as mapped in OSM (`golf=hole` way). */
    val line: List<GeoPoint>,
    /** Green outline (`golf=green`), when mapped. */
    val green: List<GeoPoint> = emptyList(),
    /** Centers of the tee boxes (`golf=tee`) belonging to this hole. */
    val tees: List<GeoPoint> = emptyList(),
    /** Flag position (`golf=pin`) if someone mapped it; usually absent. */
    val pin: GeoPoint? = null,
) {
    val tee: GeoPoint get() = line.first()

    /** Center of the green, the standard target when the pin position is unknown. */
    val greenCenter: GeoPoint
        get() = if (green.size >= 3) Geo.centroid(green) else line.last()

    val lengthMeters: Double get() = Geo.length(line)
}

@Serializable
enum class HazardType { BUNKER, WATER, LATERAL_WATER }

@Serializable
data class Hazard(val type: HazardType, val polygon: List<GeoPoint>)
