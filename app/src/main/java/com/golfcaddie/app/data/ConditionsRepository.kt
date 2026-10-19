package com.golfcaddie.app.data

import android.content.Context
import com.golfcaddie.core.Conditions
import com.golfcaddie.core.Course
import com.golfcaddie.core.ElevationGrid
import com.golfcaddie.core.GeoPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.util.Locale

/**
 * Elevation and current weather from Open-Meteo (free, no key, non-commercial use).
 * Elevation is fetched once per course and kept with it; weather is refreshed while playing.
 */
class ConditionsRepository(context: Context) {
    private val http = Http("GolfCaddie/0.1 (Android; ${context.packageName})")
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun weather(at: GeoPoint): Conditions = withContext(Dispatchers.IO) {
        val url = "https://api.open-meteo.com/v1/forecast?latitude=${fmt(at.lat)}&longitude=${fmt(at.lon)}" +
            "&current=temperature_2m,wind_speed_10m,wind_direction_10m&wind_speed_unit=ms"
        val current = json.parseToJsonElement(http.get(url)).jsonObject["current"]!!.jsonObject
        Conditions(
            windSpeedMs = current["wind_speed_10m"]!!.jsonPrimitive.double,
            windFromDeg = current["wind_direction_10m"]!!.jsonPrimitive.double,
            temperatureC = current["temperature_2m"]!!.jsonPrimitive.double,
            fetchedAtMillis = System.currentTimeMillis(),
        )
    }

    /**
     * Ground heights over [course], in batches of 100 points. Open-Meteo counts every point as
     * a call (600 a minute), so batches are paced and a 429 "slow down" is waited out.
     */
    suspend fun elevation(course: Course): ElevationGrid = withContext(Dispatchers.IO) {
        val plan = ElevationGrid.planFor(course)
        val heights = mutableListOf<Double>()
        for (chunk in plan.points.chunked(ElevationGrid.POINTS_PER_REQUEST)) {
            val url = "https://api.open-meteo.com/v1/elevation?latitude=" + chunk.joinToString(",") { fmt(it.lat) } +
                "&longitude=" + chunk.joinToString(",") { fmt(it.lon) }
            heights += withRetry { json.parseToJsonElement(http.get(url)).jsonObject["elevation"]!!.jsonArray.map { it.jsonPrimitive.double } }
            delay(500)
        }
        plan.withHeights(heights)
    }

    private suspend fun <T> withRetry(block: () -> T): T {
        var wait = 20_000L
        repeat(3) {
            try {
                return block()
            } catch (e: HttpException) {
                if (e.code != 429) throw e
                delay(wait)
                wait *= 2
            }
        }
        return block()
    }

    private fun fmt(v: Double) = String.format(Locale.ROOT, "%.6f", v)
}
