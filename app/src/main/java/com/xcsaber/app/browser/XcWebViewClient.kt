package com.xcsaber.app.browser

import android.graphics.Bitmap
import android.net.http.SslError
import android.webkit.RenderProcessGoneDetail
import android.webkit.SslErrorHandler
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.xcsaber.app.data.Prefs
import com.xcsaber.app.privacy.BlockStats
import com.xcsaber.app.privacy.TrackerBlocker
import com.xcsaber.app.search.UrlUtils
import com.xcsaber.app.userscripts.UserScriptParser
import java.io.ByteArrayInputStream

/**
 * One instance per tab. Validates every navigation, blocks trackers on sub-resources, and turns
 * network and certificate failures into the in-app error screen. Certificate errors are NEVER
 * bypassed: there is no "proceed" path anywhere in the app.
 */
class XcWebViewClient(
    private val tab: Tab,
    private val host: BrowserHost,
    private val prefs: Prefs,
    private val blocker: TrackerBlocker,
    private val stats: BlockStats
) : WebViewClient() {

    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        val url = request.url.toString()
        if (url == "about:blank") return false
        return when (UrlUtils.classifyScheme(url)) {
            UrlUtils.Kind.WEB_URL -> {
                if (request.isForMainFrame && UserScriptParser.looksLikeUserScriptUrl(url)) {
                    host.offerUserScript(url)
                    true
                } else if (prefs.effectiveHttpsOnly && url.startsWith("http://", ignoreCase = true)) {
                    view.loadUrl(UrlUtils.upgradeToHttps(url))
                    true
                } else {
                    false
                }
            }
            UrlUtils.Kind.EXTERNAL -> {
                host.openExternal(url)
                true
            }
            else -> {
                // intent:, javascript:, file:, data: and anything unknown are refused. An intent:
                // link may carry a normal web address as a fallback, which is safe to open.
                UrlUtils.fallbackFromIntentUrl(url)?.let { view.loadUrl(it) }
                true
            }
        }
    }

    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
        if (!prefs.trackerBlocking || request.isForMainFrame) return null
        // Runs on a background thread: only touch thread-safe state here.
        if (blocker.shouldBlock(request.url.host, UrlUtils.hostOf(tab.url))) {
            tab.blockedCount.incrementAndGet()
            stats.record(tab.isPrivate)
            return WebResourceResponse(
                "text/plain", "UTF-8", 403, "Blocked by XCSABER",
                emptyMap(), ByteArrayInputStream(ByteArray(0))
            )
        }
        return null
    }

    override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
        tab.url = url
        tab.loading = true
        tab.error = null
        tab.blockedCount.set(0)
        host.onPageStarted(tab, url)
    }

    override fun onPageFinished(view: WebView, url: String) {
        tab.loading = false
        tab.url = url
        if (!view.title.isNullOrBlank()) tab.title = view.title.orEmpty()
        host.onPageFinished(tab, url)
    }

    override fun onPageCommitVisible(view: WebView, url: String) {
        host.onPageCommitVisible(tab, url)
    }

    override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) {
        tab.url = url
        host.onUrlChanged(tab, url)
    }

    override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
        if (!request.isForMainFrame) return
        val url = request.url.toString()
        val offline = !host.isOnline()
        val kind = when {
            offline -> ErrorKind.OFFLINE
            error.errorCode == WebViewClient.ERROR_HOST_LOOKUP -> ErrorKind.NOT_FOUND
            error.errorCode == WebViewClient.ERROR_CONNECT || error.errorCode == WebViewClient.ERROR_TIMEOUT -> ErrorKind.UNREACHABLE
            error.errorCode == WebViewClient.ERROR_FAILED_SSL_HANDSHAKE -> ErrorKind.SECURITY
            else -> ErrorKind.OTHER
        }
        val (title, message) = when {
            offline -> "You're offline" to
                "Your device isn't connected to the internet. Check Wi-Fi or mobile data, then try again."
            error.errorCode == WebViewClient.ERROR_HOST_LOOKUP -> "Can't find that site" to
                "The address ${UrlUtils.displayHost(url)} couldn't be found. Check the spelling or search for it instead."
            error.errorCode == WebViewClient.ERROR_CONNECT || error.errorCode == WebViewClient.ERROR_TIMEOUT -> "Can't reach the site" to
                "${UrlUtils.displayHost(url)} took too long to respond or refused the connection."
            error.errorCode == WebViewClient.ERROR_FAILED_SSL_HANDSHAKE -> "Secure connection failed" to
                "XCSABER couldn't set up a secure connection to ${UrlUtils.displayHost(url)}."
            error.errorCode == WebViewClient.ERROR_UNSUPPORTED_SCHEME || error.errorCode == WebViewClient.ERROR_BAD_URL -> "Can't open that address" to
                "This kind of address isn't supported."
            else -> "Page couldn't load" to "Something went wrong (${error.description})."
        }
        tab.loading = false
        tab.error = PageError(title, message, url, kind)
        host.onError(tab, tab.error!!)
    }

    override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
        handler.cancel() // never proceed past a certificate problem
        val failing = error.url ?: return
        if (UrlUtils.hostOf(failing) != UrlUtils.hostOf(tab.url) && tab.url.isNotBlank()) return
        val reason = when (error.primaryError) {
            SslError.SSL_UNTRUSTED -> "its certificate was issued by an authority this device doesn't trust"
            SslError.SSL_EXPIRED -> "its certificate has expired"
            SslError.SSL_IDMISMATCH -> "its certificate belongs to a different website"
            SslError.SSL_NOTYETVALID -> "its certificate isn't valid yet (check your device's date and time)"
            SslError.SSL_DATE_INVALID -> "its certificate dates are invalid (check your device's date and time)"
            else -> "its certificate could not be verified"
        }
        tab.loading = false
        tab.error = PageError(
            "Your connection isn't private",
            "XCSABER blocked ${UrlUtils.displayHost(failing)} because $reason. " +
                "Attackers might be trying to steal your information. There is no option to continue anyway.",
            failing,
            ErrorKind.SECURITY
        )
        host.onError(tab, tab.error!!)
    }

    override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
        host.onRenderGone(tab)
        return true // handled: the app keeps running
    }
}
