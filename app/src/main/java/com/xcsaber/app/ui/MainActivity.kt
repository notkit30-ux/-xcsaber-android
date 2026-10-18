package com.xcsaber.app.ui

import android.Manifest
import android.app.SearchManager
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.print.PrintAttributes
import android.print.PrintManager
import android.speech.RecognizerIntent
import android.text.Editable
import android.text.TextWatcher
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.CookieManager
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.addCallback
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.ViewCompat
import androidx.core.widget.ImageViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.tabs.TabLayout
import com.xcsaber.app.BuildConfig
import com.xcsaber.app.R
import com.xcsaber.app.XcApp
import com.xcsaber.app.browser.BrowserHost
import com.xcsaber.app.browser.ErrorKind
import com.xcsaber.app.browser.PageError
import com.xcsaber.app.browser.SitePermission
import com.xcsaber.app.browser.Tab
import com.xcsaber.app.browser.TabManager
import com.xcsaber.app.browser.UserAgents
import com.xcsaber.app.browser.XcChromeClient
import com.xcsaber.app.browser.XcWebViewClient
import com.xcsaber.app.data.Prefs
import com.xcsaber.app.data.Shortcut
import com.xcsaber.app.databinding.ActivityMainBinding
import com.xcsaber.app.downloads.DownloadHelper
import com.xcsaber.app.downloads.DownloadPolicy
import com.xcsaber.app.privacy.PrivacyManager
import com.xcsaber.app.search.SearchCategory
import com.xcsaber.app.search.SearchEngine
import com.xcsaber.app.search.SearchProvider
import com.xcsaber.app.search.SuggestionClient
import com.xcsaber.app.search.UrlUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONTokener

/**
 * The single screen of XCSABER. It owns the tab list and switches between the home screen, the
 * current tab's WebView, the tab switcher and the privacy/settings panel.
 */
class MainActivity : AppCompatActivity(), BrowserHost, PanelBridge {

    private enum class Screen { CONTENT, TABS, PRIVACY, SETTINGS, EXTENSIONS }

    private lateinit var b: ActivityMainBinding
    private lateinit var xc: XcApp

    private val prefs: Prefs get() = xc.prefs
    private val tabs = TabManager()
    private val handler = Handler(Looper.getMainLooper())
    private val downloader by lazy { DownloadHelper(this) }
    private val defaultUserAgent by lazy { WebSettings.getDefaultUserAgent(this) }

    private var screen = Screen.CONTENT
    private var category = SearchCategory.WEB
    private var lastQuery = ""
    private var suggestionsVisible = false
    private var findVisible = false
    private var programmaticText = false
    private var suppressTabEvents = false
    private var suppressNav = false
    private var panelBack: Screen? = null
    private var privacyCenter: PrivacyCenter? = null
    private var settingsScreen: SettingsScreen? = null
    private var extensionsCenter: ExtensionsCenter? = null
    private var homeShownForTab = -1L
    private var suggestJob: Job? = null
    private var endPrivateRunnable: Runnable? = null

    private var fullscreenView: View? = null
    private var fullscreenCallback: WebChromeClient.CustomViewCallback? = null
    private var fileCallback: ValueCallback<Array<Uri>>? = null
    private var pendingPermissionCallback: (() -> Unit)? = null
    private var externalDialog: AlertDialog? = null

    private lateinit var suggestionAdapter: RowAdapter
    private lateinit var recentAdapter: RowAdapter
    private lateinit var shortcutAdapter: ShortcutAdapter
    private lateinit var tabsAdapter: TabsAdapter

    // ---- Activity result launchers (must be created before the activity starts) ----

    private val voiceLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
                if (!spoken.isNullOrBlank()) submitInput(spoken)
            }
        }

    private val fileLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            val uris = WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data)
            fileCallback?.onReceiveValue(uris)
            fileCallback = null
        }

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            val callback = pendingPermissionCallback
            pendingPermissionCallback = null
            callback?.invoke()
        }

    // ---------------------------------------------------------------------------------------
    // Lifecycle
    // ---------------------------------------------------------------------------------------

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)
        xc = application as XcApp

        // Wipe any private profile left behind by a previous run, before any WebView exists.
        xc.privacy.endPrivateSession()

        setupInsets()
        setupAdapters()
        setupSearchBar()
        setupCategoryTabs()
        setupHome()
        setupNavigation()
        setupTabsPanel()
        setupHomeActions()
        setupFindBar()
        setupErrorView()
        setupBackHandling()

        applyDeveloperSettings()
        if (tabs.restore(xc.secure.getString(SESSION_KEY)) == 0) tabs.newTab(isPrivate = false)
        if (savedInstanceState == null) handleIntent(intent)
        render()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        tabs.tabs.forEach { it.webView?.onResume() }
    }

    override fun onPause() {
        tabs.tabs.forEach { it.webView?.onPause() }
        super.onPause()
    }

    override fun onStop() {
        saveSession()
        if (::xc.isInitialized) xc.stats.flush()
        super.onStop()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        suggestJob?.cancel()
        fileCallback?.onReceiveValue(null)
        fileCallback = null
        exitFullscreen()
        tabs.tabs.toList().forEach { destroyWebView(it) }
        if (::xc.isInitialized) xc.privacy.endPrivateSession()
        super.onDestroy()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_BACKGROUND) {
            // Free the preview bitmaps of tabs that are not on screen; they are rebuilt when needed.
            tabs.tabs.forEach { if (it.id != tabs.currentId) it.thumbnail = null }
        }
    }

    private fun saveSession() {
        if (!::xc.isInitialized) return
        if (prefs.saveHistory) xc.secure.putString(SESSION_KEY, tabs.sessionJson())
        else xc.secure.remove(SESSION_KEY)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        when (intent.action) {
            Intent.ACTION_VIEW -> {
                val url = intent.dataString
                if (url != null && UrlUtils.classifyScheme(url) == UrlUtils.Kind.WEB_URL && UrlUtils.isValidWebUrl(url)) {
                    openNewTab(isPrivate = false, url = url)
                }
            }
            Intent.ACTION_WEB_SEARCH -> intent.getStringExtra(SearchManager.QUERY)?.let { submitInput(it) }
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)?.let { submitInput(it) }
        }
        setIntent(Intent())
    }

    // ---------------------------------------------------------------------------------------
    // Setup
    // ---------------------------------------------------------------------------------------

    private fun setupInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(b.rootLayout) { v, insets ->
            if (fullscreenView != null) {
                v.setPadding(0, 0, 0, 0)
                b.bottomInsetSpacer.layoutParams.height = 0
            } else {
                val bars = insets.getInsets(
                    WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
                )
                val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
                val keyboardUp = ime.bottom > bars.bottom
                v.setPadding(bars.left, bars.top, bars.right, if (keyboardUp) ime.bottom else 0)
                // The bottom bar extends under the system navigation bar; the keyboard covers it instead.
                b.bottomInsetSpacer.layoutParams.height = if (keyboardUp) 0 else bars.bottom
            }
            b.bottomInsetSpacer.requestLayout()
            WindowInsetsCompat.CONSUMED
        }
    }

    private fun setupAdapters() {
        suggestionAdapter = RowAdapter(onClick = { row -> submitInput(row.key) })
        b.suggestionList.layoutManager = LinearLayoutManager(this)
        b.suggestionList.adapter = suggestionAdapter

        recentAdapter = RowAdapter(
            onClick = { row -> submitInput(row.key) },
            onRemove = { row ->
                xc.recents.remove(row.key)
                refreshHome()
            }
        )
        b.recentList.layoutManager = LinearLayoutManager(this)
        b.recentList.adapter = recentAdapter

        shortcutAdapter = ShortcutAdapter(
            onOpen = { openUrl(it.url) },
            onLongPress = { confirmRemoveShortcut(it) },
            onAdd = { showAddShortcutDialog(null, null) },
            onAddLongPress = { confirmResetShortcuts() }
        )
        b.shortcutList.layoutManager = GridLayoutManager(this, 4)
        b.shortcutList.adapter = shortcutAdapter

        tabsAdapter = TabsAdapter(
            onOpen = { tab ->
                tabs.select(tab.id)
                screen = Screen.CONTENT
                render()
            },
            onClose = { tab -> closeTabNow(tab) }
        )
        b.tabsList.layoutManager = GridLayoutManager(this, 2)
        b.tabsList.adapter = tabsAdapter
    }

    private fun setupSearchBar() {
        b.searchField.setOnEditorActionListener { _, actionId, event ->
            val enter = event != null && event.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN
            if (actionId == EditorInfo.IME_ACTION_GO || enter) {
                submitInput(b.searchField.text.toString())
                true
            } else {
                false
            }
        }
        b.searchField.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (programmaticText) return
                updateClearButton()
                if (b.searchField.hasFocus()) refreshSuggestions(s?.toString().orEmpty())
            }
        })
        b.searchField.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                suggestionsVisible = true
                val tab = tabs.current
                if (tab != null && !tab.isBlank && !tab.showHome) {
                    val editable = if (SearchEngine.isResultsUrl(tab.url)) {
                        SearchEngine.extractQuery(tab.url).orEmpty()
                    } else {
                        tab.url
                    }
                    setFieldText(editable)
                    b.searchField.post { b.searchField.selectAll() }
                }
                refreshSuggestions(b.searchField.text.toString())
            } else {
                suggestionsVisible = false
                updateAddressBar()
            }
            render()
        }
        b.clearButton.setOnClickListener {
            b.searchField.setText("")
            b.searchField.requestFocus()
            showKeyboard()
        }
        b.micButton.setOnClickListener { startVoiceSearch() }
        b.securityIcon.setOnClickListener { showConnectionInfo() }
        b.menuButton.setOnClickListener { showOverflowMenu() }
        b.tabCountButton.setOnClickListener {
            if (screen == Screen.TABS) {
                screen = Screen.CONTENT
                render()
            } else {
                openTabsPanel()
            }
        }
    }

    private fun setupCategoryTabs() {
        SearchCategory.values().forEach { c ->
            b.categoryTabs.addTab(b.categoryTabs.newTab().setText(c.label))
        }
        b.categoryTabs.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                if (suppressTabEvents) return
                category = SearchCategory.values()[tab.position]
                onCategoryChosen()
            }

            override fun onTabUnselected(tab: TabLayout.Tab) {}

            override fun onTabReselected(tab: TabLayout.Tab) {
                if (suppressTabEvents) return
                onCategoryChosen()
            }
        })
    }

    private fun setupHome() {
        b.clearRecentButton.setOnClickListener {
            xc.recents.clear()
            refreshHome()
        }
    }

    /** Home-screen search pill, chips and quick actions. */
    private fun setupHomeActions() {
        b.homeSearchPill.setOnClickListener { beginSearch() }
        b.homeMicButton.setOnClickListener { startVoiceSearch() }
        b.homeProviderChip.setOnClickListener { showProviderSheet { refreshHome() } }
        b.homeProtectionChip.setOnClickListener { openPanel(Screen.PRIVACY) }
        b.qaBookmarks.setOnClickListener { showBookmarks() }
        b.qaHistory.setOnClickListener { showHistory() }
        b.qaDownloads.setOnClickListener { showDownloads() }
        b.qaPrivacy.setOnClickListener { openPanel(Screen.PRIVACY) }
        b.qaSettings.setOnClickListener { openPanel(Screen.SETTINGS) }
        listOf(b.homeSearchPill, b.qaBookmarks, b.qaHistory, b.qaDownloads, b.qaPrivacy, b.qaSettings,
            b.homeProviderChip, b.homeProtectionChip, b.retryButton, b.newTabButton, b.newPrivateTabButton
        ).forEach { Motion.pressable(it) }
    }

    /** Opens the real search field (the home screen only shows a lookalike pill). */
    private fun beginSearch() {
        suggestionsVisible = true
        render()
        b.searchField.requestFocus()
        showKeyboard()
    }

    private fun setupNavigation() {
        b.bottomNav.setOnItemSelectedListener { item ->
            if (!suppressNav) onNavItem(item.itemId)
            true
        }
        b.bottomNav.setOnItemReselectedListener { item -> onNavItem(item.itemId) }

        b.backButton.setOnClickListener {
            tabs.current?.let { tab ->
                tab.error = null
                tab.webView?.goBack()
                render()
            }
        }
        b.forwardButton.setOnClickListener {
            tabs.current?.let { tab ->
                tab.error = null
                tab.webView?.goForward()
                render()
            }
        }
        b.reloadButton.setOnClickListener {
            val tab = tabs.current ?: return@setOnClickListener
            when {
                tab.loading -> tab.webView?.stopLoading()
                tab.error != null -> loadInTab(tab, tab.error!!.url)
                else -> tab.webView?.reload()
            }
        }
        b.homeButton.setOnClickListener { showHomeScreen() }
        b.shareButton.setOnClickListener { sharePage() }
        b.bookmarkButton.setOnClickListener { toggleBookmark() }
    }

    private fun setupTabsPanel() {
        b.newTabButton.setOnClickListener { openNewTab(isPrivate = false, focusSearch = true) }
        b.newPrivateTabButton.setOnClickListener {
            openNewTab(isPrivate = true, focusSearch = true)
            toast("Private tab: history and searches aren't saved.")
        }
    }

    private fun setupFindBar() {
        b.findField.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val wv = tabs.current?.webView ?: return
                val text = s?.toString().orEmpty()
                if (text.isEmpty()) {
                    wv.clearMatches()
                    b.findCount.text = ""
                } else {
                    wv.findAllAsync(text)
                }
            }
        })
        b.findField.setOnEditorActionListener { _, _, _ ->
            tabs.current?.webView?.findNext(true)
            true
        }
        b.findNext.setOnClickListener { tabs.current?.webView?.findNext(true) }
        b.findPrev.setOnClickListener { tabs.current?.webView?.findNext(false) }
        b.findClose.setOnClickListener { hideFindBar() }
    }

    private fun setupErrorView() {
        b.retryButton.setOnClickListener {
            val tab = tabs.current ?: return@setOnClickListener
            val url = tab.error?.url ?: tab.url
            if (url.isBlank()) showHomeScreen() else loadInTab(tab, url)
        }
        b.errorHomeButton.setOnClickListener {
            tabs.current?.error = null
            showHomeScreen()
        }
    }

    private fun setupBackHandling() {
        onBackPressedDispatcher.addCallback(this) { handleBack() }
    }

    private fun handleBack() {
        when {
            fullscreenView != null -> exitFullscreen()
            findVisible -> hideFindBar()
            suggestionsVisible -> {
                b.searchField.clearFocus()
                hideKeyboard()
            }
            screen == Screen.PRIVACY || screen == Screen.SETTINGS || screen == Screen.EXTENSIONS -> closePanel()
            screen != Screen.CONTENT -> {
                screen = Screen.CONTENT
                render()
            }
            else -> {
                val tab = tabs.current
                if (tab == null) {
                    finish()
                } else if (tab.showHome && !tab.isBlank) {
                    tab.showHome = false
                    render()
                } else if (!tab.isBlank && tab.webView?.canGoBack() == true) {
                    tab.error = null
                    tab.webView?.goBack()
                    render()
                } else if (tabs.count > 1 || tab.parentId != null) {
                    closeTabNow(tab)
                } else if (!tab.isBlank) {
                    showHomeScreen()
                } else {
                    finish()
                }
            }
        }
    }

    // ---------------------------------------------------------------------------------------
    // Rendering
    // ---------------------------------------------------------------------------------------

    private fun isPanel(sc: Screen) = sc == Screen.PRIVACY || sc == Screen.SETTINGS || sc == Screen.EXTENSIONS

    private fun render() {
        val tab = tabs.current
        val showHome = tab == null || tab.isBlank || tab.showHome
        val inContent = screen == Screen.CONTENT
        val hasError = tab?.error != null
        if (!isPanel(screen)) panelBack = null

        // Make sure the current tab has a WebView when its page is about to be shown.
        if (inContent && !showHome && tab != null && tab.webView == null) {
            val wv = ensureWebView(tab)
            if (tab.needsLoad) {
                tab.needsLoad = false
                wv.loadUrl(tab.url)
            }
        }
        syncWebContainer(tab)

        b.homeScroll.visibility = vis(inContent && showHome && !suggestionsVisible)
        b.webContainer.visibility = vis(inContent && !showHome && !hasError)
        b.errorView.visibility = vis(inContent && !showHome && hasError)
        b.tabsPanel.visibility = vis(screen == Screen.TABS)
        b.panelHost.visibility = vis(isPanel(screen))
        privacyCenter?.root?.visibility = vis(screen == Screen.PRIVACY)
        settingsScreen?.root?.visibility = vis(screen == Screen.SETTINGS)
        extensionsCenter?.root?.visibility = vis(screen == Screen.EXTENSIONS)
        b.suggestionList.visibility = vis(inContent && suggestionsVisible)

        val pageVisible = inContent && !showHome && !suggestionsVisible
        // The address bar is only needed over a page or while searching; the home screen has its own pill.
        b.topBar.visibility = vis(inContent && (!showHome || suggestionsVisible))
        b.navBar.visibility = vis(pageVisible)
        b.findBar.visibility = vis(findVisible && pageVisible && !hasError)
        b.pageProgress.visibility = vis(pageVisible && tab?.loading == true)
        if (tab != null) b.pageProgress.setProgressCompat(tab.progress, true)

        val resultsPage = tab != null && !showHome && SearchEngine.isResultsUrl(tab.url)
        b.categoryTabs.visibility = vis(inContent && (resultsPage || suggestionsVisible))
        b.privateBadge.visibility = vis(tab?.isPrivate == true)

        if (hasError) {
            val err = tab?.error
            b.errorTitle.text = err?.title
            b.errorMessage.text = err?.message
            b.errorIcon.setImageResource(
                when (err?.kind) {
                    ErrorKind.OFFLINE -> R.drawable.ic_wifi_off
                    ErrorKind.NOT_FOUND -> R.drawable.ic_globe
                    ErrorKind.SECURITY -> R.drawable.ic_lock_open
                    else -> R.drawable.ic_alert
                }
            )
        }
        if (inContent && showHome && !suggestionsVisible && tab != null && homeShownForTab != tab.id) {
            homeShownForTab = tab.id
            refreshHome()
            Motion.enter(listOf(b.homeHero, b.homeSearchPill, b.homeChips, b.homeActions, b.homeShortcuts, b.homeRecent))
        }
        if (!(inContent && showHome)) homeShownForTab = -1L

        // Private pages are hidden from screenshots and the recent-apps preview.
        if (tab?.isPrivate == true) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)

        val navId = when {
            screen == Screen.TABS -> R.id.nav_tabs
            screen == Screen.PRIVACY -> R.id.nav_privacy
            screen == Screen.SETTINGS || screen == Screen.EXTENSIONS -> R.id.nav_settings
            suggestionsVisible -> R.id.nav_search
            else -> R.id.nav_home
        }
        if (b.bottomNav.selectedItemId != navId) {
            suppressNav = true
            b.bottomNav.selectedItemId = navId
            suppressNav = false
        }
        updateChrome()
        updateAddressBar()
    }

    private fun vis(show: Boolean) = if (show) View.VISIBLE else View.GONE

    private fun syncWebContainer(tab: Tab?) {
        val wanted = tab?.webView
        val current = if (b.webContainer.childCount > 0) b.webContainer.getChildAt(0) else null
        if (current === wanted) return
        b.webContainer.removeAllViews()
        if (wanted != null) {
            (wanted.parent as? ViewGroup)?.removeView(wanted)
            b.webContainer.addView(wanted)
        }
    }

    /** Back/forward/reload/bookmark states, private badge and the tab-count badge. */
    private fun updateChrome() {
        val tab = tabs.current
        val wv = tab?.webView
        val onPage = tab != null && !tab.isBlank && !tab.showHome
        setEnabledLook(b.backButton, wv?.canGoBack() == true)
        setEnabledLook(b.forwardButton, wv?.canGoForward() == true)
        val loading = tab?.loading == true
        b.reloadButton.setImageResource(if (loading) R.drawable.ic_close else R.drawable.ic_refresh)
        b.reloadButton.contentDescription = getString(if (loading) R.string.close else R.string.reload)
        val bookmarked = onPage && tab != null && xc.bookmarks.isBookmarked(tab.url)
        b.bookmarkButton.setImageResource(if (bookmarked) R.drawable.ic_star else R.drawable.ic_star_border)
        b.tabCountText.text = if (tabs.count > 99) "99+" else tabs.count.toString()
        b.tabCountButton.contentDescription = getString(R.string.cd_tab_count) + ", " + tabs.count
        b.tabCountText.setTextColor(getColor(if (tab?.isPrivate == true) R.color.xc_violet else R.color.xc_on_surface))
    }

    private fun setEnabledLook(view: View, enabled: Boolean) {
        view.isEnabled = enabled
        view.alpha = if (enabled) 1f else 0.38f
    }

    private fun updateAddressBar() {
        if (b.searchField.hasFocus()) return
        val tab = tabs.current
        val text: String
        val icon: Int
        if (tab == null || tab.isBlank || tab.showHome) {
            text = ""
            icon = R.drawable.ic_search
        } else if (SearchEngine.isResultsUrl(tab.url)) {
            text = SearchEngine.extractQuery(tab.url).orEmpty()
            icon = R.drawable.ic_search
            category = SearchEngine.categoryOf(tab.url)
            lastQuery = text
            selectCategoryTab(category)
        } else {
            text = UrlUtils.displayHost(tab.url)
            icon = if (UrlUtils.isHttps(tab.url)) R.drawable.ic_lock else R.drawable.ic_lock_open
        }
        if (b.searchField.text.toString() != text) setFieldText(text)
        b.securityIcon.setImageResource(icon)
        val tint = when (icon) {
            R.drawable.ic_lock -> R.color.xc_ok
            R.drawable.ic_lock_open -> R.color.xc_warn
            else -> R.color.xc_muted
        }
        ImageViewCompat.setImageTintList(b.securityIcon, ColorStateList.valueOf(getColor(tint)))
        updateClearButton()
    }

    private fun setFieldText(text: String) {
        programmaticText = true
        b.searchField.setText(text)
        programmaticText = false
        updateClearButton()
    }

    private fun updateClearButton() {
        b.clearButton.visibility = vis(b.searchField.text.isNotEmpty() && b.searchField.hasFocus())
    }

    private fun selectCategoryTab(c: SearchCategory) {
        if (b.categoryTabs.selectedTabPosition == c.ordinal) return
        suppressTabEvents = true
        b.categoryTabs.getTabAt(c.ordinal)?.select()
        suppressTabEvents = false
    }

    private fun refreshHome() {
        shortcutAdapter.submit(xc.shortcuts.all())
        val priv = tabs.current?.isPrivate == true
        val list = if (priv) emptyList() else xc.recents.all().take(10)
        recentAdapter.submit(list.map { Row(it, null, it, R.drawable.ic_history) })
        b.recentList.visibility = vis(list.isNotEmpty())
        b.clearRecentButton.visibility = vis(list.isNotEmpty())
        b.recentEmpty.visibility = vis(list.isEmpty())
        b.recentEmpty.setText(
            if (priv) R.string.private_no_recents else R.string.no_recent_searches
        )
        b.homeProviderChip.text = prefs.searchProvider.name
        val on = prefs.trackerBlocking
        b.homeProtectionChip.text = if (on) {
            "Protection on · " + java.text.NumberFormat.getInstance().format(xc.stats.lifetimeCount()) + " blocked"
        } else {
            "Protection off"
        }
        b.homeProtectionChip.setCompoundDrawablesRelativeWithIntrinsicBounds(
            if (on) R.drawable.ic_shield_check else R.drawable.ic_shield_off, 0, 0, 0
        )
        b.homeProtectionChip.compoundDrawableTintList = ColorStateList.valueOf(getColor(if (on) R.color.xc_ok else R.color.xc_faint))
    }

    // ---------------------------------------------------------------------------------------
    // Searching and navigating
    // ---------------------------------------------------------------------------------------

    private fun ensureTab(): Tab = tabs.current ?: tabs.newTab(isPrivate = false)

    private fun openUrl(url: String) {
        loadInTab(ensureTab(), url)
    }

    /** Address-bar submit: a web address is opened, anything else is searched for. */
    private fun submitInput(raw: String) {
        val text = raw.trim()
        if (text.isEmpty()) return
        val tab = ensureTab()
        when (val target = SearchEngine.resolve(text, prefs.searchProvider, category)) {
            is SearchEngine.Target.Url -> loadInTab(tab, target.url)
            is SearchEngine.Target.Search -> runSearch(tab, target.query)
        }
    }

    private fun runSearch(tab: Tab, query: String) {
        lastQuery = query
        if (!tab.isPrivate && prefs.saveRecentSearches) xc.recents.add(query)
        loadInTab(tab, SearchEngine.buildUrl(prefs.searchProvider, category, query))
    }

    private fun onCategoryChosen() {
        val typed = if (b.searchField.hasFocus()) b.searchField.text.toString().trim() else ""
        val query = typed.ifEmpty { lastQuery }
        val tab = ensureTab()
        when {
            query.isNotBlank() -> runSearch(tab, query)
            category == SearchCategory.MAPS -> loadInTab(tab, SearchEngine.MAPS_HOME)
        }
    }

    /** Loads [rawUrl] in [tab] after applying HTTPS-only mode. Only called with validated web URLs. */
    private fun loadInTab(tab: Tab, rawUrl: String) {
        val url = if (prefs.effectiveHttpsOnly) UrlUtils.upgradeToHttps(rawUrl) else rawUrl
        tab.error = null
        tab.showHome = false
        tab.needsLoad = false
        tab.url = url
        tab.title = ""
        tab.progress = 0
        screen = Screen.CONTENT
        hideFindBar()
        val wv = ensureWebView(tab)
        if (prefs.effectiveSendGpc) wv.loadUrl(url, mapOf("Sec-GPC" to "1", "DNT" to "1")) else wv.loadUrl(url)
        if (tabs.current?.id == tab.id) {
            b.searchField.clearFocus()
            hideKeyboard()
            render()
        }
    }

    private fun showHomeScreen() {
        val tab = ensureTab()
        tab.error = null
        tab.showHome = true
        screen = Screen.CONTENT
        hideFindBar()
        b.searchField.clearFocus()
        hideKeyboard()
        render()
    }

    private fun onNavItem(id: Int) {
        when (id) {
            R.id.nav_home -> showHomeScreen()
            R.id.nav_search -> {
                screen = Screen.CONTENT
                beginSearch()
            }
            R.id.nav_tabs -> if (screen == Screen.TABS) {
                screen = Screen.CONTENT
                render()
            } else {
                openTabsPanel()
            }
            R.id.nav_privacy -> if (screen == Screen.PRIVACY) {
                screen = Screen.CONTENT
                render()
            } else {
                panelBack = null
                openPanel(Screen.PRIVACY)
            }
            R.id.nav_settings -> if (screen == Screen.SETTINGS) {
                screen = Screen.CONTENT
                render()
            } else {
                panelBack = null
                openPanel(Screen.SETTINGS)
            }
        }
    }

    private fun startVoiceSearch() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            .putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak to search")
        try {
            voiceLauncher.launch(intent)
        } catch (e: ActivityNotFoundException) {
            toast("Voice search isn't available: no speech recognition app was found on this device.")
        }
    }

    private fun refreshSuggestions(text: String) {
        val query = text.trim()
        val priv = tabs.current?.isPrivate == true
        val rows = ArrayList<Row>()
        if (!priv && prefs.saveRecentSearches) {
            xc.recents.matching(query).take(if (query.isEmpty()) 8 else 4)
                .forEach { rows.add(Row(it, null, it, R.drawable.ic_history)) }
        }
        if (query.isNotEmpty()) {
            val needle = query.lowercase()
            xc.bookmarks.all()
                .filter { it.title.lowercase().contains(needle) || it.url.lowercase().contains(needle) }
                .take(2)
                .forEach { rows.add(Row(it.title, it.url, it.url, R.drawable.ic_star)) }
            if (!priv && prefs.saveHistory) {
                xc.history.search(query).take(2)
                    .forEach { rows.add(Row(it.title, it.url, it.url, R.drawable.ic_history)) }
            }
        }
        val local = rows.distinctBy { it.key.lowercase() }
        suggestionAdapter.submit(local)

        suggestJob?.cancel()
        if (!prefs.searchSuggestions || query.isEmpty() || UrlUtils.looksLikeUrl(query)) return
        val provider = prefs.searchProvider
        suggestJob = lifecycleScope.launch {
            delay(200)
            val fetched = withContext(Dispatchers.IO) { SuggestionClient.fetch(provider, query) }
            if (b.searchField.text.toString().trim() != query) return@launch
            val merged = (local + fetched.map { Row(it, null, it, R.drawable.ic_search) })
                .distinctBy { it.key.lowercase() }
            suggestionAdapter.submit(merged)
        }
    }

    // ---------------------------------------------------------------------------------------
    // Tabs and WebViews
    // ---------------------------------------------------------------------------------------

    private fun openNewTab(
        isPrivate: Boolean,
        url: String? = null,
        focusSearch: Boolean = false
    ): Tab {
        val parent = if (url != null) tabs.current?.id else null
        val tab = tabs.newTab(isPrivate, parent, select = true)
        screen = Screen.CONTENT
        if (url != null) {
            loadInTab(tab, url)
        } else {
            b.searchField.clearFocus()
            render()
            if (focusSearch) beginSearch()
        }
        return tab
    }

    private fun openTabsPanel() {
        tabs.current?.let { captureThumbnail(it) }
        b.searchField.clearFocus()
        hideKeyboard()
        refreshTabsPanel()
        screen = Screen.TABS
        render()
    }

    private fun refreshTabsPanel() {
        tabsAdapter.submit(tabs.tabs, tabs.currentId)
        b.tabsTitle.text = getString(R.string.tabs) + " (" + tabs.count + ")"
    }

    /** Closes a tab from the UI. Always leaves at least one tab open. */
    private fun closeTabNow(tab: Tab) {
        val removed = tabs.close(tab.id) ?: return
        destroyWebView(removed)
        if (tabs.count == 0) tabs.newTab(isPrivate = false)
        if (removed.isPrivate && !tabs.hasPrivateTabs()) scheduleEndPrivateSession()
        if (screen == Screen.TABS) refreshTabsPanel()
        if (tabs.current?.let { it.isBlank || it.showHome } == true) homeShownForTab = -1L
        render()
    }

    private fun scheduleEndPrivateSession() {
        endPrivateRunnable?.let { handler.removeCallbacks(it) }
        val runnable = Runnable {
            if (!tabs.hasPrivateTabs()) xc.privacy.endPrivateSession()
        }
        endPrivateRunnable = runnable
        handler.postDelayed(runnable, 700)
    }

    private fun destroyWebView(tab: Tab) {
        val wv = tab.webView ?: return
        tab.webView = null
        tab.thumbnail = null
        try {
            (wv.parent as? ViewGroup)?.removeView(wv)
            wv.stopLoading()
            wv.setDownloadListener(null)
            wv.destroy()
        } catch (e: Exception) {
            // Already torn down; nothing else to do.
        }
    }

    private fun ensureWebView(tab: Tab): WebView {
        tab.webView?.let { return it }
        val wv = createWebView(tab)
        tab.webView = wv
        return wv
    }

    private fun createWebView(tab: Tab): WebView {
        val wv = WebView(this)
        // New tabs follow the "request desktop sites by default" preference.
        if (tab.isBlank && prefs.desktopModeDefault) tab.desktop = true
        // Private tabs get their own isolated WebView profile where the device supports it.
        xc.privacy.assignProfile(wv, tab.isPrivate)
        wv.layoutParams = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT
        )
        val s = wv.settings
        s.javaScriptEnabled = true
        s.domStorageEnabled = true
        s.allowFileAccess = false
        s.allowContentAccess = false
        s.setSupportMultipleWindows(true)
        s.javaScriptCanOpenWindowsAutomatically = !prefs.blockPopups
        s.setSupportZoom(true)
        s.builtInZoomControls = true
        s.displayZoomControls = false
        s.mediaPlaybackRequiresUserGesture = true
        s.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        s.useWideViewPort = true
        if (tab.isPrivate && !xc.privacy.privateProfilesSupported) s.cacheMode = WebSettings.LOAD_NO_CACHE
        if (WebViewFeature.isFeatureSupported(WebViewFeature.SAFE_BROWSING_ENABLE)) {
            WebSettingsCompat.setSafeBrowsingEnabled(s, true)
        }
        applyPageSettingsTo(wv)
        applyDesktopMode(tab, wv)
        xc.privacy.applyCookiePolicy(wv)

        wv.webViewClient = XcWebViewClient(tab, this, prefs, xc.trackers.blocker, xc.stats)
        wv.webChromeClient = XcChromeClient(tab, this, prefs)
        wv.setDownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
            onDownloadRequested(tab, url, userAgent, contentDisposition, mimeType)
        }
        wv.setOnLongClickListener { onWebLongPress(tab, wv) }
        wv.setFindListener { active, total, _ ->
            b.findCount.text = if (total == 0) "0/0" else "${active + 1}/$total"
        }
        return wv
    }

    private fun applyDesktopMode(tab: Tab, wv: WebView) {
        val s = wv.settings
        s.userAgentString = if (tab.desktop) UserAgents.desktop(defaultUserAgent) else UserAgents.mobile(defaultUserAgent)
        s.loadWithOverviewMode = tab.desktop
    }

    private fun isNightMode(): Boolean =
        (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

    /** Small in-memory preview for the tab switcher. Never taken for private tabs. */
    private fun captureThumbnail(tab: Tab) {
        if (tab.isPrivate) return
        val wv = tab.webView ?: return
        if (!wv.isShown || wv.width <= 0 || wv.height <= 0) return
        try {
            val scale = 0.3f
            val bmp = Bitmap.createBitmap(
                (wv.width * scale).toInt().coerceAtLeast(1),
                (wv.height * scale).toInt().coerceAtLeast(1),
                Bitmap.Config.RGB_565
            )
            val canvas = Canvas(bmp)
            canvas.scale(scale, scale)
            wv.draw(canvas)
            tab.thumbnail = bmp
        } catch (t: Throwable) {
            // Out of memory or a draw failure: the card simply has no preview.
        }
    }

    // ---------------------------------------------------------------------------------------
    // BrowserHost
    // ---------------------------------------------------------------------------------------

    private fun isCurrent(tab: Tab) = tabs.current?.id == tab.id

    override fun isOnline(): Boolean {
        val cm = getSystemService(ConnectivityManager::class.java) ?: return true
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    override fun onPageStarted(tab: Tab, url: String) {
        if (isCurrent(tab)) render()
    }

    override fun onPageCommitVisible(tab: Tab, url: String) = injectAddons(tab, url)

    /** Runs the enabled script add-ons that apply to this page. Each add-on guards itself against running twice. */
    private fun injectAddons(tab: Tab, url: String) {
        if (tab.error != null) return
        val script = xc.addons.scriptFor(url) ?: return
        tab.webView?.evaluateJavascript(script, null)
    }

    override fun onPageFinished(tab: Tab, url: String) {
        injectAddons(tab, url)
        // Private tabs never touch history.
        if (!tab.isPrivate && prefs.saveHistory && tab.error == null &&
            UrlUtils.classifyScheme(url) == UrlUtils.Kind.WEB_URL
        ) {
            xc.history.record(tab.title, url)
        }
        if (isCurrent(tab)) render()
    }

    override fun onUrlChanged(tab: Tab, url: String) {
        if (isCurrent(tab)) {
            updateChrome()
            updateAddressBar()
        }
    }

    override fun onProgress(tab: Tab, progress: Int) {
        if (isCurrent(tab) && screen == Screen.CONTENT) {
            b.pageProgress.visibility = vis(tab.loading && !tab.showHome && !suggestionsVisible)
            b.pageProgress.setProgressCompat(progress, true)
        }
    }

    override fun onTitle(tab: Tab, title: String) {
        if (!tab.isPrivate && prefs.saveHistory) xc.history.updateTitle(tab.url, title)
    }

    override fun onFavicon(tab: Tab) {
        // Kept in memory only, and only for the tab switcher.
        if (screen == Screen.TABS) refreshTabsPanel()
    }

    override fun onError(tab: Tab, error: PageError) {
        if (isCurrent(tab)) render()
    }

    override fun onRenderGone(tab: Tab) {
        handler.post {
            destroyWebView(tab)
            tab.needsLoad = true
            tab.loading = false
            tab.error = PageError(
                "This tab stopped working",
                "Android freed up memory used by this page. Try again to reload it.",
                tab.url,
                ErrorKind.OTHER
            )
            if (isCurrent(tab)) render()
        }
    }

    override fun openExternal(url: String) {
        if (externalDialog?.isShowing == true) return
        val label = UrlUtils.schemeOf(url) ?: "another app"
        externalDialog = MaterialAlertDialogBuilder(this)
            .setTitle("Open in another app?")
            .setMessage("This page wants to open a $label link:\n\n${url.take(200)}")
            .setPositiveButton("Open") { _, _ ->
                val action = if (UrlUtils.schemeOf(url) == "tel") Intent.ACTION_DIAL else Intent.ACTION_VIEW
                try {
                    startActivity(Intent(action, Uri.parse(url)))
                } catch (e: ActivityNotFoundException) {
                    toast("No app on this device can open that link.")
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    override fun openPopup(parent: Tab): Tab {
        val popup = tabs.newTab(parent.isPrivate, parent.id, select = true)
        popup.url = "about:blank"
        ensureWebView(popup)
        screen = Screen.CONTENT
        render()
        return popup
    }

    override fun closeTab(tab: Tab) {
        // Called from inside a WebView callback, so destroy it on the next loop.
        handler.post { if (tabs.find(tab.id) != null) closeTabNow(tab) }
    }

    override fun requestSitePermission(
        tab: Tab,
        origin: String,
        needed: List<SitePermission>,
        result: (Boolean) -> Unit
    ) {
        val host = Uri.parse(origin).host ?: origin
        val remember = !tab.isPrivate
        if (remember) {
            val stored = needed.map { xc.sitePermissions.get(host, it) }
            if (stored.any { it == false }) {
                result(false)
                return
            }
            if (stored.all { it == true }) {
                ensureRuntimePermissions(needed, result)
                return
            }
        }
        val what = needed.joinToString(" and ") { it.label.lowercase() }
        var answered = false
        fun answer(allow: Boolean) {
            if (answered) return
            answered = true
            if (remember) needed.forEach { xc.sitePermissions.set(host, it, allow) }
            if (allow) ensureRuntimePermissions(needed, result) else result(false)
        }
        MaterialAlertDialogBuilder(this)
            .setTitle("Allow $host to use your $what?")
            .setMessage(
                if (remember) "Your choice is remembered for this site. You can change it later under Website permissions."
                else "Private tab: your choice is not remembered."
            )
            .setPositiveButton("Allow") { _, _ -> answer(true) }
            .setNegativeButton("Block") { _, _ -> answer(false) }
            .setOnCancelListener { answer(false) }
            .show()
    }

    /** Asks Android for the matching runtime permissions, only now that a site actually needs them. */
    private fun ensureRuntimePermissions(needed: List<SitePermission>, result: (Boolean) -> Unit) {
        fun has(p: String) = ContextCompat.checkSelfPermission(this, p) == PackageManager.PERMISSION_GRANTED
        fun granted(p: SitePermission) = when (p) {
            SitePermission.CAMERA -> has(Manifest.permission.CAMERA)
            SitePermission.MICROPHONE -> has(Manifest.permission.RECORD_AUDIO)
            SitePermission.LOCATION ->
                has(Manifest.permission.ACCESS_FINE_LOCATION) || has(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
        if (needed.all { granted(it) }) {
            result(true)
            return
        }
        val request = ArrayList<String>()
        for (p in needed.filterNot { granted(it) }) {
            when (p) {
                SitePermission.CAMERA -> request.add(Manifest.permission.CAMERA)
                SitePermission.MICROPHONE -> request.add(Manifest.permission.RECORD_AUDIO)
                SitePermission.LOCATION -> {
                    request.add(Manifest.permission.ACCESS_FINE_LOCATION)
                    request.add(Manifest.permission.ACCESS_COARSE_LOCATION)
                }
            }
        }
        pendingPermissionCallback = { result(needed.all { granted(it) }) }
        permissionLauncher.launch(request.toTypedArray())
    }

    override fun showCustomView(view: View, callback: WebChromeClient.CustomViewCallback) {
        if (fullscreenView != null) {
            callback.onCustomViewHidden()
            return
        }
        fullscreenView = view
        fullscreenCallback = callback
        b.fullscreenContainer.addView(
            view,
            FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        )
        b.fullscreenContainer.visibility = View.VISIBLE
        b.mainColumn.visibility = View.GONE
        val controller = WindowCompat.getInsetsController(window, b.rootLayout)
        controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
        ViewCompat.requestApplyInsets(b.rootLayout)
    }

    override fun hideCustomView() = exitFullscreen()

    private fun exitFullscreen() {
        val view = fullscreenView ?: return
        fullscreenView = null
        b.fullscreenContainer.removeView(view)
        b.fullscreenContainer.visibility = View.GONE
        b.mainColumn.visibility = View.VISIBLE
        WindowCompat.getInsetsController(window, b.rootLayout).show(WindowInsetsCompat.Type.systemBars())
        ViewCompat.requestApplyInsets(b.rootLayout)
        val callback = fullscreenCallback
        fullscreenCallback = null
        callback?.onCustomViewHidden()
    }

    override fun chooseFile(
        params: WebChromeClient.FileChooserParams,
        callback: ValueCallback<Array<Uri>>
    ): Boolean {
        fileCallback?.onReceiveValue(null)
        fileCallback = callback
        return try {
            fileLauncher.launch(params.createIntent())
            true
        } catch (e: ActivityNotFoundException) {
            fileCallback = null
            callback.onReceiveValue(null)
            false
        }
    }

    // ---------------------------------------------------------------------------------------
    // Long-press menus, downloads, sharing
    // ---------------------------------------------------------------------------------------

    private fun onWebLongPress(tab: Tab, wv: WebView): Boolean {
        val hit = wv.hitTestResult
        val extra = hit.extra
        when (hit.type) {
            WebView.HitTestResult.SRC_ANCHOR_TYPE -> {
                if (extra != null) showLinkMenu(tab, extra)
            }
            WebView.HitTestResult.IMAGE_TYPE -> {
                if (extra != null) showImageMenu(tab, extra, null)
            }
            WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE -> {
                // The image address is in `extra`; ask WebView for the link around it.
                val message = Handler(Looper.getMainLooper()) { msg ->
                    val link = msg.data.getString("url")
                    if (extra != null) showImageMenu(tab, extra, link)
                    true
                }.obtainMessage()
                wv.requestFocusNodeHref(message)
            }
            else -> return false
        }
        return true
    }

    private fun showLinkMenu(tab: Tab, url: String) {
        if (UrlUtils.classifyScheme(url) != UrlUtils.Kind.WEB_URL) return
        MaterialAlertDialogBuilder(this)
            .setTitle(UrlUtils.displayHost(url))
            .setItems(arrayOf("Open in new tab", "Open in new private tab", "Copy link address", "Share link")) { _, which ->
                when (which) {
                    0 -> openNewTab(tab.isPrivate, url)
                    1 -> openNewTab(true, url)
                    2 -> copyText(url)
                    3 -> shareText(url, null)
                }
            }
            .show()
    }

    private fun showImageMenu(tab: Tab, imageUrl: String, linkUrl: String?) {
        val items = ArrayList<String>()
        val actions = ArrayList<() -> Unit>()
        if (linkUrl != null && UrlUtils.classifyScheme(linkUrl) == UrlUtils.Kind.WEB_URL) {
            items.add("Open source page in new tab")
            actions.add { openNewTab(tab.isPrivate, linkUrl) }
        }
        if (UrlUtils.classifyScheme(imageUrl) == UrlUtils.Kind.WEB_URL) {
            items.add("Open image in new tab"); actions.add { openNewTab(tab.isPrivate, imageUrl) }
            items.add("Download image")
            actions.add { onDownloadRequested(tab, imageUrl, null, null, null) }
            items.add("Share image link"); actions.add { shareText(imageUrl, null) }
            items.add("Copy image address"); actions.add { copyText(imageUrl) }
        }
        if (items.isEmpty()) {
            toast("This image can't be saved or shared (it's embedded in the page).")
            return
        }
        MaterialAlertDialogBuilder(this)
            .setTitle("Image")
            .setItems(items.toTypedArray()) { _, which -> actions[which]() }
            .show()
    }

    private fun onDownloadRequested(
        tab: Tab,
        url: String,
        userAgent: String?,
        contentDisposition: String?,
        mimeType: String?
    ) {
        if (!DownloadPolicy.isAllowedUrl(url)) {
            toast("This file can't be downloaded: only normal web (http/https) links are supported.")
            return
        }
        val name = downloader.guessName(url, contentDisposition, mimeType)
        val risky = DownloadPolicy.isRisky(name, mimeType)
        if (!risky && !prefs.confirmDownloads) {
            startDownload(tab, url, name, mimeType, userAgent)
            return
        }
        val message = StringBuilder()
            .append(name).append("\nFrom ").append(UrlUtils.displayHost(url))
        if (risky) {
            message.append("\n\nWarning: this type of file can install or run software. Only download it if you trust the website.")
        }
        message.append("\n\nIt will be saved to your Downloads folder")
        if (tab.isPrivate) message.append(" and stays there after the private session ends")
        message.append('.')
        MaterialAlertDialogBuilder(this)
            .setTitle("Download file?")
            .setMessage(message.toString())
            .setPositiveButton("Download") { _, _ -> startDownload(tab, url, name, mimeType, userAgent) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun startDownload(tab: Tab, url: String, name: String, mimeType: String?, userAgent: String?) {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            // Android 9 and older need storage permission; ask only now, when a download starts.
            pendingPermissionCallback = {
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
                    PackageManager.PERMISSION_GRANTED
                ) startDownload(tab, url, name, mimeType, userAgent)
                else toast("Storage permission is needed to save downloads.")
            }
            permissionLauncher.launch(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE))
            return
        }
        try {
            // Cookies are only sent for normal tabs; private tabs live in a separate profile.
            val cookie = if (tab.isPrivate) null else CookieManager.getInstance().getCookie(url)
            val id = downloader.enqueue(url, name, mimeType, userAgent, cookie)
            if (!tab.isPrivate) xc.downloads.add(id, name, url)
            toast("Downloading $name…")
        } catch (e: Exception) {
            toast("Couldn't start the download.")
        }
    }

    private fun openDownloadedFile(id: Long) {
        val name = downloader.fileName(id).orEmpty()
        if (DownloadPolicy.isRisky(name, null)) {
            MaterialAlertDialogBuilder(this)
                .setTitle("Installer or program file")
                .setMessage("For your safety XCSABER won't open this directly. Find it in your Downloads app if you really trust it.")
                .setPositiveButton(android.R.string.ok, null)
                .show()
            return
        }
        val intent = downloader.viewIntent(id)
        if (intent == null) {
            toast("That file isn't available (still downloading, failed, or deleted).")
            return
        }
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            toast("No app can open this kind of file.")
        }
    }

    private fun sharePage() {
        val tab = tabs.current ?: return
        if (tab.isBlank || tab.showHome || UrlUtils.classifyScheme(tab.url) != UrlUtils.Kind.WEB_URL) return
        shareText(tab.url, tab.displayTitle())
    }

    private fun shareText(text: String, subject: String?) {
        val intent = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
        if (subject != null) intent.putExtra(Intent.EXTRA_SUBJECT, subject)
        startActivity(Intent.createChooser(intent, "Share"))
    }

    private fun copyText(text: String) {
        val cm = getSystemService(ClipboardManager::class.java)
        cm?.setPrimaryClip(ClipData.newPlainText("XCSABER", text))
        toast("Copied")
    }

    private fun toggleBookmark() {
        val tab = tabs.current ?: return
        if (tab.isBlank || tab.showHome) return
        val added = xc.bookmarks.toggle(tab.title, tab.url)
        toast(if (added) "Bookmark added" else "Bookmark removed")
        updateChrome()
    }

    // ---------------------------------------------------------------------------------------
    // Overflow menu and dialogs
    // ---------------------------------------------------------------------------------------

    private fun showOverflowMenu() {
        // The sheet is generated from browser_menu.xml, so the menu resource stays the single source.
        val menu = android.widget.PopupMenu(this, b.menuButton).menu
        menuInflater.inflate(R.menu.browser_menu, menu)
        val tab = tabs.current
        val onPage = tab != null && !tab.isBlank && !tab.showHome
        val placeQuery = currentPlaceQuery()
        menu.findItem(R.id.menu_desktop).apply {
            isChecked = tab?.desktop == true
            isEnabled = onPage
        }
        menu.findItem(R.id.menu_find).isEnabled = onPage
        menu.findItem(R.id.menu_share).isEnabled = onPage
        menu.findItem(R.id.menu_add_shortcut).isEnabled = onPage
        menu.findItem(R.id.menu_directions).isEnabled = placeQuery.isNotBlank()
        menu.findItem(R.id.menu_open_maps).isEnabled = placeQuery.isNotBlank()
        menu.findItem(R.id.menu_close_tab).isEnabled = tab != null
        Sheets.menu(this, Sheets.entriesOf(menu)) { id -> onMenuItem(id) }
    }

    private fun currentPlaceQuery(): String {
        val tab = tabs.current
        val fromUrl = if (tab != null && !tab.isBlank) SearchEngine.extractQuery(tab.url) else null
        return (fromUrl ?: lastQuery).trim()
    }

    private fun onMenuItem(id: Int) {
        val tab = tabs.current
        when (id) {
            R.id.menu_new_tab -> openNewTab(isPrivate = false, focusSearch = true)
            R.id.menu_new_private_tab -> {
                openNewTab(isPrivate = true, focusSearch = true)
                toast("Private tab: history and searches aren't saved.")
            }
            R.id.menu_find -> showFindBar()
            R.id.menu_desktop -> if (tab != null) {
                tab.desktop = !tab.desktop
                tab.webView?.let {
                    applyDesktopMode(tab, it)
                    it.reload()
                }
                toast(if (tab.desktop) "Desktop site on" else "Desktop site off")
            }
            R.id.menu_share -> sharePage()
            R.id.menu_add_shortcut -> if (tab != null && !tab.isBlank) {
                showAddShortcutDialog(tab.displayTitle().take(24), tab.url)
            }
            R.id.menu_directions -> {
                val q = currentPlaceQuery()
                if (q.isNotBlank()) loadInTab(ensureTab(), SearchEngine.directionsUrl(q))
            }
            R.id.menu_open_maps -> {
                val q = currentPlaceQuery()
                if (q.isNotBlank()) {
                    try {
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(SearchEngine.geoUri(q))))
                    } catch (e: ActivityNotFoundException) {
                        toast("No maps app is installed. Use the Maps tab to see the map here.")
                    }
                }
            }
            R.id.menu_bookmarks -> showBookmarks()
            R.id.menu_history -> showHistory()
            R.id.menu_downloads -> showDownloads()
            R.id.menu_close_tab -> if (tab != null) closeTabNow(tab)
            R.id.menu_ad_blocker -> openPanel(Screen.PRIVACY)
            R.id.menu_extensions -> openPanel(Screen.EXTENSIONS)
            R.id.menu_settings -> openPanel(Screen.SETTINGS)
        }
    }

    private fun showFindBar() {
        findVisible = true
        render()
        b.findField.setText("")
        b.findField.requestFocus()
        showKeyboard(b.findField)
    }

    private fun hideFindBar() {
        if (!findVisible) return
        findVisible = false
        tabs.current?.webView?.clearMatches()
        b.findCount.text = ""
        hideKeyboard()
        render()
    }

    private fun showConnectionInfo() {
        val tab = tabs.current
        val blocked = tab?.blockedCount?.get() ?: 0
        val blockedLine = if (tab != null && !tab.isBlank && !tab.showHome) {
            "\n\n" + (if (prefs.trackerBlocking) "$blocked ad or tracker requests blocked on this page." else "Ad and tracker blocking is off.")
        } else ""
        val message = when {
            tab == null || tab.isBlank || tab.showHome -> "XCSABER home. Nothing is loaded."
            UrlUtils.isHttps(tab.url) ->
                "${UrlUtils.displayHost(tab.url)}\n\nThis page was loaded over HTTPS, so the connection to the site is encrypted and its certificate was accepted. That does not tell you the site itself is trustworthy."
            else ->
                "${UrlUtils.displayHost(tab.url)}\n\nThis page was loaded over plain HTTP. Anything you send to it can be read by others on the network. Don't enter passwords or personal details."
        }
        MaterialAlertDialogBuilder(this)
            .setTitle("Connection")
            .setMessage(message + blockedLine)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun showAddShortcutDialog(presetName: String?, presetUrl: String?) {
        val view = layoutInflater.inflate(R.layout.dialog_add_shortcut, null)
        val nameField = view.findViewById<EditText>(R.id.shortcutNameField)
        val urlField = view.findViewById<EditText>(R.id.shortcutUrlField)
        presetName?.let { nameField.setText(it) }
        presetUrl?.let { urlField.setText(it) }
        MaterialAlertDialogBuilder(this)
            .setTitle("Add shortcut")
            .setView(view)
            .setPositiveButton("Add") { _, _ ->
                val url = UrlUtils.toNavigableUrl(urlField.text.toString())
                if (url == null) {
                    toast("Enter a valid web address, like example.com")
                } else {
                    val name = nameField.text.toString().trim().ifEmpty { UrlUtils.displayHost(url) }
                    xc.shortcuts.add(name, url)
                    refreshHome()
                    toast("Shortcut added")
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun confirmRemoveShortcut(shortcut: Shortcut) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Remove \"${shortcut.title}\"?")
            .setPositiveButton("Remove") { _, _ ->
                xc.shortcuts.remove(shortcut.url)
                refreshHome()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun confirmResetShortcuts() {
        MaterialAlertDialogBuilder(this)
            .setTitle("Reset shortcuts?")
            .setMessage("This restores the default shortcuts and removes the ones you added.")
            .setPositiveButton("Reset") { _, _ ->
                xc.shortcuts.resetToDefaults()
                refreshHome()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    override fun showBookmarks() {
        ListDialog.show(
            activity = this,
            title = getString(R.string.bookmarks),
            emptyText = "No bookmarks yet. Tap the star while viewing a page.",
            supplier = { xc.bookmarks.all().map { Row(it.title, it.url, it.url, R.drawable.ic_star) } },
            onClick = { row, dialog ->
                dialog.dismiss()
                openUrl(row.key)
            },
            onRemove = { xc.bookmarks.remove(it.key) },
            onClearAll = { xc.bookmarks.clear() },
            filterable = true
        )
    }

    override fun showHistory() {
        ListDialog.show(
            activity = this,
            title = getString(R.string.history),
            emptyText = "No history yet. Pages from private tabs are never saved here.",
            supplier = { xc.history.all().map { Row(it.title, it.url, it.url, R.drawable.ic_history) } },
            onClick = { row, dialog ->
                dialog.dismiss()
                openUrl(row.key)
            },
            onRemove = { xc.history.remove(it.key) },
            onClearAll = { xc.history.clear() },
            filterable = true
        )
    }

    override fun showDownloads() {
        ListDialog.show(
            activity = this,
            title = getString(R.string.downloads),
            emptyText = "No downloads yet.",
            supplier = {
                xc.downloads.all().map {
                    Row(
                        it.name,
                        downloader.status(it.id).text + " · " + UrlUtils.displayHost(it.url),
                        it.id.toString(),
                        R.drawable.ic_download
                    )
                }
            },
            onClick = { row, _ -> row.key.toLongOrNull()?.let { openDownloadedFile(it) } },
            // Removing only forgets the entry; the file itself stays in your Downloads folder.
            onRemove = { row -> row.key.toLongOrNull()?.let { xc.downloads.remove(it) } },
            onClearAll = { xc.downloads.clear() }
        )
    }

    override fun showSitePermissions() {
        ListDialog.show(
            activity = this,
            title = getString(R.string.site_permissions),
            emptyText = "No saved choices. Websites ask you before using the camera, microphone or location.",
            supplier = {
                xc.sitePermissions.all().map {
                    Row(
                        it.host,
                        it.permission.label + ": " + if (it.allowed) "Allowed" else "Blocked",
                        it.host + "|" + it.permission.key,
                        R.drawable.ic_shield
                    )
                }
            },
            onClick = { _, _ -> },
            onRemove = { row ->
                val host = row.key.substringBefore('|')
                SitePermission.fromKey(row.key.substringAfter('|'))?.let { xc.sitePermissions.clear(host, it) }
            },
            onClearAll = { xc.sitePermissions.clearAll() }
        )
    }

    // ---------------------------------------------------------------------------------------
    // Panels: Privacy Center, Extensions & Plugins, Settings
    // ---------------------------------------------------------------------------------------

    private fun ensurePanel(target: Screen) {
        when (target) {
            Screen.PRIVACY -> if (privacyCenter == null) privacyCenter = PrivacyCenter(this, xc, this, b.panelHost)
            Screen.SETTINGS -> if (settingsScreen == null) settingsScreen = SettingsScreen(this, xc, this, b.panelHost)
            Screen.EXTENSIONS -> if (extensionsCenter == null) extensionsCenter = ExtensionsCenter(this, xc, this, b.panelHost)
            else -> {}
        }
    }

    private fun refreshPanel(target: Screen) {
        when (target) {
            Screen.PRIVACY -> privacyCenter?.refresh()
            Screen.SETTINGS -> settingsScreen?.refresh()
            Screen.EXTENSIONS -> extensionsCenter?.refresh()
            else -> {}
        }
    }

    private fun openPanel(target: Screen) {
        if (screen == target) return
        b.searchField.clearFocus()
        hideKeyboard()
        suggestionsVisible = false
        // Remember the panel we came from so Back returns to it.
        panelBack = if (isPanel(screen)) screen else null
        ensurePanel(target)
        refreshPanel(target)
        screen = target
        render()
    }

    override fun closePanel() {
        val back = panelBack
        panelBack = null
        screen = back ?: Screen.CONTENT
        if (back != null) refreshPanel(back)
        render()
    }

    override fun openSettingsPanel() = openPanel(Screen.SETTINGS)

    override fun openExtensionsPanel() = openPanel(Screen.EXTENSIONS)

    override fun blockedOnCurrentPage(): Int = tabs.current?.blockedCount?.get() ?: 0

    override fun hasPrivateTabs(): Boolean = tabs.hasPrivateTabs()

    // ---- PluginHost ----

    override fun openPrivacyCenter() = openPanel(Screen.PRIVACY)

    override fun updateBlocklist() {
        ensurePanel(Screen.PRIVACY)
        privacyCenter?.updateList()
        if (screen != Screen.PRIVACY) toast("Updating the blocklist…")
    }

    override fun reloadCurrentPage() {
        tabs.current?.webView?.reload()
    }

    override fun message(text: String) = toast(text)

    override fun pluginsChanged() {
        extensionsCenter?.refresh()
        privacyCenter?.refreshStats()
        if (homeShownForTab != -1L) refreshHome()
    }

    /** Re-applies every setting that lives on the WebView to all open tabs. */
    override fun applyPageSettings() {
        tabs.tabs.forEach { t ->
            t.webView?.let {
                applyPageSettingsTo(it)
                xc.privacy.applyCookiePolicy(it)
                it.settings.javaScriptCanOpenWindowsAutomatically = !prefs.blockPopups
            }
        }
    }

    private fun applyPageSettingsTo(wv: WebView) {
        val s = wv.settings
        val tools = prefs.pageToolsEnabled
        s.textZoom = if (tools) prefs.pageTextZoom else 100
        s.blockNetworkImage = tools && prefs.blockImages
        if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
            // Only meaningful while the app theme is dark; WebView ignores it otherwise.
            WebSettingsCompat.setAlgorithmicDarkeningAllowed(s, isNightMode() && (!tools || prefs.pageDarkening))
        }
    }

    override fun printCurrentPage() {
        val tab = tabs.current
        val wv = tab?.webView
        if (tab == null || tab.isBlank || tab.showHome || wv == null) {
            toast("Open a page first")
            return
        }
        val name = (tab.displayTitle().ifBlank { "XCSABER page" }).take(40)
        try {
            val pm = getSystemService(Context.PRINT_SERVICE) as PrintManager
            pm.print(name, wv.createPrintDocumentAdapter(name), PrintAttributes.Builder().build())
        } catch (e: Exception) {
            toast("Couldn't start printing")
        }
    }

    override fun viewSourceOfCurrentPage() {
        val tab = tabs.current
        val wv = tab?.webView
        if (tab == null || tab.isBlank || tab.showHome || wv == null) {
            toast("Open a page first")
            return
        }
        wv.evaluateJavascript("(function(){return document.documentElement.outerHTML;})()") { raw ->
            val html = try {
                JSONTokener(raw).nextValue() as? String ?: ""
            } catch (e: Exception) {
                ""
            }
            showTextDialog("Page source", if (html.length > MAX_SOURCE_CHARS) html.take(MAX_SOURCE_CHARS) + "\n… (truncated)" else html, mono = true)
        }
    }

    override fun showPageInfo() {
        val tab = tabs.current
        if (tab == null || tab.isBlank || tab.showHome) {
            toast("Open a page first")
            return
        }
        val text = StringBuilder()
            .append("URL\n").append(tab.url)
            .append("\n\nTitle\n").append(tab.title.ifBlank { "(none)" })
            .append("\n\nTab\n").append(if (tab.isPrivate) "Private" else "Normal").append(if (tab.desktop) ", desktop site" else ", mobile site")
            .append("\n\nBlocked on this page\n").append(tab.blockedCount.get())
            .append("\n\nUser agent\n").append(tab.webView?.settings?.userAgentString ?: "")
        showTextDialog("Page info", text.toString(), mono = false)
    }

    private fun showTextDialog(title: String, text: String, mono: Boolean) {
        val tv = TextView(this)
        tv.text = text
        tv.setTextIsSelectable(true)
        tv.textSize = if (mono) 11f else 13f
        if (mono) tv.typeface = android.graphics.Typeface.MONOSPACE
        tv.setTextColor(getColor(R.color.xc_on_surface))
        val pad = (20 * resources.displayMetrics.density).toInt()
        tv.setPadding(pad, pad / 2, pad, pad / 2)
        val scroll = android.widget.ScrollView(this)
        scroll.addView(tv)
        MaterialAlertDialogBuilder(this)
            .setTitle(title)
            .setView(scroll)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    override fun clearHttpCache() {
        val existing = tabs.tabs.firstNotNullOfOrNull { it.webView }
        val temp = if (existing == null) WebView(this) else null
        (existing ?: temp)?.clearCache(true)
        temp?.destroy()
        toast("HTTP cache cleared")
    }

    override fun applyDeveloperSettings() {
        // Remote debugging can only be on in debug builds, and only when the user turned it on.
        val on = BuildConfig.DEBUG && prefs.devToolsEnabled && prefs.devRemoteDebugging
        try {
            WebView.setWebContentsDebuggingEnabled(on)
        } catch (e: Exception) {
            // Not supported on this WebView; nothing to do.
        }
    }

    // ---- Choosers ----

    override fun showProviderSheet(onDone: () -> Unit) {
        val providers = SearchProvider.ALL
        Sheets.choose(
            activity = this,
            title = "Search provider",
            labels = providers.map { it.name },
            selected = providers.indexOfFirst { it.id == prefs.searchProviderId },
            note = "Maps use OpenStreetMap and Shopping uses Bing Shopping, whichever provider you pick."
        ) { which ->
            prefs.searchProviderId = providers[which].id
            onDone()
            toast("Search provider: " + providers[which].name)
        }
    }

    private fun themeLabel(theme: String) = when (theme) {
        Prefs.THEME_LIGHT -> "Light"
        Prefs.THEME_DARK -> "Dark"
        else -> "System default"
    }

    override fun showThemeSheet() {
        val values = listOf(Prefs.THEME_SYSTEM, Prefs.THEME_LIGHT, Prefs.THEME_DARK)
        Sheets.choose(
            activity = this,
            title = "Theme",
            labels = values.map { themeLabel(it) },
            selected = values.indexOf(prefs.theme).coerceAtLeast(0),
            note = if (tabs.hasPrivateTabs()) "Changing the theme restarts the screen and closes private tabs." else null
        ) { which ->
            if (values[which] != prefs.theme) {
                prefs.theme = values[which]
                // Switching theme restarts the screen: normal tabs are restored, private tabs close.
                saveSession()
                XcApp.applyTheme(values[which])
            }
        }
    }

    override fun showClearDataDialog() {
        val labels = arrayOf(
            "Cookies", "Cache", "Site data (local storage)", "Browsing history",
            "Recent searches", "Saved logins and form data", "Website permissions", "Download list"
        )
        val checked = booleanArrayOf(true, true, true, true, true, true, false, false)
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.clear_browsing_data)
            .setMultiChoiceItems(labels, checked) { _, which, isChecked -> checked[which] = isChecked }
            .setPositiveButton("Clear") { _, _ -> clearData(checked) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun clearData(c: BooleanArray) {
        val existing = tabs.tabs.firstNotNullOfOrNull { it.webView }
        val temp = if (c[1] && existing == null) WebView(this) else null
        xc.privacy.clearBrowsingData(
            PrivacyManager.ClearOptions(
                cookies = c[0], cache = c[1], siteData = c[2], history = c[3],
                recentSearches = c[4], savedLogins = c[5]
            ),
            existing ?: temp
        )
        temp?.destroy()
        if (c[3]) xc.secure.remove(SESSION_KEY)
        if (c[6]) xc.sitePermissions.clearAll()
        if (c[7]) xc.downloads.clear()
        homeShownForTab = -1L
        render()
        toast("Browsing data cleared")
    }

    override fun endPrivateSessionNow() {
        val closed = tabs.closePrivateTabs()
        closed.forEach { destroyWebView(it) }
        if (tabs.count == 0) tabs.newTab(isPrivate = false)
        render()
        // Give WebView a moment to release the private profile before deleting it.
        handler.postDelayed({
            val message = xc.privacy.endPrivateSession()
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.end_private_session)
                .setMessage(message)
                .setPositiveButton(android.R.string.ok, null)
                .show()
        }, 600)
    }

    // ---------------------------------------------------------------------------------------
    // Small helpers
    // ---------------------------------------------------------------------------------------

    private fun showKeyboard(target: View = b.searchField) {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        target.post { imm.showSoftInput(target, InputMethodManager.SHOW_IMPLICIT) }
    }

    private fun hideKeyboard() {
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(b.rootLayout.windowToken, 0)
    }

    /** Short confirmation or error message. A Snackbar above the bottom bar, or a Toast in full screen. */
    private fun toast(message: String) {
        if (fullscreenView != null || isFinishing || isDestroyed) {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
            return
        }
        val bar = Snackbar.make(b.rootLayout, message, Snackbar.LENGTH_SHORT)
        if (b.bottomNav.visibility == View.VISIBLE && b.mainColumn.visibility == View.VISIBLE) bar.setAnchorView(b.bottomNav)
        bar.show()
    }

    private companion object {
        const val SESSION_KEY = "session"
        const val MAX_SOURCE_CHARS = 60_000
    }
}
