package com.golfcaddie.app.data

import android.content.Context
import com.golfcaddie.core.Course
import com.golfcaddie.core.GeoPoint
import com.golfcaddie.core.Overpass
import kotlinx.coroutines.Dispatchers
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
 */
class CourseRepository(context: Context) {
    private val cacheFile = File(context.filesDir, "courses.json")
    private val http = Http("GolfCaddie/0.1 (Android; ${context.packageName})")
    private val json = Json { ignoreUnknownKeys = true }
    private val listSerializer = ListSerializer(Course.serializer())

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
        val query = Overpass.queryAround(center, radiusMeters)
        var lastError: Exception? = null
        for (endpoint in Overpass.ENDPOINTS) {
            try {
                val body = http.post(endpoint, "data=" + URLEncoder.encode(query, "UTF-8"))
                // Keep elevation grids already fetched for the same courses.
                val previous = cachedCourses().associateBy { it.id }
                val courses = Overpass.parse(body, near = center).map { c ->
                    previous[c.id]?.elevation?.let { c.copy(elevation = it) } ?: c
                }
                if (courses.isNotEmpty()) cacheFile.writeText(json.encodeToString(listSerializer, courses))
                return@withContext courses
            } catch (e: Exception) {
                lastError = e
            }
        }
        throw IOException("Could not reach OpenStreetMap: ${lastError?.message}", lastError)
    }

    /** Looks up a place or course name with OpenStreetMap's free Nominatim geocoder. */
    suspend fun geocode(name: String): GeoPoint? = withContext(Dispatchers.IO) {
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
}
