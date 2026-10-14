package com.xcsaber.app.data

import org.json.JSONArray

/**
 * Normal-browsing history only. Private tabs never call [record], so private URLs are
 * never written here.
 */
class HistoryStore(
    private val store: KvStore,
    private val clock: () -> Long = System::currentTimeMillis,
    private val maxEntries: Int = 500
) {

    fun all(): List<HistoryEntry> = read()

    fun record(title: String, url: String) {
        if (url.isBlank() || url.startsWith("about:") || url.startsWith("data:")) return
        val list = read().toMutableList()
        // Collapse an immediate repeat of the same URL into one entry.
        if (list.isNotEmpty() && list[0].url == url) list.removeAt(0)
        list.add(0, HistoryEntry(title.ifBlank { url }, url, clock()))
        write(list.take(maxEntries))
    }

    fun updateTitle(url: String, title: String) {
        if (title.isBlank()) return
        val list = read().toMutableList()
        val i = list.indexOfFirst { it.url == url }
        if (i >= 0) {
            list[i] = list[i].copy(title = title)
            write(list)
        }
    }

    fun remove(url: String) = write(read().filter { it.url != url })

    fun clear() = store.remove(KEY)

    fun search(text: String): List<HistoryEntry> {
        val q = text.trim().lowercase()
        if (q.isEmpty()) return read()
        return read().filter { it.title.lowercase().contains(q) || it.url.lowercase().contains(q) }
    }

    private fun read(): List<HistoryEntry> = try {
        val arr = JSONArray(store.getString(KEY, "[]") ?: "[]")
        (0 until arr.length()).map { HistoryEntry.fromJson(arr.getJSONObject(it)) }
    } catch (e: Exception) {
        emptyList()
    }

    private fun write(list: List<HistoryEntry>) {
        val arr = JSONArray()
        list.forEach { arr.put(it.toJson()) }
        store.putString(KEY, arr.toString())
    }

    private companion object { const val KEY = "history" }
}
