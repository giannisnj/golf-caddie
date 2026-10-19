package com.golfcaddie.app.data

import android.content.Context
import com.golfcaddie.core.Course
import com.golfcaddie.core.CourseBundle
import com.golfcaddie.core.GeoPoint
import com.golfcaddie.core.Overpass
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.io.IOException
import java.net.URLEncoder

/**
 * Loads course layouts from OpenStreetMap (free, via the Overpass API) and caches the
 * last download on the phone, so the app keeps working with no signal on the course.
 * Regions shipped in assets/courses/ (Canberra) are answered from there, with no download.
 */
class CourseRepository(
    private val context: Context,
    private val endpoints: List<String> = Overpass.ENDPOINTS,
    /** Pause before each further pass over [endpoints]; their length is the number of retries. */
    private val retryDelaysMs: List<Long> = listOf(5_000, 20_000),
) {
    private val cacheFile = File(context.filesDir, "courses.json")
    private val http = Http("GolfCaddie/0.1 (Android; ${context.packageName})")
    private val json = Json { ignoreUnknownKeys = true }
    private val listSerializer = ListSerializer(Course.serializer())

    /** Overpass POSTs go through here, so tests can stand in for the servers. */
    internal var post: (url: String, form: String) -> String = http::post

    private val bundles: List<CourseBundle> by lazy {
        val files = context.assets.list(BUNDLE_DIR).orEmpty().filter { it.endsWith(".json") }
        files.map { name ->
            context.assets.open("$BUNDLE_DIR/$name").bufferedReader().use {
                json.decodeFromString(CourseBundle.serializer(), it.readText())
            }
        }
    }

    /** Every course shipped in the app, by name. */
    fun builtInCourses(): List<Course> = bundles.flatMap { it.courses }.sortedBy { it.name }

    fun cachedCourses(): List<Course> = runCatching {
        json.decodeFromString(listSerializer, cacheFile.readText())
    }.getOrDefault(emptyList())

    /** Replaces [course] in the offline cache (e.g. once its elevation grid has been fetched). */
    fun saveCourse(course: Course) {
        val all = cachedCourses()
        val updated = if (all.any { it.id == course.id }) all.map { if (it.id == course.id) course else it } else all + course
        cacheFile.writeText(json.encodeToString(listSerializer, updated))
    }

    /** All golf courses within [radiusMeters] of [center], nearest first. */
    suspend fun coursesNear(center: GeoPoint, radiusMeters: Int = 3000): List<Course> = withContext(Dispatchers.IO) {
        bundles.firstOrNull { it.covers(center, radiusMeters) }?.let { bundle ->
            return@withContext remember(bundle.near(center, radiusMeters))
        }
        val query = Overpass.queryAround(center, radiusMeters)
        var lastError: Exception? = null
        // The public servers often answer 502/504 or time out when busy, and a few seconds
        // later answer fine, so go round them again before giving up (after about 3 minutes:
        // a busy server takes 30-60 s even to say no).
        val giveUpAt = System.currentTimeMillis() + GIVE_UP_AFTER_MS
        for (pass in 0..retryDelaysMs.size) {
            if (pass > 0) delay(retryDelaysMs[pass - 1])
            for (endpoint in endpoints) {
                if (System.currentTimeMillis() > giveUpAt) break
                try {
                    val body = post(endpoint, "data=" + URLEncoder.encode(query, "UTF-8"))
                    return@withContext remember(Overpass.parse(body, near = center))
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    lastError = e
                }
            }
        }
        throw IOException(
            "OpenStreetMap's course servers are busy or down (${lastError?.message}). Try again in a few minutes.",
            lastError,
        )
    }

    /** Caches [courses] for offline use, keeping elevation grids already fetched for them. */
    private fun remember(courses: List<Course>): List<Course> {
        val previous = cachedCourses().associateBy { it.id }
        val merged = courses.map { c -> c.elevation?.let { c } ?: previous[c.id]?.elevation?.let { c.copy(elevation = it) } ?: c }
        if (merged.isNotEmpty()) cacheFile.writeText(json.encodeToString(listSerializer, merged))
        return merged
    }

    /**
     * Looks up a place or course name: a built-in course by its name (works offline), otherwise
     * OpenStreetMap's free Nominatim geocoder.
     */
    suspend fun geocode(name: String): GeoPoint? = withContext(Dispatchers.IO) {
        bundles.firstNotNullOfOrNull { it.named(name) }?.let { return@withContext it.center }
        val url = "https://nominatim.openstreetmap.org/search?format=json&limit=1&q=" +
            URLEncoder.encode(name, "UTF-8")
        val results = json.parseToJsonElement(http.get(url)).jsonArray
        results.firstOrNull()?.jsonObject?.let {
            GeoPoint(
                it["lat"]!!.jsonPrimitive.content.toDouble(),
                it["lon"]!!.jsonPrimitive.content.toDouble(),
            )
        }
    }

    companion object {
        const val BUNDLE_DIR = "courses"
        private const val GIVE_UP_AFTER_MS = 180_000L
    }
}
