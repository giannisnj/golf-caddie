package com.golfcaddie.app.ui

import com.golfcaddie.core.Advice
import com.golfcaddie.core.ClubStats
import com.golfcaddie.core.DistanceUnit
import com.golfcaddie.core.HazardOnLine
import com.golfcaddie.core.HazardType
import com.golfcaddie.core.PlaysLike
import kotlin.math.abs
import kotlin.math.roundToInt

/** Wording shared by the phone screen and the watch, so both say the same thing. */
object CaddieText {
    private fun n(meters: Double, unit: DistanceUnit) = unit.fromMeters(meters).roundToInt()

    /** "Plays like 141 yd", or null when nothing changes the distance by a yard or more. */
    fun playsLike(p: PlaysLike, unit: DistanceUnit): String? {
        if (!p.adjusted || n(p.playsMeters, unit) == n(p.actualMeters, unit)) return null
        return "Plays like ${formatDistance(p.playsMeters, unit)}"
    }

    /** Why: "uphill +4 · into wind +6 (14 km/h) · cool +1". */
    fun playsLikeWhy(p: PlaysLike, unit: DistanceUnit): String {
        fun signed(m: Double) = (if (m >= 0) "+" else "−") + abs(n(m, unit))
        val parts = mutableListOf<String>()
        p.riseMeters?.let { rise ->
            if (abs(n(p.elevationEffect, unit)) >= 1) parts += (if (rise > 0) "uphill " else "downhill ") + signed(p.elevationEffect)
        }
        if (abs(n(p.windEffect, unit)) >= 1) {
            val kmh = (abs(p.headwindMs) * 3.6).roundToInt()
            parts += (if (p.headwindMs > 0) "into wind " else "downwind ") + signed(p.windEffect) + " ($kmh km/h)"
        }
        val crossKmh = (abs(p.crosswindFromRightMs) * 3.6).roundToInt()
        if (crossKmh >= 8) parts += "cross wind from the " + (if (p.crosswindFromRightMs > 0) "right" else "left") + " $crossKmh km/h"
        if (abs(n(p.temperatureEffect, unit)) >= 1) {
            parts += (if (p.temperatureEffect > 0) "cool " else "warm ") + signed(p.temperatureEffect)
        }
        return parts.joinToString(" · ")
    }

    fun range(stats: ClubStats, unit: DistanceUnit) =
        "${n(stats.lowMeters, unit)}–${n(stats.highMeters, unit)} ${unit.label}"

    /** "tends 6 yd right", "straight", or null without enough walked shots. */
    fun side(stats: ClubStats, unit: DistanceUnit): String? {
        val side = stats.sideMeters ?: return null
        val amount = abs(n(side, unit))
        return if (amount < 3) "straight on average" else "tends $amount ${unit.label} ${if (side > 0) "right" else "left"}"
    }

    private fun hazardName(h: HazardOnLine) = when (h.type) {
        HazardType.BUNKER -> "Bunker"
        HazardType.WATER -> "Water"
        HazardType.LATERAL_WATER -> "Water"
    }

    fun hazardSpan(h: HazardOnLine, unit: DistanceUnit) =
        "${hazardName(h)} ${n(h.reachMeters, unit)}–${n(h.carryMeters, unit)}"

    /** "about 3 times in 10". */
    fun chance(p: Double) = "about ${(p * 10).roundToInt().coerceIn(1, 10)} times in 10"

    /** "", " right", " left": where a hazard sits relative to the line to the green. */
    fun sideOf(h: HazardOnLine) = when (h.side) {
        -1 -> " left"
        1 -> " right"
        else -> ""
    }

    fun advice(a: Advice, unit: DistanceUnit): String = when (a) {
        is Advice.InPlay -> {
            val h = a.hazard
            val where = if (h.onLine) "" else ", ${sideOf(h).trim()} of the line"
            "${hazardSpan(h, unit)} ${unit.label}$where: your ${a.club.club.name} finishes there " +
                "${chance(a.chance)}" + if (h.onLine) "" else ". Aim away from it."
        }
        is Advice.CarryWith -> "${a.club.club.name} carries it"
        is Advice.LayUpWith -> "${a.club.club.name} lays up short of it"
    }
}
