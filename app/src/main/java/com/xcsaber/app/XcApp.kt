package com.xcsaber.app

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import com.xcsaber.app.browser.SitePermissionStore
import com.xcsaber.app.data.BookmarkStore
import com.xcsaber.app.data.HistoryStore
import com.xcsaber.app.data.Prefs
import com.xcsaber.app.data.RecentSearchStore
import com.xcsaber.app.data.SecureStore
import com.xcsaber.app.data.ShortcutStore
import com.xcsaber.app.downloads.DownloadStore
import com.xcsaber.app.plugins.BuiltinPlugins
import com.xcsaber.app.plugins.PluginRegistry
import com.xcsaber.app.privacy.BlockStats
import com.xcsaber.app.privacy.PrivacyManager
import com.xcsaber.app.privacy.TrackerListManager

/** Holds the app-wide singletons so every screen shares one set of stores. */
class XcApp : Application() {

    lateinit var secure: SecureStore
        private set
    lateinit var prefs: Prefs
        private set
    lateinit var history: HistoryStore
        private set
    lateinit var bookmarks: BookmarkStore
        private set
    lateinit var recents: RecentSearchStore
        private set
    lateinit var shortcuts: ShortcutStore
        private set
    lateinit var sitePermissions: SitePermissionStore
        private set
    lateinit var downloads: DownloadStore
        private set
    lateinit var trackers: TrackerListManager
        private set
    lateinit var privacy: PrivacyManager
        private set
    lateinit var stats: BlockStats
        private set
    lateinit var plugins: PluginRegistry
        private set
    lateinit var addons: com.xcsaber.app.plugins.AddonRuntime
        private set
    lateinit var userScripts: com.xcsaber.app.userscripts.UserScriptStore
        private set

    override fun onCreate() {
        super.onCreate()
        secure = SecureStore(this)
        prefs = Prefs(secure)
        history = HistoryStore(secure)
        bookmarks = BookmarkStore(secure)
        recents = RecentSearchStore(secure)
        shortcuts = ShortcutStore(secure)
        sitePermissions = SitePermissionStore(secure)
        downloads = DownloadStore(secure)
        trackers = TrackerListManager(this, prefs)
        privacy = PrivacyManager(this, prefs, history, recents, bookmarks)
        stats = BlockStats(prefs)
        userScripts = com.xcsaber.app.userscripts.UserScriptStore(secure)
        plugins = PluginRegistry(
            BuiltinPlugins.create {
                val nf = java.text.NumberFormat.getInstance()
                nf.format(trackers.blocker.size) + " domains · " + nf.format(stats.sessionCount()) + " blocked this session"
            } + com.xcsaber.app.plugins.UserScriptsPlugin(userScripts) +
                com.xcsaber.app.plugins.AddonCatalog.all.map { com.xcsaber.app.plugins.ScriptAddonPlugin(it) },
            debugBuild = BuildConfig.DEBUG
        )
        addons = com.xcsaber.app.plugins.AddonRuntime(assets, prefs)
        applyTheme(prefs.theme)
        // Loading the block list reads a file, so keep it off the main thread.
        Thread({ trackers.load() }, "xcsaber-trackers").start()
    }

    companion object {
        fun applyTheme(theme: String) {
            AppCompatDelegate.setDefaultNightMode(
                when (theme) {
                    Prefs.THEME_LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
                    Prefs.THEME_DARK -> AppCompatDelegate.MODE_NIGHT_YES
                    else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
                }
            )
        }
    }
}
