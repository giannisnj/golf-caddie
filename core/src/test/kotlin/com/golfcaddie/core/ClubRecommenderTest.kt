package com.golfcaddie.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ClubRecommenderTest {
    private fun shots(clubId: String, vararg meters: Double) = meters.mapIndexed { i, m ->
        ShotRecord("$clubId-$i", clubId, m, timestampMillis = 1_000L + i)
    }

    private val bag = Bag(
        clubs = listOf(
            Club("driver", "Driver", ClubType.DRIVER, 200.0),
            Club("7i", "7 Iron", ClubType.IRON, 130.0),
            Club("8i", "8 Iron", ClubType.IRON, 120.0),
            Club("9i", "9 Iron", ClubType.IRON, 110.0),
            Club("pw", "PW", ClubType.WEDGE, 100.0),
            Club("putter", "Putter", ClubType.PUTTER, 0.0),
        ),
        shots = shots("7i", 140.0, 142.0, 138.0, 60.0 /* topped */) +
            shots("8i", 124.0, 131.0, 126.0) +
            shots("9i", 117.0, 121.0, 119.0),
    )

    @Test
    fun statsIgnoreMishitsAndUseHistory() {
        val s = ClubStats.of(bag.clubs[1], bag.shots)
        assertEquals(StatSource.HISTORY, s.source)
        assertEquals(4, s.shotCount)
        assertEquals(142.0, s.maxMeters)
        assertEquals(140.0, s.typicalMeters, 0.01)
    }

    @Test
    fun manualMaxOnly() {
        val club = Club("3w", "3 Wood", ClubType.WOOD, 180.0, manualMaxMeters = 200.0)
        val s = ClubStats.of(club, emptyList())
        assertEquals(StatSource.MAX_ONLY, s.source)
        assertEquals(190.0, s.typicalMeters, 0.01)
    }

    @Test
    fun noDataFallsBackToDefault() {
        val s = ClubStats.of(bag.clubs[0], bag.shots)
        assertEquals(StatSource.DEFAULT, s.source)
        assertEquals(200.0, s.typicalMeters)
    }

    @Test
    fun picksSmallestClubThatReaches() {
        val rec = ClubRecommender.recommend(125.0, bag)
        assertEquals("8i", rec.club!!.club.id) // 8i typical 127
    }

    @Test
    fun shorterOptionOnlyWhenItsMaxReaches() {
        assertNull(ClubRecommender.recommend(125.0, bag).shorterOption)
        val rec = ClubRecommender.recommend(130.5, bag)
        assertEquals("7i", rec.club!!.club.id)
        assertEquals("8i", rec.shorterOption!!.club.id) // 8i typical 127, but max 131 reaches
        assertEquals("driver", rec.longerOption!!.club.id)
    }

    @Test
    fun toleranceLetsAClubCoverSlightlyLonger() {
        assertEquals("7i", ClubRecommender.recommend(142.5, bag).club!!.club.id)
    }

    @Test
    fun beyondLongestClub() {
        val rec = ClubRecommender.recommend(260.0, bag)
        assertEquals(Recommendation.Kind.CANNOT_REACH, rec.kind)
        assertEquals("driver", rec.club!!.club.id)
        assertEquals(60.0, rec.leftAfterMeters!!, 0.01)
    }

    @Test
    fun shortPitchIsPartialWedge() {
        val rec = ClubRecommender.recommend(50.0, bag)
        assertEquals(Recommendation.Kind.PARTIAL_SWING, rec.kind)
        assertEquals("pw", rec.club!!.club.id)
    }

    @Test
    fun clubsOutOfBagAreIgnored() {
        val noSeven = bag.copy(clubs = bag.clubs.map { if (it.id == "7i") it.copy(inBag = false) else it })
        assertEquals("driver", ClubRecommender.recommend(135.0, noSeven).club!!.club.id)
    }

    @Test
    fun bagRoundTripsThroughJson() {
        assertEquals(bag, Bag.decode(bag.encode()))
        assertTrue(Bag.default().clubs.size == 14)
    }
}
