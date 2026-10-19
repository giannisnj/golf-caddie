package com.golfcaddie.core.cli

import com.golfcaddie.core.Bag
import com.golfcaddie.core.Caddie
import com.golfcaddie.core.ClubRecommender
import com.golfcaddie.core.DistanceUnit
import com.golfcaddie.core.GeoPoint
import com.golfcaddie.core.Overpass
import com.golfcaddie.core.Simulator
import java.io.File
import kotlin.math.roundToInt

/**
 * Desktop check of a real course with a faked position.
 *
 *   ./gradlew -PcoreOnly :core:run --args="course.json"               walk every hole from the tee
 *   ./gradlew -PcoreOnly :core:run --args="course.json 56.3440 -2.8060"   stand at a lat/lon
 *
 * course.json is an Overpass response; tools/fetch_course.sh downloads one.
 */
fun main(args: Array<String>) {
    require(args.isNotEmpty()) { "usage: <overpass.json> [lat lon]" }
    val courses = Overpass.parse(File(args[0]).readText())

    if (courses.isEmpty()) return println("No golf holes found in ${args[0]}")
    for (course in courses) {
        println("== ${course.name}: ${course.holes.size} holes, ${course.hazards.size} hazards")
        play(course, args)
    }
}

private fun play(course: com.golfcaddie.core.Course, args: Array<String>) {
    val bag = Bag.default()
    val unit = DistanceUnit.YARDS
    fun yd(m: Double) = "${unit.fromMeters(m).roundToInt()}${unit.label}"

    fun label(h: com.golfcaddie.core.Hole) = "Hole ${h.number}" + (h.name?.let { " ($it)" } ?: "")

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
        val rec = ClubRecommender.recommend(s.targetMeters, bag, s.onGreen)
        val hz = s.hazards.joinToString { "${it.type.name.lowercase()} ${yd(it.reachMeters)}-${yd(it.carryMeters)}" }
        val wrong = expected != null && expected != hole.id
        if (wrong) mismatches++
        println(
            "%-40s par %s  F/C/B %s/%s/%s%s  -> %s%s%s".format(
                name, hole.par ?: "?", yd(s.green.front), yd(s.green.center), yd(s.green.back),
                s.green.pin?.let { "  pin ${yd(it)}" } ?: "",
                rec.club?.club?.name ?: "-", if (hz.isEmpty()) "" else "  [$hz]",
                if (wrong) "   !! detected ${label(hole)}" else "",
            ),
        )
    }
    if (args.size < 3) println("Hole detection: ${spots.size - mismatches}/${spots.size} spots on the expected hole")
}
