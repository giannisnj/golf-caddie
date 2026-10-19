package com.golfcaddie.app.data

import android.content.Context
import com.golfcaddie.app.map.MapStyle
import com.golfcaddie.core.DistanceUnit

/** Small preferences that should survive the app being closed mid-round. */
class Settings(context: Context) {
    private val prefs = context.getSharedPreferences(NAME, Context.MODE_PRIVATE)

    var unit: DistanceUnit
        get() = runCatching { DistanceUnit.valueOf(prefs.getString("unit", null)!!) }.getOrDefault(DistanceUnit.YARDS)
        set(value) = prefs.edit().putString("unit", value.name).apply()

    var mapStyle: MapStyle
        get() = runCatching { MapStyle.valueOf(prefs.getString("mapStyle", null)!!) }.getOrElse {
            // Older versions only had a satellite on/off switch.
            if (prefs.getBoolean("satellite", true)) MapStyle.SATELLITE else MapStyle.STREET
        }
        set(value) = prefs.edit().putString("mapStyle", value.name).apply()

    /** Fetch wind and temperature for "plays like" distances (needs a data connection). */
    var liveWeather: Boolean
        get() = prefs.getBoolean(LIVE_WEATHER, true)
        set(value) = prefs.edit().putBoolean(LIVE_WEATHER, value).apply()

    /** OSM id of the course picked last, so a restart reopens it rather than the first search result. */
    var courseId: Long?
        get() = if (prefs.contains("courseId")) prefs.getLong("courseId", 0) else null
        set(value) = prefs.edit().apply { if (value == null) remove("courseId") else putLong("courseId", value) }.apply()

    companion object {
        const val NAME = "settings"
        const val LIVE_WEATHER = "liveWeather"
    }
}
