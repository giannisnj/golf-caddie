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

    /**
     * Overpass QL for the golf features the app uses within [radiusMeters] of a point.
     * Rough and cart paths are left out: they're a lot of data and aren't used. Fairways and
     * woods are kept for the offline course plan.
     * Lakes mapped as multipolygons are included; rivers are not (they can be huge).
     */
    fun queryAround(center: GeoPoint, radiusMeters: Int = 2500): String {
        val around = "(around:$radiusMeters,${center.lat},${center.lon})"
        return """
            [out:json][timeout:60];
            (
              way["leisure"="golf_course"]$around;
              relation["leisure"="golf_course"]$around;
              way["golf"~"^(hole|green|tee|fairway|bunker|water_hazard|lateral_water_hazard)$"]$around;
              relation["golf"~"^(green|bunker|water_hazard|lateral_water_hazard)$"]$around;
              node["golf"="pin"]$around;
              way["natural"="water"]$around;
              relation["natural"="water"]["water"!~"^(river|canal|stream)$"]$around;
              way["natural"="wood"]$around;
              way["landuse"="forest"]$around;
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

    /** Overpass answered HTTP 200 but the query failed (usually a timeout on a busy server). */
    class QueryFailed(message: String) : Exception(message)

    /**
     * Parse an Overpass `out geom` JSON response into courses, nearest-first to [near] if given.
     * Throws [QueryFailed] when the server reports a runtime error, whose results are partial.
     */
    fun parse(responseJson: String, near: GeoPoint? = null): List<Course> {
        val root = json.parseToJsonElement(responseJson).jsonObject
        root["remark"]?.jsonPrimitive?.content?.let { remark ->
            if ("error" in remark || "timed out" in remark) throw QueryFailed(remark)
        }
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

        val fairways = features.filter { it.tags["golf"] == "fairway" && it.firstRing.size >= 3 }.map { it.firstRing }
        val woods = features.filter {
            (it.tags["natural"] == "wood" || it.tags["landuse"] == "forest") && it.firstRing.size >= 3
        }.map { it.firstRing }

        val holes = holeWays.mapIndexed { index, f -> buildHole(f, index, greens, tees, pins) }

        val courses = if (courseAreas.isEmpty()) {
            if (holes.isEmpty()) emptyList()
            else listOf(Course(0, "Golf course", emptyList(), playingOrder(holes), hazards, fairways, nearHoles(woods, holes)))
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
            // joins the course it connects to most closely: its tee near one of their greens, or
            // its green near one of their tees. Closest links first, so chains grow outward.
            fun link(h: Hole, course: List<Hole>): Double = course.minOfOrNull {
                minOf(Geo.distance(it.greenCenter, h.tee), Geo.distance(h.greenCenter, it.tee))
            } ?: Double.POSITIVE_INFINITY
            while (unclaimed.isNotEmpty()) {
                val (h, i, d) = unclaimed.flatMap { h -> members.indices.map { i -> Triple(h, i, link(h, members[i])) } }
                    .minBy { it.third }
                if (d >= LINK_M) break
                members[i] += h
                unclaimed -= h
            }
            areas.mapIndexed { i, (area, rings) ->
                val courseHoles = members[i]
                Course(
                    id = area.id,
                    name = area.tags["name"] ?: "Golf course",
                    areas = rings,
                    holes = playingOrder(courseHoles),
                    // Any part inside the outline or near a hole: a big lake's centre can be far
                    // away while its shore runs right beside a fairway.
                    hazards = hazards.filter { h ->
                        h.polygon.any { p ->
                            rings.any { Geo.contains(it, p) } || courseHoles.any { Geo.distanceToPolyline(p, it.line) < 60.0 }
                        }
                    },
                    fairways = fairways.filter { f -> courseHoles.any { h -> Geo.contains(f, h.line[h.line.size / 2]) || f.any { Geo.distanceToPolyline(it, h.line) < 40.0 } } },
                    woods = nearHoles(woods, courseHoles),
                )
            }.filter { it.holes.isNotEmpty() } +
                listOfNotNull(unclaimed.takeIf { it.isNotEmpty() }?.let {
                    Course(-1, "Golf course", emptyList(), playingOrder(it), hazards, fairways, nearHoles(woods, it))
                })
        }

        return if (near == null) courses else courses.sortedBy { c ->
            if (c.contains(near)) 0.0 else Geo.distance(near, c.center)
        }
    }

    /** Areas with a vertex within 80 m of a hole line: tree lines beside fairways, not a park across town. */
    private fun nearHoles(areas: List<List<GeoPoint>>, holes: List<Hole>): List<List<GeoPoint>> =
        areas.filter { a -> a.any { p -> holes.any { Geo.distanceToPolyline(p, it.line) < 80.0 } } }

    /**
     * Orders holes the way they're played. With unique numbers that's just 1..18. Clubs with
     * several loops repeat numbers, so each hole N is linked to the hole N+1 whose tee is
     * nearest its green, closest pairs first. Loops come out longest first (18 before 9).
     */
    internal fun playingOrder(holes: List<Hole>): List<Hole> {
        if (holes.map { it.number }.toSet().size == holes.size) return holes.sortedBy { it.number }
        val next = mutableMapOf<Long, Hole>()
        val hasPrevious = mutableSetOf<Long>()
        holes.flatMap { a -> holes.filter { it.number == a.number + 1 }.map { b -> Triple(a, b, Geo.distance(a.greenCenter, b.tee)) } }
            .sortedBy { it.third }
            .forEach { (a, b, _) ->
                if (a.id !in next && b.id !in hasPrevious) {
                    next[a.id] = b
                    hasPrevious += b.id
                }
            }
        val loops = holes.filter { it.id !in hasPrevious }.map { start ->
            generateSequence(start) { next[it.id] }.toList()
        }
        return loops.sortedWith(compareBy({ it.first().number }, { -it.size }, { it.first().id })).flatten()
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
