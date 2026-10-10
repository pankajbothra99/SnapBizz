package com.bothrametals.cardscanner.data

import android.content.Context
import android.net.Uri
import com.bothrametals.cardscanner.util.ImageUtils
import org.json.JSONObject
import java.io.File

/**
 * Stores scanned cards inside the app's private storage:
 *   files/cards/<id>.json        – the contact details
 *   files/cards/images/<id>_front.jpg / _back.jpg – the card photos
 */
class CardRepository(private val context: Context) {

    private val dir = File(context.filesDir, "cards").apply { mkdirs() }
    private val imageDir = File(dir, "images").apply { mkdirs() }

    fun loadAll(): List<CardRecord> =
        (dir.listFiles { f -> f.isFile && f.name.endsWith(".json") } ?: emptyArray())
            .mapNotNull { f -> runCatching { JsonCodec.fromJson(JSONObject(f.readText())) }.getOrNull() }
            .sortedByDescending { it.createdAt }

    fun save(record: CardRecord) {
        val tmp = File(dir, "${record.id}.json.tmp")
        tmp.writeText(JsonCodec.toJson(record).toString())
        val target = File(dir, "${record.id}.json")
        if (!tmp.renameTo(target)) {
            target.writeText(tmp.readText())
            tmp.delete()
        }
    }

    fun delete(record: CardRecord) {
        File(dir, "${record.id}.json").delete()
        record.frontImage?.let { File(it).delete() }
        record.backImage?.let { File(it).delete() }
    }

    /** Copies a scanned/picked image into app storage (shrunk to a sensible size) and returns the file. */
    fun importImage(uri: Uri, fileName: String): File {
        val dest = File(imageDir, fileName)
        ImageUtils.copyAndShrink(context, uri, dest)
        return dest
    }
}
