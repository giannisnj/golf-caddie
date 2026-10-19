package com.golfcaddie.app

import android.Manifest
import android.app.Application
import android.location.Location
import android.location.LocationManager
import android.os.Looper
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.printToString
import androidx.lifecycle.ViewModelProvider
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import com.github.takahirom.roborazzi.captureRoboImage
import com.golfcaddie.app.ui.CaddieViewModel
import com.golfcaddie.core.Course
import com.golfcaddie.core.Geo
import com.golfcaddie.core.GeoPoint
import com.golfcaddie.core.Hole
import com.golfcaddie.core.Overpass
import com.golfcaddie.core.Simulator
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import kotlin.math.roundToInt

class PlayAndClubsTest : AppTestBase() {
    @Test
    fun simulatedGpsWalksHoleOne() {
        launch()
        compose.onNodeWithText("Moore Park Golf Club").assertIsDisplayed()
        toggleSimulate()

        // Standing on the 1st tee.
        val hole1 = course.holes.first()
        assertHoleBar(hole1)
        assertHero("${yd(Geo.distance(hole1.tee, hole1.greenCenter))}")
        settle()
        compose.onRoot().captureRoboImage("build/screenshots/1_simulated_tee_hole1.png")

        // Walk 100 yd toward the green with the simulation buttons.
        repeat(4) { compose.onNodeWithText("+25 yd").performClick() }
        compose.waitForIdle()
        val walked = Simulator.stepTowardGreen(hole1.tee, hole1, 4 * 25 * 0.9144)
        assertHero("${yd(Geo.distance(walked, hole1.greenCenter))}")
        settle(2)
        compose.onRoot().captureRoboImage("build/screenshots/2_simulated_100yd_walked.png")
    }

    @Test
    fun recordShotByWalkingItThenSeeItInClubs() {
        launch()
        toggleSimulate()
        compose.onNodeWithText("Record a shot").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("CADDIE'S PICK").assertExists()
        compose.onRoot().captureRoboImage("build/screenshots/3a_pick_club.png")
        compose.onAllNodesWithText("5 Iron", substring = true).onLast().performClick()
        compose.waitForIdle()

        // Walk 150 yd to the ball.
        repeat(6) { compose.onNodeWithText("+25 yd").performClick() }
        compose.waitForIdle()
        compose.onNodeWithText("5 Iron: 150 yd so far").assertExists()
        compose.onRoot().captureRoboImage("build/screenshots/3c_walking.png")
        compose.onNodeWithText("Ball is here").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Saved 5 Iron: 150 yd").assertExists()
        compose.onRoot().captureRoboImage("build/screenshots/3b_shot_saved.png")

        val shots = viewModel().state.value.bag.shots
        assertEquals(1, shots.size)
        assertEquals(150.0, shots.single().distanceMeters / 0.9144, 0.5)

        compose.onNodeWithText("Clubs").performClick()
        compose.waitForIdle()
        // The gapping chart's 5 Iron row opens the 5 Iron page.
        compose.onAllNodesWithText("5 Iron").onFirst().performClick()
        compose.waitForIdle()
        // One shot isn't history yet: plans on 95% of the 150 yd max.
        compose.onNodeWithText("From your max").assertExists()
        compose.onNodeWithText("143").assertExists()
        compose.onNodeWithText("150").assertExists()
        compose.onRoot().captureRoboImage("build/screenshots/4_clubs_after_recording.png")
    }

    @Test
    fun thePanelPullsUpOverTheMap() {
        launch()
        toggleSimulate()
        compose.onNodeWithContentDescription("Pull up the caddie panel").performClick()
        compose.waitForIdle()
        compose.onNodeWithContentDescription("Lower the caddie panel").assertExists()
        compose.onRoot().captureRoboImage("build/screenshots/2b_panel_pulled_up.png")
    }

    @Test
    fun courseIsShownBeforeThereIsAPosition() {
        shadowOf(app).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
        launch()
        // No GPS fix yet: the map frames the course instead of (0,0) in the ocean, and the
        // panel waits with dashes rather than zeros.
        compose.onNodeWithText("Hole —").assertExists()
        compose.onNodeWithText("Waiting for GPS").assertExists()
        assertHero("—")
        settle(2)
        compose.onRoot().captureRoboImage("build/screenshots/0_course_before_position.png")
        // ▶ goes to hole 1 (it used to skip to hole 2), and the header shows it straight away.
        nextHole()
        assertHoleBar(course.holes.first())
    }

    @Test
    fun approximateLocationOnlyExplainsWhatToDo() {
        shadowOf(app).grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION)
        launch()
        compose.onNodeWithText("Location is set to Approximate").assertExists()
        compose.onNodeWithText("Use precise location").assertExists()
    }

    @Test
    fun tappingBallIsHereStraightAwayKeepsTheShotOpen() {
        launch()
        toggleSimulate()
        compose.onNodeWithText("Record a shot").performClick()
        compose.waitForIdle()
        compose.onAllNodesWithText("Driver", substring = true).onLast().performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Ball is here").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Walk to your ball first", substring = true).assertExists()
        assertEquals(0, viewModel().state.value.bag.shots.size)
        compose.onNodeWithText("Ball is here").assertExists()
    }

    @Test
    fun leavingSimulationDropsTheFakePosition() {
        launch()
        toggleSimulate()
        toggleSimulate()
        assertEquals(null, viewModel().state.value.position)
        compose.onNodeWithText("Location is off").assertExists()
        compose.onRoot().captureRoboImage("build/screenshots/0b_location_off.png")
    }

    @Test
    fun unitAndMapStyleSurviveARestart() {
        launch()
        toggleSimulate()
        compose.onNodeWithText("m").performClick()
        compose.onNodeWithText("Satellite").performClick() // opens the style menu
        compose.onNodeWithText("Street").performClick()
        compose.waitForIdle()
        scenario.close()
        launch()
        val state = viewModel().state.value
        assertEquals(com.golfcaddie.core.DistanceUnit.METERS, state.unit)
        assertEquals(com.golfcaddie.app.map.MapStyle.STREET, state.mapStyle)
    }
}
