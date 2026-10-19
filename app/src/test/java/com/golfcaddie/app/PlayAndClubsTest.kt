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
        compose.onNodeWithText("Simulate GPS").performClick()
        compose.waitForIdle()

        // Standing on the 1st tee.
        val hole1 = course.holes.first()
        compose.onNodeWithText("Hole 1 · Par ${hole1.par}").assertExists()
        val teeDistance = yd(Geo.distance(hole1.tee, hole1.greenCenter))
        compose.onNodeWithText("$teeDistance yd").assertExists()
        settle()
        compose.onRoot().captureRoboImage("build/screenshots/1_simulated_tee_hole1.png")

        // Walk 100 yd toward the green with the simulation buttons.
        repeat(4) { compose.onNodeWithText("+25 yd").performClick() }
        compose.waitForIdle()
        val walked = Simulator.stepTowardGreen(hole1.tee, hole1, 4 * 25 * 0.9144)
        compose.onNodeWithText("${yd(Geo.distance(walked, hole1.greenCenter))} yd").assertExists()
        settle(2)
        compose.onRoot().captureRoboImage("build/screenshots/2_simulated_100yd_walked.png")
    }

    @Test
    fun recordShotByWalkingItThenSeeItInClubs() {
        launch()
        compose.onNodeWithText("Simulate GPS").performClick()
        compose.onNodeWithText("Record a shot").performClick()
        compose.waitForIdle()
        compose.onAllNodesWithText("5 Iron", substring = true).onLast().performClick()
        compose.waitForIdle()

        // Walk 150 yd to the ball.
        repeat(6) { compose.onNodeWithText("+25 yd").performClick() }
        compose.waitForIdle()
        compose.onNodeWithText("Ball is here").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Saved 5 Iron: 150 yd").assertExists()
        compose.onNodeWithText("OK").performClick()

        val shots = viewModel().state.value.bag.shots
        assertEquals(1, shots.size)
        assertEquals(150.0, shots.single().distanceMeters / 0.9144, 0.5)

        compose.onNodeWithText("Clubs").performClick()
        compose.waitForIdle()
        // The gapping chart's 5 Iron bar opens the 5 Iron card.
        compose.onAllNodesWithText("5 Iron").onFirst().performClick()
        compose.waitForIdle()
        // One shot isn't history yet: plans on 95% of the 150 yd max.
        compose.onNodeWithText("Typical 143 yd · Max 150 yd · from max").assertExists()
        compose.onRoot().captureRoboImage("build/screenshots/4_clubs_after_recording.png")
    }

    @Test
    fun courseIsShownBeforeThereIsAPosition() {
        launch()
        // No GPS and no simulation yet: the map frames the course instead of (0,0) in the ocean.
        compose.onNodeWithText("Hole -").assertExists()
        settle(2)
        compose.onRoot().captureRoboImage("build/screenshots/0_course_before_position.png")
        // ▶ goes to hole 1 (it used to skip to hole 2), and the header shows it straight away.
        compose.onNodeWithText("▶").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Hole 1 · Par ${course.holes.first().par}").assertExists()
    }

    @Test
    fun approximateLocationOnlyExplainsWhatToDo() {
        shadowOf(app).grantPermissions(Manifest.permission.ACCESS_COARSE_LOCATION)
        launch()
        compose.onNodeWithText("Location is set to Approximate", substring = true).assertExists()
    }

    @Test
    fun tappingBallIsHereStraightAwayKeepsTheShotOpen() {
        launch()
        compose.onNodeWithText("Simulate GPS").performClick()
        compose.onNodeWithText("Record a shot").performClick()
        compose.waitForIdle()
        compose.onAllNodesWithText("Driver", substring = true).onLast().performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Ball is here").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Walk to your ball first", substring = true).assertExists()
        compose.onNodeWithText("OK").performClick()
        compose.waitForIdle()
        assertEquals(0, viewModel().state.value.bag.shots.size)
        compose.onNodeWithText("Ball is here").assertExists()
    }

    @Test
    fun leavingSimulationDropsTheFakePosition() {
        launch()
        compose.onNodeWithText("Simulate GPS").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Simulate GPS").performClick()
        compose.waitForIdle()
        assertEquals(null, viewModel().state.value.position)
        compose.onNodeWithText("Location is off for this app", substring = true).assertExists()
    }

    @Test
    fun unitAndMapStyleSurviveARestart() {
        launch()
        compose.onNodeWithText("yd").performClick()
        compose.onNodeWithText("Satellite").performClick() // cycles to Street
        compose.waitForIdle()
        scenario.close()
        launch()
        val state = viewModel().state.value
        assertEquals(com.golfcaddie.core.DistanceUnit.METERS, state.unit)
        assertEquals(com.golfcaddie.app.map.MapStyle.STREET, state.mapStyle)
    }
}
