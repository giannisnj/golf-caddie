package com.golfcaddie.app.data

import android.content.Context
import com.golfcaddie.core.Bag
import java.io.File

/** Your clubs and every shot you've recorded, stored as JSON in the app's private files. */
class BagRepository(context: Context) {
    private val file = File(context.filesDir, "bag.json")

    fun load(): Bag {
        if (!file.exists()) return Bag.default()
        return runCatching { Bag.decode(file.readText()) }.getOrElse {
            // Unreadable (e.g. written by a newer version): keep it aside rather than letting the
            // next save overwrite the shot history.
            file.renameTo(File(file.parentFile, "bag.unreadable-${System.currentTimeMillis()}.json"))
            Bag.default()
        }
    }

    fun save(bag: Bag) {
        val tmp = File(file.parentFile, "bag.json.tmp")
        tmp.writeText(bag.encode())
        tmp.renameTo(file)
    }
}
