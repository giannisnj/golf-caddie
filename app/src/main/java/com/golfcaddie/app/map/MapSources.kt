package com.golfcaddie.app.map

import com.golfcaddie.core.Course
import com.golfcaddie.core.WebMercator
import org.osmdroid.tileprovider.tilesource.ITileSource
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.MapTileIndex

enum class MapStyle(val label: String) {
    SATELLITE("Satellite"),
    STREET("Street"),
    /** Drawn from the course's OpenStreetMap data: needs no map images, so it always works offline. */
    PLAN("Course plan");

    val next: MapStyle get() = entries[(ordinal + 1) % entries.size]
}

/** An ArcGIS-style tile service, which orders tiles z/y/x rather than OSM's z/x/y. */
private class ArcGisTiles(name: String, maxZoom: Int, baseUrl: String, copyright: String) :
    OnlineTileSourceBase(name, 0, maxZoom, 256, "", arrayOf(baseUrl), copyright) {
    override fun getTileURLString(index: Long): String =
        baseUrl + MapTileIndex.getZoom(index) + "/" + MapTileIndex.getY(index) + "/" + MapTileIndex.getX(index)
}

/**
 * Map image sources, and whether their terms allow keeping copies for offline use.
 * Offline downloads are only offered where the licence allows it:
 *  - Esri World Imagery: free to view online with attribution; its service says
 *    exportTilesAllowed = false, so no offline copies.
 *  - OpenStreetMap standard tiles: the tile usage policy forbids bulk downloading.
 *  - ACT Government imagery (ACTmapi, with Aerometrex): CC BY, which allows copies with credit.
 * NSW Spatial Services imagery also sets exportTilesAllowed = false, so Sydney has no free
 * offline imagery; the course plan style covers that.
 */
object MapSources {
    val esri: ITileSource = ArcGisTiles(
        "EsriWorldImagery", 19,
        "https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/",
        "Imagery © Esri, Vantor, Earthstar Geographics",
    )

    val act: ITileSource = ArcGisTiles(
        "ACTImagery", 21,
        "https://tiles.arcgis.com/tiles/E5n4f1VY84i0xSjy/arcgis/rest/services/ACT_Aerial_Imagery_Current_3857/MapServer/tile/",
        "Imagery © ACT Government and Aerometrex, CC BY",
    )

    // The ACT imagery service's published extent (EPSG:3857), as degrees.
    private val actWest = WebMercator.lonOf(16557805.179376211)
    private val actEast = WebMercator.lonOf(16633456.56337779)
    private val actSouth = WebMercator.latOf(-4290913.053241368)
    private val actNorth = WebMercator.latOf(-4179573.788050428)

    private fun inAct(course: Course): Boolean {
        val pts = course.holes.flatMap { it.line }
        return pts.isNotEmpty() && pts.all { it.lat in actSouth..actNorth && it.lon in actWest..actEast }
    }

    /** Best satellite source for [course]: the sharper, current ACT imagery in Canberra, Esri elsewhere. */
    fun satelliteFor(course: Course?): ITileSource = if (course != null && inAct(course)) act else esri

    /** A satellite source whose licence allows offline copies for [course], or null if there's none. */
    fun offlineSourceFor(course: Course): ITileSource? = if (inAct(course)) act else null

    fun forStyle(style: MapStyle, course: Course?): ITileSource? = when (style) {
        MapStyle.SATELLITE -> satelliteFor(course)
        MapStyle.STREET -> TileSourceFactory.MAPNIK
        MapStyle.PLAN -> null
    }
}
