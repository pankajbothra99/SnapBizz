package com.bothrametals.cardscanner.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Keeps the list of events on the phone (also a copy of the cloud list, so events show offline). */
class EventStore(context: Context) {

    private val file = File(context.filesDir, "events.json")

    fun load(): List<EventGroup> = runCatching {
        if (!file.exists()) return emptyList()
        val arr = JSONArray(file.readText())
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val m = o.optJSONArray("members") ?: JSONArray()
            EventGroup(
                id = o.getString("id"),
                name = o.optString("name"),
                place = o.optString("place"),
                createdBy = o.optString("createdBy"),
                members = (0 until m.length()).map { m.optString(it) }.filter { it.isNotBlank() },
                closed = o.optBoolean("closed"),
                createdAt = o.optLong("createdAt"),
                updatedAt = o.optLong("updatedAt"),
                subGroups = (o.optJSONArray("subGroups") ?: JSONArray()).let { sg ->
                    (0 until sg.length()).mapNotNull { j ->
                        val g = sg.optJSONObject(j) ?: return@mapNotNull null
                        val id = g.optString("id")
                        if (id.isBlank()) null else SubGroup(id, g.optString("name"), g.optLong("at"))
                    }
                },
            )
        }
    }.getOrDefault(emptyList())

    fun save(events: List<EventGroup>) {
        val arr = JSONArray()
        events.forEach { e ->
            arr.put(
                JSONObject()
                    .put("id", e.id).put("name", e.name).put("place", e.place).put("createdBy", e.createdBy)
                    .put("members", JSONArray(e.members)).put("closed", e.closed)
                    .put("createdAt", e.createdAt).put("updatedAt", e.updatedAt)
                    .put("subGroups", JSONArray().apply {
                        e.subGroups.forEach { put(JSONObject().put("id", it.id).put("name", it.name).put("at", it.at)) }
                    })
            )
        }
        val tmp = File(file.parentFile, "events.json.tmp")
        tmp.writeText(arr.toString())
        if (!tmp.renameTo(file)) {
            file.writeText(tmp.readText())
            tmp.delete()
        }
    }

    fun clear() {
        file.delete()
    }
}
