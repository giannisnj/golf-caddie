package com.golfcaddie.app.data

import android.content.Context
import com.golfcaddie.core.Bag
import java.io.File

/** Your clubs and every shot you've recorded, stored as JSON in the app's private files. */
class BagRepository(context: Context) {
    private val file = File(context.filesDir, "bag.json")

    fun load(): Bag = runCatching { Bag.decode(file.readText()) }.getOrElse { Bag.default() }

    fun save(bag: Bag) {
        val tmp = File(file.parentFile, "bag.json.tmp")
        tmp.writeText(bag.encode())
        tmp.renameTo(file)
    }
}
