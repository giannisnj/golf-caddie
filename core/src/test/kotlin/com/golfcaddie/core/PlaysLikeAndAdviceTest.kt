package com.golfcaddie.core

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PlaysLikeAndAdviceTest {
    private val origin = GeoPoint(-35.3, 149.1)
    private fun p(east: Double, north: Double) = Geo.destination(Geo.destination(origin, 90.0, east), 0.0, north)

    /** A 300 x 300 m grid whose height rises 1 m for every 10 m north. */
    private val slope = ElevationGrid(
        south = origin.lat, west = origin.lon,
        stepLat = p(0.0, 100.0).lat - origin.lat, stepLon = p(100.0, 0.0).lon - origin.lon,
        rows = 4, cols = 4,
        meters = (0 until 4).flatMap { r -> List(4) { 100.0 + r * 10.0 } },
    )

    // ---- Elevation & plays-like ---------------------------------------------------------------

    @Test
    fun elevationGridInterpolates() {
        assertEquals(100.0, slope.at(origin)!!, 0.01)
        assertEquals(115.0, slope.at(p(50.0, 150.0))!!, 0.1)
        assertNull(slope.at(p(0.0, -50.0)), "outside the grid")
    }

    @Test
    fun uphillPlaysLongerDownhillShorter() {
        val up = PlaysLikeCalculator.compute(p(150.0, 0.0), p(150.0, 150.0), slope, null)
        assertEquals(15.0, up.riseMeters!!, 0.1)
        assertEquals(150.0 + 15.0, up.playsMeters, 0.5)
        val down = PlaysLikeCalculator.compute(p(150.0, 150.0), p(150.0, 0.0), slope, null)
        assertEquals(150.0 - 15.0, down.playsMeters, 0.5)
    }

    @Test
    fun windAndTemperature() {
        val tee = p(0.0, 0.0)
        val green = p(0.0, 150.0) // shot heads due north
        fun plays(windFrom: Double, speed: Double, temp: Double = 21.0) =
            PlaysLikeCalculator.compute(tee, green, null, Conditions(speed, windFrom, temp, 0))

        val into = plays(windFrom = 0.0, speed = 5.0)
        assertEquals(5.0, into.headwindMs, 0.01)
        assertEquals(150 * 5 * PlaysLikeCalculator.HEADWIND_PER_MS, into.windEffect, 0.2)
        val down = plays(windFrom = 180.0, speed = 5.0)
        assertEquals(-150 * 5 * PlaysLikeCalculator.TAILWIND_PER_MS, down.windEffect, 0.2)
        assertTrue(into.windEffect > -down.windEffect, "a headwind hurts more than a tailwind helps")

        val fromRight = plays(windFrom = 90.0, speed = 5.0)
        assertEquals(5.0, fromRight.crosswindFromRightMs, 0.01)
        assertEquals(0.0, fromRight.windEffect, 0.2)

        val cold = plays(windFrom = 0.0, speed = 0.0, temp = 11.0)
        assertEquals(150 * 10 * PlaysLikeCalculator.TEMP_PER_DEGREE, cold.temperatureEffect, 0.01)
    }

    @Test
    fun noDataMeansNoAdjustment() {
        val pl = PlaysLikeCalculator.compute(p(0.0, 0.0), p(0.0, 150.0), null, null)
        assertEquals(pl.actualMeters, pl.playsMeters, 0.001)
        assertEquals(false, pl.adjusted)
        assertEquals(1.0, pl.conditionFactor, 1e-9)
    }

    @Test
    fun realMooreParkTerrain() {
        // Heights fetched from Open-Meteo for Moore Park (28-54 m above sea level).
        val course = Overpass.parse(javaClass.getResource("/osm/moore_park_sydney.json")!!.readText()).single()
        val grid = Json.decodeFromString(ElevationGrid.serializer(), javaClass.getResource("/osm/moore_park_elevation.json")!!.readText())
        for (h in course.holes) {
            assertNotNull(grid.at(h.tee), "tee of hole ${h.number} inside the grid")
            assertNotNull(grid.at(h.greenCenter), "green of hole ${h.number} inside the grid")
        }
        val rises = course.holes.map { grid.at(it.greenCenter)!! - grid.at(it.tee)!! }
        assertTrue(rises.all { kotlin.math.abs(it) < 30 }, "plausible tee-to-green rises: $rises")
    }

    // ---- Club spread ------------------------------------------------------------------------

    private fun shot(id: Int, club: String, m: Double, side: Double? = null): ShotRecord {
        val start = p(0.0, 0.0)
        return ShotRecord(
            "$club-$id", club, m, 1000L + id,
            start = side?.let { start },
            end = side?.let { p(it, m) },
            aim = side?.let { p(0.0, 200.0) },
        )
    }

    private val sevenIron = Club("7i", "7 Iron", ClubType.IRON, 130.0)

    @Test
    fun rangeComesFromYourShotsOnceThereAreFive() {
        val few = ClubStats.of(sevenIron, listOf(130.0, 140.0, 135.0).mapIndexed { i, m -> shot(i, "7i", m) })
        assertEquals(false, few.rangeFromShots)
        assertEquals(135.0 * 0.94, few.lowMeters, 0.01)

        val many = ClubStats.of(sevenIron, listOf(120.0, 130.0, 135.0, 140.0, 150.0).mapIndexed { i, m -> shot(i, "7i", m) })
        assertEquals(true, many.rangeFromShots)
        assertEquals(128.0, many.lowMeters, 0.01) // 20th percentile
        assertEquals(142.0, many.highMeters, 0.01) // 80th percentile
    }

    @Test
    fun leftRightTendencyFromWalkedShots() {
        val shots = listOf(8.0, 5.0, 11.0).mapIndexed { i, side -> shot(i, "7i", 140.0, side) }
        assertEquals(8.0, ClubStats.of(sevenIron, shots).sideMeters!!, 0.5) // right of the aim line
        val left = listOf(-6.0, -6.0, -6.0).mapIndexed { i, side -> shot(i, "7i", 140.0, side) }
        assertEquals(-6.0, ClubStats.of(sevenIron, left).sideMeters!!, 0.5)
        assertNull(ClubStats.of(sevenIron, shots.take(2)).sideMeters, "needs 3 walked shots")
    }

    // ---- Aim -----------------------------------------------------------------------------------

    @Test
    fun aimFollowsTheDoglegUntilTheGreenIsInRange() {
        // Dogleg right: 250 m north, then 150 m east.
        val hole = Hole(1, 1, line = listOf(p(0.0, 0.0), p(0.0, 250.0), p(150.0, 250.0)))
        val teeAim = Caddie.aimPoint(hole, p(0.0, 0.0), clubMeters = 220.0)
        assertEquals(0.0, Geo.toLocal(origin, teeAim).first, 1.0) // down the fairway, not across the corner
        assertEquals(220.0, Geo.toLocal(origin, teeAim).second, 1.0)
        val approachAim = Caddie.aimPoint(hole, p(0.0, 220.0), clubMeters = 160.0)
        assertEquals(hole.greenCenter, approachAim)
    }

    // ---- Hazard advice ---------------------------------------------------------------------------

    private fun bagWith(vararg clubs: Club) = Bag(clubs.toList())
    private fun situationWith(vararg hazards: HazardOnLine, center: Double = 150.0) = Situation(
        hole = Hole(1, 1, line = listOf(origin, p(0.0, center))),
        player = origin,
        green = GreenDistances(center - 12, center, center + 12, null),
        onGreen = false,
        hazards = hazards.toList(),
        offLineMeters = 0.0,
    )

    private val bag = bagWith(
        Club("5i", "5 Iron", ClubType.IRON, 170.0),
        Club("6i", "6 Iron", ClubType.IRON, 160.0),
        Club("7i", "7 Iron", ClubType.IRON, 150.0),
        Club("8i", "8 Iron", ClubType.IRON, 140.0),
        Club("9i", "9 Iron", ClubType.IRON, 130.0),
        Club("pw", "PW", ClubType.WEDGE, 110.0),
        Club("sw", "SW", ClubType.WEDGE, 90.0),
        Club("putter", "Putter", ClubType.PUTTER, 0.0),
    )

    @Test
    fun waterJustShortOfTheGreenIsFlaggedWithACarryAndALayUp() {
        // Water from 120 to 138 m; green middle at 150 m. The 7 Iron (carry ~140) gets wet about
        // 4 times in 10; the 6 Iron still 15%; the 5 Iron carries it 95% of the time. A PW
        // still runs into it 10% of the time, so the safe lay-up is the SW.
        val water = HazardOnLine(HazardType.WATER, 120.0, 138.0)
        val s = situationWith(water)
        val rec = ClubRecommender.recommend(150.0, bag)
        assertEquals("7i", rec.club!!.club.id)
        val advice = HazardAdvisor.advise(s, rec, bag)
        val inPlay = advice.filterIsInstance<Advice.InPlay>().single()
        assertTrue(inPlay.chance >= HazardAdvisor.WARN_CHANCE, "chance ${inPlay.chance}")
        assertEquals("5i", advice.filterIsInstance<Advice.CarryWith>().single().club.club.id)
        assertEquals("sw", advice.filterIsInstance<Advice.LayUpWith>().single().club.club.id)
    }

    @Test
    fun hazardsComfortablyCarriedOrShortOfAreNotMentioned() {
        val s = situationWith(
            HazardOnLine(HazardType.BUNKER, 40.0, 60.0), // easily carried
            HazardOnLine(HazardType.WATER, 200.0, 230.0), // well past the green
        )
        assertTrue(HazardAdvisor.advise(s, ClubRecommender.recommend(150.0, bag), bag).isEmpty())
    }

    @Test
    fun aBunkerBesideTheLineGetsAWarningButNoCarryOrLayUp() {
        val beside = HazardOnLine(HazardType.WATER, 120.0, 138.0, onLine = false, side = 1)
        // Make it likely enough to warn about: a big lake running alongside.
        val advice = HazardAdvisor.advise(situationWith(beside.copy(reachMeters = 100.0, carryMeters = 160.0)), ClubRecommender.recommend(150.0, bag), bag)
        assertTrue(advice.single() is Advice.InPlay, "$advice")
    }

    @Test
    fun aBunkerBesideTheLineMattersLessThanOneAcrossIt() {
        val across = HazardOnLine(HazardType.BUNKER, 135.0, 145.0, onLine = true)
        val beside = across.copy(onLine = false)
        val sevenIron = ClubStats.of(bag.clubs.single { it.id == "7i" }, emptyList())
        assertTrue(HazardAdvisor.chanceInHazard(sevenIron, beside) < HazardAdvisor.chanceInHazard(sevenIron, across))
    }

    @Test
    fun windAgainstMakesTheSameWaterMoreDangerous() {
        val water = HazardOnLine(HazardType.WATER, 120.0, 132.0)
        val sevenIron = ClubStats.of(bag.clubs.single { it.id == "7i" }, emptyList())
        val calm = HazardAdvisor.chanceInHazard(sevenIron, water, factor = 1.0)
        val intoWind = HazardAdvisor.chanceInHazard(sevenIron, water, factor = 0.9)
        assertTrue(intoWind > calm)
    }

    @Test
    fun watchStateRoundTrips() {
        val state = WatchState(hole = "Hole 7 · Par 3", center = "134", clubs = listOf(WatchClub("8i", "8 Iron")))
        assertEquals(state, WatchState.decode(state.encode()))
    }
}
