package com.xcsaber.app.plugins

import android.content.res.AssetManager
import com.xcsaber.app.data.Prefs
import java.util.concurrent.ConcurrentHashMap

/** Loads the bundled add-on scripts from assets and builds the script to inject for a page. */
class AddonRuntime(
    private val assets: AssetManager,
    private val prefs: Prefs,
    private val catalog: List<ScriptAddon> = AddonCatalog.all,
) {
    private val cache = ConcurrentHashMap<String, String>()

    private fun body(id: String): String? {
        cache[id]?.let { return it }
        return try {
            assets.open("addons/$id.js").bufferedReader().use { it.readText() }.also { cache[id] = it }
        } catch (e: Exception) {
            null // a missing file just means that add-on does nothing
        }
    }

    /** The script to run on [url], or null if no enabled add-on applies. */
    fun scriptFor(url: String): String? =
        AddonEngine.scriptFor(AddonEngine.applicable(catalog, prefs.enabledAddons, url), ::body)
}
