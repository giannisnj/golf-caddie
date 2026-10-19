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
    val bag = Bag.default()
    val unit = DistanceUnit.YARDS
    fun yd(m: Double) = "${unit.fromMeters(m).roundToInt()}${unit.label}"

    if (courses.isEmpty()) return println("No golf holes found in ${args[0]}")
    courses.forEach { println("${it.name}: ${it.holes.size} holes, ${it.hazards.size} hazards") }
    val course = courses.first()

    val spots: List<Pair<String, GeoPoint>> = if (args.size >= 3) {
        listOf("You" to GeoPoint(args[1].toDouble(), args[2].toDouble()))
    } else {
        course.holes.flatMap { h ->
            listOf("Hole ${h.number} tee" to Simulator.atTee(h)) +
                listOf(150.0, 60.0).filter { it < h.lengthMeters }
                    .map { "Hole ${h.number}, ${yd(it)} out on the line" to Simulator.onLine(h, it) }
        }
    }
    for ((label, p) in spots) {
        val hole = Caddie.locateHole(course, p) ?: continue
        val s = Caddie.situation(course, hole, p)
        val rec = ClubRecommender.recommend(s.targetMeters, bag, s.onGreen)
        val hz = s.hazards.joinToString { "${it.type.name.lowercase()} ${yd(it.reachMeters)}-${yd(it.carryMeters)}" }
        println(
            "%-32s hole %2d par %s  F/C/B %s/%s/%s  -> %s%s".format(
                label, hole.number, hole.par ?: "?", yd(s.green.front), yd(s.green.center), yd(s.green.back),
                rec.club?.club?.name ?: "-", if (hz.isEmpty()) "" else "  [$hz]",
            ),
        )
    }
}
