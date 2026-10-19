package com.xcsaber.app.plugins

/** The list of plugins compiled into this build. */
class PluginRegistry(all: List<BrowserPlugin>, private val debugBuild: Boolean) {
    private val plugins: List<BrowserPlugin> = run {
        val seen = HashSet<String>()
        all.filter { seen.add(it.info.id) } // an id can only be registered once
    }

    /** Plugins available in this build, in registration order. */
    fun visible(): List<BrowserPlugin> = plugins.filter { debugBuild || !it.info.debugOnly }

    fun byId(id: String): BrowserPlugin? = visible().firstOrNull { it.info.id == id }

    /** Visible plugins grouped by category, in category order, skipping empty categories. */
    fun grouped(): List<Pair<PluginCategory, List<BrowserPlugin>>> {
        val v = visible()
        return PluginCategory.values().mapNotNull { c ->
            val items = v.filter { it.info.category == c }
            if (items.isEmpty()) null else c to items
        }
    }
}
