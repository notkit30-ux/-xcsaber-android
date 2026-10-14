package com.xcsaber.app.data

import org.json.JSONArray

/** Customizable website shortcuts shown on the home screen. */
class ShortcutStore(private val store: KvStore) {

    fun all(): List<Shortcut> {
        val raw = store.getString(KEY, null) ?: return DEFAULTS.also { write(it) }
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { Shortcut.fromJson(arr.getJSONObject(it)) }
        } catch (e: Exception) {
            DEFAULTS
        }
    }

    fun add(title: String, url: String) {
        val list = all().filterNot { it.url == url }.toMutableList()
        list.add(Shortcut(title.ifBlank { url }, url))
        write(list)
    }

    fun remove(url: String) = write(all().filterNot { it.url == url })

    fun resetToDefaults() = write(DEFAULTS)

    private fun write(list: List<Shortcut>) {
        val arr = JSONArray()
        list.forEach { arr.put(it.toJson()) }
        store.putString(KEY, arr.toString())
    }

    companion object {
        private const val KEY = "shortcuts"

        val DEFAULTS = listOf(
            Shortcut("Wikipedia", "https://www.wikipedia.org/"),
            Shortcut("YouTube", "https://m.youtube.com/"),
            Shortcut("GitHub", "https://github.com/"),
            Shortcut("Reddit", "https://www.reddit.com/"),
            Shortcut("BBC News", "https://www.bbc.com/news"),
            Shortcut("OpenStreetMap", "https://www.openstreetmap.org/"),
            Shortcut("Weather", "https://wttr.in/"),
            Shortcut("Translate", "https://translate.google.com/")
        )
    }
}
