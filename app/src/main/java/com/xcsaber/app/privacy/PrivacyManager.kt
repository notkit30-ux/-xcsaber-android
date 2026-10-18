package com.xcsaber.app.privacy

import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView
import android.webkit.WebViewDatabase
import androidx.webkit.ProfileStore
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.xcsaber.app.data.BookmarkStore
import com.xcsaber.app.data.HistoryStore
import com.xcsaber.app.data.Prefs
import com.xcsaber.app.data.RecentSearchStore

/**
 * Central place for privacy actions.
 *
 * Private tabs: Android WebView normally has ONE cookie jar and cache per app. Newer WebView
 * versions support separate "profiles"; where [privateProfilesSupported] is true, private tabs
 * use their own profile (separate cookies, storage and cache) which is deleted when the private
 * session ends. Where it is false, private tabs still never touch history, recent searches or
 * bookmarks-by-default, but WebView cannot isolate their cookies, and the UI says so. Nothing
 * here claims anonymity: your IP address is visible to the sites you visit.
 */
class PrivacyManager(
    private val context: Context,
    private val prefs: Prefs,
    private val history: HistoryStore,
    private val recentSearches: RecentSearchStore,
    @Suppress("unused") private val bookmarks: BookmarkStore
) {

    val privateProfilesSupported: Boolean
        get() = try {
            WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)
        } catch (e: Exception) {
            false
        }

    /** Must be called right after creating a WebView, before it loads anything. */
    fun assignProfile(webView: WebView, isPrivate: Boolean) {
        if (isPrivate && privateProfilesSupported) {
            try {
                ProfileStore.getInstance().getOrCreateProfile(PRIVATE_PROFILE)
                WebViewCompat.setProfile(webView, PRIVATE_PROFILE)
            } catch (e: Exception) {
                // Leave on the default profile; the UI warning about shared cookies still applies.
            }
        }
    }

    /** Applies cookie settings to a WebView according to the user's preferences. */
    fun applyCookiePolicy(webView: WebView) {
        val cm = CookieManager.getInstance()
        cm.setAcceptCookie(true)
        cm.setAcceptThirdPartyCookies(webView, !prefs.effectiveBlockThirdPartyCookies)
    }

    /**
     * Ends the private session. Call after every private WebView has been destroyed.
     * Returns a short human-readable statement of what was and was not cleared.
     */
    fun endPrivateSession(): String {
        if (privateProfilesSupported) {
            return try {
                ProfileStore.getInstance().deleteProfile(PRIVATE_PROFILE)
                "Private session ended. Its cookies, cache and site data were deleted."
            } catch (e: Exception) {
                "Private tabs closed. Some private data could not be deleted yet; use Clear browsing data."
            }
        }
        return "Private tabs closed. This device's WebView cannot isolate private cookies, " +
            "so use Clear browsing data to remove them."
    }

    data class ClearOptions(
        val cookies: Boolean = true,
        val cache: Boolean = true,
        val siteData: Boolean = true,
        val history: Boolean = true,
        val recentSearches: Boolean = true,
        val savedLogins: Boolean = true
    )

    /** [anyWebView] is used only to clear the shared WebView cache; may be null if no tab is open. */
    fun clearBrowsingData(options: ClearOptions, anyWebView: WebView?) {
        if (options.cookies) {
            val cm = CookieManager.getInstance()
            cm.removeAllCookies(null)
            cm.flush()
        }
        if (options.cache) anyWebView?.clearCache(true)
        if (options.siteData) WebStorage.getInstance().deleteAllData()
        if (options.savedLogins) {
            @Suppress("DEPRECATION")
            WebViewDatabase.getInstance(context).clearHttpAuthUsernamePassword()
            @Suppress("DEPRECATION")
            anyWebView?.clearFormData()
        }
        if (options.history) history.clear()
        if (options.recentSearches) recentSearches.clear()
    }

    companion object {
        const val PRIVATE_PROFILE = "xcsaber_private"
    }
}
