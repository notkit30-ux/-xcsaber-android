package com.xcsaber.app.browser

import android.graphics.Bitmap
import android.net.Uri
import android.os.Message
import android.view.View
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView
import com.xcsaber.app.data.Prefs

/** One instance per tab: progress, titles, popups, full-screen video, permissions and file upload. */
class XcChromeClient(
    private val tab: Tab,
    private val host: BrowserHost,
    private val prefs: Prefs
) : WebChromeClient() {

    override fun onProgressChanged(view: WebView, newProgress: Int) {
        tab.progress = newProgress
        host.onProgress(tab, newProgress)
    }

    override fun onReceivedTitle(view: WebView, title: String?) {
        if (!title.isNullOrBlank()) {
            tab.title = title
            host.onTitle(tab, title)
        }
    }

    override fun onReceivedIcon(view: WebView, icon: Bitmap?) {
        tab.favicon = icon
        host.onFavicon(tab)
    }

    override fun onShowCustomView(view: View, callback: WebChromeClient.CustomViewCallback) = host.showCustomView(view, callback)

    override fun onHideCustomView() = host.hideCustomView()

    /** A 1x1 transparent poster avoids the grey default play-button image. */
    override fun getDefaultVideoPoster(): Bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)

    override fun onCreateWindow(
        view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: Message
    ): Boolean {
        if (prefs.blockPopups && !isUserGesture) return false
        val transport = resultMsg.obj as? WebView.WebViewTransport ?: return false
        val popup = host.openPopup(tab)
        transport.webView = popup.webView
        resultMsg.sendToTarget()
        return true
    }

    override fun onCloseWindow(window: WebView) = host.closeTab(tab)

    override fun onPermissionRequest(request: PermissionRequest) {
        val needed = ArrayList<SitePermission>()
        val grantable = ArrayList<String>()
        for (r in request.resources) {
            when (r) {
                PermissionRequest.RESOURCE_VIDEO_CAPTURE -> { needed.add(SitePermission.CAMERA); grantable.add(r) }
                PermissionRequest.RESOURCE_AUDIO_CAPTURE -> { needed.add(SitePermission.MICROPHONE); grantable.add(r) }
                // DRM identifiers, MIDI and anything else are always refused.
            }
        }
        if (needed.isEmpty()) {
            request.deny()
            return
        }
        host.requestSitePermission(tab, request.origin.toString(), needed) { allowed ->
            try {
                if (allowed) request.grant(grantable.toTypedArray()) else request.deny()
            } catch (e: IllegalStateException) {
                // The page cancelled the request while the dialog was open.
            }
        }
    }

    override fun onGeolocationPermissionsShowPrompt(origin: String, callback: GeolocationPermissions.Callback) {
        host.requestSitePermission(tab, origin, listOf(SitePermission.LOCATION)) { allowed ->
            callback.invoke(origin, allowed, false)
        }
    }

    override fun onShowFileChooser(
        webView: WebView,
        filePathCallback: ValueCallback<Array<Uri>>,
        fileChooserParams: WebChromeClient.FileChooserParams
    ): Boolean = host.chooseFile(fileChooserParams, filePathCallback)
}
