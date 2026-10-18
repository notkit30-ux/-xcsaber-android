package com.xcsaber.app.browser

import android.graphics.Bitmap
import android.webkit.WebView
import java.util.concurrent.atomic.AtomicInteger

/** What the error screen should say for a tab. */
enum class ErrorKind { OFFLINE, NOT_FOUND, UNREACHABLE, SECURITY, OTHER }

data class PageError(val title: String, val message: String, val url: String, val kind: ErrorKind = ErrorKind.OTHER)

/**
 * One browser tab. A tab with an empty [url] shows the XCSABER home screen. The [webView] is
 * created lazily, so restored tabs cost nothing until they are opened.
 */
class Tab(val id: Long, val isPrivate: Boolean, val parentId: Long? = null) {
    var webView: WebView? = null
    @Volatile
    var url: String = ""
    var title: String = ""
    var progress: Int = 0
    var loading: Boolean = false
    var desktop: Boolean = false
    var thumbnail: Bitmap? = null
    /** Small site icon reported by WebView; kept only in memory. */
    var favicon: Bitmap? = null
    var error: PageError? = null
    /** True while the home screen is shown on top of an open page (the page stays alive underneath). */
    var showHome: Boolean = false
    /** True for restored tabs whose page has not been loaded yet. */
    var needsLoad: Boolean = false
    val blockedCount = AtomicInteger(0)

    val isBlank: Boolean get() = url.isBlank()

    fun displayTitle(): String = when {
        title.isNotBlank() -> title
        url.isNotBlank() -> url
        else -> "New tab"
    }
}
