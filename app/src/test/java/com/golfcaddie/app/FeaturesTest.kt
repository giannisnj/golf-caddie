package com.golfcaddie.app

import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import com.github.takahirom.roborazzi.captureRoboImage
import com.golfcaddie.app.map.MapSources
import com.golfcaddie.app.map.OfflineMaps
import com.golfcaddie.app.ui.CaddieText
import com.golfcaddie.app.ui.toWatchState
import com.golfcaddie.core.Advice
import com.golfcaddie.core.Bag
import com.golfcaddie.core.Caddie
import com.golfcaddie.core.ClubRecommender
import com.golfcaddie.core.Course
import com.golfcaddie.core.DistanceUnit
import com.golfcaddie.core.Geo
import com.golfcaddie.core.HazardAdvisor
import com.golfcaddie.core.Overpass
import com.golfcaddie.core.PlaysLikeCalculator
import com.golfcaddie.core.Simulator
import com.golfcaddie.core.WebMercator
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeNoException
import org.junit.Test
import org.osmdroid.tileprovider.modules.SqlTileWriter
import org.osmdroid.util.MapTileIndex
import java.io.IOException
import kotlin.math.abs

/** Plays-like, hazard advice, club spread, the course plan and offline maps, in the running app. */
class FeaturesTest : AppTestBase() {
    private fun findMapView(v: android.view.View): org.osmdroid.views.MapView? = when (v) {
        is org.osmdroid.views.MapView -> v
        is android.view.ViewGroup -> (0 until v.childCount).firstNotNullOfOrNull { findMapView(v.getChildAt(it)) }
        else -> null
    }

    private val grid get() = course.elevation!!

    private fun goToHole(index: Int) {
        // Simulate GPS starts on hole 1's tee; ▶ moves (and teleports) to the next hole.
        compose.onNodeWithText("Simulate GPS").performClick()
        repeat(index) { compose.onNodeWithText("▶").performClick() }
        compose.waitForIdle()
    }

    @Test
    fun hilliestHolePlaysLongerOrShorterAndTheClubFollows() {
        val i = course.holes.indices.maxBy { abs(grid.at(course.holes[it].greenCenter)!! - grid.at(course.holes[it].tee)!!) }
        val hole = course.holes[i]
        val plays = PlaysLikeCalculator.compute(hole.tee, hole.greenCenter, grid, null)
        assertTrue("a real slope to test with: ${plays.riseMeters}", abs(plays.riseMeters!!) >= 3)

        launch()
        goToHole(i)
        val text = CaddieText.playsLike(plays, DistanceUnit.YARDS)!!
        compose.onNodeWithText(text).assertExists()
        compose.onNodeWithText(CaddieText.playsLikeWhy(plays, DistanceUnit.YARDS)).assertExists()
        // The club is picked for the plays-like distance, not the flat one.
        val club = ClubRecommender.recommend(plays.playsMeters, Bag.default()).club!!.club.name
        assertEquals(club, viewModel().state.value.recommendation!!.club!!.club.name)
        settle(2)
        compose.onRoot().captureRoboImage("build/screenshots/5_plays_like_hole${hole.number}.png")
    }

    @Test
    fun hazardAdviceAppearsWhereABunkerIsReallyInPlay() {
        // Find a real spot at Moore Park where the default bag's club has a hazard in play.
        val bag = Bag.default()
        val spot = course.holes.flatMap { h ->
            listOf(170.0, 150.0, 130.0, 110.0, 90.0).filter { it < h.lengthMeters }.map { Simulator.onLine(h, it) }
        }.first { p ->
            val h = Caddie.locateHole(course, p)!!
            val s = Caddie.situation(course, h, p)
            val pl = PlaysLikeCalculator.compute(p, h.greenCenter, grid, null)
            val rec = ClubRecommender.recommend(pl.playsMeters, bag, s.onGreen)
            HazardAdvisor.advise(s, rec, bag, pl.conditionFactor).any { it is Advice.InPlay }
        }

        launch()
        compose.onNodeWithText("Simulate GPS").performClick()
        val vm = viewModel()
        vm.onMapTap(spot)
        compose.waitForIdle()
        val state = vm.state.value
        val warning = state.advice.filterIsInstance<Advice.InPlay>().first()
        compose.onNodeWithText("⚠ " + CaddieText.advice(warning, DistanceUnit.YARDS)).assertExists()
        settle(2)
        compose.onRoot().captureRoboImage("build/screenshots/6_hazard_advice.png")
    }

    @Test
    fun walkedShotsGiveARangeAndATendencyInTheGappingChart() {
        launch()
        val vm = viewModel()
        val hole = course.holes.first()
        val start = hole.tee
        val aim = Geo.destination(start, Geo.bearing(start, hole.greenCenter), 140.0)
        // Five 7-iron shots, all finishing about 8 m right of where they were aimed.
        listOf(128.0, 135.0, 138.0, 141.0, 150.0).forEach { m ->
            val straight = Geo.destination(start, Geo.bearing(start, aim), m)
            val end = Geo.destination(straight, Geo.bearing(start, aim) + 90, 8.0)
            vm.addShot("7i", Geo.distance(start, end), start, end, aim)
        }
        compose.onNodeWithText("Clubs").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Gapping: your usual range with each club").assertExists()
        compose.onRoot().captureRoboImage("build/screenshots/7_gapping_chart.png")
        // Tapping the 7 Iron's bar opens its card, with the range and the left/right tendency.
        compose.onAllNodesWithText("7 Iron").onFirst().performClick()
        waitForText("tends 9 yd right", substring = true)
        compose.onRoot().captureRoboImage("build/screenshots/7b_club_card.png")
    }

    @Test
    fun theWatchGetsWhatThePhoneShows() {
        launch()
        goToHole(6)
        val state = viewModel().state.value
        val watch = state.toWatchState()
        val s = state.situation!!
        assertEquals("Hole ${s.hole.number}" + (s.hole.par?.let { " · Par $it" } ?: ""), watch.hole)
        // The big number on the watch is the big number on the phone.
        compose.onNodeWithText("${watch.center} yd").assertExists()
        assertEquals(state.recommendation!!.club!!.club.name, watch.club)
        assertEquals(state.recommendation!!.club!!.club.id, watch.recommendedClubId)
        // Survives the trip over the Data Layer.
        assertEquals(watch, com.golfcaddie.core.WatchState.decode(watch.encode()))

        // A shot started from the watch is a shot on the phone.
        viewModel().startShot(watch.recommendedClubId!!)
        val walking = viewModel().state.value.toWatchState()
        assertEquals("${watch.club} · 0 yd so far", walking.shotInProgress)
    }

    @Test
    fun coursePlanStyleNeedsNoMapImages() {
        launch()
        compose.onNodeWithText("Satellite").performClick()
        compose.onNodeWithText("Street").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Course plan").assertExists()
        compose.onNodeWithText("Course data © OpenStreetMap contributors").assertExists()
        goToHole(3)
        settle(1)
        compose.onRoot().captureRoboImage("build/screenshots/8_course_plan.png")
    }

    @Test
    fun sydneyHasNoOfflineImageryAndSaysWhy() {
        assertEquals(MapSources.esri, MapSources.satelliteFor(course))
        launch()
        compose.onNodeWithText("Course").performClick()
        waitForText("No free satellite imagery here allows saving for offline use", substring = true)
        compose.onRoot().captureRoboImage("build/screenshots/9_course_tab_sydney.png")
    }

    @Test
    fun canberraSavesRealSatelliteImagesForOffline() {
        val canberra: Course = Overpass.parse(javaClass.getResource("/osm/royal_canberra.json")!!.readText())
            .maxBy { it.holes.size }
        assertEquals(MapSources.act, MapSources.satelliteFor(canberra))
        saveCourses(canberra)
        launch()
        compose.onNodeWithText("Course").performClick()
        waitForText("Save satellite map for offline")
        compose.onRoot().captureRoboImage("build/screenshots/10_course_tab_canberra.png")

        // Download zoom 15-16 for real (a handful of images) into the map's cache database.
        val result = try {
            runBlocking { OfflineMaps(app).save(canberra, zooms = 15..16) { _, _ -> } }
        } catch (e: OfflineMaps.CacheWriteException) {
            throw e // a real failure, not a network problem
        } catch (e: IOException) {
            assumeNoException("ACT imagery server not reachable from this machine", e)
            return
        }
        val saved = result as OfflineMaps.Result.Saved
        val tiles = WebMercator.tilesFor(canberra, 15..16)
        assertEquals(tiles.size, saved.tiles + saved.missing)
        assertTrue("most images saved: $saved", saved.tiles >= tiles.size * 0.9)
        val writer = SqlTileWriter()
        try {
            val inCache = tiles.count { writer.exists(MapSources.act, MapTileIndex.getTileIndex(it.z, it.x, it.y)) }
            assertEquals("every saved image is in the map's cache database", saved.tiles, inCache)
        } finally {
            writer.onDetach()
        }
        // Saving again finds everything already there and downloads nothing new.
        val again = runBlocking { OfflineMaps(app).save(canberra, zooms = 15..16) { _, _ -> } } as OfflineMaps.Result.Saved
        assertEquals(saved, again)

        // With the map's data connection cut and its memory cache emptied, the satellite
        // images still show: they come from the saved database.
        compose.onNodeWithText("Play").performClick()
        compose.waitForIdle()
        scenario.onActivity { activity ->
            val map = findMapView(activity.window.decorView)!!
            map.setUseDataConnection(false)
            map.tileProvider.clearTileCache()
            map.controller.setZoom(16.0)
            map.controller.setCenter(org.osmdroid.util.GeoPoint(canberra.center.lat, canberra.center.lon))
            map.invalidate()
        }
        settle(4)
        compose.onRoot().captureRoboImage("build/screenshots/11_offline_satellite_canberra.png")
    }
}
