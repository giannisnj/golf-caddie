package com.golfcaddie.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * What the watch shows, already formatted by the phone (units, wording), so the watch app
 * stays a simple display. Sent over the Wear OS Data Layer as JSON.
 */
@Serializable
data class WatchState(
    /** e.g. "Hole 7 · Par 3"; null when there's no course or position yet. */
    val hole: String? = null,
    val front: String? = null,
    val center: String? = null,
    val back: String? = null,
    val unit: String = "yd",
    /** e.g. "Plays 141 · into wind"; null when no adjustment applies. */
    val playsLike: String? = null,
    val club: String? = null,
    /** e.g. "138–152"; your usual range with that club. */
    val clubRange: String? = null,
    /** First hazard warning, if any. */
    val warning: String? = null,
    /** Shown instead of distances when there's nothing to measure yet. */
    val status: String? = null,
    /** Clubs you can start a shot with from the watch: id to name. */
    val clubs: List<WatchClub> = emptyList(),
    val recommendedClubId: String? = null,
    /** e.g. "7 Iron · 23 yd so far" while a shot is being walked. */
    val shotInProgress: String? = null,
) {
    fun encode(): String = json.encodeToString(serializer(), this)

    companion object {
        const val DATA_PATH = "/caddie/state"
        const val DATA_KEY = "json"
        /** Watch → phone: start a shot; payload is the club id. */
        const val START_SHOT_PATH = "/caddie/shot/start"
        /** Watch → phone: the ball is here. */
        const val FINISH_SHOT_PATH = "/caddie/shot/finish"
        const val CANCEL_SHOT_PATH = "/caddie/shot/cancel"

        private val json = Json { ignoreUnknownKeys = true }
        fun decode(text: String): WatchState = json.decodeFromString(serializer(), text)
    }
}

@Serializable
data class WatchClub(val id: String, val name: String)
