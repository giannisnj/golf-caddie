package com.golfcaddie.app.ui

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Point
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.golfcaddie.app.map.MapSources
import com.golfcaddie.app.map.MapStyle
import com.golfcaddie.app.ui.theme.GolfColors
import com.golfcaddie.app.ui.theme.PlayerBlue
import com.golfcaddie.app.ui.theme.golf
import com.golfcaddie.core.Course
import com.golfcaddie.core.GeoPoint
import com.golfcaddie.core.HazardType
import com.golfcaddie.core.Hole
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.util.BoundingBox
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Overlay
import org.osmdroid.views.overlay.Polygon
import kotlin.math.max
import org.osmdroid.util.GeoPoint as OsmPoint

private fun GeoPoint.osm() = OsmPoint(lat, lon)

/**
 * How the overlays look on this base. A photo or the dark plan is a dark base: white line over
 * a dark casing. Street tiles and the light plan are light: an ink line over a white casing.
 */
private class MapLook(val style: MapStyle, val g: GolfColors) {
    val plan = style == MapStyle.PLAN
    val darkBase = style == MapStyle.SATELLITE || (plan && g.dark)
    val line = if (darkBase) Color.White else g.lineOnLight
    val casing = when {
        style == MapStyle.SATELLITE -> Color.Black.copy(alpha = 0.45f)
        darkBase -> Color.Black.copy(alpha = 0.5f)
        else -> g.casingOnLight
    }
    val greenEdge = if (plan && g.dark) Color(0xFFE9EEE9) else Color.White
    val inPlay = if (darkBase) Color(0xFFFFB547) else g.inPlayOnLight
    val inPlayFill = when {
        style == MapStyle.SATELLITE -> Color(0x4DFFB547)
        darkBase -> Color(0x38FFB547)
        else -> g.inPlayFillOnLight
    }
    val creditBackground = if (darkBase) Color.Black.copy(alpha = 0.58f) else Color.White.copy(alpha = 0.88f)
    val creditText = if (darkBase) (if (style == MapStyle.SATELLITE) Color.White else Color(0xFFC9D2CB)) else Color(0xFF2E3631)
    val key = "$style/${g.dark}"
}

/**
 * What the map has already drawn and framed. Course shapes are rebuilt only when the course,
 * hole, style or hazard in play changes, not on every GPS fix, and the camera isn't re-zoomed
 * under the player's fingers.
 */
private class MapMemory {
    var framedKey: String? = null
    var shapesKey: String? = null
    var shapes: List<Overlay> = emptyList()
    var caddie: CaddieOverlay? = null
}

@Composable
fun CourseMap(
    course: Course?,
    hole: Hole?,
    player: GeoPoint?,
    accuracyMeters: Float?,
    style: MapStyle,
    onTap: (GeoPoint) -> Unit,
    modifier: Modifier = Modifier,
    /** Outlines of the hazards the warning card is about: drawn amber. */
    inPlay: List<List<GeoPoint>> = emptyList(),
    /** Keeps the credit clear of whatever overlaps the map's bottom edge. */
    creditBottom: Dp = 6.dp,
) {
    val tap = rememberUpdatedState(onTap)
    val memory = remember { MapMemory() }
    val events = remember {
        MapEventsOverlay(object : MapEventsReceiver {
            override fun singleTapConfirmedHelper(p: OsmPoint): Boolean {
                tap.value(GeoPoint(p.latitude, p.longitude))
                return true
            }
            override fun longPressHelper(p: OsmPoint): Boolean = false
        })
    }
    val tiles = MapSources.forStyle(style, course)
    val look = MapLook(style, MaterialTheme.golf)
    val density = LocalDensity.current.density

    // MapView draws its overlays past its own edges; keep it inside its slot.
    Box(modifier.clipToBounds()) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                MapView(ctx).apply {
                    setMultiTouchControls(true)
                    zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
                    isTilesScaledToDpi = true
                    controller.setZoom(16.0)
                }
            },
            update = { map ->
                // The course plan needs no map images at all, so it works with no signal.
                map.overlayManager.tilesOverlay.isEnabled = tiles != null
                if (tiles != null && map.tileProvider.tileSource != tiles) map.setTileSource(tiles)
                map.setBackgroundColor(look.g.planRough.toArgb())

                val shapesKey = "${course?.id}/${hole?.id}/${look.key}/${inPlay.hashCode()}"
                if (shapesKey != memory.shapesKey) {
                    memory.shapesKey = shapesKey
                    memory.shapes = courseShapes(map, course, hole, look, inPlay, density)
                }
                val caddie = memory.caddie ?: CaddieOverlay(density).also { memory.caddie = it }
                caddie.look = look
                caddie.hole = hole
                caddie.player = player
                caddie.accuracyMeters = accuracyMeters

                map.overlays.clear()
                map.overlays.addAll(memory.shapes)
                map.overlays.add(caddie)
                // Added last so it sits on top and receives taps before the shapes do.
                map.overlays.add(events)
                map.invalidate()

                // Frame the hole (tee, green and you) when it changes; with no hole yet, the whole course.
                val frameKey = when {
                    hole != null -> "hole/${course?.id}/${hole.id}"
                    course != null && course.holes.isNotEmpty() -> "course/${course.id}"
                    else -> null
                }
                if (frameKey != null && frameKey != memory.framedKey) {
                    memory.framedKey = frameKey
                    val pts = if (hole != null) hole.line + hole.green + listOfNotNull(player) else course!!.holes.flatMap { it.line }
                    val border = (32 * density).toInt()
                    map.post { map.zoomToBoundingBox(BoundingBox.fromGeoPoints(pts.map { it.osm() }), false, border) }
                } else if (frameKey == null && player != null && memory.framedKey == null) {
                    map.controller.setCenter(player.osm())
                }
            },
        )
        // Course data is OpenStreetMap's (ODbL: credit required wherever it's shown). Bottom-left,
        // never covered.
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding(start = 8.dp, bottom = creditBottom)
                .background(look.creditBackground, RoundedCornerShape(6.dp))
                .padding(horizontal = 7.dp, vertical = 3.dp),
        ) {
            val credit = MaterialTheme.typography.labelSmall
            if (style == MapStyle.STREET) {
                Text("Course data and map © OpenStreetMap contributors", style = credit, color = look.creditText)
            } else {
                Text("Course data © OpenStreetMap contributors", style = credit, color = look.creditText)
                tiles?.copyrightNotice?.let { Text(it, style = credit, color = look.creditText) }
            }
        }
    }
}

/** Woods, fairways and greens for the course plan; bunkers and water on every style. */
private fun courseShapes(
    map: MapView, course: Course?, hole: Hole?, look: MapLook, inPlay: List<List<GeoPoint>>, density: Float,
): List<Overlay> {
    val shapes = mutableListOf<Overlay>()
    val g = look.g
    fun area(points: List<GeoPoint>, fill: Color, outline: Color = Color.Transparent, widthDp: Float = 0f) = Polygon(map).apply {
        setPoints(points.map { it.osm() })
        fillPaint.color = fill.toArgb()
        outlinePaint.color = outline.toArgb()
        outlinePaint.strokeWidth = widthDp * density
        outlinePaint.strokeJoin = Paint.Join.ROUND
        // Shapes are for looking at; taps go to the map.
        setOnClickListener { _, _, _ -> false }
    }
    if (course == null) return shapes
    val lightTiles = look.style == MapStyle.STREET
    if (look.plan && course.holes.isNotEmpty()) {
        // The rough: a backdrop a kilometre past the course in every direction.
        val pts = course.holes.flatMap { it.line }
        val pad = 0.01
        val (s, n) = pts.minOf { it.lat } - pad to pts.maxOf { it.lat } + pad
        val (w, e) = pts.minOf { it.lon } - pad to pts.maxOf { it.lon } + pad
        shapes += area(listOf(GeoPoint(s, w), GeoPoint(s, e), GeoPoint(n, e), GeoPoint(n, w)), g.planRough)
        course.woods.forEach { shapes += area(it, g.planTrees) }
        course.fairways.forEach { shapes += area(it, g.planFairway) }
    }
    // Greens: filled on the plan (and over street tiles); on a photo only the current one is outlined.
    course.holes.forEach { h ->
        if (h.green.size < 3) return@forEach
        val current = h.id == hole?.id
        when {
            look.plan -> shapes += area(h.green, g.planGreen, if (current) look.greenEdge else Color.Transparent, if (current) 2f else 0f)
            lightTiles -> shapes += area(h.green, g.planGreen.copy(alpha = 0.7f), if (current) look.greenEdge else Color.Transparent, if (current) 2f else 0f)
            current -> shapes += area(h.green, Color.Transparent, look.greenEdge, 2f)
        }
    }
    course.hazards.forEach { hz ->
        val water = hz.type != HazardType.BUNKER
        val amber = inPlay.any { it == hz.polygon }
        shapes += when {
            amber -> area(hz.polygon, look.inPlayFill, look.inPlay, 2.5f)
            look.style == MapStyle.SATELLITE && water -> area(hz.polygon, Color(0x3850AAE6), Color(0xFF9AD7FF), 1.5f)
            look.style == MapStyle.SATELLITE -> area(hz.polygon, Color.Transparent, Color.White.copy(alpha = 0.85f), 1.5f)
            water -> area(hz.polygon, g.planWater, g.planWaterEdge, 1.5f)
            else -> area(hz.polygon, g.planBunker, g.planBunkerEdge, 1.5f)
        }
    }
    return shapes
}

/**
 * The hole line (from you, or the tee, to the middle of the green), the middle marker the hero
 * number measures to, a mapped flag, and you: a blue dot in a white ring, with a halo as wide as
 * the GPS accuracy. Drawn last, above every shape.
 */
private class CaddieOverlay(private val density: Float) : Overlay() {
    lateinit var look: MapLook
    var hole: Hole? = null
    var player: GeoPoint? = null
    var accuracyMeters: Float? = null

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val path = Path()
    private fun dp(v: Float) = v * density

    override fun draw(canvas: Canvas, map: MapView, shadow: Boolean) {
        if (shadow) return
        val proj = map.projection
        fun px(p: GeoPoint): Point = proj.toPixels(p.osm(), null)
        val h = hole
        val you = player

        if (h != null) {
            val linePts = (if (you != null) listOf(you, h.greenCenter) else h.line.dropLast(1) + h.greenCenter).map(::px)
            path.reset()
            linePts.forEachIndexed { i, p -> if (i == 0) path.moveTo(p.x.toFloat(), p.y.toFloat()) else path.lineTo(p.x.toFloat(), p.y.toFloat()) }
            stroke(look.casing, 5.5f); canvas.drawPath(path, paint)
            stroke(look.line, 3f); canvas.drawPath(path, paint)
            // Middle marker: where the hero number measures to.
            val m = px(h.greenCenter)
            fill(look.line); canvas.drawCircle(m.x.toFloat(), m.y.toFloat(), dp(4f), paint)
            stroke(look.casing, 1.5f); canvas.drawCircle(m.x.toFloat(), m.y.toFloat(), dp(4f), paint)

            h.pin?.let { pin ->
                val f = px(pin)
                val x = f.x.toFloat()
                val y = f.y.toFloat()
                stroke(look.line, 2f); canvas.drawLine(x, y, x, y - dp(18f), paint)
                path.reset()
                path.moveTo(x, y - dp(18f)); path.lineTo(x + dp(12f), y - dp(14f)); path.lineTo(x, y - dp(10f)); path.close()
                fill(Color(0xFFE5322D)); canvas.drawPath(path, paint)
                stroke(Color.Black.copy(alpha = 0.35f), 0.75f); canvas.drawPath(path, paint)
                fill(look.line); canvas.drawCircle(x, y, dp(2.2f), paint)
            }
        }

        if (you != null) {
            val p = px(you)
            val x = p.x.toFloat()
            val y = p.y.toFloat()
            val accuracyPx = accuracyMeters?.let { proj.metersToPixels(it) } ?: 0f
            val halo = max(dp(15f), accuracyPx)
            fill(PlayerBlue.copy(alpha = 0.22f)); canvas.drawCircle(x, y, halo, paint)
            stroke(PlayerBlue.copy(alpha = 0.55f), 1f); canvas.drawCircle(x, y, halo, paint)
            fill(PlayerBlue); canvas.drawCircle(x, y, dp(7.5f), paint)
            stroke(Color.White, 3f); canvas.drawCircle(x, y, dp(7.5f), paint)
        }
    }

    private fun stroke(c: Color, widthDp: Float) {
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = dp(widthDp)
        paint.color = c.toArgb()
    }

    private fun fill(c: Color) {
        paint.style = Paint.Style.FILL
        paint.color = c.toArgb()
    }
}
