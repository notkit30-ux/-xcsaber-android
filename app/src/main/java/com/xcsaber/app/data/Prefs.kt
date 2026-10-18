package com.xcsaber.app.data

import com.xcsaber.app.search.SearchProvider

/** User settings, stored through [KvStore] (encrypted on device). */
class Prefs(private val store: KvStore) {

    var theme: String
        get() = store.getString(K_THEME, THEME_DEFAULT) ?: THEME_DEFAULT
        set(v) = store.putString(K_THEME, v)

    var searchProviderId: String
        get() = store.getString(K_PROVIDER, SearchProvider.DEFAULT_ID) ?: SearchProvider.DEFAULT_ID
        set(v) = store.putString(K_PROVIDER, v)

    val searchProvider: SearchProvider get() = SearchProvider.byId(searchProviderId)

    var trackerBlocking: Boolean
        get() = store.getBoolean(K_TRACKERS, true)
        set(v) = store.putBoolean(K_TRACKERS, v)

    /** Stored switch for third-party cookie blocking. Use [effectiveBlockThirdPartyCookies] to apply it. */
    var blockThirdPartyCookies: Boolean
        get() = store.getBoolean(K_3P_COOKIES, true)
        set(v) = store.putBoolean(K_3P_COOKIES, v)

    /** Stored switch for HTTPS-only. Use [effectiveHttpsOnly] to apply it. */
    var httpsOnly: Boolean
        get() = store.getBoolean(K_HTTPS_ONLY, false)
        set(v) = store.putBoolean(K_HTTPS_ONLY, v)

    /** Stored switch for sending Global Privacy Control / Do Not Track. Use [effectiveSendGpc]. */
    var sendGpc: Boolean
        get() = store.getBoolean(K_GPC, true)
        set(v) = store.putBoolean(K_GPC, v)

    /** Privacy Guard plugin master switch: when off, the three protections above are not applied. */
    var privacyGuard: Boolean
        get() = store.getBoolean(K_GUARD, true)
        set(v) = store.putBoolean(K_GUARD, v)

    val effectiveBlockThirdPartyCookies: Boolean get() = privacyGuard && blockThirdPartyCookies
    val effectiveHttpsOnly: Boolean get() = privacyGuard && httpsOnly
    val effectiveSendGpc: Boolean get() = privacyGuard && sendGpc

    /** Lifetime count of blocked requests from normal (non-private) tabs. */
    var blockedTotal: Long
        get() = store.getLong(K_BLOCKED_TOTAL, 0L)
        set(v) = store.putLong(K_BLOCKED_TOTAL, v)

    // ---- Page Tools plugin ----
    var pageToolsEnabled: Boolean
        get() = store.getBoolean(K_PT_ENABLED, true)
        set(v) = store.putBoolean(K_PT_ENABLED, v)

    /** Page text zoom in percent, clamped to [TEXT_ZOOM_MIN]..[TEXT_ZOOM_MAX]. */
    var pageTextZoom: Int
        get() = store.getLong(K_PT_ZOOM, 100L).toInt().coerceIn(TEXT_ZOOM_MIN, TEXT_ZOOM_MAX)
        set(v) = store.putLong(K_PT_ZOOM, v.coerceIn(TEXT_ZOOM_MIN, TEXT_ZOOM_MAX).toLong())

    /** Darken websites while the app theme is dark (WebView algorithmic darkening). */
    var pageDarkening: Boolean
        get() = store.getBoolean(K_PT_DARK, true)
        set(v) = store.putBoolean(K_PT_DARK, v)

    var blockImages: Boolean
        get() = store.getBoolean(K_PT_IMAGES, false)
        set(v) = store.putBoolean(K_PT_IMAGES, v)

    // ---- Developer tools plugin (only reachable in debug builds) ----
    var devToolsEnabled: Boolean
        get() = store.getBoolean(K_DEV_ENABLED, false)
        set(v) = store.putBoolean(K_DEV_ENABLED, v)

    var devRemoteDebugging: Boolean
        get() = store.getBoolean(K_DEV_REMOTE, false)
        set(v) = store.putBoolean(K_DEV_REMOTE, v)

    /** Ask before saving a download. Risky file types always ask regardless. */
    var confirmDownloads: Boolean
        get() = store.getBoolean(K_CONFIRM_DL, true)
        set(v) = store.putBoolean(K_CONFIRM_DL, v)

    var saveHistory: Boolean
        get() = store.getBoolean(K_SAVE_HISTORY, true)
        set(v) = store.putBoolean(K_SAVE_HISTORY, v)

    var saveRecentSearches: Boolean
        get() = store.getBoolean(K_SAVE_SEARCHES, true)
        set(v) = store.putBoolean(K_SAVE_SEARCHES, v)

    var desktopModeDefault: Boolean
        get() = store.getBoolean(K_DESKTOP, false)
        set(v) = store.putBoolean(K_DESKTOP, v)

    var blockPopups: Boolean
        get() = store.getBoolean(K_POPUPS, true)
        set(v) = store.putBoolean(K_POPUPS, v)

    var searchSuggestions: Boolean
        get() = store.getBoolean(K_SUGGEST, true)
        set(v) = store.putBoolean(K_SUGGEST, v)

    var trackerListUpdatedAt: Long
        get() = store.getLong(K_LIST_TS, 0L)
        set(v) = store.putLong(K_LIST_TS, v)

    var trackerListSource: String?
        get() = store.getString(K_LIST_SRC, null)
        set(v) = store.putString(K_LIST_SRC, v)

    companion object {
        const val THEME_SYSTEM = "system"
        const val THEME_LIGHT = "light"
        const val THEME_DARK = "dark"
        /** XCSABER is designed dark-first. */
        const val THEME_DEFAULT = THEME_DARK
        const val TEXT_ZOOM_MIN = 50
        const val TEXT_ZOOM_MAX = 200

        private const val K_THEME = "theme"
        private const val K_PROVIDER = "provider"
        private const val K_TRACKERS = "tracker_blocking"
        private const val K_3P_COOKIES = "block_3p_cookies"
        private const val K_HTTPS_ONLY = "https_only"
        private const val K_SAVE_HISTORY = "save_history"
        private const val K_SAVE_SEARCHES = "save_recent_searches"
        private const val K_DESKTOP = "desktop_default"
        private const val K_POPUPS = "block_popups"
        private const val K_SUGGEST = "search_suggestions"
        private const val K_LIST_TS = "tracker_list_ts"
        private const val K_LIST_SRC = "tracker_list_src"
        private const val K_GPC = "send_gpc"
        private const val K_GUARD = "privacy_guard"
        private const val K_BLOCKED_TOTAL = "blocked_total"
        private const val K_PT_ENABLED = "page_tools_enabled"
        private const val K_PT_ZOOM = "page_text_zoom"
        private const val K_PT_DARK = "page_darkening"
        private const val K_PT_IMAGES = "block_images"
        private const val K_DEV_ENABLED = "dev_tools_enabled"
        private const val K_DEV_REMOTE = "dev_remote_debugging"
        private const val K_CONFIRM_DL = "confirm_downloads"
    }
}
