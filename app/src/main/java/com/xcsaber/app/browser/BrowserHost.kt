package com.xcsaber.app.browser

import android.net.Uri
import android.view.View
import android.webkit.ValueCallback
import android.webkit.WebChromeClient

/** Everything the WebView clients need from the activity. Implemented by MainActivity. */
interface BrowserHost {
    fun isOnline(): Boolean
    fun onPageStarted(tab: Tab, url: String)
    fun onPageFinished(tab: Tab, url: String)
    /** The new page is first visible (earlier than onPageFinished); used to apply add-ons early. */
    fun onPageCommitVisible(tab: Tab, url: String) {}
    fun onUrlChanged(tab: Tab, url: String)
    fun onProgress(tab: Tab, progress: Int)
    fun onTitle(tab: Tab, title: String)
    fun onFavicon(tab: Tab)
    fun onError(tab: Tab, error: PageError)
    fun onRenderGone(tab: Tab)

    /** Hands an already-validated mailto:/tel:/sms:/geo: link to another app (after asking the user). */
    fun openExternal(url: String)

    /** Creates a tab with a fresh WebView for window.open()/target=_blank, linked to [parent]. */
    fun openPopup(parent: Tab): Tab
    fun closeTab(tab: Tab)

    /** Asks the user (or applies a remembered choice) and reports the result. */
    fun requestSitePermission(
        tab: Tab,
        origin: String,
        needed: List<SitePermission>,
        result: (Boolean) -> Unit
    )

    fun showCustomView(view: View, callback: WebChromeClient.CustomViewCallback)
    fun hideCustomView()
    fun chooseFile(params: WebChromeClient.FileChooserParams, callback: ValueCallback<Array<Uri>>): Boolean
}
