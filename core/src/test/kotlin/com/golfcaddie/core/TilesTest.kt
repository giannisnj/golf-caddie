package com.golfcaddie.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TilesTest {
    @Test
    fun tileNumbersMatchTheServer() {
        // Checked against the ACT imagery server: z17 tile (119821, 79288) covers Royal Canberra.
        assertEquals(119821, WebMercator.tileX(149.0985, 17))
        assertEquals(79288, WebMercator.tileY(-35.3005, 17))
    }

    @Test
    fun mercatorMetersToDegrees() {
        // The ACT imagery service's published extent.
        assertEquals(148.74, WebMercator.lonOf(16557805.179376211), 0.01)
        assertEquals(149.42, WebMercator.lonOf(16633456.56337779), 0.01)
        assertEquals(-35.92, WebMercator.latOf(-4290913.053241368), 0.01)
        assertEquals(-35.11, WebMercator.latOf(-4179573.788050428), 0.01)
    }

    @Test
    fun aCourseNeedsAFewHundredTilesFromZoom15To19() {
        val course = Overpass.parse(javaClass.getResource("/osm/moore_park_sydney.json")!!.readText()).single()
        val tiles = WebMercator.tilesFor(course, 15..19)
        val perZoom = tiles.groupingBy { it.z }.eachCount()
        assertEquals((15..19).toSet(), perZoom.keys)
        // Each zoom level has about 4x the tiles of the one before.
        assertTrue(perZoom.getValue(19) > 3 * perZoom.getValue(18))
        assertTrue(tiles.size in 200..1500, "${tiles.size} tiles")
        assertEquals(tiles.size, tiles.toSet().size)
    }
}
