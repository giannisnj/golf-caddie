package com.golfcaddie.wear

import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import com.github.takahirom.roborazzi.captureRoboImage
import com.golfcaddie.core.WatchClub
import com.golfcaddie.core.WatchState
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The watch app's screen on a simulated round Wear OS watch. Screenshots: wear/build/screenshots/. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w227dp-h227dp-small-notlong-round-watch-xhdpi-keyshidden-nonav")
class WatchScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private val onHole7 = WatchState(
        hole = "Hole 7 · Par 3",
        front = "112", center = "134", back = "157", unit = "yd",
        playsLike = "Plays 141",
        club = "7 Iron", clubRange = "136–154 yd",
        warning = "Bunker R 138–148 · 2 in 10",
        clubs = listOf(WatchClub("6i", "6 Iron"), WatchClub("7i", "7 Iron"), WatchClub("8i", "8 Iron")),
        recommendedClubId = "7i",
    )

    @Test
    fun showsDistancesAndTheClub() {
        compose.setContent { WatchScreen(onHole7, {}, {}, {}) }
        compose.onNodeWithText("134").assertExists()
        compose.onNodeWithText("F 112 · B 157 ·").assertExists()
        compose.onNodeWithText("Plays 141").assertExists()
        compose.onNodeWithText("7 Iron").assertExists()
        compose.onNodeWithText("136–154").assertExists()
        compose.onNodeWithText("Bunker R 138–148 · 2 in 10").assertExists()
        compose.onRoot().captureRoboImage("build/screenshots/watch_1_distances.png")
    }

    @Test
    fun hittingSendsTheRecommendedClubOrAnotherOne() {
        val started = mutableListOf<String>()
        compose.setContent { WatchScreen(onHole7, { started += it }, {}, {}) }
        // Hit starts the caddie's club; press and hold for another.
        compose.onNodeWithText("Hit").performClick()
        compose.onNodeWithText("Hit").performTouchInput { longClick() }
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("8 Iron"))
        compose.onNodeWithText("8 Iron").performClick()
        assertEquals(listOf("7i", "8i"), started)
    }

    @Test
    fun duringAShotTheWatchFinishesOrCancelsIt() {
        var finished = 0
        var cancelled = 0
        val walking = onHole7.copy(shotInProgress = "7 Iron · 96 yd so far", shotClub = "7 Iron", shotSoFar = "96")
        compose.setContent { WatchScreen(walking, {}, { finished++ }, { cancelled++ }) }
        compose.onNodeWithText("96").assertExists()
        compose.onNodeWithText("yd so far").assertExists()
        compose.onNodeWithText("Ball is here").performClick()
        // Press and hold to cancel.
        compose.onNodeWithText("Ball is here").performTouchInput { longClick() }
        assertEquals(1, finished)
        assertEquals(1, cancelled)
        compose.onRoot().captureRoboImage("build/screenshots/watch_2_shot.png")
    }

    @Test
    fun withoutDataItSaysWhatToDo() {
        compose.setContent { WatchScreen(WatchState(status = WAITING_FOR_PHONE), {}, {}, {}) }
        compose.onNodeWithText("Waiting for phone").assertExists()
        compose.onNodeWithText("Golf Caddie on your phone sends the numbers here.").assertExists()
        compose.onRoot().captureRoboImage("build/screenshots/watch_3_waiting.png")
    }

    @Test
    fun phoneStatusIsShownWhenThereIsNothingToMeasure() {
        compose.setContent { WatchScreen(WatchState(status = "Waiting for GPS…"), {}, {}, {}) }
        compose.onNodeWithText("Waiting for GPS…").assertExists()
    }
}
