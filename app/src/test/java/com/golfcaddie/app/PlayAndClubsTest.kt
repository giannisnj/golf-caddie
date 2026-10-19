package com.golfcaddie.app

import android.Manifest
import android.app.Application
import android.location.Location
import android.location.LocationManager
import android.os.Looper
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
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
        compose.onNodeWithText("5 Iron").performClick()
        compose.waitForIdle()
        // One shot isn't history yet: plans on 95% of the 150 yd max.
        compose.onNodeWithText("Typical 143 yd · Max 150 yd · from max").assertExists()
        compose.onRoot().captureRoboImage("build/screenshots/4_clubs_after_recording.png")
    }
}
