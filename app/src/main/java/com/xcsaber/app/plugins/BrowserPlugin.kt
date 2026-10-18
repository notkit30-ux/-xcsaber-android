package com.xcsaber.app.plugins

import com.xcsaber.app.data.Prefs
import com.xcsaber.app.ui.SettingItem

enum class PluginCategory(val label: String) {
    PRIVACY("Privacy & security"),
    READING("Reading & appearance"),
    CLEANUP("Page cleanup"),
    PAGE("Page tools"),
    DEVELOPER("Developer"),
}

/** Accent used for a plugin's icon tile. */
enum class PluginAccent { BLUE, CYAN, VIOLET, OK, WARN }

/** Static description of a plugin. */
data class PluginInfo(
    val id: String,
    val name: String,
    val description: String,
    val category: PluginCategory,
    val iconRes: Int,
    val accent: PluginAccent,
    /** Debug-build-only plugins are hidden from release builds entirely. */
    val debugOnly: Boolean = false,
)

/** What a plugin may ask the browser to do. Implemented by the browser UI; fakeable in tests. */
interface PluginHost {
    fun openPrivacyCenter()
    fun updateBlocklist()
    fun reloadCurrentPage()
    /** Re-apply page settings (zoom, image blocking, darkening) to every open tab. */
    fun applyPageSettings()
    fun printCurrentPage()
    fun viewSourceOfCurrentPage()
    fun showPageInfo()
    fun clearHttpCache()
    fun applyDeveloperSettings()
    fun message(text: String)
    /** Ask the UI to redraw plugin cards after state changed. */
    fun pluginsChanged()
}

/**
 * A native XCSABER plugin.
 *
 * Plugins are compiled into the app and registered in [PluginRegistry]; nothing is downloaded or
 * evaluated at runtime. This is deliberately NOT a Chrome/Firefox extension loader: WebView does
 * not support those, and running third-party code inside a browser would be a security risk.
 */
interface BrowserPlugin {
    val info: PluginInfo
    fun isEnabled(prefs: Prefs): Boolean
    fun setEnabled(prefs: Prefs, enabled: Boolean, host: PluginHost)
    /** One honest line of live status, e.g. "42,180 domains · 17 blocked this session". */
    fun status(prefs: Prefs): String
    /** Controls shown when the user opens the plugin's settings. */
    fun settings(prefs: Prefs, host: PluginHost): List<SettingItem>
}
