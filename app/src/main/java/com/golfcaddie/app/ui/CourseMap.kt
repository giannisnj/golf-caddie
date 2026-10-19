package com.golfcaddie.app.ui

import android.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
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

/** Remembers what the camera last framed so GPS updates don't keep re-zooming the map. */
private class CameraMemory {
    var framedKey: String? = null
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
    val camera = remember { CameraMemory() }
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
        modifier = modifier,
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
            map.overlays.clear()

            course?.hazards?.forEach { hz ->
                map.overlays.add(Polygon(map).apply {
                    setPoints(hz.polygon.map { it.osm() })
                    val c = if (hz.type == HazardType.BUNKER) Color.rgb(233, 211, 150) else Color.rgb(64, 140, 230)
                    fillPaint.color = Color.argb(150, Color.red(c), Color.green(c), Color.blue(c))
                    outlinePaint.color = c
                    outlinePaint.strokeWidth = 2f
                })
            }
            course?.holes?.forEach { h ->
                val isCurrent = h.number == hole?.number
                if (h.green.size >= 3) map.overlays.add(Polygon(map).apply {
                    setPoints(h.green.map { it.osm() })
                    fillPaint.color = Color.argb(if (isCurrent) 170 else 90, 60, 200, 90)
                    outlinePaint.color = Color.rgb(20, 120, 40)
                    outlinePaint.strokeWidth = 3f
                })
                map.overlays.add(Polyline(map).apply {
                    setPoints(h.line.map { it.osm() })
                    outlinePaint.color = if (isCurrent) Color.WHITE else Color.argb(120, 255, 255, 255)
                    outlinePaint.strokeWidth = if (isCurrent) 5f else 3f
                })
            }
            if (player != null && hole != null) {
                map.overlays.add(Polyline(map).apply {
                    setPoints(listOf(player.osm(), (hole.pin ?: hole.greenCenter).osm()))
                    outlinePaint.color = Color.YELLOW
                    outlinePaint.strokeWidth = 6f
                })
            }
            if (player != null) {
                map.overlays.add(Marker(map).apply {
                    position = player.osm()
                    setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    title = "You"
                })
            }
            map.overlays.add(CopyrightOverlay(map.context))
            // Added last so it sits on top and receives taps before the shapes do.
            map.overlays.add(events)
            map.invalidate()

            // Frame the hole (tee, green and you) whenever the hole or course changes.
            val key = "${course?.id}/${hole?.number}"
            if (hole != null && key != camera.framedKey) {
                camera.framedKey = key
                val pts = (hole.line + hole.green + listOfNotNull(player)).map { it.osm() }
                map.post { map.zoomToBoundingBox(BoundingBox.fromGeoPoints(pts), false, 80) }
            } else if (hole == null && player != null && camera.framedKey == null) {
                map.controller.setCenter(player.osm())
            }
        },
    )
}
