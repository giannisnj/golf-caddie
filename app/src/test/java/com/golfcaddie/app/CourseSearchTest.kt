package com.golfcaddie.app

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.golfcaddie.app.data.CourseRepository
import com.golfcaddie.app.data.HttpException
import com.golfcaddie.core.GeoPoint
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.io.IOException

/**
 * Finding courses when the Overpass servers misbehave, as they did in October 2026 (502s,
 * 504s and 40-second answers): Canberra comes from the built-in pack without asking them,
 * and elsewhere a failed server is retried rather than reported straight away.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CourseSearchTest {
    private val app: Application get() = ApplicationProvider.getApplicationContext()
    private val mooreParkJson = javaClass.getResource("/osm/moore_park_sydney.json")!!.readText()
    private val mooreParkClubhouse = GeoPoint(-33.8972, 151.2190)
    private val calls = mutableListOf<String>()

    private fun repo(vararg answers: () -> String) = CourseRepository(
        app,
        endpoints = listOf("https://a.example/api/interpreter", "https://b.example/api/interpreter"),
        retryDelaysMs = listOf(0, 0),
    ).apply {
        post = { url, _ ->
            calls += url
            answers.getOrNull(calls.size - 1)?.invoke() ?: throw HttpException(502, "a.example")
        }
    }

    @Before
    fun setUp() {
        File(app.filesDir, "courses.json").delete()
    }

    @Test
    fun canberraCoursesComeFromTheAppWithoutTheServers() = runBlocking {
        val repo = repo()
        val royalCanberra = repo.geocode("Royal Canberra")!!
        val courses = repo.coursesNear(royalCanberra)
        assertEquals(emptyList<String>(), calls)
        assertEquals("Royal Canberra Golf Club", courses.first().name)
        assertEquals(27, courses.first().holes.size)
        assertNotNull("slopes come built in", courses.first().elevation)
        // Kept for offline use like a download.
        assertEquals(courses.map { it.id }, repo.cachedCourses().map { it.id })

        // Every built-in course can be found by name and comes first in its own search.
        val names = listOf(
            "Belconnen", "Capital", "Fairbairn", "Federal", "Gold Creek", "Gungahlin Lakes",
            "Murrumbidgee", "Queanbeyan", "Royal Military College", "Yowani",
        )
        for (name in names) {
            val first = repo.coursesNear(repo.geocode(name) ?: error("no match for $name")).first()
            assertTrue("$name -> ${first.name}", first.name.startsWith(name))
            assertTrue("$name has ${first.holes.size} holes", first.holes.size >= 18)
        }
        assertEquals(emptyList<String>(), calls)
    }

    @Test
    fun aBusyServerIsRetriedOnTheNextPass() = runBlocking {
        val repo = repo(
            { throw HttpException(502, "a.example") },
            { throw IOException("timeout") },
            { mooreParkJson },
        )
        val courses = repo.coursesNear(mooreParkClubhouse)
        assertEquals("Moore Park Golf Club", courses.single().name)
        assertEquals(3, calls.size)
    }

    @Test
    fun givesUpWithAPlainMessageWhenEveryServerKeepsFailing() = runBlocking {
        val repo = repo()
        try {
            repo.coursesNear(mooreParkClubhouse)
            fail("expected an error")
        } catch (e: IOException) {
            assertEquals(6, calls.size) // 2 servers, 3 passes
            assertTrue(e.message!!, e.message!!.startsWith("OpenStreetMap's course servers are busy or down (HTTP 502"))
        }
    }
}
