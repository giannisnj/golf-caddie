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
import com.golfcaddie.app.data.Settings
import com.golfcaddie.core.Course
import com.golfcaddie.core.ElevationGrid
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

/**
 * Runs the real app on a simulated Android device (Robolectric, no emulator) on Moore Park
 * Golf Club, Sydney, using real OpenStreetMap data. Screenshots: app/build/screenshots/.
 * Shared setup for [PlayAndClubsTest] and [GpsTest].
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xxhdpi")
abstract class AppTestBase {
    @get:Rule
    val compose = createEmptyComposeRule()

    // Looked up each time: Robolectric creates a fresh Application for every test.
    protected val app: Application get() = ApplicationProvider.getApplicationContext()
    /** Moore Park with real ground heights (fetched from Open-Meteo), so slopes need no network. */
    protected val course: Course = Overpass.parse(
        javaClass.getResource("/osm/moore_park_sydney.json")!!.readText(),
    ).single().copy(
        elevation = Json.decodeFromString(
            ElevationGrid.serializer(),
            javaClass.getResource("/osm/moore_park_elevation.json")!!.readText(),
        ),
    )
    protected lateinit var scenario: ActivityScenario<MainActivity>

    @Before
    fun setUp() {
        // As if the course had been downloaded on an earlier visit.
        saveCourses(course)
        File(app.filesDir, "bag.json").delete()
        // Saved unit / map style / course choice would otherwise carry over between tests.
        app.getSharedPreferences(Settings.NAME, 0).edit().clear()
            // Live weather would make distances depend on today's wind; tests set it explicitly.
            .putBoolean(Settings.LIVE_WEATHER, false)
            .commit()
    }

    protected fun saveCourses(vararg courses: Course) {
        File(app.filesDir, "courses.json").writeText(
            Json.encodeToString(ListSerializer(Course.serializer()), courses.toList()),
        )
    }

    @After
    fun tearDown() {
        if (::scenario.isInitialized) scenario.close()
    }

    protected fun launch() {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.waitForIdle()
    }

    protected fun viewModel(): CaddieViewModel {
        lateinit var vm: CaddieViewModel
        scenario.onActivity { vm = ViewModelProvider(it)[CaddieViewModel::class.java] }
        return vm
    }

    /** Gives osmdroid a moment to fetch map tiles before a screenshot. */
    protected fun settle(seconds: Int = 6) {
        repeat(seconds * 4) {
            Thread.sleep(250)
            shadowOf(Looper.getMainLooper()).idle()
        }
        compose.waitForIdle()
    }

    /** Runs the main thread (where GPS callbacks arrive) until [text] is on screen. */
    protected fun waitForText(text: String, timeoutMs: Long = 5_000, substring: Boolean = false) {
        val end = System.currentTimeMillis() + timeoutMs
        while (compose.onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isEmpty()) {
            check(System.currentTimeMillis() < end) {
                "\"$text\" never appeared. On screen: " +
                    compose.onRoot().printToString(maxDepth = 60).lines().filter { "Text =" in it }.joinToString(" | ")
            }
            shadowOf(Looper.getMainLooper()).idle()
            compose.waitForIdle()
            Thread.sleep(50)
        }
    }

    protected fun yd(meters: Double) = (meters / 0.9144).roundToInt()

}
