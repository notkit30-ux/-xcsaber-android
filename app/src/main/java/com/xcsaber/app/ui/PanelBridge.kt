package com.xcsaber.app.ui

import com.xcsaber.app.plugins.PluginHost

/** What the Privacy Center, Extensions Center and Settings screens need from the activity. */
interface PanelBridge : PluginHost {
    fun closePanel()
    fun openSettingsPanel()
    fun openExtensionsPanel()
    fun showBookmarks()
    fun showHistory()
    fun showDownloads()
    fun showSitePermissions()
    fun showClearDataDialog()
    fun endPrivateSessionNow()
    fun showProviderSheet(onDone: () -> Unit)
    fun showThemeSheet()
    fun blockedOnCurrentPage(): Int
    fun hasPrivateTabs(): Boolean
}
