package com.xcsaber.app.data

import org.json.JSONArray

class RecentSearchStore(private val store: KvStore, private val maxEntries: Int = 20) {

    fun all(): List<String> = read()

    fun add(query: String) {
        val q = query.trim()
        if (q.isEmpty()) return
        val list = read().filterNot { it.equals(q, ignoreCase = true) }.toMutableList()
        list.add(0, q)
        write(list.take(maxEntries))
    }

    fun remove(query: String) = write(read().filterNot { it == query })

    fun clear() = store.remove(KEY)

    fun matching(prefix: String): List<String> {
        val p = prefix.trim().lowercase()
        return if (p.isEmpty()) read() else read().filter { it.lowercase().contains(p) }
    }

    private fun read(): List<String> = try {
        val arr = JSONArray(store.getString(KEY, "[]") ?: "[]")
        (0 until arr.length()).map { arr.getString(it) }
    } catch (e: Exception) {
        emptyList()
    }

    private fun write(list: List<String>) {
        val arr = JSONArray()
        list.forEach { arr.put(it) }
        store.putString(KEY, arr.toString())
    }

    private companion object { const val KEY = "recent_searches" }
}
