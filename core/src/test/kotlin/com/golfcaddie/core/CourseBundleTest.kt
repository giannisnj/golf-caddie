package com.golfcaddie.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** The built-in course pack must answer a search the way Overpass would have. */
class CourseBundleTest {
    private val royalCanberra = Overpass.parse(javaClass.getResource("/osm/royal_canberra.json")!!.readText())
        .single { it.name == "Royal Canberra Golf Club" }
    private val elsewhere = royalCanberra.copy(id = 1, name = "Yowani Country Club").let { c ->
        // The same layout moved 7 km north, where Yowani really is.
        fun move(p: GeoPoint) = GeoPoint(p.lat + 0.0675, p.lon + 0.0445)
        c.copy(
            areas = c.areas.map { it.map(::move) },
            holes = c.holes.map { h -> h.copy(line = h.line.map(::move), green = h.green.map(::move)) },
        )
    }
    private val bundle = CourseBundle("canberra", GeoPoint(-35.30, 149.12), 30_000.0, "2026-10-20", listOf(royalCanberra, elsewhere))

    @Test
    fun coversOnlySearchesWhollyInsideTheRegion() {
        assertTrue(bundle.covers(royalCanberra.center, 3000))
        // Goulburn: well outside.
        assertFalse(bundle.covers(GeoPoint(-34.75, 149.72), 3000))
        // 28.5 km out: the point is inside, but a 3 km search would reach past the edge.
        assertFalse(bundle.covers(Geo.destination(bundle.center, 90.0, 28_500.0), 3000))
    }

    @Test
    fun nearReturnsTheCourseYouAreOnFirstAndLeavesOutDistantOnes() {
        val onCourse = Simulator.atTee(royalCanberra.holes.first())
        assertEquals(listOf(royalCanberra.id), bundle.near(onCourse, 3000).map { it.id })
        assertEquals(listOf(elsewhere.id), bundle.near(elsewhere.center, 3000).map { it.id })
        // Between the two, a big enough search finds both, nearest first.
        val nearerYowani = GeoPoint(-35.255, 149.12)
        assertEquals(listOf(elsewhere.id, royalCanberra.id), bundle.near(nearerYowani, 8000).map { it.id })
        // Canberra's city centre: nothing within 1 km.
        assertEquals(emptyList(), bundle.near(GeoPoint(-35.2809, 149.1300), 1000))
    }

    @Test
    fun namedMatchesEveryWordButIgnoresGenericOnes() {
        assertEquals(elsewhere.id, bundle.named("yowani")?.id)
        assertEquals(royalCanberra.id, bundle.named("Royal Canberra golf")?.id)
        assertEquals(royalCanberra.id, bundle.named("  royal  ")?.id)
        assertNull(bundle.named("golf club"))
        assertNull(bundle.named("Royal Sydney"))
        assertNull(bundle.named(""))
    }
}
