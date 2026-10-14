package com.xcsaber.app.data

import com.xcsaber.app.search.SearchProvider

/** User settings, stored through [KvStore] (encrypted on device). */
class Prefs(private val store: KvStore) {

    var theme: String
        get() = store.getString(K_THEME, THEME_SYSTEM) ?: THEME_SYSTEM
        set(v) = store.putString(K_THEME, v)

    var searchProviderId: String
        get() = store.getString(K_PROVIDER, SearchProvider.DEFAULT_ID) ?: SearchProvider.DEFAULT_ID
        set(v) = store.putString(K_PROVIDER, v)

    val searchProvider: SearchProvider get() = SearchProvider.byId(searchProviderId)

    var trackerBlocking: Boolean
        get() = store.getBoolean(K_TRACKERS, true)
        set(v) = store.putBoolean(K_TRACKERS, v)

    var blockThirdPartyCookies: Boolean
        get() = store.getBoolean(K_3P_COOKIES, true)
        set(v) = store.putBoolean(K_3P_COOKIES, v)

    var httpsOnly: Boolean
        get() = store.getBoolean(K_HTTPS_ONLY, false)
        set(v) = store.putBoolean(K_HTTPS_ONLY, v)

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
    }
}
