package com.golfcaddie.app.map

import android.content.Context
import com.golfcaddie.app.data.Http
import com.golfcaddie.app.data.HttpException
import com.golfcaddie.core.Course
import com.golfcaddie.core.WebMercator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.osmdroid.tileprovider.modules.SqlTileWriter
import org.osmdroid.tileprovider.tilesource.ITileSource
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.util.MapTileIndex
import java.io.ByteArrayInputStream
import java.io.IOException

/**
 * Saves a course's satellite images into the map's tile cache (osmdroid's database in app
 * storage) so the map works with no signal. Only for sources whose licence allows it.
 */
class OfflineMaps(context: Context) {
    private val http = Http("GolfCaddie/0.1 (Android; ${context.packageName})")

    /** The phone's map cache refused a write (e.g. storage full); not a network problem. */
    class CacheWriteException : IOException("Couldn't write to the map cache")

    sealed interface Result {
        data class Saved(val tiles: Int, val missing: Int) : Result
        /** No imagery with an offline-friendly licence covers this course. */
        data object NotAvailable : Result
    }

    /** Zoom 15 (whole course) to 19 (about 0.3 m per pixel, enough to see bunkers' edges). */
    val zooms = 15..19

    suspend fun save(
        course: Course,
        zooms: IntRange = this.zooms,
        onProgress: (done: Int, total: Int) -> Unit,
    ): Result = withContext(Dispatchers.IO) {
        val source = MapSources.offlineSourceFor(course) as? OnlineTileSourceBase ?: return@withContext Result.NotAvailable
        val tiles = WebMercator.tilesFor(course, zooms)
        val writer = SqlTileWriter()
        val expires = System.currentTimeMillis() + 365L * 24 * 3600 * 1000
        var done = 0
        var missing = 0
        try {
            // Download a few at a time (quick, without hammering a free government service),
            // then write them one by one: the cache is a single SQLite database.
            for (batch in tiles.chunked(4)) {
                val fetched = coroutineScope {
                    batch.map { t ->
                        async {
                            val index = MapTileIndex.getTileIndex(t.z, t.x, t.y)
                            if (writer.exists(source, index)) return@async index to null
                            try {
                                index to http.getBytes(source.getTileURLString(index))
                            } catch (e: HttpException) {
                                if (e.code != 404) throw e
                                index to ByteArray(0) // outside the imagery's coverage
                            }
                        }
                    }.awaitAll()
                }
                for ((index, bytes) in fetched) {
                    when {
                        bytes == null -> Unit // already saved
                        bytes.isEmpty() -> missing++
                        else -> {
                            // osmdroid 6.1's saveFile returns false even when it worked, so check the result.
                            writer.saveFile(source, index, ByteArrayInputStream(bytes), expires)
                            if (!writer.exists(source, index)) throw CacheWriteException()
                        }
                    }
                }
                done += batch.size
                onProgress(done, tiles.size)
            }
        } finally {
            writer.onDetach()
        }
        Result.Saved(tiles.size - missing, missing)
    }

    /** Share (0..1) of the course's zoom-17 tiles already saved for [source]. */
    suspend fun savedShare(course: Course, source: ITileSource): Double = withContext(Dispatchers.IO) {
        val tiles = WebMercator.tilesFor(course, 17..17)
        if (tiles.isEmpty()) return@withContext 0.0
        val writer = SqlTileWriter()
        try {
            tiles.count { writer.exists(source, MapTileIndex.getTileIndex(it.z, it.x, it.y)) }.toDouble() / tiles.size
        } finally {
            writer.onDetach()
        }
    }
}
