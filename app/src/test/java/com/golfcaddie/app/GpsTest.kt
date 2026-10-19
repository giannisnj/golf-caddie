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

/**
 * Own SDK level so Robolectric gives it a fresh sandbox: coroutines started on the main
 * thread in an earlier test's sandbox never run here otherwise.
 */
@Config(sdk = [33], qualifiers = "w411dp-h891dp-xxhdpi")
class GpsTest : AppTestBase() {
    @Test
    fun realGpsPathDetectsHoleSeven() {
        // Positions go through the same LocationManager feed that the emulator's location
        // controls and `adb emu geo fix` use.
        val lm = app.getSystemService(LocationManager::class.java)
        shadowOf(lm).setProviderEnabled(LocationManager.GPS_PROVIDER, true)
        fun gpsFix(p: GeoPoint) = shadowOf(lm).simulateLocation(Location(LocationManager.GPS_PROVIDER).apply {
            latitude = p.lat
            longitude = p.lon
            accuracy = 4.5f
            time = System.currentTimeMillis()
        })
        val hole6 = course.holes.single { it.number == 6 }
        val hole7 = course.holes.single { it.number == 7 }
        fun header(h: Hole) = "Hole ${h.number}" + (h.par?.let { " · Par $it" } ?: "")

        // Phone already knows roughly where it is (on the 6th tee); the user has allowed location.
        gpsFix(Simulator.atTee(hole6))
        shadowOf(app).grantPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
        launch()
        waitForText(header(hole6))

        // Live update: walk onto the 7th fairway, 140 m from the green.
        val spot = Simulator.onLine(hole7, metersFromGreen = 140.0)
        gpsFix(spot)
        waitForText(header(hole7))
        compose.onNodeWithText("${yd(Geo.distance(spot, hole7.greenCenter))} yd").assertExists()
        compose.onNodeWithText("GPS accuracy ±5 yd").assertExists()
        settle()
        compose.onRoot().captureRoboImage("build/screenshots/3_real_gps_hole7.png")
    }

}
