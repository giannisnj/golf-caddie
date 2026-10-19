package com.golfcaddie.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Real OpenStreetMap data (© OpenStreetMap contributors, ODbL), downloaded October 2026.
 * Each one caught a bug the synthetic course couldn't.
 */
class RealCoursesTest {
    private fun load(name: String) = Overpass.parse(javaClass.getResource("/osm/$name")!!.readText())

    /** Every hole's tee, and spots 150 m and 60 m out, must be detected as that hole. */
    private fun assertDetectsEveryHole(course: Course) {
        var previous: Long? = null
        for (h in course.holes) {
            val spots = listOf(Simulator.atTee(h)) +
                listOf(150.0, 60.0).filter { it < h.lengthMeters }.map { Simulator.onLine(h, it) }
            for (p in spots) {
                val found = Caddie.locateHole(course, p, previous)!!
                assertEquals(h.id, found.id, "${course.name} hole ${h.number} (${h.name})")
                previous = found.id
            }
        }
    }

    @Test
    fun mooreParkHolesOutsideTheMappedBoundaryStillCount() {
        // OSM's outline misses holes 12-15 by up to 190 m.
        val course = load("moore_park_sydney.json").single()
        assertEquals("Moore Park Golf Club", course.name)
        assertEquals((1..18).toList(), course.holes.map { it.number })
        assertDetectsEveryHole(course)
    }

    @Test
    fun domatEmsSplitsTwentySevenHolesIntoLoops() {
        // Mapped twice (clubhouse way + multipolygon with two outers); 18-hole course plus a 9-hole loop.
        val course = load("domat_ems_switzerland.json").single()
        assertEquals(27, course.holes.size)
        assertEquals((1..18).toList() + (1..9).toList(), course.holes.map { it.number })
        assertEquals("Tuma' Arsa", course.holes[0].name)
        assertEquals("Las Sorts", course.holes[18].name)
        assertTrue(course.holes.count { it.pin != null } >= 20, "mapped flags are used")
        assertDetectsEveryHole(course)
    }
}
