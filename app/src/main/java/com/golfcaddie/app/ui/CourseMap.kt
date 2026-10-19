package com.golfcaddie.app.ui

import android.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.golfcaddie.app.map.MapSources
import com.golfcaddie.app.map.MapStyle
import com.golfcaddie.core.Course
import com.golfcaddie.core.GeoPoint
import com.golfcaddie.core.HazardType
import com.golfcaddie.core.Hole
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.util.BoundingBox
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Overlay
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.util.GeoPoint as OsmPoint
import androidx.compose.ui.graphics.Color as ComposeColor

private fun GeoPoint.osm() = OsmPoint(lat, lon)

/** Background of the course plan style: the rough. */
private val PLAN_BACKGROUND = Color.rgb(170, 200, 140)

/**
 * What the map has already drawn and framed. Course shapes are rebuilt only when the course,
 * hole or style changes, not on every GPS fix, and the camera isn't re-zoomed under the
 * player's fingers.
 */
private class MapMemory {
    var framedKey: String? = null
    var shapesKey: String? = null
    var shapes: List<Overlay> = emptyList()
    var aim: Polyline? = null
    var marker: Marker? = null
}

@Composable
fun CourseMap(
    course: Course?,
    hole: Hole?,
    player: GeoPoint?,
    style: MapStyle,
    onTap: (GeoPoint) -> Unit,
    modifier: Modifier = Modifier,
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

                val shapesKey = "${course?.id}/${hole?.id}/${style == MapStyle.PLAN}"
                if (shapesKey != memory.shapesKey) {
                    memory.shapesKey = shapesKey
                    memory.shapes = courseShapes(map, course, hole, plan = style == MapStyle.PLAN)
                }
                val aim = memory.aim ?: Polyline(map).apply {
                    outlinePaint.color = Color.YELLOW
                    outlinePaint.strokeWidth = 6f
                }.also { memory.aim = it }
                val marker = memory.marker ?: Marker(map).apply {
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    title = "You"
                }.also { memory.marker = it }

                map.overlays.clear()
                map.overlays.addAll(memory.shapes)
                if (player != null && hole != null) {
                    aim.setPoints(listOf(player.osm(), hole.greenCenter.osm()))
                    map.overlays.add(aim)
                }
                if (player != null) {
                    marker.position = player.osm()
                    map.overlays.add(marker)
                }
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
                    // Generous border so the player marker (drawn above its point) isn't clipped.
                    map.post { map.zoomToBoundingBox(BoundingBox.fromGeoPoints(pts.map { it.osm() }), false, 160) }
                } else if (frameKey == null && player != null && memory.framedKey == null) {
                    map.controller.setCenter(player.osm())
                }
            },
        )
        // Course data is OpenStreetMap's (ODbL: credit required wherever it's shown).
        Text(
            "Course data © OpenStreetMap contributors" + (tiles?.copyrightNotice?.let { " · $it" } ?: ""),
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .background(ComposeColor.White.copy(alpha = 0.7f))
                .padding(horizontal = 4.dp, vertical = 1.dp),
        )
    }
}

/** Hazards, greens and hole lines (plus woods and fairways for the course plan); the current hole drawn stronger. */
private fun courseShapes(map: MapView, course: Course?, hole: Hole?, plan: Boolean): List<Overlay> {
    val shapes = mutableListOf<Overlay>()
    fun area(points: List<GeoPoint>, fill: Int, outline: Int, width: Float) = Polygon(map).apply {
        setPoints(points.map { it.osm() })
        fillPaint.color = fill
        outlinePaint.color = outline
        outlinePaint.strokeWidth = width
    }
    if (plan && course != null && course.holes.isNotEmpty()) {
        // The rough: a backdrop a kilometre past the course in every direction.
        val pts = course.holes.flatMap { it.line }
        val pad = 0.01
        val (s, n) = pts.minOf { it.lat } - pad to pts.maxOf { it.lat } + pad
        val (w, e) = pts.minOf { it.lon } - pad to pts.maxOf { it.lon } + pad
        shapes += area(listOf(GeoPoint(s, w), GeoPoint(s, e), GeoPoint(n, e), GeoPoint(n, w)), PLAN_BACKGROUND, PLAN_BACKGROUND, 0f)
        course.woods.forEach { shapes += area(it, Color.rgb(84, 130, 72), Color.rgb(64, 110, 52), 1f) }
        course.fairways.forEach { shapes += area(it, Color.rgb(140, 196, 96), Color.rgb(120, 176, 80), 1f) }
    }
    course?.hazards?.forEach { hz ->
        val c = if (hz.type == HazardType.BUNKER) Color.rgb(233, 211, 150) else Color.rgb(64, 140, 230)
        val alpha = if (plan) 255 else 150
        shapes += area(hz.polygon, Color.argb(alpha, Color.red(c), Color.green(c), Color.blue(c)), c, 2f)
    }
    course?.holes?.forEach { h ->
        val isCurrent = h.id == hole?.id
        if (h.green.size >= 3) {
            val alpha = if (plan) 255 else if (isCurrent) 170 else 90
            shapes += area(h.green, Color.argb(alpha, 60, 200, 90), Color.rgb(20, 120, 40), if (isCurrent) 4f else 2f)
        }
        shapes += Polyline(map).apply {
            setPoints(h.line.map { it.osm() })
            outlinePaint.color = if (isCurrent) Color.WHITE else Color.argb(120, 255, 255, 255)
            outlinePaint.strokeWidth = if (isCurrent) 5f else 3f
        }
    }
    return shapes
}
