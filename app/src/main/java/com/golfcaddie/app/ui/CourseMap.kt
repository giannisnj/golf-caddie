package com.golfcaddie.app.ui

import android.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.viewinterop.AndroidView
import com.golfcaddie.core.Course
import com.golfcaddie.core.GeoPoint
import com.golfcaddie.core.HazardType
import com.golfcaddie.core.Hole
import org.osmdroid.events.MapEventsReceiver
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.MapTileIndex
import org.osmdroid.views.CustomZoomButtonsController
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.MapEventsOverlay
import org.osmdroid.views.overlay.Polygon
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.CopyrightOverlay
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Overlay
import org.osmdroid.util.GeoPoint as OsmPoint

/**
 * Esri World Imagery satellite tiles. Free to use with attribution for personal and
 * development use; a published app should register for an (also free) ArcGIS key.
 */
private object EsriWorldImagery : OnlineTileSourceBase(
    "EsriWorldImagery", 0, 19, 256, "",
    arrayOf("https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/"),
    "Imagery © Esri, Maxar, Earthstar Geographics",
) {
    // Esri orders tiles z/y/x rather than OSM's z/x/y.
    override fun getTileURLString(index: Long): String =
        baseUrl + MapTileIndex.getZoom(index) + "/" + MapTileIndex.getY(index) + "/" + MapTileIndex.getX(index)
}

private fun GeoPoint.osm() = OsmPoint(lat, lon)

/**
 * What the map has already drawn and framed. Course shapes are rebuilt only when the course or
 * hole changes, not on every GPS fix, and the camera isn't re-zoomed under the player's fingers.
 */
private class MapMemory {
    var framedKey: String? = null
    var shapesKey: String? = null
    var shapes: List<Overlay> = emptyList()
    var aim: Polyline? = null
    var marker: Marker? = null
    var copyright: CopyrightOverlay? = null
}

@Composable
fun CourseMap(
    course: Course?,
    hole: Hole?,
    player: GeoPoint?,
    satellite: Boolean,
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

    AndroidView(
        // MapView draws its overlays past its own edges; keep it inside its slot.
        modifier = modifier.clipToBounds(),
        factory = { ctx ->
            MapView(ctx).apply {
                setMultiTouchControls(true)
                zoomController.setVisibility(CustomZoomButtonsController.Visibility.NEVER)
                isTilesScaledToDpi = true
                controller.setZoom(16.0)
            }
        },
        update = { map ->
            val tiles = if (satellite) EsriWorldImagery else TileSourceFactory.MAPNIK
            if (map.tileProvider.tileSource != tiles) map.setTileSource(tiles)
            val shapesKey = "${course?.id}/${hole?.id}"
            if (shapesKey != memory.shapesKey) {
                memory.shapesKey = shapesKey
                memory.shapes = courseShapes(map, course, hole)
            }
            val aim = memory.aim ?: Polyline(map).apply {
                outlinePaint.color = Color.YELLOW
                outlinePaint.strokeWidth = 6f
            }.also { memory.aim = it }
            val marker = memory.marker ?: Marker(map).apply {
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                title = "You"
            }.also { memory.marker = it }
            val copyright = memory.copyright ?: CopyrightOverlay(map.context).also { memory.copyright = it }

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
            map.overlays.add(copyright)
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
}

/** Hazards, greens and hole lines; the current hole drawn stronger. */
private fun courseShapes(map: MapView, course: Course?, hole: Hole?): List<Overlay> {
    val shapes = mutableListOf<Overlay>()
    course?.hazards?.forEach { hz ->
        shapes += Polygon(map).apply {
            setPoints(hz.polygon.map { it.osm() })
            val c = if (hz.type == HazardType.BUNKER) Color.rgb(233, 211, 150) else Color.rgb(64, 140, 230)
            fillPaint.color = Color.argb(150, Color.red(c), Color.green(c), Color.blue(c))
            outlinePaint.color = c
            outlinePaint.strokeWidth = 2f
        }
    }
    course?.holes?.forEach { h ->
        val isCurrent = h.id == hole?.id
        if (h.green.size >= 3) shapes += Polygon(map).apply {
            setPoints(h.green.map { it.osm() })
            fillPaint.color = Color.argb(if (isCurrent) 170 else 90, 60, 200, 90)
            outlinePaint.color = Color.rgb(20, 120, 40)
            outlinePaint.strokeWidth = 3f
        }
        shapes += Polyline(map).apply {
            setPoints(h.line.map { it.osm() })
            outlinePaint.color = if (isCurrent) Color.WHITE else Color.argb(120, 255, 255, 255)
            outlinePaint.strokeWidth = if (isCurrent) 5f else 3f
        }
    }
    return shapes
}
