package com.golfcaddie.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Plays a SYNTHETIC course (made-up geometry in real Overpass format, see
 * src/test/resources/synthetic_course.json) by faking the player's GPS position.
 * Hole 1: 350 m par 4 north, green r=15 m, bunker front-left, mapped pin 5 m past center.
 * Hole 2: 150 m par 3 east over water (60-90 m out). Hole 3: par 5 dogleg.
 */
class CaddieTest {
    private val course: Course = Overpass.parse(
        javaClass.getResource("/synthetic_course.json")!!.readText(),
    ).single()

    private fun hole(n: Int) = course.holes.single { it.number == n }

    @Test
    fun parsesCourse() {
        assertEquals("Test Links (synthetic)", course.name)
        assertEquals(listOf(1, 2, 3), course.holes.map { it.number })
        assertEquals(listOf(4, 3, 5), course.holes.map { it.par })
        assertTrue(course.areas.single().let { it.first() == it.last() }, "relation outers stitched into a ring")
        assertTrue(hole(1).green.size > 3)
        assertNotNull(hole(1).pin)
        assertNull(hole(2).pin)
        assertEquals(1, hole(1).tees.size)
        assertEquals(2, course.hazards.size)
    }

    @Test
    fun teeShotOnHole1() {
        val me = Simulator.atTee(hole(1))
        val h = Caddie.locateHole(course, me)!!
        assertEquals(1, h.number)
        val s = Caddie.situation(course, h, me)
        assertEquals(350.0, s.green.center, 1.0)
        assertEquals(335.0, s.green.front, 1.0)
        assertEquals(365.0, s.green.back, 1.0)
        assertEquals(355.0, s.green.pin!!, 1.0)
        assertEquals(355.0, s.targetMeters, 1.0)
    }

    @Test
    fun approachShowsBunkerShortOfGreen() {
        val me = Simulator.onLine(hole(1), metersFromGreen = 120.0)
        val s = Caddie.situation(course, hole(1), me)
        assertEquals(120.0, s.green.center, 1.0)
        val bunker = s.hazards.single()
        assertEquals(HazardType.BUNKER, bunker.type)
        // Bunker circle r=6 centered 25 m short and 3 m left of green center.
        assertTrue(bunker.reachMeters in 88.0..97.0, "reach ${bunker.reachMeters}")
        assertTrue(bunker.carryMeters in 97.0..104.0, "carry ${bunker.carryMeters}")
    }

    @Test
    fun waterCarryOnPar3() {
        val me = Simulator.atTee(hole(2))
        val s = Caddie.situation(course, Caddie.locateHole(course, me)!!, me)
        assertEquals(2, s.hole.number)
        val water = s.hazards.single()
        assertEquals(HazardType.WATER, water.type)
        assertEquals(60.0, water.reachMeters, 1.0)
        assertEquals(90.0, water.carryMeters, 1.0)
    }

    @Test
    fun hazardAtYourFeetIsNotListed() {
        // 60 m from the green on hole 2 is exactly the water's far edge.
        val me = Simulator.onLine(hole(2), metersFromGreen = 60.0)
        assertTrue(Caddie.situation(course, hole(2), me).hazards.isEmpty())
        val beforeWater = Simulator.onLine(hole(2), metersFromGreen = 100.0)
        val water = Caddie.situation(course, hole(2), beforeWater).hazards.single()
        assertEquals(10.0, water.reachMeters, 1.0)
        assertEquals(40.0, water.carryMeters, 1.0)
    }

    @Test
    fun onGreenMeansPutter() {
        val me = Simulator.onLine(hole(1), metersFromGreen = 5.0)
        val s = Caddie.situation(course, hole(1), me)
        assertTrue(s.onGreen)
        val rec = ClubRecommender.recommend(s.targetMeters, Bag.default(), s.onGreen)
        assertEquals(Recommendation.Kind.PUTT, rec.kind)
        assertEquals("putter", rec.club!!.club.id)
    }

    @Test
    fun holeDetectionSticksWithCurrentHoleNearBoundary() {
        // Hole 2's tee (50,380) sits near hole 1's green (0,350).
        val betweenHoles = Simulator.onLine(hole(1), metersFromGreen = 0.0).let { Geo.destination(it, 70.0, 30.0) }
        assertEquals(1, Caddie.locateHole(course, betweenHoles, currentHoleId = hole(1).id)!!.number)
        // Standing right on hole 2's tee, it switches.
        assertEquals(2, Caddie.locateHole(course, Simulator.atTee(hole(2)), currentHoleId = hole(1).id)!!.number)
    }

    @Test
    fun doglegUsesStraightLineToGreen() {
        val me = Simulator.atTee(hole(3))
        val s = Caddie.situation(course, Caddie.locateHole(course, me)!!, me)
        assertEquals(3, s.hole.number)
        assertEquals(430.0, s.hole.lengthMeters, 1.0) // along the dogleg
        assertEquals(Math.hypot(180.0, 250.0), s.green.center, 1.0) // as the crow flies
    }

    @Test
    fun walkingTheHoleNeverIncreasesDistance() {
        var me = Simulator.atTee(hole(1))
        var last = Double.MAX_VALUE
        repeat(30) {
            val d = Caddie.situation(course, hole(1), me).green.center
            assertTrue(d <= last + 0.01)
            last = d
            me = Simulator.stepTowardGreen(me, hole(1), 15.0)
        }
        assertEquals(0.0, last, 0.5)
    }
}
