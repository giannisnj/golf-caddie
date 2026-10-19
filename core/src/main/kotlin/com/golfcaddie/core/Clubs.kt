package com.golfcaddie.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
enum class ClubType(
    /** Carry as a share of total distance (carry + roll). Rules of thumb; longer clubs roll more. */
    val carryFraction: Double,
    /** Typical spread either side of a club's normal distance until you've recorded enough shots. */
    val defaultSpreadFraction: Double,
) {
    DRIVER(0.86, 0.08),
    WOOD(0.88, 0.08),
    HYBRID(0.90, 0.07),
    IRON(0.93, 0.06),
    WEDGE(0.95, 0.06),
    PUTTER(1.0, 0.0),
}

@Serializable
data class Club(
    val id: String,
    val name: String,
    val type: ClubType,
    /** Typical amateur distance, used only until you have your own data for this club. */
    val defaultMeters: Double,
    /** Your best distance if you typed it in yourself. */
    val manualMaxMeters: Double? = null,
    val inBag: Boolean = true,
)

@Serializable
data class ShotRecord(
    val id: String,
    val clubId: String,
    val distanceMeters: Double,
    val timestampMillis: Long,
    /** Set when the shot was measured by walking it with GPS. */
    val start: GeoPoint? = null,
    val end: GeoPoint? = null,
    /** Where you were aiming when you started the shot, for left/right tendency. */
    val aim: GeoPoint? = null,
) {
    /** Meters right (+) or left (-) of the aim line where the ball finished, when known. */
    val sideMeters: Double?
        get() {
            if (start == null || end == null || aim == null) return null
            val (ax, ay) = Geo.toLocal(start, aim)
            val len = kotlin.math.sqrt(ax * ax + ay * ay)
            if (len < 1.0) return null
            val (ex, ey) = Geo.toLocal(start, end)
            return (ex * ay - ey * ax) / len
        }
}

@Serializable
data class Bag(val clubs: List<Club>, val shots: List<ShotRecord> = emptyList()) {
    fun encode(): String = json.encodeToString(serializer(), this)

    companion object {
        private val json = Json { ignoreUnknownKeys = true; prettyPrint = true }
        fun decode(text: String): Bag = json.decodeFromString(serializer(), text)

        private fun yd(v: Int) = v * 0.9144

        /** A starting 14-club bag with average amateur distances; edit to match yours. */
        fun default(): Bag = Bag(
            listOf(
                Club("driver", "Driver", ClubType.DRIVER, yd(225)),
                Club("3w", "3 Wood", ClubType.WOOD, yd(205)),
                Club("5w", "5 Wood", ClubType.WOOD, yd(190)),
                Club("4h", "4 Hybrid", ClubType.HYBRID, yd(180)),
                Club("5i", "5 Iron", ClubType.IRON, yd(165)),
                Club("6i", "6 Iron", ClubType.IRON, yd(155)),
                Club("7i", "7 Iron", ClubType.IRON, yd(145)),
                Club("8i", "8 Iron", ClubType.IRON, yd(135)),
                Club("9i", "9 Iron", ClubType.IRON, yd(125)),
                Club("pw", "Pitching Wedge", ClubType.WEDGE, yd(112)),
                Club("gw", "Gap Wedge", ClubType.WEDGE, yd(100)),
                Club("sw", "Sand Wedge", ClubType.WEDGE, yd(85)),
                Club("lw", "Lob Wedge", ClubType.WEDGE, yd(65)),
                Club("putter", "Putter", ClubType.PUTTER, 0.0),
            ),
        )
    }
}

enum class StatSource {
    /** Built from at least [ClubStats.MIN_SHOTS] recorded shots. */
    HISTORY,
    /** From your max distance (typed in, or best of a few shots). */
    MAX_ONLY,
    /** No data from you yet: average amateur distance. */
    DEFAULT,
}

data class ClubStats(
    val club: Club,
    val shotCount: Int,
    val maxMeters: Double?,
    val averageMeters: Double?,
    /** The distance the recommender plans around: what this club normally goes for you. */
    val typicalMeters: Double,
    val source: StatSource,
    /** Your usual range: most shots (about 6 in 10) finish between these. */
    val lowMeters: Double = typicalMeters,
    val highMeters: Double = typicalMeters,
    /** True when the range comes from your shots; otherwise it's a typical spread for the club type. */
    val rangeFromShots: Boolean = false,
    /** Average finish right (+) or left (-) of where you aimed, from GPS-walked shots. */
    val sideMeters: Double? = null,
    val sideShots: Int = 0,
) {
    /**
     * Spread as a standard deviation. The usual range is the 20th to 80th percentile, which on
     * a bell curve spans 1.68 standard deviations.
     */
    val sdMeters: Double get() = ((highMeters - lowMeters) / 1.683).coerceAtLeast(1.0)

    /** Chance a full swing finishes (total distance) shorter than [meters], with distances scaled by [factor]. */
    fun chanceShorterThan(meters: Double, factor: Double = 1.0): Double =
        Stats.normalCdf((meters - typicalMeters * factor) / (sdMeters * factor))

    companion object {
        const val MIN_SHOTS = 3
        /** Most shots fall short of a personal best; a full swing typically travels ~95% of max. */
        const val TYPICAL_FRACTION_OF_MAX = 0.95
        /** Only the most recent shots count, so the numbers follow your current game. */
        const val RECENT_SHOTS = 20
        /** Shots shorter than this fraction of the median are treated as mishits (tops, chunks). */
        const val MISHIT_FRACTION = 0.75
        /** Anything shorter is a mis-tap or GPS noise, not a full shot with this club. */
        const val MIN_SHOT_METERS = 10.0
        /** Solid shots needed before the range comes from your own shots. */
        const val MIN_RANGE_SHOTS = 5
        /** GPS-walked shots with an aim needed before showing a left/right tendency. */
        const val MIN_SIDE_SHOTS = 3

        fun of(club: Club, allShots: List<ShotRecord>): ClubStats {
            val recent = allShots.filter { it.clubId == club.id && it.distanceMeters >= MIN_SHOT_METERS }
                .sortedByDescending { it.timestampMillis }
                .take(RECENT_SHOTS)
            val shots = recent.map { it.distanceMeters }
            val shotMax = shots.maxOrNull()
            val max = listOfNotNull(shotMax, club.manualMaxMeters?.takeIf { it >= MIN_SHOT_METERS }).maxOrNull()

            val median = shots.sorted().getOrNull(shots.size / 2)
            val solidShots = recent.filter { median == null || it.distanceMeters >= median * MISHIT_FRACTION }
            val sides = solidShots.mapNotNull { it.sideMeters }
            val side = sides.takeIf { it.size >= MIN_SIDE_SHOTS }?.average()

            val base = when {
                shots.size >= MIN_SHOTS -> {
                    val avg = solidShots.map { it.distanceMeters }.average()
                    ClubStats(club, shots.size, max, avg, avg, StatSource.HISTORY)
                }
                max != null -> ClubStats(
                    club, shots.size, max, shots.takeIf { it.isNotEmpty() }?.average(),
                    max * TYPICAL_FRACTION_OF_MAX, StatSource.MAX_ONLY,
                )
                else -> ClubStats(club, 0, null, null, club.defaultMeters, StatSource.DEFAULT)
            }
            val solid = solidShots.map { it.distanceMeters }.sorted()
            return if (solid.size >= MIN_RANGE_SHOTS) {
                base.copy(
                    lowMeters = percentile(solid, 0.2), highMeters = percentile(solid, 0.8),
                    rangeFromShots = true, sideMeters = side, sideShots = sides.size,
                )
            } else {
                val spread = base.typicalMeters * club.type.defaultSpreadFraction
                base.copy(
                    lowMeters = base.typicalMeters - spread, highMeters = base.typicalMeters + spread,
                    sideMeters = side, sideShots = sides.size,
                )
            }
        }

        /** Linear-interpolated percentile of an ascending list. */
        internal fun percentile(sorted: List<Double>, q: Double): Double {
            val pos = q * (sorted.size - 1)
            val i = pos.toInt()
            val frac = pos - i
            return if (i + 1 < sorted.size) sorted[i] * (1 - frac) + sorted[i + 1] * frac else sorted[i]
        }
    }
}

data class Recommendation(
    val targetMeters: Double,
    /** The club to hit, or null if the bag is empty. */
    val club: ClubStats?,
    /** Shorter club worth considering: reaches only with your best swing. */
    val shorterOption: ClubStats?,
    /** Longer club worth considering: a smooth, controlled swing. */
    val longerOption: ClubStats?,
    val kind: Kind,
    /** For [Kind.CANNOT_REACH]: meters left after your longest typical club. */
    val leftAfterMeters: Double? = null,
) {
    enum class Kind { FULL_SWING, PARTIAL_SWING, CANNOT_REACH, PUTT }
}

object ClubRecommender {
    /** A club this close below the target still counts as reaching it (meters). */
    private const val REACH_TOLERANCE_M = 3.0

    fun recommend(targetMeters: Double, bag: Bag, onGreen: Boolean = false): Recommendation {
        val stats = bag.clubs.filter { it.inBag }.map { ClubStats.of(it, bag.shots) }
        if (onGreen) {
            val putter = stats.firstOrNull { it.club.type == ClubType.PUTTER }
            return Recommendation(targetMeters, putter, null, null, Recommendation.Kind.PUTT)
        }
        val full = stats.filter { it.club.type != ClubType.PUTTER && it.typicalMeters > 0 }
            .sortedBy { it.typicalMeters }
        if (full.isEmpty()) return Recommendation(targetMeters, null, null, null, Recommendation.Kind.FULL_SWING)

        val idx = full.indexOfFirst { it.typicalMeters >= targetMeters - REACH_TOLERANCE_M }
        if (idx == -1) {
            val longest = full.last()
            return Recommendation(
                targetMeters, longest, null, null,
                Recommendation.Kind.CANNOT_REACH,
                leftAfterMeters = targetMeters - longest.typicalMeters,
            )
        }

        val pick = full[idx]
        val shorter = full.getOrNull(idx - 1)?.takeIf { (it.maxMeters ?: 0.0) >= targetMeters }
        val longer = full.getOrNull(idx + 1)
        // Well short of the shortest club's full distance: a partial wedge, not a full swing.
        val kind = if (idx == 0 && targetMeters < pick.typicalMeters * 0.8) {
            Recommendation.Kind.PARTIAL_SWING
        } else {
            Recommendation.Kind.FULL_SWING
        }
        return Recommendation(targetMeters, pick, shorter, longer, kind)
    }
}

internal object Stats {
    /** Standard normal CDF (Abramowitz & Stegun 7.1.26, error < 1.5e-7). */
    fun normalCdf(z: Double): Double {
        val x = kotlin.math.abs(z) / kotlin.math.sqrt(2.0)
        val t = 1.0 / (1.0 + 0.3275911 * x)
        val erf = 1.0 - (((((1.061405429 * t - 1.453152027) * t) + 1.421413741) * t - 0.284496736) * t + 0.254829592) *
            t * kotlin.math.exp(-x * x)
        return if (z >= 0) 0.5 * (1 + erf) else 0.5 * (1 - erf)
    }
}
