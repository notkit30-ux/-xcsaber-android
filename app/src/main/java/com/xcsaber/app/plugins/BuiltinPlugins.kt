package com.xcsaber.app.plugins

import com.xcsaber.app.R
import com.xcsaber.app.data.Prefs
import com.xcsaber.app.ui.SettingItem

/**
 * The plugins that ship inside XCSABER. Each one is plain native Kotlin that flips preferences the
 * browser already honours; none of them loads or runs code from the network or from other apps.
 */
object BuiltinPlugins {

    fun create(blockerStatus: () -> String): List<BrowserPlugin> = listOf(
        AdTrackerBlocker(blockerStatus),
        PrivacyGuard(),
        PageTools(),
        DeveloperTools(),
    )

    private class AdTrackerBlocker(private val liveStatus: () -> String) : BrowserPlugin {
        override val info = PluginInfo(
            id = "adblock",
            name = "Ad & Tracker Blocker",
            description = "Blocks requests to known advertising and tracking domains before they load. " +
                "It reduces ads and tracking, but it cannot remove every ad.",
            category = PluginCategory.PRIVACY,
            iconRes = R.drawable.ic_shield_check,
            accent = PluginAccent.BLUE,
        )

        override fun isEnabled(prefs: Prefs) = prefs.trackerBlocking

        override fun setEnabled(prefs: Prefs, enabled: Boolean, host: PluginHost) {
            prefs.trackerBlocking = enabled
            host.pluginsChanged()
            host.message(if (enabled) "Ad & tracker blocking on" else "Ad & tracker blocking off")
        }

        override fun status(prefs: Prefs) = if (prefs.trackerBlocking) liveStatus() else "Off"

        override fun settings(prefs: Prefs, host: PluginHost): List<SettingItem> = listOf(
            SettingItem.Toggle(
                id = "adblock.enabled", title = "Block ads and trackers",
                summary = "Applies to new page loads in every tab.",
                iconRes = R.drawable.ic_shield_check,
                checked = { prefs.trackerBlocking },
                onChange = { setEnabled(prefs, it, host) },
            ),
            SettingItem.Action(
                id = "adblock.update", title = "Update blocklist",
                summary = "Downloads the latest maintained list over HTTPS.",
                iconRes = R.drawable.ic_refresh,
                onClick = { host.updateBlocklist() },
            ),
            SettingItem.Action(
                id = "adblock.center", title = "Open Privacy Center",
                summary = "Counters, list details and all privacy controls.",
                iconRes = R.drawable.ic_shield,
                onClick = { host.openPrivacyCenter() },
            ),
        )
    }

    private class PrivacyGuard : BrowserPlugin {
        override val info = PluginInfo(
            id = "guard",
            name = "Privacy Guard",
            description = "Third-party cookie blocking, HTTPS-only mode and the Global Privacy Control " +
                "signal. Turning the guard off pauses all three.",
            category = PluginCategory.PRIVACY,
            iconRes = R.drawable.ic_lock,
            accent = PluginAccent.VIOLET,
        )

        override fun isEnabled(prefs: Prefs) = prefs.privacyGuard

        override fun setEnabled(prefs: Prefs, enabled: Boolean, host: PluginHost) {
            prefs.privacyGuard = enabled
            host.applyPageSettings()
            host.pluginsChanged()
            host.message(if (enabled) "Privacy Guard on" else "Privacy Guard paused")
        }

        override fun status(prefs: Prefs): String {
            if (!prefs.privacyGuard) return "Paused"
            val active = listOf(
                prefs.blockThirdPartyCookies, prefs.httpsOnly, prefs.sendGpc
            ).count { it }
            return "$active of 3 protections active"
        }

        override fun settings(prefs: Prefs, host: PluginHost): List<SettingItem> = listOf(
            SettingItem.Toggle(
                id = "guard.enabled", title = "Privacy Guard",
                summary = "Master switch for the three protections below.",
                iconRes = R.drawable.ic_lock,
                checked = { prefs.privacyGuard },
                onChange = { setEnabled(prefs, it, host) },
            ),
            SettingItem.Toggle(
                id = "guard.cookies", title = "Block third-party cookies",
                summary = "Stops sites embedded in other pages from setting cookies.",
                iconRes = R.drawable.ic_cookie,
                enabled = { prefs.privacyGuard },
                checked = { prefs.blockThirdPartyCookies },
                onChange = { prefs.blockThirdPartyCookies = it; host.applyPageSettings(); host.pluginsChanged() },
            ),
            SettingItem.Toggle(
                id = "guard.https", title = "HTTPS-only mode",
                summary = "Upgrades http:// links to https://. Sites without HTTPS may fail to load.",
                iconRes = R.drawable.ic_lock,
                enabled = { prefs.privacyGuard },
                checked = { prefs.httpsOnly },
                onChange = { prefs.httpsOnly = it; host.pluginsChanged() },
            ),
            SettingItem.Toggle(
                id = "guard.gpc", title = "Send Global Privacy Control",
                summary = "Tells sites you do not want your data sold or shared. Sites may ignore it.",
                iconRes = R.drawable.ic_eye,
                enabled = { prefs.privacyGuard },
                checked = { prefs.sendGpc },
                onChange = { prefs.sendGpc = it; host.pluginsChanged() },
            ),
        )
    }

    private class PageTools : BrowserPlugin {
        override val info = PluginInfo(
            id = "pagetools",
            name = "Page Tools",
            description = "Text size, image blocking, dark pages and desktop-site defaults, plus " +
                "printing or saving the current page as a PDF.",
            category = PluginCategory.PAGE,
            iconRes = R.drawable.ic_wrench,
            accent = PluginAccent.CYAN,
        )

        override fun isEnabled(prefs: Prefs) = prefs.pageToolsEnabled

        override fun setEnabled(prefs: Prefs, enabled: Boolean, host: PluginHost) {
            prefs.pageToolsEnabled = enabled
            host.applyPageSettings()
            host.pluginsChanged()
        }

        override fun status(prefs: Prefs): String {
            if (!prefs.pageToolsEnabled) return "Off"
            val parts = ArrayList<String>()
            if (prefs.pageTextZoom != 100) parts.add("text ${prefs.pageTextZoom}%")
            if (prefs.blockImages) parts.add("images blocked")
            if (prefs.desktopModeDefault) parts.add("desktop sites")
            return if (parts.isEmpty()) "Default view" else parts.joinToString(" · ")
        }

        override fun settings(prefs: Prefs, host: PluginHost): List<SettingItem> = listOf(
            SettingItem.Toggle(
                id = "pagetools.enabled", title = "Page Tools",
                summary = "When off, pages use their normal size and images.",
                iconRes = R.drawable.ic_wrench,
                checked = { prefs.pageToolsEnabled },
                onChange = { setEnabled(prefs, it, host) },
            ),
            SettingItem.Slider(
                id = "pagetools.zoom", title = "Text size",
                summary = "Scales text on every page.",
                min = Prefs.TEXT_ZOOM_MIN, max = Prefs.TEXT_ZOOM_MAX, step = 10,
                iconRes = R.drawable.ic_text_size,
                enabled = { prefs.pageToolsEnabled },
                value = { prefs.pageTextZoom },
                format = { "$it%" },
                onChange = { prefs.pageTextZoom = it; host.applyPageSettings(); host.pluginsChanged() },
            ),
            SettingItem.Toggle(
                id = "pagetools.dark", title = "Darken websites",
                summary = "Dark pages when XCSABER is in dark theme. Some sites may look unusual.",
                iconRes = R.drawable.ic_moon,
                enabled = { prefs.pageToolsEnabled },
                checked = { prefs.pageDarkening },
                onChange = { prefs.pageDarkening = it; host.applyPageSettings() },
            ),
            SettingItem.Toggle(
                id = "pagetools.images", title = "Block images",
                summary = "Saves data and speeds up slow connections. Reload the page to apply.",
                iconRes = R.drawable.ic_ban,
                enabled = { prefs.pageToolsEnabled },
                checked = { prefs.blockImages },
                onChange = { prefs.blockImages = it; host.applyPageSettings(); host.pluginsChanged() },
            ),
            SettingItem.Toggle(
                id = "pagetools.desktop", title = "Request desktop sites by default",
                summary = "Applies to new tabs. You can still switch a single tab from the menu.",
                iconRes = R.drawable.ic_desktop,
                enabled = { prefs.pageToolsEnabled },
                checked = { prefs.desktopModeDefault },
                onChange = { prefs.desktopModeDefault = it; host.pluginsChanged() },
            ),
            SettingItem.Action(
                id = "pagetools.print", title = "Print or save page as PDF",
                summary = "Uses Android's print dialog for the current page.",
                iconRes = R.drawable.ic_printer,
                onClick = { host.printCurrentPage() },
            ),
        )
    }

    private class DeveloperTools : BrowserPlugin {
        override val info = PluginInfo(
            id = "devtools",
            name = "Developer Tools",
            description = "Debug-build tools: remote debugging from desktop Chrome, page info, " +
                "view source and cache clearing. Not included in release builds.",
            category = PluginCategory.DEVELOPER,
            iconRes = R.drawable.ic_terminal,
            accent = PluginAccent.WARN,
            debugOnly = true,
        )

        override fun isEnabled(prefs: Prefs) = prefs.devToolsEnabled

        override fun setEnabled(prefs: Prefs, enabled: Boolean, host: PluginHost) {
            prefs.devToolsEnabled = enabled
            host.applyDeveloperSettings()
            host.pluginsChanged()
        }

        override fun status(prefs: Prefs): String = when {
            !prefs.devToolsEnabled -> "Off"
            prefs.devRemoteDebugging -> "Remote debugging on"
            else -> "On"
        }

        override fun settings(prefs: Prefs, host: PluginHost): List<SettingItem> = listOf(
            SettingItem.Toggle(
                id = "dev.enabled", title = "Developer Tools",
                summary = "Debug builds only.",
                iconRes = R.drawable.ic_terminal,
                checked = { prefs.devToolsEnabled },
                onChange = { setEnabled(prefs, it, host) },
            ),
            SettingItem.Toggle(
                id = "dev.remote", title = "Remote debugging",
                summary = "Lets a computer inspect pages through chrome://inspect over USB. Leave off unless you need it.",
                iconRes = R.drawable.ic_code,
                enabled = { prefs.devToolsEnabled },
                checked = { prefs.devRemoteDebugging },
                onChange = { prefs.devRemoteDebugging = it; host.applyDeveloperSettings(); host.pluginsChanged() },
            ),
            SettingItem.Action(
                id = "dev.info", title = "Page info",
                summary = "URL, title, user agent and blocked-request count.",
                iconRes = R.drawable.ic_info,
                onClick = { if (prefs.devToolsEnabled) host.showPageInfo() else host.message("Turn on Developer Tools first") },
            ),
            SettingItem.Action(
                id = "dev.source", title = "View page source",
                summary = "Shows the HTML the page currently contains.",
                iconRes = R.drawable.ic_code,
                onClick = { if (prefs.devToolsEnabled) host.viewSourceOfCurrentPage() else host.message("Turn on Developer Tools first") },
            ),
            SettingItem.Action(
                id = "dev.cache", title = "Clear HTTP cache",
                summary = "Removes cached files without touching cookies or history.",
                iconRes = R.drawable.ic_trash,
                onClick = { if (prefs.devToolsEnabled) host.clearHttpCache() else host.message("Turn on Developer Tools first") },
            ),
        )
    }
}
