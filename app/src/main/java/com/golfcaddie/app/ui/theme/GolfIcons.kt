package com.golfcaddie.app.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * The redesign's icons: 24-unit line icons with round caps, drawn from the design canvas's SVG
 * paths. Tinted like any icon (`Icon(GolfIcons.Flag, tint = ...)`).
 */
object GolfIcons {
    private class Scope(val b: ImageVector.Builder, val width: Float) {
        fun stroke(d: String, w: Float = width, alpha: Float = 1f) {
            b.addPath(
                addPathNodes(d), stroke = SolidColor(Color.Black), strokeLineWidth = w, strokeAlpha = alpha,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
            )
        }
        fun fill(d: String) = b.addPath(addPathNodes(d), fill = SolidColor(Color.Black))
        fun strokeAndFill(d: String) {
            b.addPath(
                addPathNodes(d), fill = SolidColor(Color.Black), stroke = SolidColor(Color.Black),
                strokeLineWidth = width, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
            )
        }
        fun circle(cx: Float, cy: Float, r: Float) = "M${cx - r},${cy}a$r,$r 0 1,0 ${2 * r},0a$r,$r 0 1,0 ${-2 * r},0z"
        fun ellipse(cx: Float, cy: Float, rx: Float, ry: Float) = "M${cx - rx},${cy}a$rx,$ry 0 1,0 ${2 * rx},0a$rx,$ry 0 1,0 ${-2 * rx},0z"
    }

    private fun icon(name: String, width: Float, block: Scope.() -> Unit): ImageVector {
        val b = ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f)
        Scope(b, width).block()
        return b.build()
    }

    val ChevronLeft = icon("ChevronLeft", 2.25f) { stroke("M15 5l-7 7 7 7") }
    val ChevronRight = icon("ChevronRight", 2.25f) { stroke("M9 5l7 7-7 7") }
    val More = icon("More", 0f) { fill(circle(12f, 5f, 2f)); fill(circle(12f, 12f, 2f)); fill(circle(12f, 19f, 2f)) }
    val Layers = icon("Layers", 2f) { stroke("M12 3l9 5-9 5-9-5 9-5z"); stroke("M3 13l9 5 9-5") }
    val Gps = icon("Gps", 2.25f) {
        stroke(circle(12f, 12f, 6.5f)); fill(circle(12f, 12f, 2.2f)); stroke("M12 2.5v3M12 18.5v3M2.5 12h3M18.5 12h3")
    }
    val Simulated = icon("Simulated", 2.5f) { stroke(circle(12f, 12f, 6.5f)); fill(circle(12f, 12f, 2.2f)) }
    val Warning = icon("Warning", 2.25f) {
        stroke("M12 3.5L22 20.5H2L12 3.5z"); stroke("M12 10v4.5"); strokeAndFill(circle(12f, 17.4f, 0.6f))
    }
    val Info = icon("Info", 2.25f) { stroke(circle(12f, 12f, 9f)); stroke("M12 11v5"); strokeAndFill(circle(12f, 7.8f, 0.6f)) }
    val Flag = icon("Flag", 2.25f) { stroke("M6 21V3.5"); strokeAndFill("M6 4h11.5l-3 4 3 4H6") }
    val Check = icon("Check", 2.5f) { stroke("M5 12.5l4.5 4.5L19 7.5") }
    val Close = icon("Close", 2.25f) { stroke("M6 6l12 12M18 6L6 18") }
    val Add = icon("Add", 2.5f) { stroke("M12 5v14M5 12h14") }
    val Delete = icon("Delete", 2f) { stroke("M4 7h16M10 7V4h4v3M6 7l1 13h10l1-13") }
    val Search = icon("Search", 2f) { stroke(circle(11f, 11f, 7f)); stroke("M16.5 16.5L21 21") }
    val NearMe = icon("NearMe", 2f) { stroke("M21 3L3 10.5l7.5 3 3 7.5L21 3z") }
    val Download = icon("Download", 2.25f) { stroke("M12 4v11M7 10l5 5 5-5M5 20h14") }
    val DownloadOff = icon("DownloadOff", 2f) { stroke("M12 4v11M7 10l5 5 5-5M5 20h14"); stroke("M3 3l18 18") }
    val Refresh = icon("Refresh", 2.25f) { stroke("M20 11a8 8 0 1 0-2.3 5.7"); stroke("M20 4v7h-7") }
    val Wind = icon("Wind", 2f) { stroke("M3 8h11a3 3 0 1 0-3-3"); stroke("M3 12h16a3 3 0 1 1-3 3"); stroke("M3 16h7") }
    val Thermometer = icon("Thermometer", 2f) { stroke("M10 4a2 2 0 0 1 4 0v9.5a4 4 0 1 1-4 0V4z") }
    val Uphill = icon("Uphill", 2f) { stroke("M3 19h18V7L3 19z") }
    val Downhill = icon("Downhill", 2f) { stroke("M3 19h18L3 7v12z") }
    val Pin = icon("Pin", 2.25f) { stroke("M12 21s7-6.2 7-11.5a7 7 0 0 0-14 0C5 14.8 12 21 12 21z"); stroke(circle(12f, 9.5f, 2.5f)) }
    val LocationOff = icon("LocationOff", 2f) {
        stroke("M12 21s7-6.2 7-11.5a7 7 0 0 0-14 0C5 14.8 12 21 12 21z"); stroke(circle(12f, 9.5f, 2.5f)); stroke("M4 4l16 16")
    }
    val ArrowLeft = icon("ArrowLeft", 2.25f) { stroke("M19 12H5M11 6l-6 6 6 6") }
    val ArrowRight = icon("ArrowRight", 2.25f) { stroke("M5 12h14M13 6l6 6-6 6") }
    val ArrowDown = icon("ArrowDown", 2.25f) { stroke("M12 5v14M6 13l6 6 6-6") }
    val Bunker = icon("Bunker", 2f) { stroke("M3 17c3-5 6-7 9-7s6 2 9 7H3z") }
    val Water = icon("Water", 2f) { stroke("M3 9c2-1.5 4-1.5 6 0s4 1.5 6 0 4-1.5 6 0"); stroke("M3 15c2-1.5 4-1.5 6 0s4 1.5 6 0 4-1.5 6 0") }
    /** Rotate it for a spinner: a faint ring with a quarter arc. */
    val Spinner = icon("Spinner", 2.5f) { stroke(circle(12f, 12f, 9f), alpha = 0.25f); stroke("M21 12a9 9 0 0 0-9-9") }
    val Record = icon("Record", 2f) {
        stroke(circle(12f, 12f, 8f)); fill(circle(9.5f, 10f, 1f)); fill(circle(13.5f, 9f, 1f)); fill(circle(12f, 13.5f, 1f))
    }

    // Tab bar: a flag on a green, an iron, a folded map.
    val Play = icon("Play", 2f) { stroke("M9 18V3"); strokeAndFill("M9 3.5l8 3.5-8 3.5z"); stroke(ellipse(11f, 19f, 8f, 2.5f)) }
    val Clubs = icon("Clubs", 2f) {
        stroke("M6 3l7.6 13.2"); stroke("M12.6 16.4l6.6-1.6.7 2.7a1.5 1.5 0 0 1-1.1 1.8l-5 1.2a1.5 1.5 0 0 1-1.8-1.1z")
    }
    val ClubsFilled = icon("ClubsFilled", 2f) {
        stroke("M6 3l7.6 13.2"); strokeAndFill("M12.6 16.4l6.6-1.6.7 2.7a1.5 1.5 0 0 1-1.1 1.8l-5 1.2a1.5 1.5 0 0 1-1.8-1.1z")
    }
    val Course = icon("Course", 2f) { stroke("M9 4.5L3.5 6.5v13l5.5-2 6 2.5 5.5-2v-13l-5.5 2-6-2.5z"); stroke("M9 4.5v13M15 7v13") }
}
