package com.xcsaber.app.downloads

import com.xcsaber.app.data.KvStore
import org.json.JSONArray
import org.json.JSONObject

data class DownloadRecord(val id: Long, val name: String, val url: String, val time: Long)

/** The list shown in the Downloads screen. Private-tab downloads are never recorded. */
class DownloadStore(private val store: KvStore, private val clock: () -> Long = System::currentTimeMillis) {

    fun all(): List<DownloadRecord> = try {
        val arr = JSONArray(store.getString(KEY, "[]") ?: "[]")
        (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            DownloadRecord(o.optLong("i"), o.optString("n"), o.optString("u"), o.optLong("t"))
        }
    } catch (e: Exception) {
        emptyList()
    }

    fun add(id: Long, name: String, url: String) {
        val list = all().filterNot { it.id == id }.toMutableList()
        list.add(0, DownloadRecord(id, name, url, clock()))
        write(list.take(100))
    }

    fun remove(id: Long) = write(all().filterNot { it.id == id })

    fun clear() = store.remove(KEY)

    private fun write(list: List<DownloadRecord>) {
        val arr = JSONArray()
        list.forEach { arr.put(JSONObject().put("i", it.id).put("n", it.name).put("u", it.url).put("t", it.time)) }
        store.putString(KEY, arr.toString())
    }

    private companion object { const val KEY = "downloads" }
}
