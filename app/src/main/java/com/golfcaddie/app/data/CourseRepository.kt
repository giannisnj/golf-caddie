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
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Loads course layouts from OpenStreetMap (free, via the Overpass API) and caches the
 * last download on the phone, so the app keeps working with no signal on the course.
 */
class CourseRepository(context: Context) {
    private val cacheFile = File(context.filesDir, "courses.json")
    private val userAgent = "GolfCaddie/0.1 (Android; ${context.packageName})"
    private val json = Json { ignoreUnknownKeys = true }
    private val listSerializer = ListSerializer(Course.serializer())

    fun cachedCourses(): List<Course> = runCatching {
        json.decodeFromString(listSerializer, cacheFile.readText())
    }.getOrDefault(emptyList())

    /** All golf courses within [radiusMeters] of [center], nearest first. */
    suspend fun coursesNear(center: GeoPoint, radiusMeters: Int = 3000): List<Course> = withContext(Dispatchers.IO) {
        val query = Overpass.queryAround(center, radiusMeters)
        var lastError: Exception? = null
        for (endpoint in Overpass.ENDPOINTS) {
            try {
                val body = post(endpoint, "data=" + URLEncoder.encode(query, "UTF-8"))
                val courses = Overpass.parse(body, near = center)
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
        val results = json.parseToJsonElement(get(url)).jsonArray
        results.firstOrNull()?.jsonObject?.let {
            GeoPoint(
                it["lat"]!!.jsonPrimitive.content.toDouble(),
                it["lon"]!!.jsonPrimitive.content.toDouble(),
            )
        }
    }

    private fun get(url: String): String = open(url).run {
        try {
            requireOk(responseCode)
            inputStream.bufferedReader().readText()
        } finally {
            disconnect()
        }
    }

    private fun post(url: String, form: String): String = open(url).run {
        try {
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            outputStream.use { it.write(form.toByteArray()) }
            requireOk(responseCode)
            inputStream.bufferedReader().readText()
        } finally {
            disconnect()
        }
    }

    private fun open(url: String) = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 15_000
        readTimeout = 90_000
        setRequestProperty("User-Agent", userAgent)
    }

    private fun HttpURLConnection.requireOk(code: Int) {
        if (code !in 200..299) throw IOException("HTTP $code from ${url.host}")
    }
}
