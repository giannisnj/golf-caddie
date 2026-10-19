package com.golfcaddie.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long

/**
 * Golf course data from OpenStreetMap through the free Overpass API.
 *
 * OSM mappers draw golf courses with these tags (https://wiki.openstreetmap.org/wiki/Tag:leisure=golf_course):
 *  - `leisure=golf_course`  the course boundary
 *  - `golf=hole` + `ref`    a line from the tee to the middle of the green, with `par` / `handicap`
 *  - `golf=green`, `golf=tee`, `golf=bunker`, `golf=water_hazard`, `golf=lateral_water_hazard`
 *  - `golf=pin`             a flag position (rare)
 */
object Overpass {
    /** Public endpoints. All are free; please keep request volume low and cache results. */
    val ENDPOINTS = listOf(
        "https://overpass-api.de/api/interpreter",
        "https://overpass.kumi.systems/api/interpreter",
        "https://overpass.private.coffee/api/interpreter",
    )

    /** Overpass QL that fetches every golf feature within [radiusMeters] of a point. */
    fun queryAround(center: GeoPoint, radiusMeters: Int = 2500): String {
        val around = "(around:$radiusMeters,${center.lat},${center.lon})"
        return """
            [out:json][timeout:60];
            (
              way["leisure"="golf_course"]$around;
              relation["leisure"="golf_course"]$around;
              way["golf"]$around;
              node["golf"="pin"]$around;
              way["natural"="water"]$around;
            );
            out geom;
        """.trimIndent()
    }

    private val json = Json { ignoreUnknownKeys = true }

    /** Max gap between one hole's green and the next hole's tee when linking stray holes. */
    private const val LINK_M = 250.0

    private class Feature(
        val id: Long,
        val tags: Map<String, String>,
        /** Closed rings (areas) or a single open line. */
        val rings: List<List<GeoPoint>>,
        val node: GeoPoint? = null,
    ) {
        val firstRing get() = rings.firstOrNull().orEmpty()
    }

    /** Parse an Overpass `out geom` JSON response into courses, nearest-first to [near] if given. */
    fun parse(responseJson: String, near: GeoPoint? = null): List<Course> {
        val root = json.parseToJsonElement(responseJson).jsonObject
        val elements = root["elements"]?.jsonArray ?: return emptyList()
        val features = elements.mapNotNull { toFeature(it.jsonObject) }

        val courseAreas = features.filter { it.tags["leisure"] == "golf_course" && it.firstRing.size >= 3 }
        val holeWays = features.filter { it.tags["golf"] == "hole" && it.firstRing.size >= 2 }
        val greens = features.filter { it.tags["golf"] == "green" && it.firstRing.size >= 3 }.map { it.firstRing }
        val tees = features.filter { it.tags["golf"] == "tee" && it.firstRing.isNotEmpty() }
            .map { if (it.firstRing.size >= 3) Geo.centroid(it.firstRing) else it.firstRing.first() }
        val pins = features.mapNotNull { f -> f.node?.takeIf { f.tags["golf"] == "pin" } }
        val hazards = features.mapNotNull { f ->
            val type = when {
                f.tags["golf"] == "bunker" -> HazardType.BUNKER
                f.tags["golf"] == "water_hazard" -> HazardType.WATER
                f.tags["golf"] == "lateral_water_hazard" -> HazardType.LATERAL_WATER
                f.tags["natural"] == "water" -> HazardType.WATER
                else -> null
            }
            if (type != null && f.firstRing.size >= 3) Hazard(type, f.firstRing) else null
        }

        val holes = holeWays.mapIndexed { index, f -> buildHole(f, index, greens, tees, pins) }

        val courses = if (courseAreas.isEmpty()) {
            if (holes.isEmpty()) emptyList()
            else listOf(Course(0, "Golf course", emptyList(), playingOrder(holes), hazards))
        } else {
            // Clubs are often mapped twice (clubhouse way + multipolygon); bigger areas claim holes first.
            val areas = courseAreas.sortedByDescending { a -> a.rings.sumOf { it.size } }
                .map { a -> a to a.rings.filter { it.size >= 3 } }
            val members = areas.map { mutableListOf<Hole>() }
            val unclaimed = holes.toMutableList()
            for ((i, area) in areas.withIndex()) {
                val inside = unclaimed.filter { h ->
                    area.second.any { Geo.contains(it, h.greenCenter) || Geo.contains(it, h.tee) }
                }
                members[i] += inside
                unclaimed -= inside.toSet()
            }
            // Course outlines in OSM are sometimes drawn too small. A hole outside every outline
            // joins the course it connects to: its tee near their green, or its green near their tee.
            var changed = true
            while (changed && unclaimed.isNotEmpty()) {
                changed = false
                for (h in unclaimed.toList()) {
                    val i = members.indexOfFirst { course ->
                        course.any { Geo.distance(it.greenCenter, h.tee) < LINK_M || Geo.distance(h.greenCenter, it.tee) < LINK_M }
                    }
                    if (i >= 0) {
                        members[i] += h
                        unclaimed -= h
                        changed = true
                    }
                }
            }
            areas.mapIndexed { i, (area, rings) ->
                val courseHoles = members[i]
                Course(
                    id = area.id,
                    name = area.tags["name"] ?: "Golf course",
                    areas = rings,
                    holes = playingOrder(courseHoles),
                    hazards = hazards.filter { h ->
                        val c = Geo.centroid(h.polygon)
                        rings.any { Geo.contains(it, c) } || courseHoles.any { Geo.distanceToPolyline(c, it.line) < 60.0 }
                    },
                )
            }.filter { it.holes.isNotEmpty() } +
                listOfNotNull(unclaimed.takeIf { it.isNotEmpty() }?.let {
                    Course(-1, "Golf course", emptyList(), playingOrder(it), hazards)
                })
        }

        return if (near == null) courses else courses.sortedBy { c ->
            if (c.contains(near)) 0.0 else Geo.distance(near, c.center)
        }
    }

    /**
     * Orders holes the way they're played. With unique numbers that's just 1..18. Clubs with
     * several loops repeat numbers, so each loop is chained by walking from a green to the
     * nearest tee of the next number.
     */
    internal fun playingOrder(holes: List<Hole>): List<Hole> {
        if (holes.map { it.number }.toSet().size == holes.size) return holes.sortedBy { it.number }
        val left = holes.sortedBy { it.id }.toMutableList()
        val ordered = mutableListOf<Hole>()
        while (left.isNotEmpty()) {
            var cur = left.minBy { it.number }
            left.remove(cur)
            ordered += cur
            while (true) {
                val next = left.filter { it.number == cur.number + 1 }
                    .minByOrNull { Geo.distance(cur.greenCenter, it.tee) } ?: break
                left.remove(next)
                ordered += next
                cur = next
            }
        }
        return ordered
    }

    private fun buildHole(
        f: Feature,
        index: Int,
        greens: List<List<GeoPoint>>,
        tees: List<GeoPoint>,
        pins: List<GeoPoint>,
    ): Hole {
        val line = f.firstRing
        val end = line.last()
        val green = greens.firstOrNull { Geo.contains(it, end) }
            ?: greens.minByOrNull { Geo.distance(Geo.centroid(it), end) }
                ?.takeIf { Geo.distance(Geo.centroid(it), end) < 60.0 }
            ?: emptyList()
        val target = if (green.size >= 3) Geo.centroid(green) else end
        val pin = pins.firstOrNull { (green.size >= 3 && Geo.contains(green, it)) || Geo.distance(it, target) < 25.0 }
        // Refs are usually "7", but "Altberg 7" or "18Bis" occur too.
        val digits = Regex("\\d+")
        val number = f.tags["ref"]?.let { digits.find(it)?.value?.toIntOrNull() }
            ?: f.tags["name"]?.let { digits.find(it)?.value?.toIntOrNull() }
            ?: (index + 1)
        return Hole(
            id = f.id,
            number = number,
            name = f.tags["name"]?.takeIf { it.isNotBlank() },
            par = f.tags["par"]?.toIntOrNull(),
            handicap = f.tags["handicap"]?.toIntOrNull(),
            line = line,
            green = green,
            tees = tees.filter { Geo.distance(it, line.first()) < 60.0 },
            pin = pin,
        )
    }

    private fun toFeature(el: JsonObject): Feature? {
        val type = el["type"]?.jsonPrimitive?.content ?: return null
        val id = el["id"]?.jsonPrimitive?.long ?: return null
        val tags = el["tags"]?.jsonObject?.mapValues { it.value.jsonPrimitive.content } ?: emptyMap()
        return when (type) {
            "node" -> {
                val lat = el["lat"]?.jsonPrimitive?.double ?: return null
                val lon = el["lon"]?.jsonPrimitive?.double ?: return null
                Feature(id, tags, emptyList(), GeoPoint(lat, lon))
            }
            "way" -> Feature(id, tags, listOf(readGeometry(el["geometry"] as? JsonArray)))
            "relation" -> {
                val outers = (el["members"] as? JsonArray).orEmpty()
                    .map { it.jsonObject }
                    .filter { it["role"]?.jsonPrimitive?.content == "outer" }
                    .map { readGeometry(it["geometry"] as? JsonArray) }
                    .filter { it.isNotEmpty() }
                Feature(id, tags, stitchRings(outers).sortedByDescending { it.size })
            }
            else -> null
        }
    }

    private fun readGeometry(arr: JsonArray?): List<GeoPoint> = arr.orEmpty().mapNotNull { p ->
        val o = p as? JsonObject ?: return@mapNotNull null
        val lat = o["lat"]?.jsonPrimitive?.double ?: return@mapNotNull null
        val lon = o["lon"]?.jsonPrimitive?.double ?: return@mapNotNull null
        GeoPoint(lat, lon)
    }

    /** Joins multipolygon outer ways that share end points into closed rings. */
    internal fun stitchRings(parts: List<List<GeoPoint>>): List<List<GeoPoint>> {
        val pending = parts.map { it.toMutableList() }.toMutableList()
        val rings = mutableListOf<List<GeoPoint>>()
        while (pending.isNotEmpty()) {
            val ring = pending.removeAt(0)
            var extended = true
            while (ring.first() != ring.last() && extended) {
                extended = false
                val iter = pending.iterator()
                while (iter.hasNext()) {
                    val part = iter.next()
                    when (ring.last()) {
                        part.first() -> ring.addAll(part.drop(1))
                        part.last() -> ring.addAll(part.reversed().drop(1))
                        else -> continue
                    }
                    iter.remove()
                    extended = true
                    break
                }
            }
            rings += ring
        }
        return rings
    }
}
