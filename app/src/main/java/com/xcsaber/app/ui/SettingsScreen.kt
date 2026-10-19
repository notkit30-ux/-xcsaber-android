package com.xcsaber.app.ui

import android.os.Build
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.webkit.WebViewCompat
import com.xcsaber.app.BuildConfig
import com.xcsaber.app.R
import com.xcsaber.app.XcApp
import com.xcsaber.app.data.Prefs
import com.xcsaber.app.databinding.PanelSettingsBinding
import java.text.NumberFormat

/** The Settings screen: grouped, searchable, and backed by the same [SettingItem] rows as everywhere else. */
class SettingsScreen(
    private val activity: AppCompatActivity,
    private val xc: XcApp,
    private val bridge: PanelBridge,
    container: ViewGroup
) {
    private val b = PanelSettingsBinding.inflate(LayoutInflater.from(activity), container, false)
    val root: View get() = b.root
    private val prefs: Prefs get() = xc.prefs
    private val adapter = SettingsAdapter { /* values persist as they change */ }
    private var catalog: List<SettingItem> = emptyList()

    init {
        container.addView(b.root)
        b.root.visibility = View.GONE
        b.stBack.setOnClickListener { bridge.closePanel() }
        b.stList.layoutManager = LinearLayoutManager(activity)
        b.stList.adapter = adapter
        b.stSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) = applyFilter()
        })
        refresh()
    }

    fun themeLabel(theme: String) = when (theme) {
        Prefs.THEME_LIGHT -> "Light"
        Prefs.THEME_DARK -> "Dark"
        else -> "System default"
    }

    /** Rebuilds the catalog so values that changed elsewhere (provider, theme) show correctly. */
    fun refresh() {
        catalog = buildCatalog()
        applyFilter()
    }

    private fun applyFilter() {
        val shown = SettingsFilter.filter(catalog, b.stSearch.text?.toString().orEmpty())
        adapter.submit(shown)
        b.stEmpty.visibility = if (shown.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun buildCatalog(): List<SettingItem> = listOf(
        SettingItem.Header("h.appearance", "Appearance"),
        SettingItem.Choice(
            "theme", "Theme", R.drawable.ic_moon, "dark light system night day appearance",
            value = { themeLabel(prefs.theme) }, onClick = { bridge.showThemeSheet() }
        ),

        SettingItem.Header("h.search", "Search"),
        SettingItem.Choice(
            "provider", "Search provider", R.drawable.ic_search_engine, "duckduckgo google bing brave engine",
            value = { prefs.searchProvider.name }, onClick = { bridge.showProviderSheet { refresh() } }
        ),
        SettingItem.Toggle(
            "suggestions", "Search suggestions", "Sends what you type to your search provider to show suggestions.",
            R.drawable.ic_search, "autocomplete",
            checked = { prefs.searchSuggestions }, onChange = { prefs.searchSuggestions = it }
        ),
        SettingItem.Toggle(
            "recents", "Save recent searches", "Shows them on the home screen. Private tabs never save searches.",
            R.drawable.ic_history, "",
            checked = { prefs.saveRecentSearches }, onChange = { prefs.saveRecentSearches = it }
        ),

        SettingItem.Header("h.privacy", "Privacy & security"),
        SettingItem.Toggle(
            "trackers", "Block ads and trackers", "Blocks requests to known ad and tracking domains.",
            R.drawable.ic_shield_check, "adblock ad blocker tracking",
            checked = { prefs.trackerBlocking }, onChange = { prefs.trackerBlocking = it; bridge.pluginsChanged() }
        ),
        SettingItem.Toggle(
            "guard", "Privacy Guard", "Master switch for cookie blocking, HTTPS-only and privacy signals.",
            R.drawable.ic_lock, "",
            checked = { prefs.privacyGuard }, onChange = { prefs.privacyGuard = it; bridge.applyPageSettings(); bridge.pluginsChanged() }
        ),
        SettingItem.Toggle(
            "cookies", "Block third-party cookies", "Stops embedded sites from setting cookies.",
            R.drawable.ic_cookie, "",
            checked = { prefs.blockThirdPartyCookies }, onChange = { prefs.blockThirdPartyCookies = it; bridge.applyPageSettings() }
        ),
        SettingItem.Toggle(
            "https", "HTTPS-only mode", "Upgrades http:// links to https://. Some old sites may not load.",
            R.drawable.ic_lock, "secure",
            checked = { prefs.httpsOnly }, onChange = { prefs.httpsOnly = it }
        ),
        SettingItem.Toggle(
            "gpc", "Send Global Privacy Control", "Asks sites not to sell or share your data. Sites may ignore it.",
            R.drawable.ic_eye, "dnt do not track",
            checked = { prefs.sendGpc }, onChange = { prefs.sendGpc = it }
        ),
        SettingItem.Toggle(
            "popups", "Block pop-ups", "Blocks windows a page opens without you tapping.",
            R.drawable.ic_ban, "",
            checked = { prefs.blockPopups }, onChange = { prefs.blockPopups = it; bridge.applyPageSettings() }
        ),
        SettingItem.Action(
            "privacycenter", "Privacy Center", "Counters, blocklist details and update.",
            R.drawable.ic_shield, "", onClick = { bridge.openPrivacyCenter() }
        ),
        SettingItem.Action(
            "perms", "Website permissions", "Camera, microphone and location choices.",
            R.drawable.ic_pin, "", onClick = { bridge.showSitePermissions() }
        ),

        SettingItem.Header("h.browsing", "Browsing"),
        SettingItem.Toggle(
            "history", "Save browsing history", "Pages you visit in normal tabs. Also restores your tabs after restart.",
            R.drawable.ic_history, "",
            checked = { prefs.saveHistory }, onChange = { prefs.saveHistory = it }
        ),
        SettingItem.Toggle(
            "desktop", "Request desktop sites by default", "Applies to new tabs.",
            R.drawable.ic_desktop, "",
            checked = { prefs.desktopModeDefault }, onChange = { prefs.desktopModeDefault = it; bridge.pluginsChanged() }
        ),
        SettingItem.Action(
            "extensions", "Extensions & plugins", "Ad & Tracker Blocker, Privacy Guard, Page Tools.",
            R.drawable.ic_extension, "plugins addons", onClick = { bridge.openExtensionsPanel() }
        ),

        SettingItem.Header("h.downloads", "Downloads"),
        SettingItem.Toggle(
            "confirmdl", "Ask before downloading", "Programs and installers always ask, whatever this is set to.",
            R.drawable.ic_download, "",
            checked = { prefs.confirmDownloads }, onChange = { prefs.confirmDownloads = it }
        ),
        SettingItem.Action(
            "downloads", "Downloads", "Files are saved to your device's Downloads folder.",
            R.drawable.ic_list, "", onClick = { bridge.showDownloads() }
        ),

        SettingItem.Header("h.data", "Your data"),
        SettingItem.Action(
            "bookmarks", "Bookmarks", "", R.drawable.ic_star, "", onClick = { bridge.showBookmarks() }
        ),
        SettingItem.Action(
            "historylist", "History", "", R.drawable.ic_history, "", onClick = { bridge.showHistory() }
        ),
        SettingItem.Action(
            "clear", "Clear browsing data", "Cookies, cache, history and more.",
            R.drawable.ic_trash, "delete wipe", destructive = true, onClick = { bridge.showClearDataDialog() }
        ),

        SettingItem.Header("h.about", "About"),
        SettingItem.Action(
            "about", "About XCSABER", "Version " + BuildConfig.VERSION_NAME,
            R.drawable.ic_info, "version build licence", onClick = { showAbout() }
        ),
    )

    private fun webViewVersion(): String = try {
        val pkg = WebViewCompat.getCurrentWebViewPackage(activity)
        if (pkg != null) pkg.packageName + " " + pkg.versionName else "unknown"
    } catch (e: Exception) {
        "unknown"
    }

    fun showAbout() {
        val inflater = LayoutInflater.from(activity)
        val root = inflater.inflate(R.layout.sheet_about, null)
        val rows = root.findViewById<LinearLayout>(R.id.aboutRows)
        val nf = NumberFormat.getInstance()
        val items = listOf(
            "Version" to BuildConfig.VERSION_NAME,
            "Build number" to BuildConfig.VERSION_CODE.toString(),
            "Build type" to BuildConfig.BUILD_TYPE,
            "Package" to BuildConfig.APPLICATION_ID,
            "Android" to Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")",
            "WebView" to webViewVersion(),
            "Blocklist" to nf.format(xc.trackers.blocker.size) + " domains",
            "Plugins" to xc.plugins.visible().size.toString(),
        )
        for ((k, v) in items) {
            val row = inflater.inflate(R.layout.item_setting_info, rows, false)
            row.findViewById<TextView>(R.id.settingTitle).text = k
            row.findViewById<TextView>(R.id.settingValue).text = v
            rows.addView(row)
        }
        root.findViewById<TextView>(R.id.aboutNote).text =
            "XCSABER uses Android's system WebView to show pages. It blocks known trackers and never " +
                "stores private-tab history, but it does not hide your IP address."
        Sheets.show(activity, root)
    }
}
