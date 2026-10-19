package com.golfcaddie.core.cli

import com.golfcaddie.core.Advice
import com.golfcaddie.core.Bag
import com.golfcaddie.core.Caddie
import com.golfcaddie.core.ClubRecommender
import com.golfcaddie.core.Conditions
import com.golfcaddie.core.Course
import com.golfcaddie.core.DistanceUnit
import com.golfcaddie.core.ElevationGrid
import com.golfcaddie.core.GeoPoint
import com.golfcaddie.core.HazardAdvisor
import com.golfcaddie.core.Hole
import com.golfcaddie.core.Overpass
import com.golfcaddie.core.PlaysLikeCalculator
import com.golfcaddie.core.Simulator
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.double
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.net.URI
import kotlin.math.roundToInt

/**
 * Desktop check of a real course with a faked position.
 *
 *   ./gradlew -PcoreOnly :core:run --args="course.json"                  walk every hole from the tee
 *   ./gradlew -PcoreOnly :core:run --args="course.json 56.3440 -2.8060"  stand at a lat/lon
 *   ./gradlew -PcoreOnly :core:run --args="course.json --elevation=grid.json --wind=5@200 --temp=12"
 *   ./gradlew -PcoreOnly :core:run --args="elevation course.json grid.json"  fetch heights (Open-Meteo)
 *
 * course.json is an Overpass response; tools/fetch_course.sh downloads one.
 * --wind=<m/s>@<degrees the wind blows from>.
 */
fun main(args: Array<String>) {
    require(args.isNotEmpty()) { "usage: [elevation] <overpass.json> [lat lon] [--elevation=grid.json] [--wind=ms@deg] [--temp=C]" }
    if (args[0] == "elevation") return fetchElevation(args[1], args[2])

    val options = args.filter { it.startsWith("--") }.associate { it.removePrefix("--").substringBefore("=") to it.substringAfter("=") }
    val positional = args.filterNot { it.startsWith("--") }
    val grid = options["elevation"]?.let { Json.decodeFromString(ElevationGrid.serializer(), File(it).readText()) }
    val conditions = options["wind"]?.let { w ->
        Conditions(w.substringBefore("@").toDouble(), w.substringAfter("@").toDouble(), options["temp"]?.toDouble() ?: 21.0, 0)
    } ?: options["temp"]?.let { Conditions(0.0, 0.0, it.toDouble(), 0) }

    val courses = Overpass.parse(File(positional[0]).readText())
    if (courses.isEmpty()) return println("No golf holes found in ${positional[0]}")
    for (course in courses) {
        println("== ${course.name}: ${course.holes.size} holes, ${course.hazards.size} hazards")
        play(course.copy(elevation = grid), positional, conditions)
    }
}

/** Fetches an elevation grid for the course with the most holes in [overpassFile]. */
private fun fetchElevation(overpassFile: String, outFile: String) {
    val course = Overpass.parse(File(overpassFile).readText()).maxBy { it.holes.size }
    val plan = ElevationGrid.planFor(course)
    println("${course.name}: ${plan.rows} x ${plan.cols} grid, ${plan.points.size} points")
    val heights = plan.points.chunked(ElevationGrid.POINTS_PER_REQUEST).flatMap { chunk ->
        val url = "https://api.open-meteo.com/v1/elevation?latitude=" + chunk.joinToString(",") { "%.6f".format(it.lat) } +
            "&longitude=" + chunk.joinToString(",") { "%.6f".format(it.lon) }
        val body = URI(url).toURL().readText()
        Thread.sleep(300)
        Json.parseToJsonElement(body).jsonObject["elevation"]!!.jsonArray.map { it.jsonPrimitive.double }
    }
    val grid = plan.withHeights(heights)
    File(outFile).writeText(Json.encodeToString(ElevationGrid.serializer(), grid))
    println("Heights ${heights.min()}..${heights.max()} m -> $outFile")
}

private fun play(course: Course, args: List<String>, conditions: Conditions?) {
    val bag = Bag.default()
    val unit = DistanceUnit.YARDS
    fun yd(m: Double) = "${unit.fromMeters(m).roundToInt()}${unit.label}"

    fun label(h: Hole) = "Hole ${h.number}" + (h.name?.let { " ($it)" } ?: "")

    // Each spot remembers which hole it was generated on, so wrong hole detection shows up.
    val spots: List<Triple<String, GeoPoint, Long?>> = if (args.size >= 3) {
        listOf(Triple("You", GeoPoint(args[1].toDouble(), args[2].toDouble()), null))
    } else {
        course.holes.flatMap { h ->
            listOf(Triple("${label(h)} tee", Simulator.atTee(h), h.id)) +
                listOf(150.0, 60.0).filter { it < h.lengthMeters }
                    .map { Triple("${label(h)}, ${yd(it)} out", Simulator.onLine(h, it), h.id) }
        }
    }
    var previous: Long? = null
    var mismatches = 0
    for ((name, p, expected) in spots) {
        val hole = Caddie.locateHole(course, p, previous) ?: continue
        previous = hole.id
        val s = Caddie.situation(course, hole, p)
        val plays = PlaysLikeCalculator.compute(p, hole.greenCenter, course.elevation, conditions)
        val rec = ClubRecommender.recommend(plays.playsMeters, bag, s.onGreen)
        val advice = HazardAdvisor.advise(s, rec, bag, plays.conditionFactor)
            .filterIsInstance<Advice.InPlay>()
            .joinToString { "${it.hazard.type.name.lowercase()} ${yd(it.hazard.reachMeters)}-${yd(it.hazard.carryMeters)} in range" }
        val wrong = expected != null && expected != hole.id
        if (wrong) mismatches++
        val adjust = if (plays.adjusted) {
            " plays %s%s".format(yd(plays.playsMeters), plays.riseMeters?.let { " (%+.0fm)".format(it) } ?: "")
        } else {
            ""
        }
        println(
            "%-40s par %s  F/C/B %s/%s/%s%s  -> %s%s%s".format(
                name, hole.par ?: "?", yd(s.green.front), yd(s.green.center), yd(s.green.back), adjust,
                rec.club?.club?.name ?: "-", if (advice.isEmpty()) "" else "  [$advice]",
                if (wrong) "   !! detected ${label(hole)}" else "",
            ),
        )
    }
    if (args.size < 3) println("Hole detection: ${spots.size - mismatches}/${spots.size} spots on the expected hole")
}
