package com.golfcaddie.app.data

import android.content.Context
import com.golfcaddie.core.DistanceUnit

/** Small preferences that should survive the app being closed mid-round. */
class Settings(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)

    var unit: DistanceUnit
        get() = runCatching { DistanceUnit.valueOf(prefs.getString("unit", null)!!) }.getOrDefault(DistanceUnit.YARDS)
        set(value) = prefs.edit().putString("unit", value.name).apply()

    var satellite: Boolean
        get() = prefs.getBoolean("satellite", true)
        set(value) = prefs.edit().putBoolean("satellite", value).apply()

    /** OSM id of the course picked last, so a restart reopens it rather than the first search result. */
    var courseId: Long?
        get() = if (prefs.contains("courseId")) prefs.getLong("courseId", 0) else null
        set(value) = prefs.edit().apply { if (value == null) remove("courseId") else putLong("courseId", value) }.apply()
}
