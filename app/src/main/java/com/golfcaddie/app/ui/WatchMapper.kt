package com.golfcaddie.app.ui

import com.golfcaddie.core.Advice
import com.golfcaddie.core.ClubType
import com.golfcaddie.core.Geo
import com.golfcaddie.core.Recommendation
import com.golfcaddie.core.WatchClub
import com.golfcaddie.core.WatchState
import kotlin.math.roundToInt

/** What the watch shows for this phone state; same wording as the phone. */
fun UiState.toWatchState(): WatchState {
    val u = unit
    fun n(m: Double) = u.fromMeters(m).roundToInt().toString()
    val clubs = bag.clubs.filter { it.inBag && it.type != ClubType.PUTTER }.map { WatchClub(it.id, it.name) }
    val shot = pendingShot?.let { p ->
        val soFar = position?.let { Geo.distance(p.start, it) } ?: 0.0
        "${p.club.name} · ${formatDistance(soFar, u)} so far"
    }
    val s = situation
    if (course == null || s == null) {
        val status = when {
            course == null -> "Choose a course on your phone"
            position == null && simulate -> "Tap the map on your phone to place yourself"
            position == null -> "Waiting for GPS…"
            else -> "No hole found here"
        }
        return WatchState(status = status, unit = u.label, clubs = clubs, shotInProgress = shot)
    }
    val hole = s.hole
    val rec = recommendation
    val club = rec?.club
    return WatchState(
        hole = "Hole ${hole.number}" + (hole.par?.let { " · Par $it" } ?: ""),
        front = n(s.green.front),
        center = n(s.green.center),
        back = n(s.green.back),
        unit = u.label,
        playsLike = playsLike?.let { CaddieText.playsLike(it, u) }?.replace("Plays like", "Plays"),
        club = club?.let {
            when (rec.kind) {
                Recommendation.Kind.PUTT -> "Putter"
                Recommendation.Kind.PARTIAL_SWING -> "${it.club.name} (partial)"
                else -> it.club.name
            }
        },
        clubRange = club?.takeIf { rec.kind != Recommendation.Kind.PUTT }?.let { CaddieText.range(it, u) },
        warning = advice.filterIsInstance<Advice.InPlay>().firstOrNull()?.let { a ->
            CaddieText.hazardSpan(a.hazard, u) + CaddieText.sideOf(a.hazard) + ": " + CaddieText.chance(a.chance)
        },
        clubs = clubs,
        recommendedClubId = club?.club?.id?.takeIf { rec.kind != Recommendation.Kind.PUTT },
        shotInProgress = shot,
    )
}
