package com.xcsaber.app.browser

import org.json.JSONArray
import org.json.JSONObject

/**
 * Keeps the list of open tabs and which one is selected. Has no Android view code, so it is
 * unit tested on the JVM. Private tabs are never written by [sessionJson].
 */
class TabManager {
    private val list = ArrayList<Tab>()
    private var nextId = 1L

    val tabs: List<Tab> get() = list
    var currentId: Long = -1L
        private set

    val current: Tab? get() = list.firstOrNull { it.id == currentId }
    val count: Int get() = list.size

    fun newTab(isPrivate: Boolean, parentId: Long? = null, select: Boolean = true): Tab {
        val tab = Tab(nextId++, isPrivate, parentId)
        list.add(tab)
        if (select || currentId == -1L) currentId = tab.id
        return tab
    }

    fun find(id: Long): Tab? = list.firstOrNull { it.id == id }

    fun findByWebView(view: Any?): Tab? = list.firstOrNull { it.webView === view }

    fun select(id: Long): Boolean {
        if (find(id) == null) return false
        currentId = id
        return true
    }

    /**
     * Removes a tab. If it was the selected one, selects its opener when that is still open,
     * otherwise the neighbouring tab. Returns the removed tab, or null if the id is unknown.
     */
    fun close(id: Long): Tab? {
        val index = list.indexOfFirst { it.id == id }
        if (index < 0) return null
        val removed = list.removeAt(index)
        if (id == currentId) {
            val parent = removed.parentId?.let { find(it) }
            currentId = when {
                parent != null -> parent.id
                list.isEmpty() -> -1L
                else -> list[minOf(index, list.size - 1)].id
            }
        }
        return removed
    }

    fun privateTabs(): List<Tab> = list.filter { it.isPrivate }

    fun hasPrivateTabs(): Boolean = list.any { it.isPrivate }

    /** Removes every private tab and returns them so the caller can destroy their WebViews. */
    fun closePrivateTabs(): List<Tab> {
        val removed = privateTabs()
        removed.forEach { close(it.id) }
        return removed
    }

    /** JSON describing the normal (non-private) tabs, for restoring the session next launch. */
    fun sessionJson(): String {
        val normal = list.filter { !it.isPrivate && it.url.isNotBlank() }
        val arr = JSONArray()
        var selected = -1
        normal.forEachIndexed { i, t ->
            arr.put(JSONObject().put("u", t.url).put("t", t.title).put("d", t.desktop))
            if (t.id == currentId) selected = i
        }
        return JSONObject().put("tabs", arr).put("selected", selected).toString()
    }

    /** Re-creates tabs from [sessionJson] output. Returns how many tabs were restored. */
    fun restore(json: String?): Int {
        if (json.isNullOrBlank()) return 0
        return try {
            val root = JSONObject(json)
            val arr = root.optJSONArray("tabs") ?: return 0
            val selected = root.optInt("selected", -1)
            var restored = 0
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val url = o.optString("u")
                if (url.isBlank()) continue
                val tab = newTab(isPrivate = false, select = i == selected)
                tab.url = url
                tab.title = o.optString("t")
                tab.desktop = o.optBoolean("d")
                tab.needsLoad = true
                restored++
            }
            restored
        } catch (e: Exception) {
            0
        }
    }
}
