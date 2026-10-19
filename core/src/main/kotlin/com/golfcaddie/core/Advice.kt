package com.golfcaddie.core

/** A caddie remark about a hazard and the club you're about to hit. Distances are ground meters. */
sealed interface Advice {
    val hazard: HazardOnLine

    /** About [chance] of your shots with [club] finish in [hazard]: they get there but don't carry it. */
    data class InPlay(override val hazard: HazardOnLine, val club: ClubStats, val chance: Double) : Advice

    /** [club] carries [hazard] on all but the weakest strikes, without flying the green. */
    data class CarryWith(override val hazard: HazardOnLine, val club: ClubStats) : Advice

    /** [club] stays short of [hazard] on all but the best strikes. */
    data class LayUpWith(override val hazard: HazardOnLine, val club: ClubStats) : Advice
}

/**
 * Hazard advice from your own spread. Each club's distance is treated as a bell curve fitted
 * to its usual range; a ball is in a hazard when it gets past the near edge but doesn't carry
 * the far edge (carry = total distance × the club type's carry fraction).
 */
object HazardAdvisor {
    /** Mention a hazard when at least this share of your shots would end up in it. */
    const val WARN_CHANCE = 0.20
    /** A carry or lay-up club is "safe" when no more than this share would find the hazard. */
    const val SAFE_CHANCE = 0.05
    /** Laying up closer than this is pointless; it's a chip, not a lay-up. */
    private const val MIN_LAYUP_M = 50.0
    /**
     * A hazard beside the line (not across it) also needs the ball to miss to that side.
     * A rough share, until left/right spread is modelled per club.
     */
    const val BESIDE_LINE_SHARE = 0.4

    /**
     * Share of [club]'s shots that finish in [h]. Across the line: the ball gets past the near
     * edge without carrying the far edge (it can land short and roll in). Beside the line: the
     * ball stops level with it and has missed to that side; a ball rolling on down the line
     * passes it, so carry doesn't matter.
     */
    fun chanceInHazard(club: ClubStats, h: HazardOnLine, factor: Double = 1.0): Double {
        val reachesIt = 1 - club.chanceShorterThan(h.reachMeters, factor)
        return if (h.onLine) {
            val doesNotCarry = club.chanceShorterThan(h.carryMeters / club.club.type.carryFraction, factor)
            (doesNotCarry + reachesIt - 1).coerceAtLeast(0.0)
        } else {
            val stopsLevel = club.chanceShorterThan(h.carryMeters, factor) + reachesIt - 1
            stopsLevel.coerceAtLeast(0.0) * BESIDE_LINE_SHARE
        }
    }

    /**
     * Advice for the recommended club against hazards ahead, nearest first, at most [max] warnings.
     * [conditionFactor] (see [PlaysLike.conditionFactor]) scales club distances so wind, slope and
     * temperature count for hazards the same way they do for the green.
     */
    fun advise(
        situation: Situation,
        recommendation: Recommendation,
        bag: Bag,
        conditionFactor: Double = 1.0,
        max: Int = 2,
    ): List<Advice> {
        val pick = recommendation.club ?: return emptyList()
        if (recommendation.kind == Recommendation.Kind.PUTT || recommendation.kind == Recommendation.Kind.PARTIAL_SWING) {
            return emptyList()
        }
        val clubs = bag.clubs.filter { it.inBag && it.type != ClubType.PUTTER }
            .map { ClubStats.of(it, bag.shots) }
            .sortedBy { it.typicalMeters }
        val f = conditionFactor
        val greenBack = situation.green.back

        val advice = mutableListOf<Advice>()
        for (h in situation.hazards.sortedBy { it.reachMeters }) {
            val chance = chanceInHazard(pick, h, f)
            if (chance < WARN_CHANCE) continue
            advice += Advice.InPlay(h, pick, chance)
            // Carrying it or laying up short only makes sense when it's across the line; for a
            // hazard to one side, the answer is to aim away from it (the warning says which side).
            // A carry is only offered when the far edge is short of the back of the green (you
            // can't "carry" water that runs past the flag), with a club that doesn't fly the green.
            if (h.onLine && h.carryMeters < greenBack) {
                clubs.firstOrNull { c ->
                    c.chanceShorterThan(h.carryMeters / c.club.type.carryFraction, f) <= SAFE_CHANCE &&
                        c.chanceShorterThan(greenBack + 10, f) >= 0.5
                }?.takeIf { it.club.id != pick.club.id }?.let { advice += Advice.CarryWith(h, it) }
            }
            if (h.onLine && h.reachMeters >= MIN_LAYUP_M) {
                clubs.lastOrNull { c -> 1 - c.chanceShorterThan(h.reachMeters, f) <= SAFE_CHANCE }
                    ?.let { advice += Advice.LayUpWith(h, it) }
            }
            if (advice.count { it is Advice.InPlay } >= max) break
        }
        return advice
    }
}
