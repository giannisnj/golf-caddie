package com.golfcaddie.core

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import kotlinx.serialization.json.putJsonObject
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/** Small hand-built OSM layouts for mapping quirks found in real data. Coordinates are meters east/north. */
class ParserEdgeCasesTest {
    private val origin = GeoPoint(-33.9, 151.2)
    private fun p(east: Double, north: Double) =
        Geo.destination(Geo.destination(origin, 90.0, east), 0.0, north)

    private fun rect(x0: Double, y0: Double, x1: Double, y1: Double, perSide: Int = 1): List<GeoPoint> {
        // perSide > 1 adds extra vertices, which makes an outline sort as "bigger".
        fun side(ax: Double, ay: Double, bx: Double, by: Double) =
            (0 until perSide).map { i -> p(ax + (bx - ax) * i / perSide, ay + (by - ay) * i / perSide) }
        val ring = side(x0, y0, x1, y0) + side(x1, y0, x1, y1) + side(x1, y1, x0, y1) + side(x0, y1, x0, y0)
        return ring + ring.first()
    }

    private fun way(id: Long, tags: Map<String, String>, pts: List<GeoPoint>): JsonObject = buildJsonObject {
        put("type", "way")
        put("id", id)
        putJsonObject("tags") { tags.forEach { (k, v) -> put(k, v) } }
        putJsonArray("geometry") { pts.forEach { addJsonObject { put("lat", it.lat); put("lon", it.lon) } } }
    }

    private fun overpass(vararg elements: JsonObject) = buildJsonObject {
        putJsonArray("elements") { elements.forEach { add(it) } }
    }.toString()

    private fun hole(id: Long, number: Int, tee: GeoPoint, green: GeoPoint) =
        Hole(id = id, number = number, line = listOf(tee, green))

    @Test
    fun loopsAreChainedByProximityNotByOsmId() {
        // 18-hole course along y=0; a separate 9-hole loop 1 km north with LOWER ids.
        val a = (1..18).map { k -> hole(100L + k, k, p(k * 200.0, 0.0), p(k * 200.0 + 150, 0.0)) }
        val b = (1..9).map { k -> hole(k.toLong(), k, p(k * 200.0, 1000.0), p(k * 200.0 + 150, 1000.0)) }
        val order = Overpass.playingOrder(b + a)
        assertEquals(a.map { it.id } + b.map { it.id }, order.map { it.id })
    }

    @Test
    fun strayHoleJoinsTheNearestCourseNotTheBiggest() {
        val json = overpass(
            // Big course (more outline vertices, so it is considered first) and a small one.
            way(1, mapOf("leisure" to "golf_course", "name" to "Big"), rect(0.0, 0.0, 400.0, 400.0, perSide = 5)),
            way(2, mapOf("leisure" to "golf_course", "name" to "Small"), rect(600.0, 0.0, 900.0, 400.0)),
            way(10, mapOf("golf" to "hole", "ref" to "1"), listOf(p(380.0, 50.0), p(380.0, 350.0))),
            way(20, mapOf("golf" to "hole", "ref" to "1"), listOf(p(700.0, 50.0), p(700.0, 350.0))),
            // Stray hole 2 of Small, outside both outlines: tee 120 m from Small's green, 200 m from Big's.
            way(21, mapOf("golf" to "hole", "ref" to "2"), listOf(p(580.0, 350.0), p(580.0, 600.0))),
        )
        val courses = Overpass.parse(json).associateBy { it.name }
        assertEquals(listOf(20L, 21L), courses.getValue("Small").holes.map { it.id })
        assertEquals(listOf(10L), courses.getValue("Big").holes.map { it.id })
    }

    @Test
    fun lakesMappedAsRelationsAreDownloaded() {
        val query = Overpass.queryAround(origin)
        assertTrue("""relation["natural"="water"]""" in query)
        assertTrue("""relation["golf"~""" in query)
    }

    @Test
    fun lakeMappedAsRelationIsAHazard() {
        val lake = buildJsonObject {
            put("type", "relation")
            put("id", 50)
            putJsonObject("tags") { put("type", "multipolygon"); put("natural", "water") }
            putJsonArray("members") {
                addJsonObject {
                    put("type", "way"); put("ref", 51); put("role", "outer")
                    putJsonArray("geometry") {
                        rect(-50.0, 100.0, 50.0, 140.0).forEach { addJsonObject { put("lat", it.lat); put("lon", it.lon) } }
                    }
                }
            }
        }
        val course = Overpass.parse(
            overpass(way(1, mapOf("golf" to "hole", "ref" to "1"), listOf(p(0.0, 0.0), p(0.0, 200.0))), lake),
        ).single()
        val water = Caddie.situation(course, course.holes.single(), p(0.0, 0.0)).hazards.single()
        assertEquals(HazardType.WATER, water.type)
        assertEquals(100.0, water.reachMeters, 1.0)
        assertEquals(140.0, water.carryMeters, 1.0)
    }

    @Test
    fun longShorelineBesideTheShotIsReported() {
        // Lake 8 m right of the line; its corners are 50 m behind and 100 m past the green.
        val course = Overpass.parse(
            overpass(
                way(1, mapOf("golf" to "hole", "ref" to "1"), listOf(p(0.0, 0.0), p(0.0, 300.0))),
                way(2, mapOf("natural" to "water"), rect(8.0, -50.0, 60.0, 400.0)),
            ),
        ).single()
        val water = Caddie.situation(course, course.holes.single(), p(0.0, 0.0)).hazards.single()
        assertEquals(HazardType.WATER, water.type)
        assertTrue(water.carryMeters >= 300.0)
        assertEquals(false, water.onLine)
        assertEquals(1, water.side, "the lake is on the right as you face the green")
    }

    @Test
    fun timedOutQueryIsAnErrorNotAnEmptyCourseList() {
        val json = """{"elements":[],"remark":"runtime error: Query timed out in \"query\" at line 3 after 61 seconds."}"""
        assertFailsWith<Overpass.QueryFailed> { Overpass.parse(json) }
    }
}
