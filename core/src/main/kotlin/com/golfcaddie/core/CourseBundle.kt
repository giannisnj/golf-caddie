package com.golfcaddie.core

import kotlinx.serialization.Serializable

/**
 * Every course with mapped holes in one region, downloaded ahead of time and shipped inside the
 * app. Finding one of these needs neither a signal nor the Overpass servers, which can be slow
 * or down for hours. Elevation grids are included, so slopes work straight away too.
 */
@Serializable
data class CourseBundle(
    val region: String,
    val center: GeoPoint,
    /** Every course with mapped holes within this distance of [center] is in [courses]. */
    val radiusMeters: Double,
    /** When the OpenStreetMap data was downloaded, e.g. "2026-10-20". */
    val downloaded: String,
    val courses: List<Course>,
) {
    /** True when a search circle lies wholly inside the region, so [near] is the full answer. */
    fun covers(p: GeoPoint, searchRadiusMeters: Int): Boolean =
        Geo.distance(center, p) + searchRadiusMeters <= radiusMeters

    /**
     * The courses an Overpass search of [searchRadiusMeters] around [p] would return: those with
     * a hole or outline point within reach, nearest first.
     */
    fun near(p: GeoPoint, searchRadiusMeters: Int): List<Course> = Overpass.nearestFirst(
        courses.filter { c ->
            c.contains(p) || (c.areas.flatten() + c.holes.flatMap { it.line })
                .any { Geo.distance(it, p) <= searchRadiusMeters }
        },
        p,
    )

    /**
     * The course a search like "yowani" or "royal canberra golf" means: every word of [query]
     * appears in its name. Words every course shares ("golf", "club") don't count, so a query
     * of only those matches nothing.
     */
    fun named(query: String): Course? {
        val words = words(query) - GENERIC
        if (words.isEmpty()) return null
        return courses.filter { c -> words(c.name).containsAll(words) }.maxByOrNull { it.holes.size }
    }
}

private val GENERIC = setOf("golf", "club", "course", "country", "the", "gc", "cc")

private fun words(s: String) = s.lowercase().split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() }.toSet()
