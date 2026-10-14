package com.xcsaber.app.data

import org.json.JSONArray

class BookmarkStore(private val store: KvStore, private val clock: () -> Long = System::currentTimeMillis) {

    fun all(): List<Bookmark> = read()

    fun isBookmarked(url: String): Boolean = read().any { it.url == url }

    /** Adds the bookmark, or removes it if it already exists. Returns true if now bookmarked. */
    fun toggle(title: String, url: String): Boolean {
        val list = read().toMutableList()
        val existing = list.indexOfFirst { it.url == url }
        return if (existing >= 0) {
            list.removeAt(existing)
            write(list)
            false
        } else {
            list.add(0, Bookmark(title.ifBlank { url }, url, clock()))
            write(list)
            true
        }
    }

    fun remove(url: String) = write(read().filter { it.url != url })

    fun clear() = store.remove(KEY)

    private fun read(): List<Bookmark> = try {
        val arr = JSONArray(store.getString(KEY, "[]") ?: "[]")
        (0 until arr.length()).map { Bookmark.fromJson(arr.getJSONObject(it)) }
    } catch (e: Exception) {
        emptyList()
    }

    private fun write(list: List<Bookmark>) {
        val arr = JSONArray()
        list.forEach { arr.put(it.toJson()) }
        store.putString(KEY, arr.toString())
    }

    private companion object { const val KEY = "bookmarks" }
}
