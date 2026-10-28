package com.nearchat.app.core

import android.app.Application
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** Saves each conversation as a small JSON file inside the app's private storage (never leaves the phone). */
class ChatStore(app: Application) {
    private val dir = File(app.filesDir, "chats").apply { mkdirs() }

    private fun file(addr: String) = File(dir, addr.replace(Regex("[^A-Za-z0-9]"), "_") + ".json")

    private fun JSONObject.str(k: String): String? = if (has(k)) getString(k) else null

    fun save(addr: String, name: String, msgs: List<Msg>) {
        val arr = JSONArray()
        for (m in msgs) {
            val o = JSONObject()
            o.put("id", m.id); o.put("mine", m.mine); o.put("status", m.status.name)
            o.put("time", m.time); o.put("size", m.fileSize)
            m.text?.let { o.put("text", it) }
            m.fileName?.let { o.put("file", it) }
            m.savedPath?.let { o.put("path", it) }
            m.thumb?.let { o.put("thumb", it) }
            arr.put(o)
        }
        val root = JSONObject().put("name", name).put("address", addr).put("messages", arr)
        val f = file(addr)
        val tmp = File(dir, f.name + ".tmp")
        tmp.writeText(root.toString())
        if (!tmp.renameTo(f)) { f.delete(); tmp.renameTo(f) }
    }

    private fun parse(f: File): StoredChat? {
        if (!f.exists()) return null
        val root = JSONObject(f.readText())
        val arr = root.getJSONArray("messages")
        val list = (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            var st = Status.valueOf(o.getString("status"))
            if (st == Status.TRANSFERRING || st == Status.OFFERED || st == Status.SENDING) st = Status.FAILED
            Msg(
                id = o.getLong("id"), mine = o.getBoolean("mine"), status = st,
                text = o.str("text"), fileName = o.str("file"), fileSize = o.optLong("size", 0),
                time = o.optLong("time", 0), savedPath = o.str("path"), thumb = o.str("thumb"),
            )
        }
        return StoredChat(root.getString("address"), root.getString("name"), list)
    }

    fun load(addr: String): StoredChat? = runCatching { parse(file(addr)) }.getOrNull()

    fun list(): List<ChatMeta> =
        (dir.listFiles { f -> f.name.endsWith(".json") } ?: emptyArray())
            .mapNotNull { runCatching { parse(it) }.getOrNull() }
            .filter { it.messages.isNotEmpty() }
            .map { ChatMeta(it.address, it.name, Media.previewOf(it.messages.last()), it.messages.last().time) }
            .sortedByDescending { it.lastTime }

    fun delete(addr: String) {
        file(addr).delete()
    }
}
