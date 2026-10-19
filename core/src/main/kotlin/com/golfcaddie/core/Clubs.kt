package com.golfcaddie.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
enum class ClubType { DRIVER, WOOD, HYBRID, IRON, WEDGE, PUTTER }

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
)

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
) {
    companion object {
        const val MIN_SHOTS = 3
        /** Most shots fall short of a personal best; a full swing typically travels ~95% of max. */
        const val TYPICAL_FRACTION_OF_MAX = 0.95
        /** Only the most recent shots count, so the numbers follow your current game. */
        const val RECENT_SHOTS = 20
        /** Shots shorter than this fraction of the median are treated as mishits (tops, chunks). */
        const val MISHIT_FRACTION = 0.75

        fun of(club: Club, allShots: List<ShotRecord>): ClubStats {
            val shots = allShots.filter { it.clubId == club.id }
                .sortedByDescending { it.timestampMillis }
                .take(RECENT_SHOTS)
                .map { it.distanceMeters }
            val shotMax = shots.maxOrNull()
            val max = listOfNotNull(shotMax, club.manualMaxMeters).maxOrNull()

            if (shots.size >= MIN_SHOTS) {
                val median = shots.sorted()[shots.size / 2]
                val solid = shots.filter { it >= median * MISHIT_FRACTION }
                val avg = solid.average()
                return ClubStats(club, shots.size, max, avg, avg, StatSource.HISTORY)
            }
            if (max != null) {
                return ClubStats(club, shots.size, max, shots.takeIf { it.isNotEmpty() }?.average(),
                    max * TYPICAL_FRACTION_OF_MAX, StatSource.MAX_ONLY)
            }
            return ClubStats(club, 0, null, null, club.defaultMeters, StatSource.DEFAULT)
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
